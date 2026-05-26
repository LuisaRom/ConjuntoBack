package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.HistorialNotificacion;
import com.example.APP.Model.Notificacion;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.HistorialNotificacionRepository;
import com.example.APP.Repository.NotificacionRepository;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionServiceImpl.class);

    @Autowired
    private NotificacionRepository notificacionRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private HistorialNotificacionRepository historialNotificacionRepository;

    @Value("${app.backend.base-url:https://conjuntoback.onrender.com}")
    private String backendBaseUrl;

    @Value("${app.uploads.dir:}")
    private String uploadsDirConfigurado;

    @Override
    public List<Notificacion> obtenerTodos() {
        archivarRecibosVencidosSeguro();
        return notificacionRepository.findAll().stream()
                .filter(n -> !esMensajeChat(n))
                .sorted(Comparator.comparing(Notificacion::getFechaEnvio, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
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
                log.debug("Usuario cargado para notificación id={}", usuarioCompleto.getId());
            } else {
                throw new IllegalArgumentException("Usuario no encontrado con id: " + usuarioId);
            }
        } else if (notificacion.getUsuario() == null) {
            throw new IllegalArgumentException("La notificación debe tener un usuario asignado");
        }
        
        // Si no hay fecha, asignar la fecha actual
        if (notificacion.getFechaEnvio() == null) {
            notificacion.setFechaEnvio(java.time.LocalDateTime.now());
        }
        validarMultimediaOpcional(notificacion.getImagenUrl(), "imagenUrl");
        validarMultimediaOpcional(notificacion.getVideoUrl(), "videoUrl");
        
        try {
            return notificacionRepository.save(notificacion);
        } catch (DataAccessException ex) {
            String causa = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            log.error("Error SQL al guardar notificación (usuarioId={}): {}", 
                    notificacion.getUsuario() != null ? notificacion.getUsuario().getId() : null, causa, ex);
            throw new IllegalStateException(
                    "No se pudo guardar la publicación en la base de datos. Verifica columnas imagen_url, video_url y usuarios_etiquetados."
            );
        }
    }

    @Override
    public Notificacion guardar(Notificacion notificacion, String usernameAutenticado) {
        if (usernameAutenticado == null || usernameAutenticado.isBlank()) {
            throw new IllegalArgumentException("No hay usuario autenticado");
        }
        Usuario usuarioAutenticado = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        if (usuarioAutenticado.getRol() == Usuario.Rol.CELADOR) {
            if (!esNotificacionRecibo(notificacion)) {
                throw new IllegalArgumentException("El celador solo puede enviar notificaciones de recibos");
            }
            Usuario destinatario = resolverResidenteDesdeNotificacion(notificacion);
            notificacion.setUsuario(destinatario);
        } else {
            notificacion.setUsuario(usuarioAutenticado);
        }
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
                .orElseThrow(() -> new RuntimeException("Notificaci?n no encontrada con id: " + id));
    }

    @Override
    public List<Map<String, Object>> obtenerNovedades(String search, String usernameAutenticado) {
        if (usernameAutenticado != null && !usernameAutenticado.isBlank()) {
            usuarioRepository.findByUsuario(usernameAutenticado)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        }
        String filtro = search != null ? search.trim().toLowerCase() : "";
        return listarPublicacionesNovedades(filtro).stream()
                .map(this::mapearNotificacionDetalle)
                .toList();
    }

    /** Publicaciones del muro (novedades): visibles para admin, celador y residentes. */
    private List<Notificacion> listarPublicacionesNovedades(String filtroMinusculas) {
        String filtro = filtroMinusculas != null ? filtroMinusculas : "";
        return notificacionRepository.findAll().stream()
                .filter(this::esPublicacionNovedad)
                .filter(n -> filtro.isBlank() || contieneFiltroNotificacion(n, filtro))
                .sorted(Comparator.comparing(Notificacion::getFechaEnvio, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .toList();
    }

    private boolean esPublicacionNovedad(Notificacion notificacion) {
        return notificacion != null && !esMensajeChat(notificacion) && !esNotificacionRecibo(notificacion);
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
            throw new IllegalArgumentException("destinatarioId inv?lido");
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
                .filter(usuario -> usuario.getRol() == Usuario.Rol.RESIDENTE)
                .filter(usuario -> filtro.isBlank()
                        || contieneTexto(usuario.getNombre(), filtro)
                        || contieneTexto(usuario.getUsuario(), filtro)
                        || contieneTexto(usuario.getTorre(), filtro)
                        || contieneTexto(usuario.getApartamento(), filtro)
                        || contieneTexto(usuario.getTorre() + " " + usuario.getApartamento(), filtro))
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
        List<Usuario> destinatarios = resolverDestinatariosRecibo(tipoEnvio, payload);
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

    @Override
    public Notificacion crearConMultimedia(
            String mensaje,
            String fechaEnvio,
            String usuariosEtiquetados,
            MultipartFile imagen,
            MultipartFile video,
            String usernameAutenticado
    ) {
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("El campo mensaje es obligatorio");
        }

        Notificacion notificacion = new Notificacion();
        notificacion.setMensaje(mensaje.trim());
        notificacion.setFechaEnvio(parsearFechaEnvio(fechaEnvio));
        notificacion.setUsuariosEtiquetados(usuariosEtiquetados);

        Notificacion guardada = guardar(notificacion, usernameAutenticado);
        Long id = guardada.getId();
        if (id == null) {
            throw new IllegalStateException("No se pudo crear la publicación");
        }

        MultipartFile archivoImagen = imagen != null && !imagen.isEmpty() ? imagen : null;
        MultipartFile archivoVideo = video != null && !video.isEmpty() ? video : null;

        if (archivoImagen != null) {
            guardarArchivoNotificacion(id, archivoImagen, "imagen");
            guardada.setImagenUrl(construirUrlPublica(id, "imagen"));
        }
        if (archivoVideo != null) {
            guardarArchivoNotificacion(id, archivoVideo, "video");
            guardada.setVideoUrl(construirUrlPublica(id, "video"));
        }

        validarMultimediaOpcional(guardada.getImagenUrl(), "imagenUrl");
        validarMultimediaOpcional(guardada.getVideoUrl(), "videoUrl");
        return notificacionRepository.save(guardada);
    }

    @Override
    public Resource obtenerImagen(Long id) {
        return obtenerRecursoNotificacion(id, "imagen");
    }

    @Override
    public Resource obtenerVideo(Long id) {
        return obtenerRecursoNotificacion(id, "video");
    }
    
    private void archivarRecibosVencidosSeguro() {
        try {
            archivarRecibosVencidos();
        } catch (Exception ex) {
            log.warn("No se pudo archivar recibos vencidos (se omite): {}", ex.getMessage());
        }
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

    private boolean contieneTexto(String valor, String filtro) {
        return valor != null && valor.toLowerCase().contains(filtro);
    }

    @Override
    public Map<String, Object> mapearPublicacion(Notificacion notificacion) {
        return mapearNotificacionDetalle(notificacion);
    }

    private Map<String, Object> mapearNotificacionDetalle(Notificacion notificacion) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", notificacion.getId());
        item.put("mensaje", notificacion.getMensaje());
        item.put("fechaEnvio", notificacion.getFechaEnvio() != null ? notificacion.getFechaEnvio().toString() : null);
        item.put("imagenUrl", notificacion.getImagenUrl());
        item.put("videoUrl", notificacion.getVideoUrl());
        item.put("usuariosEtiquetados", notificacion.getUsuariosEtiquetados());
        item.put("usuario", mapearUsuarioResumen(notificacion.getUsuario()));
        return item;
    }

    private List<Usuario> resolverDestinatariosRecibo(String tipoEnvio, Map<String, Object> payload) {
        String tipo = tipoEnvio.trim().toLowerCase();
        if ("todos".equals(tipo)) {
            return usuarioRepository.findByRolOrderByNombreAsc(Usuario.Rol.RESIDENTE);
        }
        if ("varios".equals(tipo) || "multiple".equals(tipo) || "multiples".equals(tipo)) {
            return resolverResidentesPorIds(payload.get("usuarioIds"));
        }
        Object usuarioIdObj = payload.get("usuarioIds") != null ? payload.get("usuarioIds") : payload.get("usuarioId");
        if (!"individual".equals(tipo)) {
            throw new IllegalArgumentException("tipoEnvio inv?lido. Usa 'todos' o 'individual'");
        }
        return resolverResidentesPorIds(usuarioIdObj);
    }

    private List<Usuario> resolverResidentesPorIds(Object idsObj) {
        List<Long> ids = extraerIds(idsObj);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Debes seleccionar al menos un residente");
        }
        List<Usuario> residentes = new ArrayList<>();
        for (Long usuarioId : ids) {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + usuarioId));
            if (usuario.getRol() != Usuario.Rol.RESIDENTE) {
                throw new IllegalArgumentException("Todos los destinatarios deben tener rol RESIDENTE");
            }
            residentes.add(usuario);
        }
        return residentes.stream().distinct().toList();
    }

    private List<Long> extraerIds(Object idsObj) {
        if (idsObj == null) {
            return List.of();
        }
        if (idsObj instanceof List<?> lista) {
            List<Long> ids = new ArrayList<>();
            for (Object item : lista) {
                ids.add(parseLongId(item));
            }
            return ids;
        }
        String texto = idsObj.toString().replace("[", "").replace("]", "");
        if (texto.isBlank()) {
            return List.of();
        }
        if (texto.contains(",")) {
            List<Long> ids = new ArrayList<>();
            for (String parte : texto.split(",")) {
                ids.add(parseLongId(parte.trim()));
            }
            return ids;
        }
        return List.of(parseLongId(texto));
    }

    private Long parseLongId(Object value) {
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("ID de residente invalido: " + value);
        }
    }

    private Usuario resolverResidenteDesdeNotificacion(Notificacion notificacion) {
        if (notificacion == null || notificacion.getUsuario() == null || notificacion.getUsuario().getId() == null) {
            throw new IllegalArgumentException("Debes seleccionar un residente destinatario");
        }
        Usuario destinatario = usuarioRepository.findById(notificacion.getUsuario().getId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario destinatario no encontrado"));
        if (destinatario.getRol() != Usuario.Rol.RESIDENTE) {
            throw new IllegalArgumentException("El destinatario debe tener rol RESIDENTE");
        }
        return destinatario;
    }

    private Map<String, Object> mapearUsuarioResumen(Usuario usuario) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (usuario == null) {
            return item;
        }
        item.put("id", usuario.getId());
        item.put("nombre", usuario.getNombre());
        item.put("username", usuario.getUsuario());
        item.put("usuario", usuario.getUsuario());
        item.put("rol", usuario.getRol());
        item.put("torre", usuario.getTorre());
        item.put("apartamento", usuario.getApartamento());
        item.put("label", construirLabelResidente(usuario));
        return item;
    }

    private String construirLabelResidente(Usuario usuario) {
        String nombre = usuario.getNombre() != null && !usuario.getNombre().isBlank()
                ? usuario.getNombre()
                : usuario.getUsuario();
        String torre = usuario.getTorre() != null && !usuario.getTorre().isBlank()
                ? "Torre " + usuario.getTorre()
                : "Torre sin asignar";
        String apartamento = usuario.getApartamento() != null && !usuario.getApartamento().isBlank()
                ? "Apto " + usuario.getApartamento()
                : "Apto sin asignar";
        return nombre + " - " + torre + " - " + apartamento;
    }

    private String extraerTexto(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    private LocalDateTime parsearFechaEnvio(String fechaEnvio) {
        if (fechaEnvio == null || fechaEnvio.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(fechaEnvio.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private String construirUrlPublica(Long id, String tipo) {
        String base = backendBaseUrl != null ? backendBaseUrl.trim() : "https://conjuntoback.onrender.com";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/notificaciones/" + id + "/" + tipo;
    }

    private Path resolverDirectorioUploads(Long notificacionId) {
        String base = uploadsDirConfigurado != null ? uploadsDirConfigurado.trim() : "";
        if (base.isBlank()) {
            base = System.getenv("UPLOAD_DIR");
        }
        if (base == null || base.isBlank()) {
            base = System.getProperty("java.io.tmpdir", ".");
        }
        return Paths.get(base, "uploads", "notificaciones", notificacionId.toString())
                .toAbsolutePath()
                .normalize();
    }

    private void guardarArchivoNotificacion(Long notificacionId, MultipartFile archivo, String tipo) {
        try {
            Path carpeta = resolverDirectorioUploads(notificacionId);
            Files.createDirectories(carpeta);

            String nombreOriginal = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : tipo;
            String extension = "";
            int idx = nombreOriginal.lastIndexOf('.');
            if (idx >= 0) {
                extension = nombreOriginal.substring(idx);
            } else if ("imagen".equals(tipo)) {
                extension = ".jpg";
            } else if ("video".equals(tipo)) {
                extension = ".mp4";
            }

            Path destino = carpeta.resolve(tipo + extension);
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo de la publicación");
        }
    }

    private Resource obtenerRecursoNotificacion(Long id, String tipo) {
        notificacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notificación no encontrada"));

        Path carpeta = resolverDirectorioUploads(id);
        if (!Files.exists(carpeta)) {
            Path carpetaLegacy = Paths.get("uploads", "notificaciones", id.toString()).toAbsolutePath().normalize();
            if (Files.exists(carpetaLegacy)) {
                carpeta = carpetaLegacy;
            } else {
                throw new IllegalArgumentException("La publicación no tiene " + tipo);
            }
        }

        try {
            Path encontrado = Files.list(carpeta)
                    .filter(path -> path.getFileName().toString().startsWith(tipo))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("La publicación no tiene " + tipo));
            Resource resource = new UrlResource(encontrado.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Archivo no disponible");
            }
            return resource;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo de la publicación");
        }
    }

    private void validarMultimediaOpcional(String url, String campo) {
        if (url == null || url.isBlank()) {
            return;
        }
        String valor = url.trim();
        if (!(valor.startsWith("http://") || valor.startsWith("https://"))) {
            throw new IllegalArgumentException(campo + " inv?lida: debe ser URL p?blica http(s)");
        }
        try {
            URI uri = URI.create(valor);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException(campo + " inv?lida: host no v?lido");
            }
            String host = uri.getHost().toLowerCase();
            if ("localhost".equals(host) || host.startsWith("127.") || "0.0.0.0".equals(host)) {
                throw new IllegalArgumentException(campo + " inv?lida: no se permiten URLs locales");
            }
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("inv?lida")) {
                throw ex;
            }
            throw new IllegalArgumentException(campo + " inv?lida: formato no reconocido");
        }
    }
}
