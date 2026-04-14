package com.example.APP.Repository;

import com.example.APP.Model.ReservaZonaComun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReservaZonaComunRepository extends JpaRepository<ReservaZonaComun, Long> {
    List<ReservaZonaComun> findByUsuarioId(Long usuarioId);
    List<ReservaZonaComun> findByZonaComunIgnoreCaseAndFechaReserva(String zonaComun, LocalDate fechaReserva);
    List<ReservaZonaComun> findByZonaComunIgnoreCaseAndFechaReservaAndUsuarioId(String zonaComun, LocalDate fechaReserva, Long usuarioId);
}
