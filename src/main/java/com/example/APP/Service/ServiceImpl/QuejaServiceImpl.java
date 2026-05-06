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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
    public List<Map<String, Object>> obtenerTarjetasAdmin(String categoria) {
        List<Queja> quejas = quejaRepository.findAll();
        List<Map<String, Object>> tarjetas = new ArrayList<>();
        String categoriaFiltro = categoria != null ? categoria.trim().toUpperCase() : "";

        for (Queja queja : quejas) {
            String categoriaQueja = categorizarQueja(queja);
            if (!categoriaFiltro.isBlank() && !categoriaQueja.equals(categoriaFiltro)) {
                continue;
            }
            tarjetas.add(mapearTarjetaAdmin(queja, categoriaQueja));
        }
        return tarjetas;
    }

    @Override
    public Map<String, Object> obtenerTarjetasAdminPaginadas(String categoria, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("El parámetro page no puede ser negativo");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("El parámetro size debe ser mayor a 0");
        }
        if (size > 100) {
            throw new IllegalArgumentException("El parámetro size no debe ser mayor a 100");
        }

        String categoriaFiltro = categoria != null ? categoria.trim().toUpperCase() : "";
        if (categoriaFiltro.isBlank()) {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fechaCreacion"));
            Page<Queja> pagina = quejaRepository.findAll(pageable);
            List<Map<String, Object>> tarjetas = pagina.getContent().stream()
                    .map(queja -> mapearTarjetaAdmin(queja, categorizarQueja(queja)))
                    .toList();

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("content", tarjetas);
            respuesta.put("page", pagina.getNumber());
            respuesta.put("size", pagina.getSize());
            respuesta.put("totalElements", pagina.getTotalElements());
            respuesta.put("totalPages", pagina.getTotalPages());
            respuesta.put("first", pagina.isFirst());
            respuesta.put("last", pagina.isLast());
            return respuesta;
        }

        List<Map<String, Object>> filtradas = obtenerTarjetasAdmin(categoriaFiltro);
        int total = filtradas.size();
        int inicio = page * size;
        int fin = Math.min(inicio + size, total);
        List<Map<String, Object>> contenido = inicio >= total ? List.of() : filtradas.subList(inicio, fin);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("content", contenido);
        respuesta.put("page", page);
        respuesta.put("size", size);
        respuesta.put("totalElements", total);
        respuesta.put("totalPages", (int) Math.ceil(total / (double) size));
        respuesta.put("first", page == 0);
        respuesta.put("last", fin >= total);
        return respuesta;
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
        
        String descripcion = textoPreferido(payload, "mensaje", "descripcion");
        String tipoQueja = textoPreferido(payload, "tipo", "tipoQueja");
        if (tipoQueja == null || tipoQueja.isBlank()) {
            tipoQueja = extraerTexto(payload, "clasificacion");
        }
        if (tipoQueja == null || tipoQueja.isBlank()) {
            tipoQueja = extraerTexto(payload, "tipo");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("El campo mensaje es obligatorio");
        }
        if (tipoQueja == null || tipoQueja.isBlank()) {
            throw new IllegalArgumentException("El campo tipo es obligatorio");
        }
        
        String torreAcusado = textoPreferido(payload, "torreAcusado", "torre");
        if (torreAcusado == null || torreAcusado.isBlank()) {
            throw new IllegalArgumentException("El campo torreAcusado es obligatorio");
        }
        String apartamentoAcusado = textoPreferido(payload, "apartamentoAcusado", "apartamento");
        
        LocalDateTime fecha = parseFecha(extraerTexto(payload, "fecha"));
        if (fecha == null) {
            throw new IllegalArgumentException("El campo fecha es obligatorio");
        }
        
        Queja queja = new Queja();
        queja.setUsuario(usuario);
        queja.setDescripcion(descripcion.trim());
        queja.setTipoQueja(tipoQueja.trim().toUpperCase());
        queja.setTorre(torreAcusado.trim());
        queja.setApartamento(apartamentoAcusado != null ? apartamentoAcusado.trim() : null);
        queja.setFechaCreacion(fecha);
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

    private Map<String, Object> construirCategoria(String categoria) {
        Map<String, Object> categoriaMap = new LinkedHashMap<>();
        categoriaMap.put("id", categoria.toLowerCase());
        categoriaMap.put("nombre", categoria.replace("_", " "));
        return categoriaMap;
    }

    private Map<String, Object> construirUsuarioResumen(Usuario usuario) {
        Map<String, Object> usuarioMap = new LinkedHashMap<>();
        if (usuario == null) {
            return usuarioMap;
        }
        usuarioMap.put("id", usuario.getId());
        usuarioMap.put("nombre", usuario.getNombre());
        usuarioMap.put("usuario", usuario.getUsuario());
        usuarioMap.put("torre", usuario.getTorre());
        usuarioMap.put("apartamento", usuario.getApartamento());
        return usuarioMap;
    }

    private Map<String, Object> mapearTarjetaAdmin(Queja queja, String categoriaQueja) {
        Map<String, Object> tarjeta = new LinkedHashMap<>();
        tarjeta.put("id", queja.getId());
        tarjeta.put("descripcion", queja.getDescripcion());
        tarjeta.put("fecha", queja.getFechaCreacion());
        tarjeta.put("estado", queja.getEstado());
        tarjeta.put("categoria", construirCategoria(categoriaQueja));
        tarjeta.put("usuario", construirUsuarioResumen(queja.getUsuario()));
        return tarjeta;
    }
    
    private String extraerTexto(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value != null ? value.toString() : null;
    }

    private String textoPreferido(Map<String, Object> payload, String principal, String alterno) {
        String valorPrincipal = extraerTexto(payload, principal);
        if (valorPrincipal != null && !valorPrincipal.isBlank()) {
            return valorPrincipal;
        }
        return extraerTexto(payload, alterno);
    }
    
    private LocalDateTime parseFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        String valor = fecha.trim();
        try {
            return LocalDateTime.parse(valor);
        } catch (Exception ignored) {
            // Continua con formatos alternos para compatibilidad con frontend.
        }
        try {
            return LocalDate.parse(valor).atStartOfDay();
        } catch (Exception ignored) {
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(valor, formatter).atStartOfDay();
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Formato de fecha inválido. Usa ISO 8601, yyyy-MM-dd o dd/MM/yyyy");
    }
}
