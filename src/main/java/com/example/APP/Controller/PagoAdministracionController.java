package com.example.APP.Controller;

import com.example.APP.Model.PagoAdministracion;
import com.example.APP.Service.PagoAdministracionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/pagos")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class PagoAdministracionController {

    @Autowired
    private PagoAdministracionService pagoAdministracionService;

    @GetMapping
    public List<PagoAdministracion> obtenerTodos() {
        return pagoAdministracionService.obtenerTodos();
    }

    @GetMapping("/mis-pagos")
    @PreAuthorize("hasRole('RESIDENTE')")
    public List<Map<String, Object>> listarMisPagos(Authentication authentication) {
        return pagoAdministracionService.listarPagosResidente(authentication.getName());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<Map<String, Object>> listarPagosAdmin(Authentication authentication) {
        return pagoAdministracionService.listarPagosAdmin();
    }

    @GetMapping("/{id}")
    public Optional<PagoAdministracion> obtenerPorId(@PathVariable Long id) {
        return pagoAdministracionService.obtenerPorId(id);
    }

    @PostMapping
    public PagoAdministracion guardar(@RequestBody PagoAdministracion pagoAdministracion) {
        return pagoAdministracionService.guardar(pagoAdministracion);
    }

    @PostMapping("/checkout/administracion")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<?> crearCheckoutAdministracion(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            return ResponseEntity.ok(pagoAdministracionService.crearCheckoutAdministracion(payload, authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/mercadopago/webhook")
    public ResponseEntity<?> webhookMercadoPago(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(pagoAdministracionService.procesarRetornoCheckout(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        pagoAdministracionService.eliminar(id);
    }
}
