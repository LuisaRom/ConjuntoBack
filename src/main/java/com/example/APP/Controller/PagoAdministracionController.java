package com.example.APP.Controller;

import com.example.APP.Model.PagoAdministracion;
import com.example.APP.Service.PagoAdministracionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

    private static final Logger log = LoggerFactory.getLogger(PagoAdministracionController.class);

    @Autowired
    private PagoAdministracionService pagoAdministracionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<PagoAdministracion> obtenerTodos() {
        return pagoAdministracionService.obtenerTodos();
    }

    @GetMapping("/mis-pagos")
    @PreAuthorize("hasRole('RESIDENTE')")
    public List<Map<String, Object>> listarMisPagos(Authentication authentication) {
        return pagoAdministracionService.listarPagosResidente(authentication.getName());
    }

    @GetMapping("/sandbox-instrucciones")
    @PreAuthorize("hasRole('RESIDENTE')")
    public Map<String, Object> instruccionesSandboxPago() {
        return pagoAdministracionService.obtenerInstruccionesSandbox();
    }

    @GetMapping("/residente")
    @PreAuthorize("hasRole('RESIDENTE')")
    public List<Map<String, Object>> listarPagosResidenteAlias(Authentication authentication) {
        return listarMisPagos(authentication);
    }

    @GetMapping("/residente/pagos")
    @PreAuthorize("hasRole('RESIDENTE')")
    public List<Map<String, Object>> listarPagosResidentePagosAlias(Authentication authentication) {
        return listarMisPagos(authentication);
    }

    @PostMapping("/residente/pagos")
    @PreAuthorize("hasRole('RESIDENTE')")
    public List<Map<String, Object>> listarPagosResidentePagosAliasPost(Authentication authentication) {
        return listarMisPagos(authentication);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<Map<String, Object>> listarPagosAdmin(Authentication authentication) {
        return pagoAdministracionService.listarPagosAdmin();
    }

    @GetMapping("/admin/estado-pagos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> listarEstadoPagosAdmin() {
        try {
            return ResponseEntity.ok(pagoAdministracionService.listarPagosAdmin());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No fue posible consultar el estado de pagos en este momento");
        }
    }

    @PostMapping("/admin/estado-pagos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> listarEstadoPagosAdminPost() {
        return listarEstadoPagosAdmin();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'RESIDENTE')")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id, Authentication authentication) {
        Optional<PagoAdministracion> pago = pagoAdministracionService.obtenerPorId(id);
        if (pago.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"))) {
            return ResponseEntity.ok(pago.get());
        }
        String username = authentication.getName();
        if (pago.get().getUsuario() != null && username.equals(pago.get().getUsuario().getUsuario())) {
            return ResponseEntity.ok(pago.get());
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No autorizado para consultar este pago");
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public PagoAdministracion guardar(@RequestBody PagoAdministracion pagoAdministracion) {
        return pagoAdministracionService.guardar(pagoAdministracion);
    }

    @PostMapping("/checkout/administracion")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<?> crearCheckoutAdministracion(@RequestBody Map<String, Object> payload, Authentication authentication) {
        return ejecutarCheckout(payload, authentication);
    }

    /** Alias usados por la app Android (Retrofit prueba varias rutas). */
    @PostMapping({"/crear", "/checkout", "/mercadopago", "/mercadopago/crear", "/mercado-pago", "/mercado-pago/crear"})
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<?> crearCheckoutAlias(@RequestBody Map<String, Object> payload, Authentication authentication) {
        return ejecutarCheckout(payload, authentication);
    }

    @PostMapping("/confirmar")
    @PreAuthorize("hasRole('RESIDENTE')")
    public ResponseEntity<?> confirmarPago(@RequestBody Map<String, Object> payload, Authentication authentication) {
        try {
            String referencia = extraerReferencia(payload);
            return ResponseEntity.ok(pagoAdministracionService.confirmarPagoPorReferencia(referencia, authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        }
    }

    @PostMapping("/mercadopago/webhook")
    public ResponseEntity<?> webhookMercadoPagoPost(@RequestBody(required = false) Map<String, Object> payload) {
        return responderWebhook(payload, null, null);
    }

    @GetMapping("/mercadopago/webhook")
    public ResponseEntity<?> webhookMercadoPagoGet(
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "id", required = false) String id
    ) {
        return responderWebhook(null, topic, id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void eliminar(@PathVariable Long id) {
        pagoAdministracionService.eliminar(id);
    }

    private ResponseEntity<?> ejecutarCheckout(Map<String, Object> payload, Authentication authentication) {
        try {
            return ResponseEntity.ok(pagoAdministracionService.crearCheckoutAdministracion(payload, authentication.getName()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
        } catch (Exception e) {
            log.error("Error interno al crear checkout de administracion para usuario {}", authentication.getName(), e);
            String detalle = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "mensaje", "No fue posible iniciar el pago en linea. Intenta de nuevo en unos minutos.",
                            "error", detalle
                    ));
        }
    }

    private ResponseEntity<?> responderWebhook(Map<String, Object> payload, String topic, String id) {
        try {
            return ResponseEntity.ok(pagoAdministracionService.procesarNotificacionMercadoPago(payload, topic, id));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("procesado", false, "mensaje", "Error al procesar notificación"));
        }
    }

    private String extraerReferencia(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("referenciaExterna es obligatoria");
        }
        Object ref = payload.get("referenciaExterna");
        if (ref == null) {
            ref = payload.get("ref");
        }
        if (ref == null || ref.toString().isBlank()) {
            throw new IllegalArgumentException("referenciaExterna es obligatoria");
        }
        return ref.toString().trim();
    }
}
