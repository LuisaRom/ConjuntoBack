package com.example.APP.Service;

import com.example.APP.Model.ReservaZonaComun;

import java.util.List;
import java.util.Optional;

public interface ReservaZonaComunService {

    List<ReservaZonaComun> obtenerTodos();
    Optional<ReservaZonaComun> obtenerPorId(Long id);
    ReservaZonaComun guardar(ReservaZonaComun reservaZonaComun);
    ReservaZonaComun crearReserva(ReservaZonaComun reservaZonaComun);
    void eliminar(Long id);
}
