package com.example.APP.Repository;

import com.example.APP.Model.ConversacionIA;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversacionIARepository extends JpaRepository<ConversacionIA, Long> {
    List<ConversacionIA> findByUsuarioIdOrderByFechaActualizacionDesc(Long usuarioId);
    Optional<ConversacionIA> findByIdAndUsuarioId(Long id, Long usuarioId);
}
