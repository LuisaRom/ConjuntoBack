package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.PagoAdministracion;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.PagoAdministracionRepository;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.PagoAdministracionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PagoAdministracionServiceImpl implements PagoAdministracionService {

    private static final int DIA_LIMITE_PAGO_ADMINISTRACION = 5;

    @Autowired
    private PagoAdministracionRepository pagoAdministracionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${mercadopago.access-token:}")
    private String mercadoPagoAccessToken;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.backend.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    @Override
    public List<PagoAdministracion> obtenerTodos() {
        return pagoAdministracionRepository.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<PagoAdministracion> obtenerPorId(Long id) {
        return pagoAdministracionRepository.findById(id);
    }

    @Override
    public PagoAdministracion guardar(PagoAdministracion pagoAdministracion) {
        return pagoAdministracionRepository.save(pagoAdministracion);
    }

    @Override
    public Map<String, Object> crearCheckoutAdministracion(Map<String, Object> payload, String usernameAutenticado) {
        Usuario usuario = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        if (usuario.getRol() != Usuario.Rol.RESIDENTE) {
            throw new IllegalArgumentException("Solo los residentes pueden iniciar pagos de administración");
        }
        if (mercadoPagoAccessToken == null || mercadoPagoAccessToken.isBlank()) {
            throw new IllegalArgumentException("Falta configurar mercadopago.access-token para sandbox");
        }

        BigDecimal monto = extraerMonto(payload.get("monto"));
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        String concepto = extraerTexto(payload.get("concepto"));
        if (concepto == null || concepto.isBlank()) {
            concepto = "Pago administración";
        }
        String periodo = extraerTexto(payload.get("periodo"));
        if (periodo == null || periodo.isBlank()) {
            periodo = YearMonth.now().toString();
        }
        YearMonth periodoPago;
        try {
            periodoPago = YearMonth.parse(periodo);
        } catch (Exception ex) {
            throw new IllegalArgumentException("El periodo debe tener formato yyyy-MM");
        }

        String referenciaExterna = "ADM-" + usuario.getId() + "-" + UUID.randomUUID();
        String successUrl = frontendBaseUrl + "/pagos/resultado?estado=aprobado&ref=" + referenciaExterna;
        String pendingUrl = frontendBaseUrl + "/pagos/resultado?estado=pendiente&ref=" + referenciaExterna;
        String failureUrl = frontendBaseUrl + "/pagos/resultado?estado=rechazado&ref=" + referenciaExterna;

        Map<String, Object> preferencePayload = new LinkedHashMap<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", concepto + " " + periodo);
        item.put("quantity", 1);
        item.put("currency_id", "COP");
        item.put("unit_price", monto);
        preferencePayload.put("items", List.of(item));
        preferencePayload.put("external_reference", referenciaExterna);
        preferencePayload.put("statement_descriptor", "CONJUNTO APP");
        preferencePayload.put("notification_url", backendBaseUrl + "/api/pagos/mercadopago/webhook");
        Map<String, Object> backUrls = new LinkedHashMap<>();
        backUrls.put("success", successUrl);
        backUrls.put("pending", pendingUrl);
        backUrls.put("failure", failureUrl);
        preferencePayload.put("back_urls", backUrls);
        preferencePayload.put("auto_return", "approved");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mercadoPagoAccessToken);

        Map body;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.mercadopago.com/checkout/preferences",
                    HttpMethod.POST,
                    new HttpEntity<>(preferencePayload, headers),
                    Map.class
            );
            body = response.getBody();
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("No fue posible crear la preferencia en Mercado Pago (sandbox)");
        }
        if (body == null || body.get("id") == null) {
            throw new IllegalArgumentException("No fue posible crear la preferencia de pago en Mercado Pago");
        }

        Object sandboxInitPoint = body.get("sandbox_init_point");
        Object initPointNormal = body.get("init_point");
        if (sandboxInitPoint == null && initPointNormal == null) {
            throw new IllegalArgumentException("Mercado Pago no devolvió init_point para continuar el pago");
        }

        String initPoint = sandboxInitPoint != null
                ? sandboxInitPoint.toString()
                : initPointNormal.toString();

        PagoAdministracion pago = new PagoAdministracion();
        pago.setUsuario(usuario);
        pago.setMonto(monto);
        pago.setFechaPago(LocalDateTime.now());
        pago.setMetodoPago(PagoAdministracion.MetodoPago.EN_LINEA);
        pago.setEstadoPago(PagoAdministracion.EstadoPago.PENDIENTE);
        pago.setConcepto(concepto);
        pago.setPeriodo(periodo);
        pago.setReferenciaExterna(referenciaExterna);
        pago.setMercadoPagoPreferenceId(body.get("id").toString());
        pago.setCheckoutUrl(initPoint);
        pagoAdministracionRepository.save(pago);

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("pagoId", pago.getId());
        salida.put("estado", pago.getEstadoPago());
        salida.put("metodoPago", "Pago en linea");
        salida.put("referenciaExterna", referenciaExterna);
        salida.put("mercadoPagoPreferenceId", body.get("id").toString());
        salida.put("initPoint", initPoint);
        salida.put("checkoutUrl", initPoint);
        salida.put("periodo", periodo);
        salida.put("diaLimitePago", DIA_LIMITE_PAGO_ADMINISTRACION);
        salida.put("fechaLimitePago", periodoPago.atDay(DIA_LIMITE_PAGO_ADMINISTRACION));
        salida.put("estadoAdministracion", LocalDate.now().isAfter(periodoPago.atDay(DIA_LIMITE_PAGO_ADMINISTRACION))
                ? "EN_MORA"
                : "PENDIENTE_EN_PLAZO");
        salida.put("mensaje", "Pago creado. Redirige al usuario a Mercado Pago.");
        return salida;
    }

    @Override
    public Map<String, Object> procesarRetornoCheckout(Map<String, Object> payload) {
        String referenciaExterna = extraerTexto(payload.get("external_reference"));
        String estado = extraerTexto(payload.get("status"));
        String paymentId = extraerTexto(payload.get("payment_id"));

        if (referenciaExterna == null || referenciaExterna.isBlank()) {
            throw new IllegalArgumentException("external_reference es obligatorio");
        }

        PagoAdministracion pago = pagoAdministracionRepository.findByReferenciaExterna(referenciaExterna)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado para la referencia enviada"));
        pago.setMercadoPagoPaymentId(paymentId);
        pago.setEstadoPago(mapearEstado(estado));
        pago.setFechaPago(LocalDateTime.now());
        pagoAdministracionRepository.save(pago);

        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("pagoId", pago.getId());
        salida.put("referenciaExterna", pago.getReferenciaExterna());
        salida.put("estadoPago", pago.getEstadoPago());
        salida.put("mensaje", mensajeEstado(pago.getEstadoPago()));
        return salida;
    }

    @Override
    public List<Map<String, Object>> listarPagosResidente(String usernameAutenticado) {
        Usuario usuario = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        return pagoAdministracionRepository.findByUsuarioIdOrderByIdDesc(usuario.getId())
                .stream()
                .map(this::mapearResumenPago)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listarPagosAdmin() {
        YearMonth periodoActual = YearMonth.now();
        LocalDate fechaLimite = periodoActual.atDay(DIA_LIMITE_PAGO_ADMINISTRACION);
        LocalDate hoy = LocalDate.now();
        List<PagoAdministracion> pagos = pagoAdministracionRepository.findAllByOrderByIdDesc();

        return usuarioRepository.findByRolOrderByNombreAsc(Usuario.Rol.RESIDENTE)
                .stream()
                .map(residente -> mapearEstadoPagoResidente(residente, pagos, periodoActual, fechaLimite, hoy))
                .collect(Collectors.toList());
    }

    @Override
    public void eliminar(Long id) {
        pagoAdministracionRepository.deleteById(id);
    }

    private BigDecimal extraerMonto(Object montoObj) {
        if (montoObj == null) {
            throw new IllegalArgumentException("El campo monto es obligatorio");
        }
        try {
            return new BigDecimal(montoObj.toString());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El monto no es válido");
        }
    }

    private String extraerTexto(Object valor) {
        return valor != null ? valor.toString().trim() : null;
    }

    private PagoAdministracion.EstadoPago mapearEstado(String estadoMercadoPago) {
        if (estadoMercadoPago == null) {
            return PagoAdministracion.EstadoPago.PENDIENTE;
        }
        return switch (estadoMercadoPago.toLowerCase()) {
            case "approved" -> PagoAdministracion.EstadoPago.APROBADO;
            case "rejected", "cancelled", "failed" -> PagoAdministracion.EstadoPago.RECHAZADO;
            default -> PagoAdministracion.EstadoPago.PENDIENTE;
        };
    }

    private String mensajeEstado(PagoAdministracion.EstadoPago estadoPago) {
        return switch (estadoPago) {
            case APROBADO -> "Pago realizado con exito";
            case RECHAZADO -> "El pago fue rechazado";
            case PENDIENTE -> "El pago se encuentra pendiente";
        };
    }

    private Map<String, Object> mapearResumenPago(PagoAdministracion pago) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", pago.getId());
        item.put("monto", pago.getMonto());
        item.put("fechaPago", pago.getFechaPago());
        item.put("metodoPago", pago.getMetodoPago());
        item.put("estadoPago", pago.getEstadoPago());
        item.put("concepto", pago.getConcepto());
        item.put("periodo", pago.getPeriodo());
        item.put("referenciaExterna", pago.getReferenciaExterna());
        item.put("mercadoPagoPreferenceId", pago.getMercadoPagoPreferenceId());
        item.put("mercadoPagoPaymentId", pago.getMercadoPagoPaymentId());
        item.put("checkoutUrl", pago.getCheckoutUrl());
        agregarDatosVencimientoPago(item, pago);
        if (pago.getUsuario() != null) {
            item.put("usuarioId", pago.getUsuario().getId());
            item.put("usuarioNombre", pago.getUsuario().getNombre());
            item.put("usuarioUsername", pago.getUsuario().getUsuario());
        }
        return item;
    }

    private void agregarDatosVencimientoPago(Map<String, Object> item, PagoAdministracion pago) {
        if (pago.getPeriodo() == null || pago.getPeriodo().isBlank()) {
            return;
        }
        try {
            LocalDate fechaLimite = YearMonth.parse(pago.getPeriodo()).atDay(DIA_LIMITE_PAGO_ADMINISTRACION);
            item.put("diaLimitePago", DIA_LIMITE_PAGO_ADMINISTRACION);
            item.put("fechaLimitePago", fechaLimite);
            item.put("pagoExtemporaneo", pago.getFechaPago() != null && pago.getFechaPago().toLocalDate().isAfter(fechaLimite));
        } catch (Exception ignored) {
            // Mantiene compatibilidad con pagos antiguos que puedan tener periodo no ISO yyyy-MM.
        }
    }

    private Map<String, Object> mapearEstadoPagoResidente(
            Usuario residente,
            List<PagoAdministracion> todosLosPagos,
            YearMonth periodoActual,
            LocalDate fechaLimite,
            LocalDate hoy
    ) {
        List<PagoAdministracion> pagosResidente = todosLosPagos.stream()
                .filter(pago -> pago.getUsuario() != null && residente.getId().equals(pago.getUsuario().getId()))
                .toList();
        List<PagoAdministracion> pagosPeriodo = pagosResidente.stream()
                .filter(pago -> periodoActual.toString().equals(pago.getPeriodo()))
                .toList();
        Optional<PagoAdministracion> pagoAprobadoPeriodo = pagosPeriodo.stream()
                .filter(pago -> pago.getEstadoPago() == PagoAdministracion.EstadoPago.APROBADO)
                .findFirst();
        Optional<PagoAdministracion> ultimoPagoPeriodo = pagosPeriodo.stream().findFirst();

        String estadoAdministracion = calcularEstadoAdministracion(pagoAprobadoPeriodo, fechaLimite, hoy);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("usuarioId", residente.getId());
        item.put("usuarioNombre", residente.getNombre());
        item.put("usuarioUsername", residente.getUsuario());
        item.put("torre", residente.getTorre());
        item.put("apartamento", residente.getApartamento());
        item.put("periodoActual", periodoActual.toString());
        item.put("diaLimitePago", DIA_LIMITE_PAGO_ADMINISTRACION);
        item.put("fechaLimitePago", fechaLimite);
        item.put("estadoAdministracion", estadoAdministracion);
        item.put("estaAlDia", "AL_DIA".equals(estadoAdministracion));
        item.put("estaEnMora", "EN_MORA".equals(estadoAdministracion));
        item.put("diasMora", calcularDiasMora(estadoAdministracion, fechaLimite, hoy));
        item.put("pagoPeriodoActual", pagoAprobadoPeriodo.or(() -> ultimoPagoPeriodo)
                .map(this::mapearResumenPago)
                .orElse(null));
        item.put("historialPagos", pagosResidente.stream()
                .map(this::mapearResumenPago)
                .toList());
        return item;
    }

    private String calcularEstadoAdministracion(
            Optional<PagoAdministracion> pagoAprobadoPeriodo,
            LocalDate fechaLimite,
            LocalDate hoy
    ) {
        if (pagoAprobadoPeriodo.isPresent()) {
            LocalDate fechaPago = pagoAprobadoPeriodo.get().getFechaPago() != null
                    ? pagoAprobadoPeriodo.get().getFechaPago().toLocalDate()
                    : hoy;
            return fechaPago.isAfter(fechaLimite) ? "PAGADO_EN_MORA" : "AL_DIA";
        }
        return hoy.isAfter(fechaLimite) ? "EN_MORA" : "PENDIENTE_EN_PLAZO";
    }

    private long calcularDiasMora(String estadoAdministracion, LocalDate fechaLimite, LocalDate hoy) {
        if (!"EN_MORA".equals(estadoAdministracion)) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(fechaLimite, hoy));
    }
}
