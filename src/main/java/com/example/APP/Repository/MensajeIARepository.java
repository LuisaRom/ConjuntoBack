package com.example.APP.Repository;

import com.example.APP.Model.MensajeIA;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MensajeIARepository extends JpaRepository<MensajeIA, Long> {
    List<MensajeIA> findByConversacionIdOrderByIdAsc(Long conversacionId);
    List<MensajeIA> findTop20ByConversacionIdOrderByIdDesc(Long conversacionId);
    List<MensajeIA> findTop30ByRolAndUtilOrderByIdDesc(MensajeIA.Rol rol, Boolean util);
    long countByUtil(Boolean util);

    @Modifying
    @Transactional
    @Query("delete from MensajeIA m where m.conversacion.id = :conversacionId")
    void eliminarPorConversacion(@Param("conversacionId") Long conversacionId);
}
