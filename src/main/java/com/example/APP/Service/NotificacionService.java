package com.example.APP.Service;

import com.example.APP.Model.Notificacion;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface NotificacionService {

    List<Notificacion> obtenerTodos();
    Optional<Notificacion> obtenerPorId(Long id);
    Notificacion guardar(Notificacion notificacion);
    Notificacion guardar(Notificacion notificacion, String usernameAutenticado);
    Notificacion actualizar(Long id, Notificacion notificacion);
    List<Map<String, Object>> obtenerNovedades(String search, String usernameAutenticado);
    List<Map<String, Object>> obtenerHistorialChat(String search, String usernameAutenticado);
    Notificacion enviarMensajeChat(Map<String, Object> payload, String usernameAutenticado);
    List<Map<String, Object>> listarUsuariosParaNotificaciones(String search);
    List<Notificacion> enviarNotificacionRecibo(Map<String, Object> payload);
    void eliminar(Long id);
    Map<String, Object> mapearPublicacion(Notificacion notificacion);

    Notificacion crearConMultimedia(
            String mensaje,
            String fechaEnvio,
            String usuariosEtiquetados,
            MultipartFile imagen,
            MultipartFile video,
            String usernameAutenticado
    );
    Resource obtenerImagen(Long id);
    Resource obtenerVideo(Long id);
}
