package com.example.APP.Service;

import com.example.APP.Model.AccesoPeatonal;

import java.util.List;
import java.util.Optional;

public interface AccesoPeatonalService {

    List<AccesoPeatonal> obtenerTodos();
    Optional<AccesoPeatonal> obtenerPorId(Long id);
    AccesoPeatonal guardar(AccesoPeatonal accesoPeatonal);
    AccesoPeatonal guardarParaUsuarioAutenticado(AccesoPeatonal accesoPeatonal, String usernameAutenticado);
    void eliminar(Long id);
}

