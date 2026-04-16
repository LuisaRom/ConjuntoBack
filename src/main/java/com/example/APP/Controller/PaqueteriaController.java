package com.example.APP.Controller;

import com.example.APP.Model.Paqueteria;
import com.example.APP.Service.PaqueteriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/paqueteria")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class PaqueteriaController {

    @Autowired
    private PaqueteriaService paqueteriaService;

    @GetMapping
    public List<Paqueteria> obtenerTodos() {
        return paqueteriaService.obtenerTodos();
    }

    @GetMapping("/residentes")
    public List<Map<String, Object>> obtenerResidentes() {
        return paqueteriaService.obtenerResidentesParaPaqueteria();
    }

    @GetMapping("/{id}")
    public Optional<Paqueteria> obtenerPorId(@PathVariable Long id) {
        return paqueteriaService.obtenerPorId(id);
    }

    @PostMapping
    public Paqueteria guardar(@RequestBody Paqueteria paqueteria) {
        return paqueteriaService.guardar(paqueteria);
    }

    @PostMapping("/crear")
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(paqueteriaService.crearPaquete(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Paqueteria actualizar(@PathVariable Long id, @RequestBody Paqueteria paqueteria) {
        return paqueteriaService.actualizar(id, paqueteria);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        paqueteriaService.eliminar(id);
    }
}
