package com.example.APP.Controller;

import com.example.APP.Model.Mascota;
import com.example.APP.Service.MascotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/mascotas")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class MascotaController {

    private static final Logger log = LoggerFactory.getLogger(MascotaController.class);

    @Autowired
    private MascotaService mascotaService;

    @GetMapping
    public List<Mascota> obtenerTodos() {
        return mascotaService.obtenerTodos();
    }

    @GetMapping("/todas")
    public List<Mascota> obtenerTodas() {
        return mascotaService.obtenerTodos();
    }

    @GetMapping("/{id}")
    public Optional<Mascota> obtenerPorId(@PathVariable Long id) {
        return mascotaService.obtenerPorId(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mascota guardar(@RequestBody Mascota mascota, Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("No hay usuario autenticado");
        }
        return mascotaService.guardar(mascota, authentication.getName());
    }

    @PostMapping(value = "/crear", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mascota crearJson(@RequestBody Mascota mascota, Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("No hay usuario autenticado");
        }
        return mascotaService.guardar(mascota, authentication.getName());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> guardarMultipart(
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam(value = "nombreMascota", required = false) String nombreMascota,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "tipoMascota", required = false) String tipoMascota,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam(value = "raza", required = false) String raza,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            @RequestParam(value = "imagen", required = false) MultipartFile imagen,
            Authentication authentication
    ) {
        return crear(nombre, nombreMascota, tipo, tipoMascota, descripcion, raza, foto, imagen, authentication);
    }
    
    @PostMapping(value = "/crear", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crear(
            @RequestParam(value = "nombre", required = false) String nombre,
            @RequestParam(value = "nombreMascota", required = false) String nombreMascota,
            @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "tipoMascota", required = false) String tipoMascota,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam(value = "raza", required = false) String raza,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            @RequestParam(value = "imagen", required = false) MultipartFile imagen,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
                throw new IllegalArgumentException("No hay usuario autenticado");
            }
            String nombreFinal = (nombreMascota != null && !nombreMascota.isBlank()) ? nombreMascota : nombre;
            String tipoFinal = (tipoMascota != null && !tipoMascota.isBlank()) ? tipoMascota : tipo;
            MultipartFile archivoImagen = (imagen != null && !imagen.isEmpty()) ? imagen : foto;
            Mascota creada = mascotaService.crearMascota(nombreFinal, tipoFinal, descripcion, raza, authentication.getName(), archivoImagen);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", creada.getId());
            resp.put("nombre", creada.getNombre());
            resp.put("tipo", creada.getTipo());
            resp.put("descripcion", creada.getDescripcion());
            resp.put("raza", creada.getRaza());
            resp.put("fotoUrl", creada.getFotoUrl());
            resp.put("fotoHttpUrl", "/api/mascotas/" + creada.getId() + "/foto");
            resp.put("usuario", creada.getUsuario());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            boolean fotoPresente = (foto != null && !foto.isEmpty()) || (imagen != null && !imagen.isEmpty());
            log.warn("Error 400 al crear mascota. usuario={}, nombre='{}', tipo='{}', raza='{}', fotoPresente={}, motivo={}",
                    authentication != null ? authentication.getName() : "anon",
                    nombre, tipo, raza, fotoPresente, e.getMessage(), e);
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            boolean fotoPresente = (foto != null && !foto.isEmpty()) || (imagen != null && !imagen.isEmpty());
            log.error("Error inesperado al crear mascota. usuario={}, nombre='{}', tipo='{}', raza='{}', fotoPresente={}",
                    authentication != null ? authentication.getName() : "anon",
                    nombre, tipo, raza, fotoPresente, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo crear la publicación de mascota");
        }
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        mascotaService.eliminar(id);
    }

    @DeleteMapping("/publicaciones/{id}")
    public ResponseEntity<Map<String, Object>> eliminarPublicacion(@PathVariable Long id) {
        mascotaService.eliminar(id);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("mensaje", "Publicación eliminada correctamente");
        resp.put("idMascota", id);
        return ResponseEntity.ok(resp);
    }
    
    @GetMapping("/{id}/foto")
    public ResponseEntity<Resource> obtenerFoto(@PathVariable Long id) {
        Mascota mascota = mascotaService.obtenerPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mascota no encontrada"));
        
        String fotoUrl = mascota.getFotoUrl();
        if (fotoUrl == null || fotoUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La mascota no tiene foto");
        }
        
        try {
            Path path = Paths.get(fotoUrl).toAbsolutePath().normalize();
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto no encontrada");
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ruta de foto inválida");
        }
    }
}
