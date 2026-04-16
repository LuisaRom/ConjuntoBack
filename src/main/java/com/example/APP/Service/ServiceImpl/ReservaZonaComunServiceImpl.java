package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.ReservaZonaComun;
import com.example.APP.Repository.ReservaZonaComunRepository;
import com.example.APP.Service.ReservaZonaComunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReservaZonaComunServiceImpl implements ReservaZonaComunService {

    @Autowired
    private ReservaZonaComunRepository reservaZonaComunRepository;

    @Override
    public List<ReservaZonaComun> obtenerTodos() {
        return reservaZonaComunRepository.findAll();
    }

    @Override
    public Optional<ReservaZonaComun> obtenerPorId(Long id) {
        return reservaZonaComunRepository.findById(id);
    }

    @Override
    public ReservaZonaComun guardar(ReservaZonaComun reservaZonaComun) {
        return reservaZonaComunRepository.save(reservaZonaComun);
    }
    
    @Override
    public ReservaZonaComun crearReserva(ReservaZonaComun reservaZonaComun) {
        validarCamposObligatorios(reservaZonaComun);
        
        String tipo = normalizarTipo(reservaZonaComun.getZonaComun());
        DayOfWeek dia = reservaZonaComun.getFechaReserva().getDayOfWeek();
        LocalTime inicio = reservaZonaComun.getHoraInicio();
        LocalTime fin = reservaZonaComun.getHoraFin();
        
        if (!fin.isAfter(inicio)) {
            throw new IllegalArgumentException("La hora fin debe ser posterior a la hora inicio");
        }
        
        validarReglasPorTipo(tipo, dia, inicio, fin);
        validarDisponibilidad(tipo, reservaZonaComun);
        String detalleServicios = calcularServiciosAdicionales(tipo, reservaZonaComun.getServiciosAdicionales());
        reservaZonaComun.setServiciosAdicionales(detalleServicios);
        
        return reservaZonaComunRepository.save(reservaZonaComun);
    }

    @Override
    public Map<String, Object> obtenerHorariosDisponibles(String zonaComun, LocalDate fechaReserva, Long usuarioId) {
        if (zonaComun == null || zonaComun.isBlank()) {
            throw new IllegalArgumentException("El campo zonaComun es obligatorio");
        }
        if (fechaReserva == null) {
            throw new IllegalArgumentException("El campo fechaReserva es obligatorio");
        }

        String tipo = normalizarTipo(zonaComun);
        List<Map<String, String>> franjasHabilitadas = obtenerFranjasHabilitadas(tipo, fechaReserva.getDayOfWeek());
        List<ReservaZonaComun> reservasDelDia = reservaZonaComunRepository
                .findByZonaComunIgnoreCaseAndFechaReserva(tipo, fechaReserva);

        List<Map<String, String>> horariosDisponibles = new ArrayList<>();
        for (Map<String, String> franja : franjasHabilitadas) {
            LocalTime inicioFranja = LocalTime.parse(franja.get("inicio"));
            LocalTime finFranja = LocalTime.parse(franja.get("fin"));
            horariosDisponibles.addAll(calcularBloquesDisponibles(inicioFranja, finFranja, reservasDelDia));
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("zonaComun", tipo);
        respuesta.put("fechaReserva", fechaReserva);
        respuesta.put("franjasHabilitadas", franjasHabilitadas);
        respuesta.put("horariosDisponibles", horariosDisponibles);
        respuesta.put("sinHorariosRepetidos", true);

        if ("zona bbq".equals(tipo) && usuarioId != null) {
            List<ReservaZonaComun> reservasUsuario = reservaZonaComunRepository
                    .findByZonaComunIgnoreCaseAndFechaReservaAndUsuarioId(tipo, fechaReserva, usuarioId);
            respuesta.put("usuarioId", usuarioId);
            respuesta.put("usuarioYaReservoEnElDia", !reservasUsuario.isEmpty());
            respuesta.put("maximoReservasPorDiaUsuario", 1);
        }
        return respuesta;
    }

    @Override
    public boolean estaDisponibleSalonComunal(java.time.LocalDate fechaReserva, LocalTime horaInicio, LocalTime horaFin) {
        if (fechaReserva == null) {
            throw new IllegalArgumentException("El campo fechaReserva es obligatorio");
        }
        if (horaInicio == null || horaFin == null) {
            throw new IllegalArgumentException("Los campos horaInicio y horaFin son obligatorios");
        }
        if (!horaFin.isAfter(horaInicio)) {
            throw new IllegalArgumentException("La hora fin debe ser posterior a la hora inicio");
        }

        validarHorarioSalonComunal(horaInicio, horaFin);

        List<ReservaZonaComun> reservasSalonDia = reservaZonaComunRepository
                .findByZonaComunIgnoreCaseAndFechaReserva("salon comunal", fechaReserva);

        for (ReservaZonaComun existente : reservasSalonDia) {
            if (existente.getHoraInicio() == null || existente.getHoraFin() == null) {
                continue;
            }
            boolean seCruza = horaInicio.isBefore(existente.getHoraFin())
                    && horaFin.isAfter(existente.getHoraInicio());
            if (seCruza) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean estaDisponibleZonaBbq(java.time.LocalDate fechaReserva, LocalTime horaInicio, LocalTime horaFin, Long usuarioId) {
        if (fechaReserva == null) {
            throw new IllegalArgumentException("El campo fechaReserva es obligatorio");
        }
        if (horaInicio == null || horaFin == null) {
            throw new IllegalArgumentException("Los campos horaInicio y horaFin son obligatorios");
        }
        if (usuarioId == null) {
            throw new IllegalArgumentException("El campo usuarioId es obligatorio");
        }
        if (!horaFin.isAfter(horaInicio)) {
            throw new IllegalArgumentException("La hora fin debe ser posterior a la hora inicio");
        }

        validarHorarioZonaBbq(fechaReserva.getDayOfWeek(), horaInicio, horaFin);

        List<ReservaZonaComun> reservasUsuario = reservaZonaComunRepository
                .findByZonaComunIgnoreCaseAndFechaReservaAndUsuarioId("zona bbq", fechaReserva, usuarioId);
        if (!reservasUsuario.isEmpty()) {
            return false;
        }

        List<ReservaZonaComun> reservasBbqDia = reservaZonaComunRepository
                .findByZonaComunIgnoreCaseAndFechaReserva("zona bbq", fechaReserva);

        for (ReservaZonaComun existente : reservasBbqDia) {
            if (existente.getHoraInicio() == null || existente.getHoraFin() == null) {
                continue;
            }
            boolean seCruza = horaInicio.isBefore(existente.getHoraFin())
                    && horaFin.isAfter(existente.getHoraInicio());
            if (seCruza) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void eliminar(Long id) {
        reservaZonaComunRepository.deleteById(id);
    }
    
    private void validarCamposObligatorios(ReservaZonaComun reserva) {
        if (reserva == null) {
            throw new IllegalArgumentException("La reserva es obligatoria");
        }
        if (reserva.getZonaComun() == null || reserva.getZonaComun().isBlank()) {
            throw new IllegalArgumentException("El campo zonaComun es obligatorio");
        }
        if (reserva.getFechaReserva() == null) {
            throw new IllegalArgumentException("El campo fechaReserva es obligatorio");
        }
        if (reserva.getHoraInicio() == null || reserva.getHoraFin() == null) {
            throw new IllegalArgumentException("Los campos horaInicio y horaFin son obligatorios");
        }
        if (reserva.getUsuario() == null || reserva.getUsuario().getId() == null) {
            throw new IllegalArgumentException("El usuario de la reserva es obligatorio");
        }
    }
    
    private String normalizarTipo(String zonaComun) {
        String zona = zonaComun.trim().toLowerCase();
        if (zona.equals("piscina")) return "piscina";
        if (zona.equals("gimnasio")) return "gimnasio";
        if (zona.equals("salon comunal") || zona.equals("salón comunal")) return "salon comunal";
        if (zona.equals("zona bbq")) return "zona bbq";
        throw new IllegalArgumentException("Tipo de reserva no válido. Usa piscina, gimnasio, salon comunal o zona bbq");
    }
    
    private void validarReglasPorTipo(String tipo, DayOfWeek dia, LocalTime inicio, LocalTime fin) {
        Duration duracion = Duration.between(inicio, fin);
        long minutos = duracion.toMinutes();
        
        switch (tipo) {
            case "piscina" -> {
                if (dia.getValue() < DayOfWeek.WEDNESDAY.getValue() || dia.getValue() > DayOfWeek.SUNDAY.getValue()) {
                    throw new IllegalArgumentException("Piscina solo permite reservas de miércoles a domingo");
                }
                boolean franjaManana = !inicio.isBefore(LocalTime.of(8, 0)) && !fin.isAfter(LocalTime.NOON);
                boolean franjaTarde = !inicio.isBefore(LocalTime.of(16, 0)) && !fin.isAfter(LocalTime.of(20, 0));
                if (!franjaManana && !franjaTarde) {
                    throw new IllegalArgumentException("Piscina permite horarios 8:00-12:00 o 16:00-20:00");
                }
                if (minutos > 180) {
                    throw new IllegalArgumentException("Piscina permite máximo 3 horas por reserva");
                }
            }
            case "gimnasio" -> {
                if (dia == DayOfWeek.SUNDAY) {
                    throw new IllegalArgumentException("Gimnasio solo permite reservas de lunes a sábado");
                }
                boolean franjaManana = !inicio.isBefore(LocalTime.of(5, 0)) && !fin.isAfter(LocalTime.of(10, 0));
                boolean franjaTarde = !inicio.isBefore(LocalTime.of(15, 0)) && !fin.isAfter(LocalTime.of(20, 0));
                if (!franjaManana && !franjaTarde) {
                    throw new IllegalArgumentException("Gimnasio permite horarios 5:00-10:00 o 15:00-20:00");
                }
                if (minutos > 180) {
                    throw new IllegalArgumentException("Gimnasio permite máximo 3 horas por reserva");
                }
            }
            case "salon comunal" -> {
                validarHorarioSalonComunal(inicio, fin);
            }
            case "zona bbq" -> {
                validarHorarioZonaBbq(dia, inicio, fin);
            }
            default -> throw new IllegalArgumentException("Tipo de reserva no soportado");
        }
    }
    
    private void validarDisponibilidad(String tipo, ReservaZonaComun nuevaReserva) {
        List<ReservaZonaComun> reservasMismoTipoDia = reservaZonaComunRepository
                .findByZonaComunIgnoreCaseAndFechaReserva(tipo, nuevaReserva.getFechaReserva());
        
        for (ReservaZonaComun existente : reservasMismoTipoDia) {
            if (existente.getHoraInicio() == null || existente.getHoraFin() == null) {
                continue;
            }
            boolean seCruza = nuevaReserva.getHoraInicio().isBefore(existente.getHoraFin())
                    && nuevaReserva.getHoraFin().isAfter(existente.getHoraInicio());
            if (seCruza) {
                throw new IllegalArgumentException("No hay disponibilidad: ya existe una reserva en ese horario");
            }
        }
        
        if ("zona bbq".equals(tipo)) {
            Long usuarioId = nuevaReserva.getUsuario().getId();
            List<ReservaZonaComun> reservasUsuario = reservaZonaComunRepository
                    .findByZonaComunIgnoreCaseAndFechaReservaAndUsuarioId(tipo, nuevaReserva.getFechaReserva(), usuarioId);
            if (!reservasUsuario.isEmpty()) {
                throw new IllegalArgumentException("Zona BBQ permite solo 1 reserva por día para el usuario");
            }
        }
    }
    
    private String calcularServiciosAdicionales(String tipo, String serviciosEntrada) {
        if (serviciosEntrada == null || serviciosEntrada.trim().isEmpty()) {
            return "total=0";
        }
        String texto = serviciosEntrada.toLowerCase();
        int total = 0;
        StringBuilder detalle = new StringBuilder();
        
        Integer sillas = extraerNumero(texto, "sillas");
        if (sillas != null) {
            int costoSillas;
            if (sillas == 25) costoSillas = 32000;
            else if (sillas == 50) costoSillas = 60000;
            else if (sillas == 100) costoSillas = 125000;
            else throw new IllegalArgumentException("Sillas permitidas: 25, 50 o 100");
            total += costoSillas;
            appendDetalle(detalle, "sillas=" + sillas + "(" + costoSillas + ")");
        }
        
        Integer mesas = extraerNumero(texto, "mesas");
        if (mesas != null) {
            int costoMesas;
            if (mesas == 6) costoMesas = 8000;
            else if (mesas == 10) costoMesas = 18000;
            else throw new IllegalArgumentException("Mesas permitidas: 6 o 10 puestos");
            total += costoMesas;
            appendDetalle(detalle, "mesas=" + mesas + "(" + costoMesas + ")");
        }
        
        boolean incluyeAseo = texto.contains("aseo");
        if (incluyeAseo) {
            if (!"salon comunal".equals(tipo)) {
                throw new IllegalArgumentException("El aseo de salón solo aplica para Salón Comunal");
            }
            total += 150000;
            appendDetalle(detalle, "aseoSalon=si(150000)");
        }
        
        if (detalle.length() == 0) {
            return "total=0";
        }
        appendDetalle(detalle, "total=" + total);
        return detalle.toString();
    }
    
    private Integer extraerNumero(String texto, String campo) {
        Pattern pattern = Pattern.compile(campo + "\\s*[:=]\\s*(\\d+)");
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
    
    private void appendDetalle(StringBuilder sb, String valor) {
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(valor);
    }

    private void validarHorarioSalonComunal(LocalTime inicio, LocalTime fin) {
        if (inicio.isBefore(LocalTime.of(8, 0)) || fin.isAfter(LocalTime.of(22, 0))) {
            throw new IllegalArgumentException(
                    "Salon comunal permite seleccionar hora inicio y hora fin entre 8:00 y 22:00"
            );
        }
        long minutos = Duration.between(inicio, fin).toMinutes();
        if (minutos < 300) {
            throw new IllegalArgumentException("Salon comunal requiere un rango mínimo de 5 horas");
        }
    }

    private void validarHorarioZonaBbq(DayOfWeek dia, LocalTime inicio, LocalTime fin) {
        if (dia.getValue() < DayOfWeek.THURSDAY.getValue() || dia.getValue() > DayOfWeek.SUNDAY.getValue()) {
            throw new IllegalArgumentException("Zona BBQ solo permite reservas de jueves a domingo");
        }
        if (inicio.isBefore(LocalTime.of(10, 0)) || fin.isAfter(LocalTime.of(22, 0))) {
            throw new IllegalArgumentException(
                    "Zona BBQ permite seleccionar hora inicio y hora fin entre 10:00 y 22:00"
            );
        }
    }

    private List<Map<String, String>> obtenerFranjasHabilitadas(String tipo, DayOfWeek dia) {
        List<Map<String, String>> franjas = new ArrayList<>();
        switch (tipo) {
            case "piscina" -> {
                if (dia.getValue() < DayOfWeek.WEDNESDAY.getValue() || dia.getValue() > DayOfWeek.SUNDAY.getValue()) {
                    throw new IllegalArgumentException("Piscina solo permite reservas de miércoles a domingo");
                }
                franjas.add(crearFranja("08:00", "12:00"));
                franjas.add(crearFranja("16:00", "20:00"));
            }
            case "gimnasio" -> {
                if (dia == DayOfWeek.SUNDAY) {
                    throw new IllegalArgumentException("Gimnasio solo permite reservas de lunes a sábado");
                }
                franjas.add(crearFranja("05:00", "10:00"));
                franjas.add(crearFranja("15:00", "20:00"));
            }
            case "salon comunal" -> franjas.add(crearFranja("08:00", "22:00"));
            case "zona bbq" -> {
                if (dia.getValue() < DayOfWeek.THURSDAY.getValue() || dia.getValue() > DayOfWeek.SUNDAY.getValue()) {
                    throw new IllegalArgumentException("Zona BBQ solo permite reservas de jueves a domingo");
                }
                franjas.add(crearFranja("10:00", "22:00"));
            }
            default -> throw new IllegalArgumentException("Tipo de reserva no soportado");
        }
        return franjas;
    }

    private List<Map<String, String>> calcularBloquesDisponibles(
            LocalTime inicioFranja,
            LocalTime finFranja,
            List<ReservaZonaComun> reservasDelDia
    ) {
        List<Map<String, String>> disponibles = new ArrayList<>();
        LocalTime cursor = inicioFranja;

        List<ReservaZonaComun> reservasOrdenadas = reservasDelDia.stream()
                .filter(r -> r.getHoraInicio() != null && r.getHoraFin() != null)
                .filter(r -> r.getHoraInicio().isBefore(finFranja) && r.getHoraFin().isAfter(inicioFranja))
                .sorted((a, b) -> a.getHoraInicio().compareTo(b.getHoraInicio()))
                .toList();

        for (ReservaZonaComun reserva : reservasOrdenadas) {
            LocalTime inicioReserva = max(cursor, reserva.getHoraInicio());
            LocalTime finReserva = min(finFranja, reserva.getHoraFin());

            if (cursor.isBefore(inicioReserva)) {
                disponibles.add(crearFranja(cursor.toString(), inicioReserva.toString()));
            }
            if (cursor.isBefore(finReserva)) {
                cursor = finReserva;
            }
            if (!cursor.isBefore(finFranja)) {
                break;
            }
        }

        if (cursor.isBefore(finFranja)) {
            disponibles.add(crearFranja(cursor.toString(), finFranja.toString()));
        }
        return unificarBloquesContiguos(disponibles);
    }

    private List<Map<String, String>> unificarBloquesContiguos(List<Map<String, String>> bloques) {
        if (bloques.isEmpty()) {
            return bloques;
        }
        List<Map<String, String>> resultado = new ArrayList<>();
        Map<String, String> actual = new LinkedHashMap<>(bloques.get(0));

        for (int i = 1; i < bloques.size(); i++) {
            Map<String, String> siguiente = bloques.get(i);
            if (actual.get("fin").equals(siguiente.get("inicio"))) {
                actual.put("fin", siguiente.get("fin"));
            } else {
                resultado.add(actual);
                actual = new LinkedHashMap<>(siguiente);
            }
        }
        resultado.add(actual);
        return resultado;
    }

    private Map<String, String> crearFranja(String inicio, String fin) {
        Map<String, String> franja = new LinkedHashMap<>();
        franja.put("inicio", inicio);
        franja.put("fin", fin);
        return franja;
    }

    private LocalTime max(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalTime min(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }
}
