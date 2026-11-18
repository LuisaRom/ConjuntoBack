package com.example.APP.Controller;

import com.example.APP.Model.VehiculoResidente;
import com.example.APP.Service.VehiculoResidenteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/vehiculos-residentes")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class VehiculoResidenteController {

    @Autowired
    private VehiculoResidenteService vehiculoResidenteService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public List<VehiculoResidente> obtenerTodos() {
        return vehiculoResidenteService.obtenerTodos();
    }

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public Map<String, Object> resumen() {
        return vehiculoResidenteService.resumen();
    }

    @GetMapping("/mis-vehiculos")
    public List<VehiculoResidente> misVehiculos(Authentication auth) {
        return vehiculoResidenteService.listarDeUsuario(auth.getName());
    }

    @PostMapping("/mis-vehiculos")
    public VehiculoResidente registrarMiVehiculo(@RequestBody Map<String, Object> datos, Authentication auth) {
        return vehiculoResidenteService.registrarParaUsuario(auth.getName(), datos);
    }

    @DeleteMapping("/mis-vehiculos/{id}")
    public ResponseEntity<Void> eliminarMiVehiculo(@PathVariable Long id, Authentication auth) {
        vehiculoResidenteService.eliminarDeUsuario(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/parqueadero")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public VehiculoResidente asignarParqueadero(@PathVariable Long id, @RequestBody Map<String, Object> datos) {
        Object parqueadero = datos.get("parqueadero");
        return vehiculoResidenteService.asignarParqueadero(id, parqueadero == null ? null : parqueadero.toString());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public Optional<VehiculoResidente> obtenerPorId(@PathVariable Long id) {
        return vehiculoResidenteService.obtenerPorId(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public VehiculoResidente guardar(@RequestBody VehiculoResidente vehiculoResidente) {
        return vehiculoResidenteService.guardar(vehiculoResidente);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public void eliminar(@PathVariable Long id) {
        vehiculoResidenteService.eliminar(id);
    }
}
