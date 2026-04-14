package com.example.APP.Repository;

import com.example.APP.Model.AccesoPeatonal;
import com.example.APP.Model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccesoPeatonalRepository extends JpaRepository<AccesoPeatonal, Long> {
    List<AccesoPeatonal> findByAutorizadoPor_Rol(Usuario.Rol rol);
}
