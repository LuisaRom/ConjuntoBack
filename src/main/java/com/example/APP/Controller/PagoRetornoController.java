package com.example.APP.Controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pagina estatica de retorno tras pagar en Mercado Pago.
 * No redirige a otras URLs (evita bucles con auto_return o deep links).
 */
@RestController
public class PagoRetornoController {

    @GetMapping(value = "/pagos/resultado", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paginaResultadoPago(
            @RequestParam(value = "ref", required = false) String ref,
            @RequestParam(value = "estado", required = false) String estado,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "payment_id", required = false) String paymentId,
            @RequestParam(value = "preference_id", required = false) String preferenceId
    ) {
        String estadoFinal = estado != null && !estado.isBlank() ? estado : status;
        String mensaje = mensajeSegunEstado(estadoFinal);
        String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="Cache-Control" content="no-store, no-cache, must-revalidate">
                <title>Pago administración</title>
                </head>
                <body style="font-family:sans-serif;text-align:center;padding:2rem;max-width:32rem;margin:0 auto;">
                <h2>%s</h2>
                <p>Referencia: <strong>%s</strong></p>
                <p>Estado: <strong>%s</strong></p>
                <p style="color:#555;margin-top:2rem;">Cierra esta ventana y vuelve a la app Conjunto para ver el resultado actualizado.</p>
                </body>
                </html>
                """.formatted(
                mensaje,
                ref != null && !ref.isBlank() ? ref : "-",
                estadoFinal != null && !estadoFinal.isBlank() ? estadoFinal : "-"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store, no-cache, must-revalidate");
        headers.setPragma("no-cache");
        return ResponseEntity.ok().headers(headers).body(html);
    }

    private String mensajeSegunEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return "Operación de pago finalizada";
        }
        String e = estado.toLowerCase();
        if (e.contains("aprob") || "approved".equals(e) || "success".equals(e)) {
            return "Pago registrado correctamente";
        }
        if (e.contains("pend") || "pending".equals(e)) {
            return "Pago pendiente de confirmación";
        }
        if (e.contains("rechaz") || "rejected".equals(e) || "failure".equals(e)) {
            return "El pago no fue aprobado";
        }
        return "Operación de pago finalizada";
    }
}
