package com.example.APP.Controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * URL de retorno tras pagar en Mercado Pago (deep link Android: conjuntoback.onrender.com/pagos/resultado).
 */
@RestController
public class PagoRetornoController {

    @GetMapping(value = "/pagos/resultado", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paginaResultadoPago(
            @RequestParam(value = "ref", required = false) String ref,
            @RequestParam(value = "estado", required = false) String estado
    ) {
        String mensaje = ref != null && !ref.isBlank()
                ? "Pago registrado. Vuelve a la app Conjunto para ver el resultado."
                : "Operación de pago finalizada. Vuelve a la app Conjunto.";
        String html = """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Pago administración</title></head>
                <body style="font-family:sans-serif;text-align:center;padding:2rem;">
                <h2>%s</h2>
                <p>Referencia: %s</p>
                <p>Estado: %s</p>
                </body></html>
                """.formatted(mensaje, ref != null ? ref : "-", estado != null ? estado : "-");
        return ResponseEntity.ok(html);
    }
}
