package com.example.APP.Service;

import com.example.APP.Model.VehiculoResidente;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface VehiculoResidenteService {

    List<VehiculoResidente> obtenerTodos();
    Optional<VehiculoResidente> obtenerPorId(Long id);
    VehiculoResidente guardar(VehiculoResidente vehiculoResidente);
    void eliminar(Long id);
    List<VehiculoResidente> listarDeUsuario(String username);
    VehiculoResidente registrarParaUsuario(String username, Map<String, Object> datos);
    void eliminarDeUsuario(String username, Long id);
    VehiculoResidente asignarParqueadero(Long id, String parqueadero);
    Map<String, Object> resumen();
}
