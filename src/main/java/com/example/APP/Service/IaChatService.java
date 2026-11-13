package com.example.APP.Service;

import com.example.APP.IA.HerramientasIA;
import com.example.APP.IA.LlmClient;
import com.example.APP.IA.LlmClient.MensajeLlm;
import com.example.APP.IA.TextoIA;
import com.example.APP.Model.ConocimientoIA;
import com.example.APP.Model.ConversacionIA;
import com.example.APP.Model.MensajeIA;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.ConversacionIARepository;
import com.example.APP.Repository.MensajeIARepository;
import com.example.APP.Repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IaChatService {

    private static final Logger log = LoggerFactory.getLogger(IaChatService.class);
    private static final int MAX_CARACTERES = 1500;
    private static final Set<String> HERRAMIENTAS_PERSONALES = Set.of(
            "consultar_quejas", "consultar_paqueteria", "consultar_reservas", "consultar_accesos",
            "consultar_mascotas", "consultar_pagos", "resumen_conjunto", "buscar_residente", "residentes_en_mora");

    private final LlmClient llm;
    private final HerramientasIA herramientas;
    private final ConocimientoIAService conocimientoService;
    private final ConversacionIARepository conversacionRepository;
    private final MensajeIARepository mensajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final Map<Long, Deque<Instant>> usoPorUsuario = new ConcurrentHashMap<>();

    @Value("${ia.nombre-asistente:Asistente del Conjunto}")
    private String nombreAsistente;

    @Value("${ia.nombre-conjunto:el conjunto residencial}")
    private String nombreConjunto;

    @Value("${ia.limite-mensajes-hora:40}")
    private int limiteMensajesHora;

    public IaChatService(LlmClient llm, HerramientasIA herramientas, ConocimientoIAService conocimientoService,
                         ConversacionIARepository conversacionRepository, MensajeIARepository mensajeRepository,
                         UsuarioRepository usuarioRepository) {
        this.llm = llm;
        this.herramientas = herramientas;
        this.conocimientoService = conocimientoService;
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public Map<String, Object> chat(String username, Long conversacionId, String texto) {
        Usuario usuario = usuario(username);
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El mensaje es obligatorio");
        }
        String mensaje = texto.trim();
        if (mensaje.length() > MAX_CARACTERES) {
            throw new IllegalArgumentException("El mensaje supera los " + MAX_CARACTERES + " caracteres");
        }
        validarLimite(usuario.getId());

        ConversacionIA conversacion = conversacionId != null
                ? conversacionRepository.findByIdAndUsuarioId(conversacionId, usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"))
                : nuevaConversacion(usuario, mensaje);

        List<MensajeIA> previos = new ArrayList<>(mensajeRepository.findTop20ByConversacionIdOrderByIdDesc(conversacion.getId()));
        Collections.reverse(previos);
        guardarMensaje(conversacion, MensajeIA.Rol.USUARIO, mensaje, null);

        List<ConocimientoIA> relevantes = conocimientoService.buscarRelevantes(mensaje, 4);
        String respuesta;
        List<String> usadas = new ArrayList<>();

        if (!llm.disponible()) {
            respuesta = respuestaSinIA(relevantes);
        } else {
            try {
                List<MensajeLlm> historial = construirHistorial(previos, mensaje);
                LlmClient.ResultadoLlm resultado = llm.conversar(promptSistema(usuario, relevantes), historial,
                        herramientas.disponiblesPara(usuario), llamada -> herramientas.ejecutar(llamada, usuario));
                respuesta = resultado.texto() == null || resultado.texto().isBlank()
                        ? "No tengo una respuesta para eso en este momento." : resultado.texto();
                usadas = resultado.herramientasUsadas();
            } catch (Exception e) {
                log.error("Error consultando el proveedor de IA: {}", e.getMessage());
                respuesta = "El asistente inteligente no está disponible en este momento. " + respuestaSinIA(relevantes);
            }
        }
        if (!relevantes.isEmpty()) {
            conocimientoService.registrarUso(relevantes);
        }

        MensajeIA guardado = guardarMensaje(conversacion, MensajeIA.Rol.ASISTENTE, respuesta,
                usadas.isEmpty() ? null : TextoIA.recortar(String.join(",", new LinkedHashSet<>(usadas)), 490));
        conversacion.setFechaActualizacion(LocalDateTime.now());
        conversacionRepository.save(conversacion);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("conversacionId", conversacion.getId());
        r.put("mensajeId", guardado.getId());
        r.put("respuesta", respuesta);
        r.put("herramientasUsadas", new ArrayList<>(new LinkedHashSet<>(usadas)));
        r.put("iaDisponible", llm.disponible());
        return r;
    }

    public List<Map<String, Object>> conversaciones(String username) {
        Usuario usuario = usuario(username);
        return conversacionRepository.findByUsuarioIdOrderByFechaActualizacionDesc(usuario.getId()).stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("titulo", c.getTitulo());
                    m.put("fechaActualizacion", c.getFechaActualizacion());
                    return m;
                }).toList();
    }

    public List<MensajeIA> mensajes(String username, Long conversacionId) {
        Usuario usuario = usuario(username);
        conversacionRepository.findByIdAndUsuarioId(conversacionId, usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));
        return mensajeRepository.findByConversacionIdOrderByIdAsc(conversacionId);
    }

    public void eliminarConversacion(String username, Long conversacionId) {
        Usuario usuario = usuario(username);
        ConversacionIA c = conversacionRepository.findByIdAndUsuarioId(conversacionId, usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada"));
        mensajeRepository.eliminarPorConversacion(c.getId());
        conversacionRepository.delete(c);
    }

    public Map<String, Object> feedback(String username, Long mensajeId, boolean util) {
        Usuario usuario = usuario(username);
        MensajeIA respuesta = mensajeRepository.findById(mensajeId)
                .orElseThrow(() -> new IllegalArgumentException("Mensaje no encontrado"));
        if (respuesta.getConversacion() == null || respuesta.getConversacion().getUsuario() == null
                || !usuario.getId().equals(respuesta.getConversacion().getUsuario().getId())
                || respuesta.getRol() != MensajeIA.Rol.ASISTENTE) {
            throw new IllegalArgumentException("Mensaje no válido para calificar");
        }
        respuesta.setUtil(util);
        mensajeRepository.save(respuesta);

        boolean aprendido = false;
        if (util && !usoDatosPersonales(respuesta)) {
            Optional<MensajeIA> pregunta = preguntaAnterior(respuesta);
            if (pregunta.isPresent()) {
                String titulo = TextoIA.recortar(pregunta.get().getContenido(), 195);
                if (!conocimientoService.listar(null).stream().anyMatch(c -> c.getTitulo().equalsIgnoreCase(titulo))) {
                    conocimientoService.registrar(titulo, respuesta.getContenido(), "GENERAL", usuario,
                            ConocimientoIA.Origen.FEEDBACK);
                    aprendido = true;
                }
            }
        }
        return Map.of("util", util, "propuestoComoConocimiento", aprendido);
    }

    public List<Map<String, Object>> respuestasMalCalificadas() {
        return mensajeRepository.findTop30ByRolAndUtilOrderByIdDesc(MensajeIA.Rol.ASISTENTE, false).stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mensajeId", r.getId());
                    m.put("pregunta", preguntaAnterior(r).map(MensajeIA::getContenido).orElse(""));
                    m.put("respuesta", r.getContenido());
                    m.put("fecha", r.getFecha());
                    return m;
                }).toList();
    }

    public Map<String, Object> estado() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("disponible", llm.disponible());
        m.put("proveedor", llm.proveedor());
        m.put("modelo", llm.modelo());
        m.put("nombreAsistente", nombreAsistente);
        m.put("respuestasUtiles", mensajeRepository.countByUtil(true));
        m.put("respuestasNoUtiles", mensajeRepository.countByUtil(false));
        m.put("conocimiento", conocimientoService.conteoPorEstado());
        return m;
    }

    private String promptSistema(Usuario u, List<ConocimientoIA> relevantes) {
        String ahora = LocalDateTime.now(HerramientasIA.ZONA)
                .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.of("es", "CO")));
        StringBuilder sb = new StringBuilder();
        sb.append("Eres ").append(nombreAsistente).append(", el asistente virtual de ").append(nombreConjunto)
                .append(". Ayudas a residentes, celadores y administración con la gestión del conjunto.\n\n");
        sb.append("Usuario actual: ").append(u.getNombre()).append(" | Rol: ").append(u.getRol());
        if (u.getRol() == Usuario.Rol.RESIDENTE) {
            sb.append(" | Torre ").append(u.getTorre()).append(", apartamento ").append(u.getApartamento());
        }
        sb.append("\nFecha y hora actual (Colombia): ").append(ahora).append("\n\n");
        sb.append("""
                Reglas:
                - Responde siempre en español, de forma breve, clara y amable. Usa listas cortas solo si ayudan.
                - Para cualquier dato del conjunto (quejas, pagos, paquetes, reservas, accesos, mascotas, residentes) usa las herramientas; nunca inventes cifras, nombres ni fechas.
                - Para normas, horarios, tarifas o procedimientos consulta primero buscar_conocimiento. Si no hay información, dilo con honestidad y sugiere contactar a la administración.
                - Las herramientas ya limitan la información según el rol. No intentes obtener datos de otros residentes para un residente.
                - No realizas acciones como crear reservas, pagos o quejas: explica en qué sección de la app se hacen.
                - Cuando el usuario te enseñe una norma, horario, tarifa o procedimiento GENERAL del conjunto, guárdalo con registrar_conocimiento. Nunca guardes datos personales.
                - Si te piden algo fuera del ámbito del conjunto, responde brevemente y vuelve al tema de la convivencia y la gestión.
                """);
        if (u.getRol() == Usuario.Rol.ADMINISTRADOR) {
            sb.append("- Con la administración puedes analizar indicadores, detectar problemas recurrentes, priorizar quejas y proponer acciones concretas.\n");
        } else if (u.getRol() == Usuario.Rol.CELADOR) {
            sb.append("- Con el celador prioriza información operativa: paquetes pendientes, accesos del día, reservas y quejas abiertas.\n");
        }
        if (!relevantes.isEmpty()) {
            sb.append("\nConocimiento del conjunto relacionado con la pregunta:\n");
            for (ConocimientoIA c : relevantes) {
                sb.append("* ").append(c.getTitulo()).append(": ").append(TextoIA.recortar(c.getContenido(), 800)).append("\n");
            }
        }
        return sb.toString();
    }

    private List<MensajeLlm> construirHistorial(List<MensajeIA> previos, String mensajeActual) {
        List<MensajeLlm> lista = new ArrayList<>();
        for (MensajeIA m : previos) {
            if (m.getContenido() == null || m.getContenido().isBlank()) {
                continue;
            }
            agregar(lista, m.getRol() == MensajeIA.Rol.USUARIO ? "user" : "assistant", m.getContenido());
        }
        agregar(lista, "user", mensajeActual);
        while (!lista.isEmpty() && !"user".equals(lista.get(0).rol())) {
            lista.remove(0);
        }
        return lista;
    }

    private void agregar(List<MensajeLlm> lista, String rol, String contenido) {
        if (!lista.isEmpty() && lista.get(lista.size() - 1).rol().equals(rol)) {
            MensajeLlm ultimo = lista.remove(lista.size() - 1);
            lista.add(new MensajeLlm(rol, ultimo.contenido() + "\n\n" + contenido));
        } else {
            lista.add(new MensajeLlm(rol, contenido));
        }
    }

    private String respuestaSinIA(List<ConocimientoIA> relevantes) {
        if (relevantes.isEmpty()) {
            return "No encontré información sobre eso. Puedes consultar con la administración del conjunto.";
        }
        ConocimientoIA mejor = relevantes.get(0);
        return mejor.getTitulo() + ": " + mejor.getContenido();
    }

    private boolean usoDatosPersonales(MensajeIA respuesta) {
        String usadas = respuesta.getHerramientasUsadas();
        if (usadas == null || usadas.isBlank()) {
            return false;
        }
        return Arrays.stream(usadas.split(",")).map(String::trim).anyMatch(HERRAMIENTAS_PERSONALES::contains);
    }

    private Optional<MensajeIA> preguntaAnterior(MensajeIA respuesta) {
        if (respuesta.getConversacion() == null) {
            return Optional.empty();
        }
        return mensajeRepository.findByConversacionIdOrderByIdAsc(respuesta.getConversacion().getId()).stream()
                .filter(m -> m.getId() < respuesta.getId() && m.getRol() == MensajeIA.Rol.USUARIO)
                .reduce((a, b) -> b);
    }

    private ConversacionIA nuevaConversacion(Usuario usuario, String mensaje) {
        ConversacionIA c = new ConversacionIA();
        c.setUsuario(usuario);
        c.setTitulo(TextoIA.recortar(mensaje, 60));
        c.setFechaCreacion(LocalDateTime.now());
        c.setFechaActualizacion(LocalDateTime.now());
        return conversacionRepository.save(c);
    }

    private MensajeIA guardarMensaje(ConversacionIA c, MensajeIA.Rol rol, String contenido, String herramientasUsadas) {
        MensajeIA m = new MensajeIA();
        m.setConversacion(c);
        m.setRol(rol);
        m.setContenido(contenido);
        m.setHerramientasUsadas(herramientasUsadas);
        m.setFecha(LocalDateTime.now());
        return mensajeRepository.save(m);
    }

    private void validarLimite(Long usuarioId) {
        Instant haceUnaHora = Instant.now().minusSeconds(3600);
        Deque<Instant> usos = usoPorUsuario.computeIfAbsent(usuarioId, k -> new ArrayDeque<>());
        synchronized (usos) {
            while (!usos.isEmpty() && usos.peekFirst().isBefore(haceUnaHora)) {
                usos.pollFirst();
            }
            if (usos.size() >= limiteMensajesHora) {
                throw new IllegalArgumentException("Alcanzaste el límite de mensajes por hora. Intenta más tarde.");
            }
            usos.addLast(Instant.now());
        }
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByUsuario(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }
}
