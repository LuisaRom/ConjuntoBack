package com.example.APP.Controller;

import com.example.APP.Model.ConocimientoIA;
import com.example.APP.Model.MensajeIA;
import com.example.APP.Model.Queja;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.ConocimientoIAService;
import com.example.APP.Service.IaChatService;
import com.example.APP.Service.IaGestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class IaController {

    private final IaChatService chatService;
    private final IaGestionService gestionService;
    private final ConocimientoIAService conocimientoService;
    private final UsuarioRepository usuarioRepository;

    public IaController(IaChatService chatService, IaGestionService gestionService,
                        ConocimientoIAService conocimientoService, UsuarioRepository usuarioRepository) {
        this.chatService = chatService;
        this.gestionService = gestionService;
        this.conocimientoService = conocimientoService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return chatService.estado();
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> payload, Authentication auth) {
        Object id = payload.get("conversacionId");
        Long conversacionId = id == null || id.toString().isBlank() ? null : Long.valueOf(id.toString().replace(".0", ""));
        return chatService.chat(auth.getName(), conversacionId, texto(payload, "mensaje"));
    }

    @GetMapping("/conversaciones")
    public List<Map<String, Object>> conversaciones(Authentication auth) {
        return chatService.conversaciones(auth.getName());
    }

    @GetMapping("/conversaciones/{id}/mensajes")
    public List<MensajeIA> mensajes(@PathVariable Long id, Authentication auth) {
        return chatService.mensajes(auth.getName(), id);
    }

    @DeleteMapping("/conversaciones/{id}")
    public ResponseEntity<Void> eliminarConversacion(@PathVariable Long id, Authentication auth) {
        chatService.eliminarConversacion(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mensajes/{id}/feedback")
    public Map<String, Object> feedback(@PathVariable Long id, @RequestBody Map<String, Object> payload, Authentication auth) {
        boolean util = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("util", "false")));
        return chatService.feedback(auth.getName(), id, util);
    }

    @GetMapping("/conocimiento")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<ConocimientoIA> listarConocimiento(@RequestParam(name = "estado", required = false) String estado) {
        return conocimientoService.listar(estado);
    }

    @PostMapping("/conocimiento")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ConocimientoIA crearConocimiento(@RequestBody Map<String, Object> payload, Authentication auth) {
        Usuario admin = usuarioRepository.findByUsuario(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return conocimientoService.registrar(texto(payload, "titulo"), texto(payload, "contenido"),
                texto(payload, "categoria"), admin, ConocimientoIA.Origen.ADMINISTRADOR);
    }

    @PutMapping("/conocimiento/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ConocimientoIA actualizarConocimiento(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return conocimientoService.actualizar(id, payload);
    }

    @PutMapping("/conocimiento/{id}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ConocimientoIA aprobar(@PathVariable Long id) {
        return conocimientoService.cambiarEstado(id, ConocimientoIA.Estado.APROBADO);
    }

    @PutMapping("/conocimiento/{id}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ConocimientoIA rechazar(@PathVariable Long id) {
        return conocimientoService.cambiarEstado(id, ConocimientoIA.Estado.RECHAZADO);
    }

    @DeleteMapping("/conocimiento/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> eliminarConocimiento(@PathVariable Long id) {
        conocimientoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/feedback/negativos")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<Map<String, Object>> respuestasMalCalificadas() {
        return chatService.respuestasMalCalificadas();
    }

    @GetMapping("/gestion/resumen")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String, Object> resumenGestion() {
        return gestionService.resumenGestion();
    }

    @PostMapping("/comunicados/redactar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public Map<String, Object> redactarComunicado(@RequestBody Map<String, Object> payload) {
        return gestionService.redactarComunicado(texto(payload, "tema"), texto(payload, "tono"), texto(payload, "detalles"));
    }

    @PostMapping("/quejas/{id}/analizar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'CELADOR')")
    public Queja analizarQueja(@PathVariable Long id) {
        return gestionService.analizarQueja(id);
    }

    private String texto(Map<String, Object> payload, String clave) {
        Object v = payload.get(clave);
        return v == null ? null : v.toString();
    }
}
