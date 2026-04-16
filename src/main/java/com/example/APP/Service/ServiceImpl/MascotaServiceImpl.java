package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.Mascota;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.MascotaRepository;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.MascotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MascotaServiceImpl implements MascotaService {

    @Autowired
    private MascotaRepository mascotaRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public List<Mascota> obtenerTodos() {
        return mascotaRepository.findAll();
    }

    @Override
    public Optional<Mascota> obtenerPorId(Long id) {
        return mascotaRepository.findById(id);
    }

    @Override
    public Mascota guardar(Mascota mascota) {
        return mascotaRepository.save(mascota);
    }
    
    @Override
    public Mascota crearMascota(String nombre, String tipo, String raza, String usernameAutenticado, MultipartFile foto) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El campo nombre es obligatorio");
        }
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("El campo tipo es obligatorio");
        }
        if (usernameAutenticado == null || usernameAutenticado.isBlank()) {
            throw new IllegalArgumentException("No hay usuario autenticado");
        }
        if (foto == null || foto.isEmpty()) {
            throw new IllegalArgumentException("La foto es obligatoria");
        }
        validarArchivoImagen(foto);
        
        Usuario usuario = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        String fotoUrl = guardarFoto(foto);
        
        Mascota mascota = new Mascota();
        mascota.setNombre(nombre.trim());
        mascota.setTipo(tipo.trim());
        mascota.setRaza(raza != null ? raza.trim() : "");
        mascota.setFotoUrl(fotoUrl);
        mascota.setUsuario(usuario);
        
        return mascotaRepository.save(mascota);
    }

    @Override
    public void eliminar(Long id) {
        mascotaRepository.deleteById(id);
    }
    
    private String guardarFoto(MultipartFile foto) {
        try {
            Path carpeta = Paths.get("uploads", "mascotas").toAbsolutePath().normalize();
            Files.createDirectories(carpeta);
            
            String nombreOriginal = foto.getOriginalFilename() != null ? foto.getOriginalFilename() : "foto";
            String extension = "";
            int idx = nombreOriginal.lastIndexOf('.');
            if (idx >= 0) {
                extension = nombreOriginal.substring(idx);
            }
            
            String nombreArchivo = UUID.randomUUID() + extension;
            Path destino = carpeta.resolve(nombreArchivo);
            Files.copy(foto.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            return destino.toString();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar la foto de la mascota");
        }
    }

    private void validarArchivoImagen(MultipartFile foto) {
        String contentType = foto.getContentType() != null ? foto.getContentType().toLowerCase() : "";
        if (!(contentType.equals("image/jpeg")
                || contentType.equals("image/jpg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp"))) {
            throw new IllegalArgumentException("La foto debe ser JPG, PNG o WEBP");
        }

        long maxBytes = 5L * 1024L * 1024L;
        if (foto.getSize() > maxBytes) {
            throw new IllegalArgumentException("La foto no debe superar 5MB");
        }
    }
}