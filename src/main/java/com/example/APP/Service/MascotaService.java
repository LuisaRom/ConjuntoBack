package com.example.APP.Service;

import com.example.APP.Model.Mascota;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface MascotaService {

    List<Mascota> obtenerTodos();
    Optional<Mascota> obtenerPorId(Long id);
    Mascota guardar(Mascota mascota);
    Mascota crearMascota(String nombre, String tipo, String raza, Long usuarioId, MultipartFile foto);
    void eliminar(Long id);
}
