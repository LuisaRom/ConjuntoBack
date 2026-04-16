package com.example.APP.Controller;

import com.example.APP.Model.ReservaZonaComun;
import com.example.APP.Model.Usuario;
import com.example.APP.Service.ReservaZonaComunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reservas")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class ReservaZonaComunController {
    private static final Set<String> TIPOS_RESERVA_ADMIN = new HashSet<>(
            Arrays.asList("piscina", "salon comunal", "gimnasio", "zona bbq")
    );

    @Autowired
    private ReservaZonaComunService reservaZonaComunService;

    @GetMapping
    public List<ReservaZonaComun> obtenerTodos() {
        return reservaZonaComunService.obtenerTodos();
    }

    @GetMapping("/todas")
    public List<Map<String, Object>> obtenerTodasParaCelador() {
        return reservaZonaComunService.obtenerTodos().stream()
                .filter(this::esDeResidente)
                .filter(this::esReservaActiva)
                .filter(reserva -> {
                    String zona = leerComoTexto(reserva, "zonaComun");
                    return zona != null && TIPOS_RESERVA_ADMIN.contains(zona.trim().toLowerCase());
                })
                .map(this::mapearReservaConTipo)
                .collect(Collectors.toList());
    }

    @GetMapping("/resumen")
    public List<Map<String, Object>> obtenerResumenReservas() {
        return reservaZonaComunService.obtenerTodos().stream()
                .map(this::mapearReservaResumen)
                .collect(Collectors.toList());
    }

    private boolean esDeResidente(ReservaZonaComun reserva) {
        BeanWrapperImpl beanReserva = new BeanWrapperImpl(reserva);
        if (!beanReserva.isReadableProperty("usuario")) {
            return false;
        }
        Object usuarioObj = beanReserva.getPropertyValue("usuario");
        if (!(usuarioObj instanceof Usuario usuario)) {
            return false;
        }
        return usuario.getRol() == Usuario.Rol.RESIDENTE;
    }

    private String leerComoTexto(Object bean, String propiedad) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        if (!wrapper.isReadableProperty(propiedad)) {
            return null;
        }
        Object valor = wrapper.getPropertyValue(propiedad);
        return valor != null ? valor.toString() : null;
    }
    
    private boolean esReservaActiva(ReservaZonaComun reserva) {
        BeanWrapperImpl bean = new BeanWrapperImpl(reserva);
        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        
        LocalDate fechaReserva = null;
        if (bean.isReadableProperty("fechaReserva")) {
            Object fechaObj = bean.getPropertyValue("fechaReserva");
            if (fechaObj instanceof LocalDate fecha) {
                fechaReserva = fecha;
            }
        }
        if (fechaReserva == null) {
            return false;
        }
        if (fechaReserva.isAfter(hoy)) {
            return true;
        }
        if (fechaReserva.isBefore(hoy)) {
            return false;
        }
        
        if (bean.isReadableProperty("horaFin")) {
            Object horaFinObj = bean.getPropertyValue("horaFin");
            if (horaFinObj instanceof LocalTime horaFin) {
                return !horaFin.isBefore(ahora);
            }
        }
        return true;
    }
    
    private Map<String, Object> mapearReservaConTipo(ReservaZonaComun reserva) {
        BeanWrapperImpl bean = new BeanWrapperImpl(reserva);
        Map<String, Object> item = new LinkedHashMap<>();
        
        String zona = leerComoTexto(reserva, "zonaComun");
        String tipo = zona != null ? zona.trim().toLowerCase() : null;
        
        item.put("id", leerPropiedad(bean, "id"));
        item.put("tipo", tipo);
        item.put("zonaComun", zona);
        item.put("fechaReserva", leerPropiedad(bean, "fechaReserva"));
        item.put("horaInicio", leerPropiedad(bean, "horaInicio"));
        item.put("horaFin", leerPropiedad(bean, "horaFin"));
        item.put("usuario", leerPropiedad(bean, "usuario"));
        return item;
    }
    
    private Object leerPropiedad(BeanWrapperImpl bean, String propiedad) {
        return bean.isReadableProperty(propiedad) ? bean.getPropertyValue(propiedad) : null;
    }

    @GetMapping("/{id}")
    public Optional<ReservaZonaComun> obtenerPorId(@PathVariable Long id) {
        return reservaZonaComunService.obtenerPorId(id);
    }

    @GetMapping("/{id}/detalle")
    public ResponseEntity<?> obtenerDetalle(@PathVariable Long id) {
        return reservaZonaComunService.obtenerPorId(id)
                .map(reserva -> ResponseEntity.ok(mapearReservaDetalle(reserva)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/horarios-disponibles")
    public ResponseEntity<?> obtenerHorariosDisponibles(
            @RequestParam String zonaComun,
            @RequestParam LocalDate fechaReserva,
            @RequestParam(required = false) Long usuarioId
    ) {
        try {
            return ResponseEntity.ok(reservaZonaComunService.obtenerHorariosDisponibles(zonaComun, fechaReserva, usuarioId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/salon-comunal/disponibilidad")
    public ResponseEntity<?> consultarDisponibilidadSalonComunal(
            @RequestParam LocalDate fechaReserva,
            @RequestParam LocalTime horaInicio,
            @RequestParam LocalTime horaFin
    ) {
        try {
            boolean disponible = reservaZonaComunService
                    .estaDisponibleSalonComunal(fechaReserva, horaInicio, horaFin);
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("zonaComun", "salon comunal");
            respuesta.put("fechaReserva", fechaReserva);
            respuesta.put("horaInicio", horaInicio);
            respuesta.put("horaFin", horaFin);
            respuesta.put("duracionMinimaHoras", 5);
            respuesta.put("rangoPermitido", "08:00-22:00");
            respuesta.put("disponible", disponible);
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/zona-bbq/disponibilidad")
    public ResponseEntity<?> consultarDisponibilidadZonaBbq(
            @RequestParam LocalDate fechaReserva,
            @RequestParam LocalTime horaInicio,
            @RequestParam LocalTime horaFin,
            @RequestParam Long usuarioId
    ) {
        try {
            boolean disponible = reservaZonaComunService
                    .estaDisponibleZonaBbq(fechaReserva, horaInicio, horaFin, usuarioId);
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("zonaComun", "zona bbq");
            respuesta.put("fechaReserva", fechaReserva);
            respuesta.put("horaInicio", horaInicio);
            respuesta.put("horaFin", horaFin);
            respuesta.put("usuarioId", usuarioId);
            respuesta.put("maximoReservasPorDiaUsuario", 1);
            respuesta.put("diasPermitidos", "jueves-domingo");
            respuesta.put("rangoPermitido", "10:00-22:00");
            respuesta.put("disponible", disponible);
            return ResponseEntity.ok(respuesta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping
    public ReservaZonaComun guardar(@RequestBody ReservaZonaComun reservaZonaComun) {
        return reservaZonaComunService.crearReserva(reservaZonaComun);
    }
    
    @PostMapping("/crear")
    public ResponseEntity<?> crear(@RequestBody ReservaZonaComun reservaZonaComun) {
        try {
            ReservaZonaComun creada = reservaZonaComunService.crearReserva(reservaZonaComun);
            return ResponseEntity.status(HttpStatus.CREATED).body(creada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        reservaZonaComunService.eliminar(id);
    }

    private Map<String, Object> mapearReservaResumen(ReservaZonaComun reserva) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", reserva.getId());
        item.put("zonaComun", reserva.getZonaComun());
        item.put("fechaReserva", reserva.getFechaReserva());
        item.put("horaInicio", reserva.getHoraInicio());
        item.put("horaFin", reserva.getHoraFin());
        item.put("usuario", reserva.getUsuario() != null ? reserva.getUsuario().getNombre() : null);
        item.put("torre", reserva.getUsuario() != null ? reserva.getUsuario().getTorre() : null);
        item.put("apartamento", reserva.getUsuario() != null ? reserva.getUsuario().getApartamento() : null);
        return item;
    }

    private Map<String, Object> mapearReservaDetalle(ReservaZonaComun reserva) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("id", reserva.getId());
        detalle.put("zonaComun", reserva.getZonaComun());
        detalle.put("fechaReserva", reserva.getFechaReserva());
        detalle.put("horaInicio", reserva.getHoraInicio());
        detalle.put("horaFin", reserva.getHoraFin());
        detalle.put("serviciosAdicionales", reserva.getServiciosAdicionales());
        detalle.put("usuario", reserva.getUsuario());
        return detalle;
    }
}
