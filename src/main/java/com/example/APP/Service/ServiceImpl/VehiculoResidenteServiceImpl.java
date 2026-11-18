package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.Notificacion;
import com.example.APP.Model.Usuario;
import com.example.APP.Model.VehiculoResidente;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Repository.VehiculoResidenteRepository;
import com.example.APP.Service.NotificacionService;
import com.example.APP.Service.VehiculoResidenteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class VehiculoResidenteServiceImpl implements VehiculoResidenteService {

    private static final Logger log = LoggerFactory.getLogger(VehiculoResidenteServiceImpl.class);
    private static final Set<String> TIPOS = Set.of("CARRO", "MOTO", "CAMIONETA", "OTRO");

    @Autowired
    private VehiculoResidenteRepository vehiculoResidenteRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Override
    public List<VehiculoResidente> obtenerTodos() {
        return vehiculoResidenteRepository.findAll();
    }

    @Override
    public Optional<VehiculoResidente> obtenerPorId(Long id) {
        return vehiculoResidenteRepository.findById(id);
    }

    @Override
    public VehiculoResidente guardar(VehiculoResidente vehiculoResidente) {
        vehiculoResidente.setPlaca(normalizarPlaca(vehiculoResidente.getPlaca()));
        validarPlacaUnica(vehiculoResidente.getPlaca(), vehiculoResidente.getId());
        if (vehiculoResidente.getFechaRegistro() == null) {
            vehiculoResidente.setFechaRegistro(LocalDateTime.now());
        }
        return vehiculoResidenteRepository.save(vehiculoResidente);
    }

    @Override
    public void eliminar(Long id) {
        vehiculoResidenteRepository.deleteById(id);
    }

    @Override
    public List<VehiculoResidente> listarDeUsuario(String username) {
        return vehiculoResidenteRepository.findByUsuarioId(usuario(username).getId());
    }

    @Override
    public VehiculoResidente registrarParaUsuario(String username, Map<String, Object> datos) {
        Usuario usuario = usuario(username);
        String placa = normalizarPlaca(texto(datos, "placa"));
        String marca = texto(datos, "marca");
        String modelo = texto(datos, "modelo");
        if (marca == null || modelo == null) {
            throw new IllegalArgumentException("Marca y modelo son obligatorios");
        }
        validarPlacaUnica(placa, null);

        String tipo = Optional.ofNullable(texto(datos, "tipo")).orElse("CARRO").toUpperCase();
        VehiculoResidente v = new VehiculoResidente();
        v.setUsuario(usuario);
        v.setPlaca(placa);
        v.setMarca(capitalizar(marca));
        v.setModelo(modelo.trim());
        v.setColor(Optional.ofNullable(texto(datos, "color")).map(this::capitalizar).orElse(null));
        v.setTipo(TIPOS.contains(tipo) ? tipo : "OTRO");
        v.setFechaRegistro(LocalDateTime.now());
        return vehiculoResidenteRepository.save(v);
    }

    @Override
    public void eliminarDeUsuario(String username, Long id) {
        Usuario usuario = usuario(username);
        VehiculoResidente v = vehiculoResidenteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado"));
        if (v.getUsuario() == null || !usuario.getId().equals(v.getUsuario().getId())) {
            throw new IllegalArgumentException("Solo puedes eliminar tus propios vehículos");
        }
        vehiculoResidenteRepository.delete(v);
    }

    @Override
    public VehiculoResidente asignarParqueadero(Long id, String parqueadero) {
        VehiculoResidente v = vehiculoResidenteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado"));
        String valor = parqueadero == null ? "" : parqueadero.trim().toUpperCase();

        if (valor.isEmpty()) {
            v.setParqueadero(null);
            v.setFechaAsignacion(null);
            return vehiculoResidenteRepository.save(v);
        }
        vehiculoResidenteRepository.findByParqueaderoIgnoreCase(valor)
                .filter(otro -> !otro.getId().equals(v.getId()))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException("El parqueadero " + valor + " ya está asignado al vehículo " + otro.getPlaca());
                });
        v.setParqueadero(valor);
        v.setFechaAsignacion(LocalDateTime.now());
        VehiculoResidente guardado = vehiculoResidenteRepository.save(v);
        notificarAsignacion(guardado);
        return guardado;
    }

    @Override
    public Map<String, Object> resumen() {
        List<VehiculoResidente> todos = vehiculoResidenteRepository.findAll();
        long asignados = todos.stream().filter(v -> v.getParqueadero() != null && !v.getParqueadero().isBlank()).count();
        Map<String, Long> porTipo = new LinkedHashMap<>();
        for (VehiculoResidente v : todos) {
            porTipo.merge(Optional.ofNullable(v.getTipo()).orElse("CARRO"), 1L, Long::sum);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", todos.size());
        r.put("conParqueadero", asignados);
        r.put("sinParqueadero", todos.size() - asignados);
        r.put("porTipo", porTipo);
        return r;
    }

    private void notificarAsignacion(VehiculoResidente v) {
        if (v.getUsuario() == null) {
            return;
        }
        try {
            Notificacion n = new Notificacion();
            n.setUsuario(v.getUsuario());
            n.setFechaEnvio(LocalDateTime.now());
            n.setMensaje("Se asignó el parqueadero " + v.getParqueadero() + " a tu vehículo de placa " + v.getPlaca() + ".");
            notificacionService.guardar(n);
        } catch (Exception e) {
            log.warn("No se pudo notificar la asignación de parqueadero: {}", e.getMessage());
        }
    }

    private void validarPlacaUnica(String placa, Long idActual) {
        vehiculoResidenteRepository.findByPlacaIgnoreCase(placa)
                .filter(otro -> idActual == null || !otro.getId().equals(idActual))
                .ifPresent(otro -> {
                    throw new IllegalArgumentException("La placa " + placa + " ya está registrada");
                });
    }

    private String normalizarPlaca(String placa) {
        if (placa == null || placa.isBlank()) {
            throw new IllegalArgumentException("La placa es obligatoria");
        }
        String limpia = placa.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (!limpia.matches("[A-Z]{3}\\d{2}[A-Z0-9]?")) {
            throw new IllegalArgumentException("Placa no válida. Usa el formato ABC123 para carros o ABC12D para motos");
        }
        return limpia;
    }

    private String capitalizar(String texto) {
        String t = texto.trim();
        return t.isEmpty() ? t : t.substring(0, 1).toUpperCase() + t.substring(1);
    }

    private String texto(Map<String, Object> datos, String clave) {
        Object v = datos.get(clave);
        return v == null || v.toString().isBlank() ? null : v.toString().trim();
    }

    private Usuario usuario(String username) {
        return usuarioRepository.findByUsuario(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }
}
