package com.example.APP.Controller;

import com.example.APP.DTO.LoginRequest;
import com.example.APP.DTO.LoginResponseDto;
import com.example.APP.DTO.UsuarioResponseDto;
import com.example.APP.Model.Usuario;
import com.example.APP.Security.CustomUserDetailsService;
import com.example.APP.Security.JwtService;
import com.example.APP.Service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public List<UsuarioResponseDto> obtenerTodos() {
        return usuarioService.obtenerTodos()
                .stream()
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
}

