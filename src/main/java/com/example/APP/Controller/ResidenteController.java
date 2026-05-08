package com.example.APP.Controller;

import com.example.APP.Model.Usuario;
import com.example.APP.Service.UsuarioService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.text.Normalizer;

@RestController
@RequestMapping("/api/residentes")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class ResidenteController {

    private final UsuarioService usuarioService;

    public ResidenteController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<Map<String, Object>> obtenerResidentes(@RequestParam(name = "search", required = false) String search) {
        String filtro = normalizarTexto(search);
        return usuarioService.obtenerResidentes().stream()
                .filter(u -> u != null)
                .filter(u -> filtro.isBlank() || coincideFiltroResidente(u, filtro))
                .sorted(Comparator
                        .comparing(Usuario::getTorre, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Usuario::getApartamento, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Usuario::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::mapearResidente)
                .collect(Collectors.toList());
    }

    @PostMapping
    public List<Map<String, Object>> obtenerResidentesPost(@RequestParam(name = "search", required = false) String search) {
        return obtenerResidentes(search);
    }

    private Map<String, Object> mapearResidente(Usuario usuario) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", usuario.getId());
        item.put("nombre", usuario.getNombre());
        item.put("torre", usuario.getTorre());
        item.put("apartamento", usuario.getApartamento());
        item.put("usuario", usuario.getUsuario());
        item.put("rol", usuario.getRol());
        item.put("label", (usuario.getNombre() != null ? usuario.getNombre() : "Sin nombre")
                + " - Torre " + (usuario.getTorre() != null ? usuario.getTorre() : "-")
                + " Apto " + (usuario.getApartamento() != null ? usuario.getApartamento() : "-"));
        return item;
    }

    private boolean coincideFiltroResidente(Usuario usuario, String filtro) {
        return normalizarTexto(usuario.getNombre()).contains(filtro)
                || normalizarTexto(usuario.getUsuario()).contains(filtro)
                || normalizarTexto(usuario.getTorre()).contains(filtro)
                || normalizarTexto(usuario.getApartamento()).contains(filtro)
                || normalizarTexto(usuario.getTorre() + " " + usuario.getApartamento()).contains(filtro)
                || normalizarTexto("torre " + usuario.getTorre() + " apto " + usuario.getApartamento()).contains(filtro);
    }

    private String normalizarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String textoNormalizado = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD);
        return textoNormalizado.replaceAll("\\p{M}", "").toLowerCase();
    }
}
