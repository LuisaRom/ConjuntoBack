package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.Usuario;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.UsuarioService;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
    }

    @Override
    public Optional<Usuario> obtenerPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }
    
    @Override
    public Usuario crearUsuario(Map<String, Object> payload) {
        String nombre = extraerTexto(payload, "nombre");
        String email = extraerTexto(payload, "email");
        String telefono = extraerTexto(payload, "telefono");
        String rolTexto = extraerTexto(payload, "rol");

        validarObligatorio(nombre, "nombre completo");
        validarObligatorio(email, "email");
        validarObligatorio(telefono, "telefono");
        validarObligatorio(rolTexto, "rol");

        Usuario.Rol rol;
        try {
            rol = Usuario.Rol.valueOf(rolTexto.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Rol inválido. Usa RESIDENTE o CELADOR");
        }

        String usuarioTexto = extraerTexto(payload, "usuario");
        String apartamento = extraerTexto(payload, "apartamento");
        String torre = extraerTexto(payload, "torre");
        String password = extraerTexto(payload, "password");

        if (rol == Usuario.Rol.RESIDENTE) {
            validarObligatorio(usuarioTexto, "usuario");
            validarObligatorio(apartamento, "apartamento");
            validarObligatorio(torre, "torre");
            validarObligatorio(password, "password");
        } else if (rol == Usuario.Rol.CELADOR) {
            validarObligatorio(usuarioTexto, "usuario");
            validarObligatorio(password, "password");
            apartamento = "";
            torre = "";
        } else {
            throw new IllegalArgumentException("Este endpoint solo permite RESIDENTE o CELADOR");
        }

        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setNombre(nombre.trim());
        // Se reutiliza 'documento' para persistir email, manteniendo el modelo existente.
        nuevoUsuario.setDocumento(email.trim());
        nuevoUsuario.setTelefono(telefono.trim());
        nuevoUsuario.setUsuario(usuarioTexto.trim());
        nuevoUsuario.setRol(rol);
        nuevoUsuario.setApartamento(apartamento != null ? apartamento.trim() : "");
        nuevoUsuario.setTorre(torre != null ? torre.trim() : "");
        nuevoUsuario.setPassword(passwordEncoder.encode(password));

        return usuarioRepository.save(nuevoUsuario);
    }

    @Override
    public void eliminar(Long id) {
        usuarioRepository.deleteById(id);
    }

    @Override
    public Usuario login(String usuario, String password) {
        return usuarioRepository
                .findByUsuario(usuario)
                .filter(u -> {
                    String hash = u.getPassword();
                    if (hash == null) return false;
                    if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
                        return passwordEncoder.matches(password, hash);
                    }
                    // Compatibilidad temporal con usuarios antiguos no encriptados.
                    return hash.equals(password);
                })
                .orElseThrow(() -> new RuntimeException("Usuario o contraseña incorrectos"));
    }
    
    private String extraerTexto(Map<String, Object> payload, String key) {
        Object valor = payload.get(key);
        return valor != null ? valor.toString() : null;
    }
    
    private void validarObligatorio(String valor, String nombreCampo) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El campo '" + nombreCampo + "' es obligatorio");
        }
    }

}