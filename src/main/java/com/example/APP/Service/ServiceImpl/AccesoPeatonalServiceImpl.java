package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.AccesoPeatonal;
import com.example.APP.Repository.AccesoPeatonalRepository;
import com.example.APP.Service.AccesoPeatonalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AccesoPeatonalServiceImpl implements AccesoPeatonalService {

    @Autowired
    private AccesoPeatonalRepository accesoPeatonalRepository;

    @Override
    public List<AccesoPeatonal> obtenerTodos() {
        return accesoPeatonalRepository.findAll();
    }

    @Override
    public Optional<AccesoPeatonal> obtenerPorId(Long id) {
        return accesoPeatonalRepository.findById(id);
    }

    @Override
    public AccesoPeatonal guardar(AccesoPeatonal accesoPeatonal) {
        if (accesoPeatonal.getHoraAutorizada() == null) {
            accesoPeatonal.setHoraAutorizada(LocalDateTime.now());
        }
        if (accesoPeatonal.getCodigoQr() == null || accesoPeatonal.getCodigoQr().isBlank()) {
            String nombre = accesoPeatonal.getNombreVisitante() != null ? accesoPeatonal.getNombreVisitante().trim() : "VISITANTE";
            String torre = accesoPeatonal.getTorre() != null ? accesoPeatonal.getTorre().trim() : "";
            String apartamento = accesoPeatonal.getApartamento() != null ? accesoPeatonal.getApartamento().trim() : "";
            accesoPeatonal.setCodigoQr("PEATONAL|" + nombre + "|" + torre + "|" + apartamento + "|" + System.currentTimeMillis());
        }
        return accesoPeatonalRepository.save(accesoPeatonal);
    }

    @Override
    public void eliminar(Long id) {
        accesoPeatonalRepository.deleteById(id);
    }
}