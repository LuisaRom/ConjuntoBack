package com.example.APP.Controller;

import com.example.APP.DTO.LoginRequest;
import com.example.APP.DTO.LoginResponseDto;
import com.example.APP.DTO.UsuarioResponseDto;
import com.example.APP.Model.Usuario;
import com.example.APP.Security.CustomUserDetailsService;
import com.example.APP.Security.JwtService;
import com.example.APP.Service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

    public UsuarioController(
            UsuarioService usuarioService,
            JwtService jwtService,
            CustomUserDetailsService customUserDetailsService
    ){
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.customUserDetailsService = customUserDetailsService;
    }

    @GetMapping
    public List<UsuarioResponseDto> obtenerTodos(@RequestParam(name = "search", required = false) String search) {
        String filtro = search != null ? search.trim().toLowerCase() : "";
        return usuarioService.obtenerTodos()
                .stream()
                .filter(usuario -> filtro.isBlank()
                        || (usuario.getNombre() != null && usuario.getNombre().toLowerCase().contains(filtro))
                        || (usuario.getUsuario() != null && usuario.getUsuario().toLowerCase().contains(filtro)))
                .sorted(Comparator.comparing(Usuario::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(UsuarioResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping("/mensajeria")
    public List<UsuarioResponseDto> obtenerUsuariosMensajeria(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "rol", required = false, defaultValue = "CELADOR") String rol
    ) {
        String filtro = normalizarTexto(search);
        String rolSolicitado = normalizarTexto(rol);
        return usuarioService.obtenerTodos().stream()
                .filter(u -> coincideRolMensajeria(u, rolSolicitado))
                .filter(u -> filtro.isBlank() || coincideFiltroMensajeria(u, filtro))
                .sorted(Comparator.comparing(Usuario::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(UsuarioResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDto> obtenerPorId(@PathVariable Long id) {
        return usuarioService.obtenerPorId(id)
                .map(UsuarioResponseDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public UsuarioResponseDto guardar(@RequestBody Usuario usuario) {
        Usuario saved = usuarioService.guardar(usuario);
        return UsuarioResponseDto.fromEntity(saved);
    }
    
    @PostMapping("/crear")
    public ResponseEntity<UsuarioResponseDto> crear(@RequestBody Map<String, Object> payload) {
        Usuario creado = usuarioService.crearUsuario(payload);
        return ResponseEntity.status(201).body(UsuarioResponseDto.fromEntity(creado));
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        usuarioService.eliminar(id);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequest request) {
        Usuario usuario = usuarioService.login(request.getUsuario(), request.getPassword());

        Map<String, Object> claims = new HashMap<>();
        claims.put("rol", usuario.getRol().name());
        claims.put("userId", usuario.getId());

        String token = jwtService.generateToken(
                customUserDetailsService.loadUserByUsername(usuario.getUsuario()),
                claims
        );

        LoginResponseDto response = new LoginResponseDto(
                token,
                "Bearer",
                UsuarioResponseDto.fromEntity(usuario)
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(Authentication authentication) {
        SecurityContextHolder.clearContext();
        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("status", HttpStatus.OK.value());
        response.put("mensaje", "Sesión cerrada correctamente");
        response.put("usuario", authentication != null ? authentication.getName() : null);
        response.put("accionRecomendada", "Eliminar token del cliente");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/perfil")
    public ResponseEntity<Map<String, Object>> perfil(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Usuario usuario = usuarioService.obtenerPorUsuario(authentication.getName());
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", usuario.getId());
        resp.put("nombre", usuario.getNombre());
        resp.put("username", usuario.getUsuario());
        resp.put("rol", usuario.getRol());
        resp.put("torre", usuario.getTorre());
        resp.put("apartamento", usuario.getApartamento());
        resp.put("passwordMasked", "****");
        return ResponseEntity.ok(resp);
    }

    private boolean coincideRolMensajeria(Usuario usuario, String rolSolicitado) {
        if (usuario == null || usuario.getRol() == null) {
            return false;
        }
        if ("TODOS".equals(rolSolicitado)) {
            return usuario.getRol() == Usuario.Rol.ADMINISTRADOR || usuario.getRol() == Usuario.Rol.CELADOR;
        }
        return switch (rolSolicitado) {
            case "ADMINISTRADOR" -> usuario.getRol() == Usuario.Rol.ADMINISTRADOR;
            case "CELADOR" -> usuario.getRol() == Usuario.Rol.CELADOR;
            default -> usuario.getRol() == Usuario.Rol.CELADOR;
        };
    }

    private boolean coincideFiltroMensajeria(Usuario usuario, String filtro) {
        return normalizarTexto(usuario.getNombre()).contains(filtro)
                || normalizarTexto(usuario.getUsuario()).contains(filtro);
    }

    private String normalizarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String textoNormalizado = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD);
        return textoNormalizado.replaceAll("\\p{M}", "").toLowerCase();
    }
}

