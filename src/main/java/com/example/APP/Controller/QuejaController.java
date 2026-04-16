package com.example.APP.Controller;


import com.example.APP.Model.Queja;
import com.example.APP.Service.QuejaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/quejas")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class QuejaController {

    @Autowired
    private QuejaService quejaService;

    @GetMapping
    public List<Queja> obtenerTodos() {
        return quejaService.obtenerTodos();
    }
    
    @GetMapping("/todas")
    public List<Queja> obtenerTodasComoLista(
            @RequestParam(name = "categoria", required = false) String categoria
    ) {
        if (categoria == null || categoria.isBlank()) {
            return quejaService.obtenerTodos();
        }
        return quejaService.obtenerTodasAgrupadasPorCategoria(categoria)
                .values()
                .stream()
                .findFirst()
                .orElse(List.of());
    }

    @GetMapping("/agrupadas")
    public Map<String, List<Queja>> obtenerTodasAgrupadasPorCategoria(
            @RequestParam(name = "categoria", required = false) String categoria
    ) {
        return quejaService.obtenerTodasAgrupadasPorCategoria(categoria);
    }

    @GetMapping("/{id}")
    public Optional<Queja> obtenerPorId(@PathVariable Long id) {
        return quejaService.obtenerPorId(id);
    }

    @PostMapping
    public Queja guardar(@RequestBody Queja queja) {
        return quejaService.guardar(queja);
    }
    
    @PostMapping("/crear")
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            return ResponseEntity.ok(quejaService.crearQueja(payload, authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PutMapping("/{id}/en-proceso")
    public Queja enProceso(@PathVariable Long id) {
        return quejaService.enProceso(id);
    }
    
    @PutMapping("/{id}/finalizar")
    public Queja finalizar(@PathVariable Long id) {
        return quejaService.finalizar(id);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        quejaService.eliminar(id);
    }
}


