package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.Usuario;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.UsuarioService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;

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
        if (usuario.getPassword() == null || usuario.getPassword().isBlank()) {
            throw new IllegalArgumentException("El campo 'password' es obligatorio");
        }
        usuario.setPassword(usuario.getPassword().trim());
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
        nuevoUsuario.setPassword(password.trim());

        return usuarioRepository.save(nuevoUsuario);
    }

    @Override
    public Usuario resetearPasswordPorId(Long id, String nuevaPassword) {
        if (nuevaPassword == null || nuevaPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("El campo 'nuevaPassword' es obligatorio");
        }
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        usuario.setPassword(nuevaPassword.trim());
        return usuarioRepository.save(usuario);
    }

    @Override
    public Usuario resetearPasswordPorUsuario(String usuario, String nuevaPassword) {
        if (usuario == null || usuario.trim().isEmpty()) {
            throw new IllegalArgumentException("El campo 'usuario' es obligatorio");
        }
        if (nuevaPassword == null || nuevaPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("El campo 'nuevaPassword' es obligatorio");
        }
        Usuario usuarioExistente = usuarioRepository.findByUsuario(usuario.trim())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        usuarioExistente.setPassword(nuevaPassword.trim());
        return usuarioRepository.save(usuarioExistente);
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

        if (!storedPassword.equals(password)) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        return user;
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

}