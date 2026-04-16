package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.AccesoVehicular;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.AccesoVehicularRepository;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.AccesoVehicularService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AccesoVehicularServiceImpl implements AccesoVehicularService {

    @Autowired
    private AccesoVehicularRepository accesoVehicularRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public List<AccesoVehicular> obtenerTodos() {
        return accesoVehicularRepository.findAll();
    }

    @Override
    public Optional<AccesoVehicular> obtenerPorId(Long id) {
        return accesoVehicularRepository.findById(id);
    }

    @Override
    public AccesoVehicular guardar(AccesoVehicular accesoVehicular) {
        String placa = textoSeguro(accesoVehicular.getPlacaVehiculo()).toUpperCase();
        if (placa.isBlank()) {
            throw new IllegalArgumentException("El campo 'placaVehiculo' es obligatorio");
        }
        accesoVehicular.setPlacaVehiculo(placa);

        if (accesoVehicular.getHoraAutorizada() == null) {
            accesoVehicular.setHoraAutorizada(LocalDateTime.now());
        }
        if (accesoVehicular.getCodigoQr() == null || accesoVehicular.getCodigoQr().isBlank()) {
            String torre = textoSeguro(accesoVehicular.getTorre());
            String apartamento = textoSeguro(accesoVehicular.getApartamento());
            accesoVehicular.setCodigoQr("VEHICULAR|" + placa + "|" + torre + "|" + apartamento + "|" + System.currentTimeMillis());
        }
        return accesoVehicularRepository.save(accesoVehicular);
    }

    @Override
    public AccesoVehicular guardarParaUsuarioAutenticado(AccesoVehicular accesoVehicular, String usernameAutenticado) {
        Usuario usuarioAutenticado = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));

        if (usuarioAutenticado.getRol() != Usuario.Rol.RESIDENTE) {
            throw new IllegalArgumentException("Solo los residentes pueden generar accesos vehiculares");
        }

        accesoVehicular.setAutorizadoPor(usuarioAutenticado);
        accesoVehicular.setTorre(textoSeguro(usuarioAutenticado.getTorre()));
        accesoVehicular.setApartamento(textoSeguro(usuarioAutenticado.getApartamento()));

        return guardar(accesoVehicular);
    }

    @Override
    public void eliminar(Long id) {
        accesoVehicularRepository.deleteById(id);
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor.trim() : "";
    }
}