package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.Paqueteria;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.PaqueteriaRepository;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.PaqueteriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaqueteriaServiceImpl implements PaqueteriaService {

    @Autowired
    private PaqueteriaRepository paqueteriaRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public List<Paqueteria> obtenerTodos() {
        return paqueteriaRepository.findAll();
    }

    @Override
    public Optional<Paqueteria> obtenerPorId(Long id) {
        return paqueteriaRepository.findById(id);
    }

    @Override
    public Paqueteria guardar(Paqueteria paqueteria) {
        // Si el usuario tiene solo el ID, cargar el usuario completo desde la base de datos
        if (paqueteria.getUsuario() != null && paqueteria.getUsuario().getId() != null) {
            Optional<Usuario> usuarioOpt = usuarioRepository.findById(paqueteria.getUsuario().getId());
            if (usuarioOpt.isPresent()) {
                paqueteria.setUsuario(usuarioOpt.get());
            } else {
                throw new RuntimeException("Usuario no encontrado con id: " + paqueteria.getUsuario().getId());
            }
        }
        
        // Si no hay fecha, asignar la fecha actual
        if (paqueteria.getFechaRecepcion() == null) {
            paqueteria.setFechaRecepcion(java.time.LocalDateTime.now());
        }
        
        // Si no hay estado, asignar PENDIENTE por defecto
        if (paqueteria.getEstado() == null) {
            paqueteria.setEstado(Paqueteria.Estado.PENDIENTE);
        }
        
        return paqueteriaRepository.save(paqueteria);
    }

    @Override
    public Paqueteria crearPaquete(Map<String, Object> payload) {
        Long usuarioId = parseLong(payload.get("usuarioId"), "usuarioId");
        if (usuarioId == null) {
            throw new IllegalArgumentException("El campo usuarioId es obligatorio");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (usuario.getRol() != Usuario.Rol.RESIDENTE) {
            throw new IllegalArgumentException("Solo se permiten usuarios con rol RESIDENTE");
        }

        String transportadora = texto(payload.get("transportadora"));
        if (transportadora == null || transportadora.isBlank()) {
            throw new IllegalArgumentException("El campo transportadora es obligatorio");
        }

        Paqueteria paquete = new Paqueteria();
        paquete.setUsuario(usuario);
        paquete.setTransportadora(transportadora.trim());
        paquete.setFechaRecepcion(LocalDateTime.now());
        paquete.setEstado(Paqueteria.Estado.PENDIENTE);
        return paqueteriaRepository.save(paquete);
    }

    @Override
    public List<Map<String, Object>> obtenerResidentesParaPaqueteria() {
        return usuarioRepository.findAll().stream()
                .filter(usuario -> usuario.getRol() == Usuario.Rol.RESIDENTE)
                .sorted(Comparator
                        .comparing(Usuario::getTorre, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Usuario::getApartamento, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Usuario::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::mapearResidente)
                .toList();
    }

    @Override
    public Paqueteria actualizar(Long id, Paqueteria paqueteria) {
        return paqueteriaRepository.findById(id)
                .map(existing -> {
                    if (paqueteria.getTransportadora() != null) {
                        existing.setTransportadora(paqueteria.getTransportadora());
                    }
                    if (paqueteria.getFechaRecepcion() != null) {
                        existing.setFechaRecepcion(paqueteria.getFechaRecepcion());
                    }
                    if (paqueteria.getEstado() != null) {
                        existing.setEstado(paqueteria.getEstado());
                    }
                    if (paqueteria.getUsuario() != null) {
                        // Si el usuario tiene solo el ID, cargar el usuario completo desde la base de datos
                        if (paqueteria.getUsuario().getId() != null) {
                            Optional<Usuario> usuarioOpt = usuarioRepository.findById(paqueteria.getUsuario().getId());
                            if (usuarioOpt.isPresent()) {
                                existing.setUsuario(usuarioOpt.get());
                            }
                        } else {
                            existing.setUsuario(paqueteria.getUsuario());
                        }
                    }
                    return paqueteriaRepository.save(existing);
                })
                .orElseThrow(() -> new RuntimeException("Paquete no encontrado con id: " + id));
    }

    @Override
    public void eliminar(Long id) {
        paqueteriaRepository.deleteById(id);
    }

    private Map<String, Object> mapearResidente(Usuario usuario) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", usuario.getId());
        item.put("nombre", usuario.getNombre());
        item.put("torre", usuario.getTorre());
        item.put("apartamento", usuario.getApartamento());
        item.put("label", (usuario.getNombre() != null ? usuario.getNombre() : "Sin nombre")
                + " - Torre " + (usuario.getTorre() != null ? usuario.getTorre() : "-")
                + " Apto " + (usuario.getApartamento() != null ? usuario.getApartamento() : "-"));
        return item;
    }

    private String texto(Object value) {
        return value != null ? value.toString() : null;
    }

    private Long parseLong(Object value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El campo " + field + " es inválido");
        }
    }
}
