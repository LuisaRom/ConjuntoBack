package com.example.APP.Controller;

import com.example.APP.Model.Notificacion;
import com.example.APP.Service.NotificacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/{id}/imagen")
    public ResponseEntity<Resource> obtenerImagen(@PathVariable Long id) {
        try {
            Resource resource = notificacionService.obtenerImagen(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"imagen-" + id + "\"")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}/video")
    public ResponseEntity<Resource> obtenerVideo(@PathVariable Long id) {
        try {
            Resource resource = notificacionService.obtenerVideo(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"video-" + id + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> guardar(@RequestBody Notificacion notificacion, Authentication authentication) {
        try {
            if (notificacion.getFechaEnvio() == null) {
                notificacion.setFechaEnvio(java.time.LocalDateTime.now());
            }
            Notificacion creada = notificacionService.guardar(notificacion, authentication.getName());
            return ResponseEntity.ok(notificacionService.mapearPublicacion(creada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensaje", "Error al guardar en base de datos. Revisa el esquema de notificaciones."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensaje", "Error interno al crear la publicación"));
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> guardarMultipart(
            @RequestParam(value = "mensaje", required = false) String mensaje,
            @RequestParam(value = "fechaEnvio", required = false) String fechaEnvio,
            @RequestParam(value = "usuariosEtiquetados", required = false) String usuariosEtiquetados,
            @RequestParam(value = "imagen", required = false) MultipartFile imagen,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            @RequestParam(value = "video", required = false) MultipartFile video,
            Authentication authentication
    ) {
        try {
            MultipartFile archivoImagen = (imagen != null && !imagen.isEmpty()) ? imagen : foto;
            Notificacion creada = notificacionService.crearConMultimedia(
                    mensaje,
                    fechaEnvio,
                    usuariosEtiquetados,
                    archivoImagen,
                    video,
                    authentication.getName()
            );
            return ResponseEntity.ok(notificacionService.mapearPublicacion(creada));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        } catch (DataAccessException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensaje", "Error al guardar en base de datos. Revisa columnas imagen_url, video_url y usuarios_etiquetados."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensaje", "No se pudo crear la publicación"));
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
