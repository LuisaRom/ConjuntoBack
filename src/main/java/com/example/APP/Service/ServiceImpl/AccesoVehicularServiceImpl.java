package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.AccesoVehicular;
import com.example.APP.Repository.AccesoVehicularRepository;
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
        if (accesoVehicular.getHoraAutorizada() == null) {
            accesoVehicular.setHoraAutorizada(LocalDateTime.now());
        }
        if (accesoVehicular.getCodigoQr() == null || accesoVehicular.getCodigoQr().isBlank()) {
            String placa = accesoVehicular.getPlacaVehiculo() != null ? accesoVehicular.getPlacaVehiculo().trim() : "SIN-PLACA";
            String torre = accesoVehicular.getTorre() != null ? accesoVehicular.getTorre().trim() : "";
            String apartamento = accesoVehicular.getApartamento() != null ? accesoVehicular.getApartamento().trim() : "";
            accesoVehicular.setCodigoQr("VEHICULAR|" + placa + "|" + torre + "|" + apartamento + "|" + System.currentTimeMillis());
        }
        return accesoVehicularRepository.save(accesoVehicular);
    }

    @Override
    public void eliminar(Long id) {
        accesoVehicularRepository.deleteById(id);
    }
}