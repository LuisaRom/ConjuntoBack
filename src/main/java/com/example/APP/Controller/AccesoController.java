package com.example.APP.Controller;

import com.example.APP.Model.AccesoPeatonal;
import com.example.APP.Model.AccesoVehicular;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.AccesoPeatonalRepository;
import com.example.APP.Repository.AccesoVehicularRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
}
