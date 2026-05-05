package com.example.APP.Repository;

import com.example.APP.Model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsuario(String usuario);
    List<Usuario> findByRolOrderByNombreAsc(Usuario.Rol rol);

}
