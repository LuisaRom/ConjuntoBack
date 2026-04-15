package com.example.APP.Service;

import com.example.APP.Model.Queja;

import java.util.Map;
import java.util.List;
import java.util.Optional;

public interface QuejaService {

    List<Queja> obtenerTodos();
    Map<String, List<Queja>> obtenerTodasAgrupadasPorCategoria();
    Map<String, List<Queja>> obtenerTodasAgrupadasPorCategoria(String categoria);
    Optional<Queja> obtenerPorId(Long id);
    Queja guardar(Queja queja);
    Queja crearQueja(Map<String, Object> payload, String usernameAutenticado);
    Queja enProceso(Long id);
    Queja finalizar(Long id);
    void eliminar(Long id);
}
