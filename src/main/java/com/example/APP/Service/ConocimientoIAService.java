package com.example.APP.Service;

import com.example.APP.IA.TextoIA;
import com.example.APP.Model.ConocimientoIA;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.ConocimientoIARepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ConocimientoIAService {

    private static final Logger log = LoggerFactory.getLogger(ConocimientoIAService.class);

    private final ConocimientoIARepository repository;

    public ConocimientoIAService(ConocimientoIARepository repository) {
        this.repository = repository;
    }

    public List<ConocimientoIA> buscarRelevantes(String consulta, int limite) {
        Set<String> tokensConsulta = TextoIA.tokens(consulta);
        if (tokensConsulta.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<ConocimientoIA, Integer>> puntuados = new ArrayList<>();
        for (ConocimientoIA c : repository.findByEstado(ConocimientoIA.Estado.APROBADO)) {
            Set<String> tokensTitulo = TextoIA.tokens(c.getTitulo() + " " + Objects.toString(c.getCategoria(), ""));
            Set<String> tokensContenido = TextoIA.tokens(c.getContenido());
            int puntaje = 0;
            for (String t : tokensConsulta) {
                if (tokensTitulo.contains(t)) {
                    puntaje += 3;
                } else if (tokensContenido.contains(t)) {
                    puntaje += 1;
                }
            }
            if (puntaje > 0) {
                puntuados.add(Map.entry(c, puntaje));
            }
        }
        puntuados.sort((a, b) -> b.getValue() - a.getValue());
        return puntuados.stream().limit(limite).map(Map.Entry::getKey).toList();
    }

    public void registrarUso(List<ConocimientoIA> usados) {
        for (ConocimientoIA c : usados) {
            c.setUsos((c.getUsos() == null ? 0 : c.getUsos()) + 1);
        }
        repository.saveAll(usados);
    }

    public ConocimientoIA registrar(String titulo, String contenido, String categoria, Usuario autor,
                                    ConocimientoIA.Origen origen) {
        if (titulo == null || titulo.isBlank() || contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("Título y contenido son obligatorios");
        }
        boolean esAdmin = autor != null && autor.getRol() == Usuario.Rol.ADMINISTRADOR;
        boolean aprobadoDirecto = origen == ConocimientoIA.Origen.SISTEMA
                || (esAdmin && origen != ConocimientoIA.Origen.FEEDBACK);

        ConocimientoIA c = new ConocimientoIA();
        c.setTitulo(TextoIA.recortar(titulo.trim(), 195));
        c.setContenido(contenido.trim());
        c.setCategoria(categoria != null && !categoria.isBlank() ? categoria.trim().toUpperCase() : "GENERAL");
        c.setOrigen(origen);
        c.setEstado(aprobadoDirecto ? ConocimientoIA.Estado.APROBADO : ConocimientoIA.Estado.PENDIENTE);
        c.setCreadoPor(autor);
        c.setFechaCreacion(LocalDateTime.now());
        c.setFechaActualizacion(LocalDateTime.now());
        c.setUsos(0);
        return repository.save(c);
    }

    public List<ConocimientoIA> listar(String estado) {
        if (estado == null || estado.isBlank()) {
            return repository.findAllByOrderByFechaCreacionDesc();
        }
        return repository.findByEstadoOrderByFechaCreacionDesc(ConocimientoIA.Estado.valueOf(estado.trim().toUpperCase()));
    }

    public ConocimientoIA cambiarEstado(Long id, ConocimientoIA.Estado estado) {
        ConocimientoIA c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conocimiento no encontrado: " + id));
        c.setEstado(estado);
        c.setFechaActualizacion(LocalDateTime.now());
        return repository.save(c);
    }

    public ConocimientoIA actualizar(Long id, Map<String, Object> datos) {
        ConocimientoIA c = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conocimiento no encontrado: " + id));
        if (datos.get("titulo") != null) {
            c.setTitulo(TextoIA.recortar(datos.get("titulo").toString().trim(), 195));
        }
        if (datos.get("contenido") != null) {
            c.setContenido(datos.get("contenido").toString().trim());
        }
        if (datos.get("categoria") != null) {
            c.setCategoria(datos.get("categoria").toString().trim().toUpperCase());
        }
        c.setFechaActualizacion(LocalDateTime.now());
        return repository.save(c);
    }

    public void eliminar(Long id) {
        repository.deleteById(id);
    }

    public Map<String, Long> conteoPorEstado() {
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (ConocimientoIA.Estado e : ConocimientoIA.Estado.values()) {
            conteo.put(e.name(), 0L);
        }
        for (ConocimientoIA c : repository.findAll()) {
            if (c.getEstado() != null) {
                conteo.merge(c.getEstado().name(), 1L, Long::sum);
            }
        }
        return conteo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sembrarConocimientoBase() {
        try {
            if (repository.count() > 0) {
                return;
            }
            sembrar("Reservas de piscina", "RESERVAS",
                    "La piscina se reserva de miércoles a domingo en las franjas 8:00-12:00 o 16:00-20:00, con un máximo de 3 horas por reserva. No puede cruzarse con otra reserva existente.");
            sembrar("Reservas de gimnasio", "RESERVAS",
                    "El gimnasio se reserva de lunes a sábado en las franjas 5:00-10:00 o 15:00-20:00, con un máximo de 3 horas por reserva.");
            sembrar("Reservas del salón comunal", "RESERVAS",
                    "El salón comunal se reserva entre las 8:00 y las 22:00 con un rango mínimo de 5 horas. Servicios adicionales: sillas (25 por $32.000, 50 por $60.000, 100 por $125.000), mesas (6 puestos $8.000, 10 puestos $18.000) y aseo del salón ($150.000).");
            sembrar("Reservas de zona BBQ", "RESERVAS",
                    "La zona BBQ se reserva de jueves a domingo entre las 10:00 y las 22:00. Cada residente puede tener solo 1 reserva de BBQ por día. Admite sillas y mesas como servicios adicionales.");
            sembrar("Pago de administración", "PAGOS",
                    "La cuota de administración se paga por periodo mensual (formato año-mes). Se puede pagar en línea por PSE a través de Mercado Pago desde la sección Pagos de la app, o en efectivo en administración. Solo puede existir un pago aprobado por periodo.");
            sembrar("Cómo registrar una queja", "QUEJAS",
                    "Los residentes registran quejas desde la sección Quejas indicando tipo, descripción, torre y apartamento involucrados y fecha. La queja pasa por los estados PENDIENTE, EN_PROCESO y RESUELTO; al resolverse el residente recibe una notificación.");
            sembrar("Paquetería", "PAQUETERIA",
                    "La portería registra los paquetes recibidos con la transportadora y la fecha. El residente puede ver en la app sus paquetes en estado PENDIENTE o ENTREGADO.");
            sembrar("Accesos de visitantes", "ACCESOS",
                    "Los residentes autorizan visitantes peatonales o vehiculares desde la sección Accesos, lo que genera un código QR. El celador valida ese código al ingreso y registra la hora de entrada y salida.");
            log.info("Conocimiento base de IA sembrado");
        } catch (Exception e) {
            log.warn("No se pudo sembrar el conocimiento base de IA: {}", e.getMessage());
        }
    }

    private void sembrar(String titulo, String categoria, String contenido) {
        registrar(titulo, contenido, categoria, null, ConocimientoIA.Origen.SISTEMA);
    }
}
