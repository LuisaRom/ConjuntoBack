package com.example.APP.Controller;

import com.example.APP.Model.Mascota;
import com.example.APP.Service.MascotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    public Mascota guardar(@RequestBody Mascota mascota) {
        return mascotaService.guardar(mascota);
    }
    
    @PostMapping("/crear")
    public ResponseEntity<?> crear(
            @RequestParam("nombre") String nombre,
            @RequestParam("tipo") String tipo,
            @RequestParam("raza") String raza,
            @RequestPart("foto") MultipartFile foto,
            Authentication authentication
    ) {
        try {
            Mascota creada = mascotaService.crearMascota(nombre, tipo, raza, authentication.getName(), foto);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", creada.getId());
            resp.put("nombre", creada.getNombre());
            resp.put("tipo", creada.getTipo());
            resp.put("raza", creada.getRaza());
            resp.put("fotoUrl", creada.getFotoUrl());
            resp.put("fotoHttpUrl", "/api/mascotas/" + creada.getId() + "/foto");
            resp.put("usuario", creada.getUsuario());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        mascotaService.eliminar(id);
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
