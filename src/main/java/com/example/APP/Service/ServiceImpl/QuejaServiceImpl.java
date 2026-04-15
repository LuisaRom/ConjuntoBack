package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.Notificacion;
import com.example.APP.Model.Queja;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.QuejaRepository;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.NotificacionService;
import com.example.APP.Service.QuejaService;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QuejaServiceImpl implements QuejaService {

    @Autowired
    private QuejaRepository quejaRepository;
    
    @Autowired
    private NotificacionService notificacionService;
    
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public List<Queja> obtenerTodos() {
        return quejaRepository.findAll();
    }
    
    @Override
    public Map<String, List<Queja>> obtenerTodasAgrupadasPorCategoria() {
        return quejaRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        this::categorizarQueja,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }
    
    @Override
    public Map<String, List<Queja>> obtenerTodasAgrupadasPorCategoria(String categoria) {
        Map<String, List<Queja>> agrupadas = obtenerTodasAgrupadasPorCategoria();
        if (categoria == null || categoria.trim().isEmpty()) {
            return agrupadas;
        }
        String categoriaBuscada = categoria.trim().toUpperCase();
        List<Queja> lista = agrupadas.getOrDefault(categoriaBuscada, List.of());
        Map<String, List<Queja>> resultado = new LinkedHashMap<>();
        resultado.put(categoriaBuscada, lista);
        return resultado;
    }

    @Override
    public Optional<Queja> obtenerPorId(Long id) {
        return quejaRepository.findById(id);
    }

    @Override
    public Queja guardar(Queja queja) {
        return quejaRepository.save(queja);
    }
    
    @Override
    public Queja crearQueja(Map<String, Object> payload, String usernameAutenticado) {
        if (usernameAutenticado == null || usernameAutenticado.isBlank()) {
            throw new IllegalArgumentException("No hay usuario autenticado");
        }
        
        Usuario usuario = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        String descripcion = extraerTexto(payload, "descripcion");
        String tipoQueja = extraerTexto(payload, "tipoQueja");
        if (tipoQueja == null || tipoQueja.isBlank()) {
            tipoQueja = extraerTexto(payload, "clasificacion");
        }
        if (tipoQueja == null || tipoQueja.isBlank()) {
            tipoQueja = extraerTexto(payload, "tipo");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("El campo descripcion es obligatorio");
        }
        if (tipoQueja == null || tipoQueja.isBlank()) {
            throw new IllegalArgumentException("El campo tipo de queja/clasificacion es obligatorio");
        }
        
        String torreUsuario = usuario.getTorre();
        String aptoUsuario = usuario.getApartamento();
        String torrePayload = extraerTexto(payload, "torre");
        String apartamentoPayload = extraerTexto(payload, "apartamento");
        
        String torreFinal = (torreUsuario != null && !torreUsuario.isBlank()) ? torreUsuario : torrePayload;
        if (torreFinal == null || torreFinal.isBlank()) {
            throw new IllegalArgumentException("La torre es obligatoria");
        }
        String apartamentoFinal = (aptoUsuario != null && !aptoUsuario.isBlank()) ? aptoUsuario : apartamentoPayload;
        
        LocalDateTime fecha = parseFecha(extraerTexto(payload, "fecha"));
        
        Queja queja = new Queja();
        queja.setUsuario(usuario);
        queja.setDescripcion(descripcion.trim());
        queja.setTipoQueja(tipoQueja.trim().toUpperCase());
        queja.setTorre(torreFinal.trim());
        queja.setApartamento(apartamentoFinal != null ? apartamentoFinal.trim() : null);
        queja.setFechaCreacion(fecha != null ? fecha : LocalDateTime.now());
        queja.setEstado(Queja.Estado.PENDIENTE);
        
        return quejaRepository.save(queja);
    }
    
    @Override
    public Queja enProceso(Long id) {
        Queja queja = quejaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Queja no encontrada con id: " + id));
        BeanWrapperImpl beanQueja = new BeanWrapperImpl(queja);
        if (beanQueja.isWritableProperty("estado")) {
            beanQueja.setPropertyValue("estado", Queja.Estado.EN_PROCESO);
        }
        return quejaRepository.save(queja);
    }
    
    @Override
    public Queja finalizar(Long id) {
        Queja queja = quejaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Queja no encontrada con id: " + id));
        
        BeanWrapperImpl beanQueja = new BeanWrapperImpl(queja);
        if (beanQueja.isWritableProperty("estado")) {
            beanQueja.setPropertyValue("estado", Queja.Estado.RESUELTO);
        }
        
        Queja quejaActualizada = quejaRepository.save(queja);
        
        Usuario usuario = null;
        if (beanQueja.isReadableProperty("usuario")) {
            Object usuarioObj = beanQueja.getPropertyValue("usuario");
            if (usuarioObj instanceof Usuario u) {
                usuario = u;
            }
        }
        if (usuario == null) {
            throw new RuntimeException("La queja no tiene usuario asociado para notificación");
        }
        
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(usuario);
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacion.setMensaje("Tu queja #" + id + " fue finalizada por administración.");
        notificacionService.guardar(notificacion);
        
        return quejaActualizada;
    }

    @Override
    public void eliminar(Long id) {
        quejaRepository.deleteById(id);
    }
    
    private String categorizarQueja(Queja queja) {
        BeanWrapperImpl beanQueja = new BeanWrapperImpl(queja);
        if (beanQueja.isReadableProperty("tipoQueja")) {
            Object tipoObj = beanQueja.getPropertyValue("tipoQueja");
            if (tipoObj != null && !tipoObj.toString().isBlank()) {
                return tipoObj.toString().trim().toUpperCase();
            }
        }
        String descripcion = "";
        if (beanQueja.isReadableProperty("descripcion")) {
            Object valor = beanQueja.getPropertyValue("descripcion");
            descripcion = valor != null ? valor.toString().toLowerCase() : "";
        }
        
        if (descripcion.contains("ruido")) return "RUIDO";
        if (descripcion.contains("mascota") || descripcion.contains("perro") || descripcion.contains("gato")) return "MASCOTA";
        if (descripcion.contains("violencia") || descripcion.contains("agres")) return "VIOLENCIA";
        if (descripcion.contains("bbq") || descripcion.contains("piscina") || descripcion.contains("gimnasio") || descripcion.contains("salon")) return "ZONAS_COMUNES";
        return "GENERAL";
    }
    
    private String extraerTexto(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }
    
    private LocalDateTime parseFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(fecha);
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de fecha inválido. Usa ISO 8601");
        }
    }
}
