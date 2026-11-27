package com.example.APP.Controller;

import com.example.APP.Model.AceptacionTerminos;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.AceptacionTerminosRepository;
import com.example.APP.Repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/terminos")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class TerminosController {

    private final AceptacionTerminosRepository repository;
    private final UsuarioRepository usuarioRepository;

    @Value("${terminos.version:1.0}")
    private String versionVigente;

    public TerminosController(AceptacionTerminosRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/estado")
    public Map<String, Object> estado(Authentication auth) {
        Usuario usuario = usuario(auth);
        Optional<AceptacionTerminos> aceptacion = repository
                .findFirstByUsuarioIdAndVersionOrderByIdDesc(usuario.getId(), versionVigente);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("versionVigente", versionVigente);
        r.put("aceptado", aceptacion.isPresent());
        r.put("fechaAceptacion", aceptacion.map(AceptacionTerminos::getFechaAceptacion).orElse(null));
        return r;
    }

    @PostMapping("/aceptar")
    public Map<String, Object> aceptar(@RequestBody Map<String, Object> datos, Authentication auth) {
        Usuario usuario = usuario(auth);
        String version = String.valueOf(datos.getOrDefault("version", ""));
        if (!versionVigente.equals(version)) {
            throw new IllegalArgumentException("La versión de los términos no está vigente. Actualiza la aplicación.");
        }
        boolean terminos = Boolean.parseBoolean(String.valueOf(datos.get("aceptaTerminos")));
        boolean datosPersonales = Boolean.parseBoolean(String.valueOf(datos.get("autorizaTratamientoDatos")));
        if (!terminos || !datosPersonales) {
            throw new IllegalArgumentException("Debes aceptar los términos y autorizar el tratamiento de datos personales para usar la aplicación.");
        }
        AceptacionTerminos a = repository.findFirstByUsuarioIdAndVersionOrderByIdDesc(usuario.getId(), versionVigente)
                .orElseGet(AceptacionTerminos::new);
        a.setUsuario(usuario);
        a.setVersion(versionVigente);
        a.setRol(usuario.getRol() != null ? usuario.getRol().name() : null);
        a.setAceptaTerminos(true);
        a.setAutorizaTratamientoDatos(true);
        a.setAceptaAvisoIA(Boolean.parseBoolean(String.valueOf(datos.getOrDefault("aceptaAvisoIA", "true"))));
        a.setDispositivo(datos.get("dispositivo") != null ? truncar(datos.get("dispositivo").toString(), 200) : null);
        a.setFechaAceptacion(LocalDateTime.now());
        repository.save(a);
        return estado(auth);
    }

    @GetMapping("/aceptaciones")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<AceptacionTerminos> aceptaciones() {
        return repository.findAllByOrderByIdDesc();
    }

    private Usuario usuario(Authentication auth) {
        return usuarioRepository.findByUsuario(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }

    private String truncar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
