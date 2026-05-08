package com.example.APP.Controller;

import com.example.APP.Model.Notificacion;
import com.example.APP.Service.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notificaciones")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class NotificacionController {

    @Autowired
    private NotificacionService notificacionService;

    @GetMapping
    public List<Notificacion> obtenerTodos() {
        return notificacionService.obtenerTodos();
    }

    @GetMapping("/novedades")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR', 'RESIDENTE')")
    public List<Map<String, Object>> obtenerNovedades(
            @RequestParam(name = "search", required = false) String search,
            Authentication authentication
    ) {
        return notificacionService.obtenerNovedades(search, authentication.getName());
    }

    @GetMapping("/chat/historial")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public List<Map<String, Object>> obtenerHistorialChat(
            @RequestParam(name = "search", required = false) String search,
            Authentication authentication
    ) {
        return notificacionService.obtenerHistorialChat(search, authentication.getName());
    }

    @PostMapping("/chat/enviar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public ResponseEntity<?> enviarMensajeChat(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            return ResponseEntity.ok(notificacionService.enviarMensajeChat(payload, authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuariosNotificables(@RequestParam(name = "search", required = false) String search) {
        return notificacionService.listarUsuariosParaNotificaciones(search);
    }

    @GetMapping("/{id}")
    public Optional<Notificacion> obtenerPorId(@PathVariable Long id) {
        return notificacionService.obtenerPorId(id);
    }

    @PostMapping
    public ResponseEntity<?> guardar(@RequestBody Notificacion notificacion, Authentication authentication) {
        try {
            if (notificacion.getFechaEnvio() == null) {
                notificacion.setFechaEnvio(java.time.LocalDateTime.now());
            }
            return ResponseEntity.ok(notificacionService.guardar(notificacion, authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Notificacion actualizar(@PathVariable Long id, @RequestBody Notificacion notificacion) {
        return notificacionService.actualizar(id, notificacion);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        notificacionService.eliminar(id);
    }

    @PostMapping("/recibos/enviar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public ResponseEntity<?> enviarRecibo(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(notificacionService.enviarNotificacionRecibo(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
