package com.example.APP.Service;

import com.example.APP.Model.PagoAdministracion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PagoAdministracionService {

    List<PagoAdministracion> obtenerTodos();
    Optional<PagoAdministracion> obtenerPorId(Long id);
    PagoAdministracion guardar(PagoAdministracion pagoAdministracion);
    Map<String, Object> crearCheckoutAdministracion(Map<String, Object> payload, String usernameAutenticado);
    Map<String, Object> procesarNotificacionMercadoPago(Map<String, Object> payload, String topic, String paymentIdQuery);
    Map<String, Object> confirmarPagoPorReferencia(String referenciaExterna, String usernameAutenticado);
    List<Map<String, Object>> listarPagosResidente(String usernameAutenticado);
    List<Map<String, Object>> listarPagosAdmin();
    void eliminar(Long id);
}
