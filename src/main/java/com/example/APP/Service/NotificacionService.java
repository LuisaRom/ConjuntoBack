package com.example.APP.Service;

import com.example.APP.Model.Notificacion;
import com.example.APP.Model.Usuario;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface NotificacionService {

    List<Notificacion> obtenerTodos();
    Optional<Notificacion> obtenerPorId(Long id);
    Notificacion guardar(Notificacion notificacion);
    Notificacion actualizar(Long id, Notificacion notificacion);
    List<Map<String, Object>> listarUsuariosParaNotificaciones(String search);
    List<Notificacion> enviarNotificacionRecibo(Map<String, Object> payload);
    void eliminar(Long id);
}
