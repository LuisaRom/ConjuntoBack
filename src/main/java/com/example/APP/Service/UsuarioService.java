package com.example.APP.Service;

import com.example.APP.Model.Usuario;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UsuarioService {

    List<Usuario> obtenerTodos();
    Optional<Usuario> obtenerPorId(Long id);
    Usuario guardar(Usuario usuario);
    Usuario crearUsuario(Map<String, Object> payload);
    void eliminar(Long id);
    Usuario login(String usuario, String password);

}
