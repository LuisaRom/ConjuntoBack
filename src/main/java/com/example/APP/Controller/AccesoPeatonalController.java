package com.example.APP.Controller;

import com.example.APP.Model.AccesoPeatonal;
import com.example.APP.Service.AccesoPeatonalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/accesos-peatonales")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class AccesoPeatonalController {

    @Autowired
    private AccesoPeatonalService accesoPeatonalService;

    @GetMapping
    public List<AccesoPeatonal> obtenerTodos() {
        return accesoPeatonalService.obtenerTodos();
    }

    @GetMapping("/{id}")
    public Optional<AccesoPeatonal> obtenerPorId(@PathVariable Long id) {
        return accesoPeatonalService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('RESIDENTE')")
    public AccesoPeatonal guardar(@RequestBody AccesoPeatonal accesoPeatonal, Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return accesoPeatonalService.guardarParaUsuarioAutenticado(accesoPeatonal, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        accesoPeatonalService.eliminar(id);
    }
}
