package com.example.APP.Controller;

import com.example.APP.Model.AccesoVehicular;
import com.example.APP.Service.AccesoVehicularService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/accesos-vehiculares")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class AccesoVehicularController {

    @Autowired
    private AccesoVehicularService accesoVehicularService;

    @GetMapping
    public
    List<AccesoVehicular> obtenerTodos() {
        return accesoVehicularService.obtenerTodos();
    }

    @GetMapping("/{id}")
    public Optional<AccesoVehicular> obtenerPorId(@PathVariable Long id) {
        return accesoVehicularService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('RESIDENTE')")
    public Map<String, Object> guardar(@RequestBody AccesoVehicular accesoVehicular, Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        AccesoVehicular guardado = accesoVehicularService.guardarParaUsuarioAutenticado(accesoVehicular, authentication.getName());
        return construirRespuestaQr(guardado);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        accesoVehicularService.eliminar(id);
    }

    private Map<String, Object> construirRespuestaQr(AccesoVehicular acceso) {
        String codigoQr = acceso.getCodigoQr() != null ? acceso.getCodigoQr().trim() : "";
        if (codigoQr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo generar el código QR del acceso");
        }

        String qrBase64 = generarQrBase64(codigoQr);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("id", acceso.getId());
        respuesta.put("placaVehiculo", acceso.getPlacaVehiculo());
        respuesta.put("torre", acceso.getTorre());
        respuesta.put("apartamento", acceso.getApartamento());
        respuesta.put("horaAutorizada", acceso.getHoraAutorizada());
        respuesta.put("codigoQr", codigoQr);
        respuesta.put("qrBase64", qrBase64);
        respuesta.put("qrDataUrl", "data:image/png;base64," + qrBase64);
        return respuesta;
    }

    private String generarQrBase64(String contenido) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            var matrix = writer.encode(contenido, BarcodeFormat.QR_CODE, 350, 350, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar la imagen QR");
        }
    }
}
