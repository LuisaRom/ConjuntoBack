package com.example.APP.Controller;

import com.example.APP.Model.Usuario;
import com.example.APP.Service.UsuarioService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/residentes")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class ResidenteController {

    private final UsuarioService usuarioService;

    public ResidenteController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<Map<String, Object>> obtenerResidentes() {
        return usuarioService.obtenerResidentes().stream()
                .filter(u -> u != null)
                .sorted(Comparator.comparing(Usuario::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::mapearResidente)
                .collect(Collectors.toList());
    }

    @PostMapping
    public List<Map<String, Object>> obtenerResidentesPost() {
        return obtenerResidentes();
    }

    private Map<String, Object> mapearResidente(Usuario usuario) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", usuario.getId());
        item.put("nombre", usuario.getNombre());
        item.put("torre", usuario.getTorre());
        item.put("apartamento", usuario.getApartamento());
        item.put("usuario", usuario.getUsuario());
        item.put("rol", usuario.getRol());
        return item;
    }
}
