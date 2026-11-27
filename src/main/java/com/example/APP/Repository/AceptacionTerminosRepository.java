package com.example.APP.Repository;

import com.example.APP.Model.AceptacionTerminos;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AceptacionTerminosRepository extends JpaRepository<AceptacionTerminos, Long> {
    Optional<AceptacionTerminos> findFirstByUsuarioIdAndVersionOrderByIdDesc(Long usuarioId, String version);
    List<AceptacionTerminos> findAllByOrderByIdDesc();
}
