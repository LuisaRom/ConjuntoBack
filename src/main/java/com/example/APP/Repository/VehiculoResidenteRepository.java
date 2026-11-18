package com.example.APP.Repository;

import com.example.APP.Model.VehiculoResidente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehiculoResidenteRepository extends JpaRepository<VehiculoResidente, Long> {
    List<VehiculoResidente> findByUsuarioId(Long usuarioId);
    Optional<VehiculoResidente> findByPlacaIgnoreCase(String placa);
    Optional<VehiculoResidente> findByParqueaderoIgnoreCase(String parqueadero);
}
