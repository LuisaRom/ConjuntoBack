package com.example.APP.Service;

import com.example.APP.Model.ReservaZonaComun;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ReservaZonaComunService {

    List<ReservaZonaComun> obtenerTodos();
    Optional<ReservaZonaComun> obtenerPorId(Long id);
    ReservaZonaComun guardar(ReservaZonaComun reservaZonaComun);
    ReservaZonaComun crearReserva(ReservaZonaComun reservaZonaComun);
    boolean estaDisponibleSalonComunal(LocalDate fechaReserva, LocalTime horaInicio, LocalTime horaFin);
    boolean estaDisponibleZonaBbq(LocalDate fechaReserva, LocalTime horaInicio, LocalTime horaFin, Long usuarioId);
    void eliminar(Long id);
}
