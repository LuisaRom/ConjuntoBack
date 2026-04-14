package com.example.APP.Controller;

import com.example.APP.Model.ReservaZonaComun;
import com.example.APP.Model.Usuario;
import com.example.APP.Service.ReservaZonaComunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
    public List<ReservaZonaComun> obtenerTodasParaAdmin() {
        return reservaZonaComunService.obtenerTodos().stream()
                .filter(this::esDeResidente)
                .filter(reserva -> {
                    String zona = leerComoTexto(reserva, "zonaComun");
                    return zona != null && TIPOS_RESERVA_ADMIN.contains(zona.trim().toLowerCase());
                })
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

    @GetMapping("/{id}")
    public Optional<ReservaZonaComun> obtenerPorId(@PathVariable Long id) {
        return reservaZonaComunService.obtenerPorId(id);
    }

    @PostMapping
    public ReservaZonaComun guardar(@RequestBody ReservaZonaComun reservaZonaComun) {
        return reservaZonaComunService.guardar(reservaZonaComun);
    }

    @DeleteMapping("/{id}")
    public void eliminar(@PathVariable Long id) {
        reservaZonaComunService.eliminar(id);
    }
}
