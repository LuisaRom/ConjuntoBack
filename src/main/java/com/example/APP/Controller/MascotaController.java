package com.example.APP.Controller;

import com.example.APP.Model.Mascota;
import com.example.APP.Service.MascotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        mascotaService.eliminar(id);
    }
}
