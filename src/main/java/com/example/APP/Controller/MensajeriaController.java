package com.example.APP.Controller;

import com.example.APP.DTO.MensajeriaUsuarioDto;
import com.example.APP.Model.Usuario;
import com.example.APP.Service.UsuarioService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mensajes")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class MensajeriaController {

    private final UsuarioService usuarioService;

    public MensajeriaController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/usuarios-celadores")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public List<MensajeriaUsuarioDto> obtenerUsuariosCeladores() {
        return usuarioService.obtenerTodos().stream()
                .filter(this::usuarioActivo)
                .filter(usuario -> usuario.getRol() == Usuario.Rol.CELADOR)
                .sorted(Comparator.comparing(Usuario::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(MensajeriaUsuarioDto::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping("/usuarios-admin")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public List<MensajeriaUsuarioDto> obtenerUsuariosAdmin() {
        return usuarioService.obtenerTodos().stream()
                .filter(this::usuarioActivo)
                .filter(usuario -> usuario.getRol() == Usuario.Rol.ADMINISTRADOR)
                .sorted(Comparator.comparing(Usuario::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(MensajeriaUsuarioDto::fromEntity)
                .collect(Collectors.toList());
    }

    private boolean usuarioActivo(Usuario usuario) {
        if (usuario == null || usuario.getRol() == null) {
            return false;
        }
        return usuario.getUsuario() != null
                && !usuario.getUsuario().isBlank()
                && usuario.getPassword() != null
                && !usuario.getPassword().isBlank();
    }
}
