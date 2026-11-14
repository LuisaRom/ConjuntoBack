package com.example.APP.Repository;

import com.example.APP.Model.ConocimientoIA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConocimientoIARepository extends JpaRepository<ConocimientoIA, Long> {
    List<ConocimientoIA> findByEstado(ConocimientoIA.Estado estado);
    List<ConocimientoIA> findByEstadoOrderByFechaCreacionDesc(ConocimientoIA.Estado estado);
    List<ConocimientoIA> findAllByOrderByFechaCreacionDesc();
    boolean existsByTituloIgnoreCase(String titulo);
}
