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
        return notificacionRepository.findAll();
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
        
        System.out.println("NotificacionServiceImpl: Guardando notificación - Mensaje: " + notificacion.getMensaje() + 
                           ", Usuario ID: " + (notificacion.getUsuario() != null ? notificacion.getUsuario().getId() : "null"));
        
        return notificacionRepository.save(notificacion);
    }

    @Override
    public Notificacion actualizar(Long id, Notificacion notificacion) {
        return notificacionRepository.findById(id)
                .map(existing -> {
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

        Object usuarioIdObj = payload.get("usuarioId");
        List<Usuario> destinatarios;
        if (usuarioIdObj == null || usuarioIdObj.toString().isBlank() || "todos".equalsIgnoreCase(usuarioIdObj.toString())) {
            destinatarios = usuarioRepository.findAll();
        } else {
            Long usuarioId;
            try {
                usuarioId = Long.parseLong(usuarioIdObj.toString());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("usuarioId inválido");
            }
            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
            destinatarios = List.of(usuario);
        }

        List<Notificacion> creadas = new ArrayList<>();
        for (Usuario usuario : destinatarios) {
            Notificacion notificacion = new Notificacion();
            notificacion.setUsuario(usuario);
            notificacion.setMensaje(mensaje.trim());
            notificacion.setFechaEnvio(LocalDateTime.now());
            notificacion.setImagenUrl(extraerTexto(payload, "imagenUrl"));
            notificacion.setVideoUrl(extraerTexto(payload, "videoUrl"));
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
}
