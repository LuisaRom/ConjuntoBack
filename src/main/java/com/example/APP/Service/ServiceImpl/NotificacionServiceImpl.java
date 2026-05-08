package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.HistorialNotificacion;
import com.example.APP.Model.Notificacion;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.HistorialNotificacionRepository;
import com.example.APP.Repository.NotificacionRepository;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    @Autowired
    private NotificacionRepository notificacionRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private HistorialNotificacionRepository historialNotificacionRepository;

    @Override
    public List<Notificacion> obtenerTodos() {
        archivarRecibosVencidos();
        return notificacionRepository.findAll().stream()
                .filter(n -> !esMensajeChat(n))
                .toList();
    }

    @Override
    public Optional<Notificacion> obtenerPorId(Long id) {
        return notificacionRepository.findById(id);
    }

    @Override
    public Notificacion guardar(Notificacion notificacion) {
        // Siempre cargar el usuario completo desde la base de datos usando el ID
        // Esto asegura que el usuario tenga todos los campos correctos, incluyendo el rol
        if (notificacion.getUsuario() != null && notificacion.getUsuario().getId() != null) {
            Long usuarioId = notificacion.getUsuario().getId();
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(usuarioId);
            if (usuarioOpt.isPresent()) {
                // Usar el usuario completo desde la base de datos
                Usuario usuarioCompleto = usuarioOpt.get();
                notificacion.setUsuario(usuarioCompleto);
                System.out.println("NotificacionServiceImpl: Usuario cargado desde BD - ID: " + usuarioCompleto.getId() + 
                                   ", Nombre: " + usuarioCompleto.getNombre() + 
                                   ", Rol: " + usuarioCompleto.getRol());
            } else {
                throw new RuntimeException("Usuario no encontrado con id: " + usuarioId);
            }
        } else if (notificacion.getUsuario() == null) {
            throw new RuntimeException("La notificación debe tener un usuario asignado");
        }
        
        // Si no hay fecha, asignar la fecha actual
        if (notificacion.getFechaEnvio() == null) {
            notificacion.setFechaEnvio(java.time.LocalDateTime.now());
        }
        validarMultimediaOpcional(notificacion.getImagenUrl(), "imagenUrl");
        validarMultimediaOpcional(notificacion.getVideoUrl(), "videoUrl");
        
        System.out.println("NotificacionServiceImpl: Guardando notificación - Mensaje: " + notificacion.getMensaje() + 
                           ", Usuario ID: " + (notificacion.getUsuario() != null ? notificacion.getUsuario().getId() : "null"));
        
        return notificacionRepository.save(notificacion);
    }

    @Override
    public Notificacion guardar(Notificacion notificacion, String usernameAutenticado) {
        if (usernameAutenticado == null || usernameAutenticado.isBlank()) {
            throw new IllegalArgumentException("No hay usuario autenticado");
        }
        Usuario usuarioAutenticado = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        notificacion.setUsuario(usuarioAutenticado);
        return guardar(notificacion);
    }

    @Override
    public Notificacion actualizar(Long id, Notificacion notificacion) {
        return notificacionRepository.findById(id)
                .map(existing -> {
                    validarMultimediaOpcional(notificacion.getImagenUrl(), "imagenUrl");
                    validarMultimediaOpcional(notificacion.getVideoUrl(), "videoUrl");
                    existing.setMensaje(notificacion.getMensaje());
                    existing.setFechaEnvio(notificacion.getFechaEnvio() != null ? notificacion.getFechaEnvio() : existing.getFechaEnvio());
                    existing.setImagenUrl(notificacion.getImagenUrl());
                    existing.setVideoUrl(notificacion.getVideoUrl());
                    existing.setUsuariosEtiquetados(notificacion.getUsuariosEtiquetados());
                    existing.setUsuario(notificacion.getUsuario());
                    return notificacionRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada con id: " + id));
    }

    @Override
    public List<Map<String, Object>> obtenerNovedades(String search, String usernameAutenticado) {
        String filtro = search != null ? search.trim().toLowerCase() : "";
        Usuario usuarioAutenticado = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        return notificacionRepository.findAll().stream()
                .filter(n -> !esMensajeChat(n))
                .filter(n -> usuarioAutenticado.getRol() != Usuario.Rol.RESIDENTE
                        || (n.getUsuario() != null && n.getUsuario().getId() != null
                        && n.getUsuario().getId().equals(usuarioAutenticado.getId())))
                .filter(n -> filtro.isBlank() || contieneFiltroNotificacion(n, filtro))
                .sorted(Comparator.comparing(Notificacion::getFechaEnvio, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .map(this::mapearNotificacionDetalle)
                .toList();
    }

    @Override
    public List<Map<String, Object>> obtenerHistorialChat(String search, String usernameAutenticado) {
        String filtro = search != null ? search.trim().toLowerCase() : "";
        Usuario usuarioAutenticado = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        return notificacionRepository.findAll().stream()
                .filter(this::esMensajeChat)
                .filter(n -> participaEnChat(n, usuarioAutenticado.getId()))
                .filter(n -> filtro.isBlank() || contieneFiltroNotificacion(n, filtro))
                .sorted(Comparator.comparing(Notificacion::getFechaEnvio, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .map(this::mapearNotificacionDetalle)
                .toList();
    }

    @Override
    public Notificacion enviarMensajeChat(Map<String, Object> payload, String usernameAutenticado) {
        if (usernameAutenticado == null || usernameAutenticado.isBlank()) {
            throw new IllegalArgumentException("No hay usuario autenticado");
        }
        Usuario remitente = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        if (!puedeUsarChat(remitente)) {
            throw new IllegalArgumentException("No tienes permisos para enviar mensajes de chat");
        }

        String mensaje = extraerTexto(payload, "mensaje");
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("El campo mensaje es obligatorio");
        }

        Object destinatarioIdObj = payload.get("destinatarioId");
        if (destinatarioIdObj == null || destinatarioIdObj.toString().isBlank()) {
            throw new IllegalArgumentException("El campo destinatarioId es obligatorio");
        }

        Long destinatarioId;
        try {
            destinatarioId = Long.parseLong(destinatarioIdObj.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("destinatarioId inválido");
        }
        Usuario destinatario = usuarioRepository.findById(destinatarioId)
                .orElseThrow(() -> new IllegalArgumentException("Destinatario no encontrado"));
        if (!puedeUsarChat(destinatario)) {
            throw new IllegalArgumentException("El destinatario no puede recibir mensajes de chat");
        }
        if (remitente.getRol() == Usuario.Rol.RESIDENTE
                && destinatario.getRol() != Usuario.Rol.ADMINISTRADOR
                && destinatario.getRol() != Usuario.Rol.CELADOR) {
            throw new IllegalArgumentException("Los residentes solo pueden escribir a ADMINISTRADOR o CELADOR");
        }

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(destinatario);
        notificacion.setMensaje(construirPayloadChat(remitente.getId(), destinatario.getId(), mensaje.trim()));
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacion.setUsuariosEtiquetados(destinatarioId.toString());
        return notificacionRepository.save(notificacion);
    }

    @Override
    public List<Map<String, Object>> listarUsuariosParaNotificaciones(String search) {
        String filtro = search != null ? search.trim().toLowerCase() : "";
        return usuarioRepository.findAll().stream()
                .filter(usuario -> filtro.isBlank()
                        || (usuario.getNombre() != null && usuario.getNombre().toLowerCase().contains(filtro))
                        || (usuario.getUsuario() != null && usuario.getUsuario().toLowerCase().contains(filtro)))
                .sorted(Comparator
                        .comparing(Usuario::getTorre, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Usuario::getApartamento, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Usuario::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::mapearUsuarioResumen)
                .toList();
    }

    @Override
    public List<Notificacion> enviarNotificacionRecibo(Map<String, Object> payload) {
        String mensaje = extraerTexto(payload, "mensaje");
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("El campo mensaje es obligatorio");
        }
        String tipoEnvio = extraerTexto(payload, "tipoEnvio");
        if (tipoEnvio == null || tipoEnvio.isBlank()) {
            tipoEnvio = "todos";
        }
        List<Usuario> destinatarios = resolverDestinatariosRecibo(tipoEnvio, payload.get("usuarioId"));
        String imagenUrl = extraerTexto(payload, "imagenUrl");
        String videoUrl = extraerTexto(payload, "videoUrl");
        validarMultimediaOpcional(imagenUrl, "imagenUrl");
        validarMultimediaOpcional(videoUrl, "videoUrl");

        List<Notificacion> creadas = new ArrayList<>();
        for (Usuario usuario : destinatarios) {
            Notificacion notificacion = new Notificacion();
            notificacion.setUsuario(usuario);
            notificacion.setMensaje(mensaje.trim());
            notificacion.setFechaEnvio(LocalDateTime.now());
            notificacion.setImagenUrl(imagenUrl);
            notificacion.setVideoUrl(videoUrl);
            creadas.add(notificacionRepository.save(notificacion));
        }
        return creadas;
    }

    @Override
    public void eliminar(Long id) {
        notificacionRepository.deleteById(id);
    }
    
    private void archivarRecibosVencidos() {
        List<Notificacion> todas = notificacionRepository.findAll();
        LocalDateTime limite = LocalDateTime.now().minusDays(20);
        List<Notificacion> paraEliminar = new ArrayList<>();
        
        for (Notificacion n : todas) {
            if (!esNotificacionRecibo(n)) {
                continue;
            }
            if (n.getFechaEnvio() == null || !n.getFechaEnvio().isBefore(limite)) {
                continue;
            }
            
            boolean yaArchivada = historialNotificacionRepository.findByNotificacionOriginalId(n.getId()).isPresent();
            if (!yaArchivada) {
                HistorialNotificacion h = new HistorialNotificacion();
                h.setNotificacionOriginalId(n.getId());
                h.setMensaje(n.getMensaje());
                h.setFechaEnvio(n.getFechaEnvio());
                h.setImagenUrl(n.getImagenUrl());
                h.setVideoUrl(n.getVideoUrl());
                h.setUsuariosEtiquetados(n.getUsuariosEtiquetados());
                h.setUsuarioId(n.getUsuario() != null ? n.getUsuario().getId() : null);
                h.setFechaArchivado(LocalDateTime.now());
                historialNotificacionRepository.save(h);
            }
            paraEliminar.add(n);
        }
        
        if (!paraEliminar.isEmpty()) {
            notificacionRepository.deleteAll(paraEliminar);
        }
    }
    
    private boolean esNotificacionRecibo(Notificacion n) {
        if (n == null || n.getMensaje() == null) {
            return false;
        }
        String mensaje = n.getMensaje().toLowerCase();
        boolean tieneRecibo = mensaje.contains("recibo");
        boolean tieneTipo = mensaje.contains("enel") || mensaje.contains("vanti") || mensaje.contains("epz");
        return tieneRecibo && tieneTipo;
    }

    private boolean esMensajeChat(Notificacion notificacion) {
        if (notificacion == null) {
            return false;
        }
        String mensaje = notificacion.getMensaje() != null ? notificacion.getMensaje().trim().toLowerCase() : "";
        return mensaje.startsWith("[chat]") || mensaje.startsWith("chat|");
    }

    private boolean puedeUsarChat(Usuario usuario) {
        return usuario != null
                && (usuario.getRol() == Usuario.Rol.ADMINISTRADOR
                || usuario.getRol() == Usuario.Rol.CELADOR
                || usuario.getRol() == Usuario.Rol.RESIDENTE);
    }

    private boolean participaEnChat(Notificacion notificacion, Long usuarioId) {
        if (usuarioId == null) {
            return false;
        }
        Long emisorId = extraerParticipanteChat(notificacion, "from");
        Long receptorId = extraerParticipanteChat(notificacion, "to");
        return usuarioId.equals(emisorId) || usuarioId.equals(receptorId);
    }

    private Long extraerParticipanteChat(Notificacion notificacion, String campo) {
        if (notificacion == null || notificacion.getMensaje() == null) {
            return null;
        }
        String mensaje = notificacion.getMensaje().trim();
        if (mensaje.toLowerCase().startsWith("chat|")) {
            String prefijo = campo + "=";
            String[] partes = mensaje.split("\\|");
            for (String parte : partes) {
                if (parte.startsWith(prefijo)) {
                    try {
                        return Long.parseLong(parte.substring(prefijo.length()));
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                }
            }
        }
        if ("from".equals(campo) && notificacion.getUsuario() != null) {
            return notificacion.getUsuario().getId();
        }
        if ("to".equals(campo) && notificacion.getUsuariosEtiquetados() != null) {
            try {
                return Long.parseLong(notificacion.getUsuariosEtiquetados().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String construirPayloadChat(Long emisorId, Long receptorId, String mensaje) {
        String textoSeguro = mensaje.replace("|", "/");
        return "CHAT|from=" + emisorId + "|to=" + receptorId + "|msg=" + textoSeguro;
    }

    private boolean contieneFiltroNotificacion(Notificacion notificacion, String filtro) {
        String mensaje = notificacion.getMensaje() != null ? notificacion.getMensaje().toLowerCase() : "";
        String nombre = notificacion.getUsuario() != null && notificacion.getUsuario().getNombre() != null
                ? notificacion.getUsuario().getNombre().toLowerCase() : "";
        return mensaje.contains(filtro) || nombre.contains(filtro);
    }

    private Map<String, Object> mapearNotificacionDetalle(Notificacion notificacion) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", notificacion.getId());
        item.put("mensaje", notificacion.getMensaje());
        item.put("fechaEnvio", notificacion.getFechaEnvio());
        item.put("imagenUrl", notificacion.getImagenUrl());
        item.put("videoUrl", notificacion.getVideoUrl());
        item.put("usuariosEtiquetados", notificacion.getUsuariosEtiquetados());
        item.put("usuario", mapearUsuarioResumen(notificacion.getUsuario()));
        return item;
    }

    private List<Usuario> resolverDestinatariosRecibo(String tipoEnvio, Object usuarioIdObj) {
        String tipo = tipoEnvio.trim().toLowerCase();
        if ("todos".equals(tipo)) {
            return usuarioRepository.findByRolOrderByNombreAsc(Usuario.Rol.RESIDENTE);
        }
        if (!"individual".equals(tipo)) {
            throw new IllegalArgumentException("tipoEnvio inválido. Usa 'todos' o 'individual'");
        }
        if (usuarioIdObj == null || usuarioIdObj.toString().isBlank()) {
            throw new IllegalArgumentException("usuarioId es obligatorio para tipoEnvio individual");
        }
        Long usuarioId;
        try {
            usuarioId = Long.parseLong(usuarioIdObj.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("usuarioId inválido");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (usuario.getRol() != Usuario.Rol.RESIDENTE) {
            throw new IllegalArgumentException("El usuario destino debe ser RESIDENTE");
        }
        return List.of(usuario);
    }

    private Map<String, Object> mapearUsuarioResumen(Usuario usuario) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", usuario.getId());
        item.put("nombre", usuario.getNombre());
        item.put("username", usuario.getUsuario());
        item.put("rol", usuario.getRol());
        item.put("torre", usuario.getTorre());
        item.put("apartamento", usuario.getApartamento());
        return item;
    }

    private String extraerTexto(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    private void validarMultimediaOpcional(String url, String campo) {
        if (url == null || url.isBlank()) {
            return;
        }
        String valor = url.trim();
        if (!(valor.startsWith("http://") || valor.startsWith("https://"))) {
            throw new IllegalArgumentException(campo + " inválida: debe ser URL pública http(s)");
        }
        try {
            URI uri = URI.create(valor);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException(campo + " inválida: host no válido");
            }
            String host = uri.getHost().toLowerCase();
            if ("localhost".equals(host) || host.startsWith("127.") || "0.0.0.0".equals(host)) {
                throw new IllegalArgumentException(campo + " inválida: no se permiten URLs locales");
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("inválida")) {
                throw ex;
            }
            throw new IllegalArgumentException(campo + " inválida: formato no reconocido");
        }
    }
}
