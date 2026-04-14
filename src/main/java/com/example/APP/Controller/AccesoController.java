package com.example.APP.Controller;

import com.example.APP.Model.AccesoPeatonal;
import com.example.APP.Model.AccesoVehicular;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.AccesoPeatonalRepository;
import com.example.APP.Repository.AccesoVehicularRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/accesos")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class AccesoController {

    @Autowired
    private AccesoPeatonalRepository accesoPeatonalRepository;

    @Autowired
    private AccesoVehicularRepository accesoVehicularRepository;

    @GetMapping("/todos")
    public List<Map<String, Object>> obtenerTodosLosAccesosDeResidentes() {
        List<Map<String, Object>> accesos = new ArrayList<>();

        List<AccesoPeatonal> peatonales = accesoPeatonalRepository.findByAutorizadoPor_Rol(Usuario.Rol.RESIDENTE);
        for (AccesoPeatonal acceso : peatonales) {
            accesos.add(mapearAcceso(acceso, "PEATONAL"));
        }

        List<AccesoVehicular> vehiculares = accesoVehicularRepository.findByAutorizadoPor_Rol(Usuario.Rol.RESIDENTE);
        for (AccesoVehicular acceso : vehiculares) {
            accesos.add(mapearAcceso(acceso, "VEHICULAR"));
        }

        return accesos;
    }
    
    @GetMapping("/hoy")
    public List<Map<String, Object>> obtenerAccesosDeHoy() {
        List<Map<String, Object>> accesos = new ArrayList<>();
        LocalDate hoy = LocalDate.now();

        List<AccesoPeatonal> peatonales = accesoPeatonalRepository.findByAutorizadoPor_Rol(Usuario.Rol.RESIDENTE);
        for (AccesoPeatonal acceso : peatonales) {
            if (esDelDiaActual(acceso, hoy)) {
                accesos.add(mapearAcceso(acceso, "PEATONAL"));
            }
        }

        List<AccesoVehicular> vehiculares = accesoVehicularRepository.findByAutorizadoPor_Rol(Usuario.Rol.RESIDENTE);
        for (AccesoVehicular acceso : vehiculares) {
            if (esDelDiaActual(acceso, hoy)) {
                accesos.add(mapearAcceso(acceso, "VEHICULAR"));
            }
        }
        return accesos;
    }
    
    @PostMapping("/validar-qr")
    public Map<String, Object> validarQr(@RequestBody Map<String, Object> payload) {
        String qrEscaneado = payload.get("codigoQr") != null ? payload.get("codigoQr").toString().trim() : "";
        if (qrEscaneado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo 'codigoQr' es obligatorio");
        }
        
        List<AccesoPeatonal> peatonales = accesoPeatonalRepository.findByAutorizadoPor_Rol(Usuario.Rol.RESIDENTE);
        for (AccesoPeatonal acceso : peatonales) {
            String codigoQr = leerTextoAcceso(acceso, "codigoQr");
            if (codigoQr != null && codigoQr.trim().equalsIgnoreCase(qrEscaneado)) {
                return mapearAcceso(acceso, "PEATONAL");
            }
        }
        
        List<AccesoVehicular> vehiculares = accesoVehicularRepository.findByAutorizadoPor_Rol(Usuario.Rol.RESIDENTE);
        for (AccesoVehicular acceso : vehiculares) {
            String placaVehiculo = leerTextoAcceso(acceso, "placaVehiculo");
            if (placaVehiculo != null && placaVehiculo.trim().equalsIgnoreCase(qrEscaneado)) {
                return mapearAcceso(acceso, "VEHICULAR");
            }
        }
        
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR no válido o acceso no encontrado");
    }
    
    @GetMapping("/{id}/qr")
    public Map<String, Object> obtenerQrAcceso(@PathVariable Long id) {
        Optional<AccesoPeatonal> accesoPeatonal = accesoPeatonalRepository.findById(id);
        if (accesoPeatonal.isPresent()) {
            Map<String, Object> acceso = mapearAcceso(accesoPeatonal.get(), "PEATONAL");
            return construirRespuestaQr(acceso);
        }
        Optional<AccesoVehicular> accesoVehicular = accesoVehicularRepository.findById(id);
        if (accesoVehicular.isPresent()) {
            Map<String, Object> acceso = mapearAcceso(accesoVehicular.get(), "VEHICULAR");
            return construirRespuestaQr(acceso);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Acceso no encontrado");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAcceso(@PathVariable Long id) {
        Optional<AccesoPeatonal> accesoPeatonal = accesoPeatonalRepository.findById(id);
        if (accesoPeatonal.isPresent()) {
            accesoPeatonalRepository.deleteById(id);
            return;
        }

        Optional<AccesoVehicular> accesoVehicular = accesoVehicularRepository.findById(id);
        if (accesoVehicular.isPresent()) {
            accesoVehicularRepository.deleteById(id);
            return;
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Acceso no encontrado");
    }

    private Map<String, Object> mapearAcceso(Object acceso, String tipoAcceso) {
        BeanWrapperImpl bean = new BeanWrapperImpl(acceso);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", leerPropiedad(bean, "id"));
        item.put("tipoAcceso", tipoAcceso);
        item.put("nombreVisitante", leerPropiedad(bean, "nombreVisitante"));
        item.put("placaVehiculo", leerPropiedad(bean, "placaVehiculo"));
        item.put("codigoQr", leerPropiedad(bean, "codigoQr"));
        item.put("torre", leerPropiedad(bean, "torre"));
        item.put("apartamento", leerPropiedad(bean, "apartamento"));
        item.put("horaAutorizada", leerPropiedad(bean, "horaAutorizada"));
        item.put("horaEntrada", leerPropiedad(bean, "horaEntrada"));
        item.put("horaSalida", leerPropiedad(bean, "horaSalida"));
        return item;
    }
    
    private boolean esDelDiaActual(Object acceso, LocalDate hoy) {
        BeanWrapperImpl bean = new BeanWrapperImpl(acceso);
        if (!bean.isReadableProperty("horaAutorizada")) {
            return false;
        }
        Object hora = bean.getPropertyValue("horaAutorizada");
        if (hora instanceof LocalDateTime fechaHora) {
            return fechaHora.toLocalDate().equals(hoy);
        }
        return false;
    }
    
    private String leerTextoAcceso(Object acceso, String propiedad) {
        BeanWrapperImpl bean = new BeanWrapperImpl(acceso);
        if (!bean.isReadableProperty(propiedad)) {
            return null;
        }
        Object valor = bean.getPropertyValue(propiedad);
        return valor != null ? valor.toString() : null;
    }

    private Object leerPropiedad(BeanWrapperImpl bean, String propiedad) {
        return bean.isReadableProperty(propiedad) ? bean.getPropertyValue(propiedad) : null;
    }
    
    private Map<String, Object> construirRespuestaQr(Map<String, Object> acceso) {
        String codigoQr = acceso.get("codigoQr") != null ? acceso.get("codigoQr").toString() : "";
        if (codigoQr.isBlank()) {
            codigoQr = construirPayloadDesdeAcceso(acceso);
        }
        String base64 = generarQrBase64(codigoQr);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("id", acceso.get("id"));
        respuesta.put("tipoAcceso", acceso.get("tipoAcceso"));
        respuesta.put("codigoQr", codigoQr);
        respuesta.put("qrBase64", base64);
        respuesta.put("qrDataUrl", "data:image/png;base64," + base64);
        return respuesta;
    }
    
    private String construirPayloadDesdeAcceso(Map<String, Object> acceso) {
        String tipo = acceso.get("tipoAcceso") != null ? acceso.get("tipoAcceso").toString() : "ACCESO";
        String nombre = acceso.get("nombreVisitante") != null ? acceso.get("nombreVisitante").toString() : "";
        String placa = acceso.get("placaVehiculo") != null ? acceso.get("placaVehiculo").toString() : "";
        String torre = acceso.get("torre") != null ? acceso.get("torre").toString() : "";
        String apartamento = acceso.get("apartamento") != null ? acceso.get("apartamento").toString() : "";
        return tipo + "|" + nombre + "|" + placa + "|" + torre + "|" + apartamento + "|" + System.currentTimeMillis();
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el QR");
        }
    }
}
