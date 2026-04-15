package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.Usuario;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.UsuarioService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("El campo 'password' es obligatorio");
        }
        usuario.setPassword(codificarPasswordSiHaceFalta(usuario.getPassword()));
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
        nuevoUsuario.setPassword(codificarPasswordSiHaceFalta(password));

        return usuarioRepository.save(nuevoUsuario);
    }

    @Override
    public void eliminar(Long id) {
        usuarioRepository.deleteById(id);
    }

    @Override
    public Usuario login(String usuario, String password) {
        Usuario user = usuarioRepository
                .findByUsuario(usuario)
                .orElseThrow(() -> new BadCredentialsException("Usuario o contraseña incorrectos"));

        String storedPassword = user.getPassword();
        if (storedPassword == null || storedPassword.isBlank()) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        // Compatibilidad controlada: si el password estaba en plano, migrarlo al hash al primer login exitoso.
        if (esHashBcrypt(storedPassword)) {
            if (!passwordEncoder.matches(password, storedPassword)) {
                throw new BadCredentialsException("Usuario o contraseña incorrectos");
            }
            return user;
        }

        if (!storedPassword.equals(password)) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        user.setPassword(codificarPasswordSiHaceFalta(password));
        return usuarioRepository.save(user);
    }

    @Override
    public Usuario obtenerPorUsuario(String usuario) {
        return usuarioRepository.findByUsuario(usuario)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
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

    private String codificarPasswordSiHaceFalta(String rawPassword) {
        String trimmed = rawPassword.trim();
        return esHashBcrypt(trimmed) ? trimmed : passwordEncoder.encode(trimmed);
    }

    private boolean esHashBcrypt(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

}