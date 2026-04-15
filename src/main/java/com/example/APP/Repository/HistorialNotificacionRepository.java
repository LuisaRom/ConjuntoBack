package com.example.APP.Repository;

import com.example.APP.Model.HistorialNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HistorialNotificacionRepository extends JpaRepository<HistorialNotificacion, Long> {
    Optional<HistorialNotificacion> findByNotificacionOriginalId(Long notificacionOriginalId);
}
