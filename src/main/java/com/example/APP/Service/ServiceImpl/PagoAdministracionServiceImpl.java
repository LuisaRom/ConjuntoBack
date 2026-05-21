package com.example.APP.Service.ServiceImpl;

import com.example.APP.Model.PagoAdministracion;
import com.example.APP.Model.Usuario;
import com.example.APP.Repository.PagoAdministracionRepository;
import com.example.APP.Repository.UsuarioRepository;
import com.example.APP.Service.PagoAdministracionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import jakarta.annotation.PostConstruct;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PagoAdministracionServiceImpl implements PagoAdministracionService {

    private static final Logger log = LoggerFactory.getLogger(PagoAdministracionServiceImpl.class);
    private static final int DIA_LIMITE_PAGO_ADMINISTRACION = 5;

    @Autowired
    private PagoAdministracionRepository pagoAdministracionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${mercadopago.access-token:}")
    private String mercadoPagoAccessToken;

    @Value("${mercadopago.public-key:}")
    private String mercadoPagoPublicKey;

    @PostConstruct
    void normalizarTokenMercadoPago() {
        if (mercadoPagoAccessToken != null) {
            mercadoPagoAccessToken = mercadoPagoAccessToken.trim();
        }
        if (mercadoPagoPublicKey != null) {
            mercadoPagoPublicKey = mercadoPagoPublicKey.trim();
        }
        validarCredencialesMercadoPago();
        if (mercadoPagoAccessToken != null && !mercadoPagoAccessToken.isBlank()) {
            String prefijo = mercadoPagoAccessToken.length() > 16
                    ? mercadoPagoAccessToken.substring(0, 16) + "..."
                    : mercadoPagoAccessToken;
            log.info("Mercado Pago Access Token cargado (prefijo: {}, modo sandbox={})",
                    prefijo, esModoSandbox());
        }
    }

    private void validarCredencialesMercadoPago() {
        if (mercadoPagoAccessToken == null || mercadoPagoAccessToken.isBlank()) {
            log.warn("MERCADOPAGO_ACCESS_TOKEN no configurado. Los pagos en linea no funcionaran.");
            return;
        }
        if (mercadoPagoPublicKey != null && !mercadoPagoPublicKey.isBlank()
                && mercadoPagoAccessToken.equals(mercadoPagoPublicKey)) {
            log.error(
                    "MERCADOPAGO_ACCESS_TOKEN y MERCADOPAGO_PUBLIC_KEY son iguales. "
                            + "En Render usa solo el Access Token TEST-7403..., no la Public Key TEST-7f88..."
            );
        }
        if (mercadoPagoAccessToken.contains("7f88b41a-b185-4466-914c")) {
            log.error(
                    "MERCADOPAGO_ACCESS_TOKEN parece ser la Public Key. Usa el Access Token TEST-7403276532353229-..."
            );
        }
    }

    private void validarCredencialesMercadoPagoParaCheckout() {
        if (mercadoPagoAccessToken == null || mercadoPagoAccessToken.isBlank()) {
            throw new IllegalArgumentException("Falta configurar MERCADOPAGO_ACCESS_TOKEN (Access Token TEST- de prueba)");
        }
        if (mercadoPagoPublicKey != null && !mercadoPagoPublicKey.isBlank()
                && mercadoPagoAccessToken.equals(mercadoPagoPublicKey)) {
            throw new IllegalArgumentException(
                    "MERCADOPAGO_ACCESS_TOKEN en Render es incorrecto (coincide con la Public Key). "
                            + "Usa el Access Token largo TEST-7403276532353229-..."
            );
        }
        if (mercadoPagoAccessToken.contains("7f88b41a-b185-4466-914c")) {
            throw new IllegalArgumentException(
                    "MERCADOPAGO_ACCESS_TOKEN parece ser la Public Key. Configura el Access Token TEST-7403276532353229-..."
            );
        }
    }

    private boolean esModoSandbox() {
        return mercadoPagoAccessToken != null
                && (mercadoPagoAccessToken.startsWith("TEST-") || mercadoPagoAccessToken.startsWith("APP_USR-"));
    }

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
        log.info("Iniciando checkout administracion para usuario={}", usernameAutenticado);
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("El cuerpo de la solicitud es obligatorio (monto y periodo)");
        }
        Usuario usuario = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
        if (usuario.getRol() != Usuario.Rol.RESIDENTE) {
            throw new IllegalArgumentException("Solo los residentes pueden iniciar pagos de administración");
        }
        validarCredencialesMercadoPagoParaCheckout();
        BigDecimal monto = extraerMontoDePayload(payload);
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a 0");
        }

        String concepto = extraerConceptoDePayload(payload);
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

        if (existePagoAprobadoEnPeriodo(usuario.getId(), periodo)) {
            throw new IllegalArgumentException("Ya existe un pago aprobado para el periodo " + periodo);
        }

        int precioEntero = monto.intValue();
        if (precioEntero <= 0) {
            throw new IllegalArgumentException("El monto debe ser un valor entero mayor a 0 en COP");
        }

        // 1) Persistir en BD antes de Mercado Pago (reutiliza un PENDIENTE del mes si existe).
        PagoAdministracion pago = prepararPagoPendienteEnBaseDatos(usuario, periodo, monto, concepto);
        String referenciaExterna = pago.getReferenciaExterna();

        String urlBackend = asegurarUrlHttpsParaMercadoPago(resolverUrlBackend());
        String urlRetorno = asegurarUrlHttpsParaMercadoPago(resolverUrlRetorno(payload));
        log.info("Checkout MP: pagoId={}, ref={}, webhook={}, retorno={}",
                pago.getId(), referenciaExterna, urlBackend + "/api/pagos/mercadopago/webhook", urlRetorno);

        String refCodificada = URLEncoder.encode(referenciaExterna, StandardCharsets.UTF_8);
        String successUrl = urlRetorno + "/pagos/resultado?ref=" + refCodificada + "&estado=aprobado";
        String pendingUrl = urlRetorno + "/pagos/resultado?ref=" + refCodificada + "&estado=pendiente";
        String failureUrl = urlRetorno + "/pagos/resultado?ref=" + refCodificada + "&estado=rechazado";

        // 2) Crear preferencia en Mercado Pago con la referencia ya guardada.
        Map<String, Object> preferencePayload = new LinkedHashMap<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", (concepto + " " + periodo).substring(0, Math.min(127, (concepto + " " + periodo).length())));
        item.put("quantity", 1);
        item.put("currency_id", "COP");
        item.put("unit_price", precioEntero);
        preferencePayload.put("items", List.of(item));
        preferencePayload.put("external_reference", referenciaExterna);
        preferencePayload.put("statement_descriptor", "CONJUNTO");
        preferencePayload.put("notification_url", urlBackend + "/api/pagos/mercadopago/webhook");
        Map<String, Object> backUrls = new LinkedHashMap<>();
        backUrls.put("success", successUrl);
        backUrls.put("pending", pendingUrl);
        backUrls.put("failure", failureUrl);
        preferencePayload.put("back_urls", backUrls);

        Map<?, ?> body = invocarMercadoPago(
                "https://api.mercadopago.com/checkout/preferences",
                HttpMethod.POST,
                preferencePayload
        );
        if (body == null || body.get("id") == null) {
            throw new IllegalArgumentException("No fue posible crear la preferencia de pago en Mercado Pago");
        }

        Object sandboxInitPoint = body.get("sandbox_init_point");
        Object initPointNormal = body.get("init_point");
        if (sandboxInitPoint == null && initPointNormal == null) {
            throw new IllegalArgumentException("Mercado Pago no devolvió init_point para continuar el pago");
        }

        String initPoint = resolverInitPointCheckout(sandboxInitPoint, initPointNormal);

        // 3) Actualizar el registro existente con datos de Mercado Pago.
        pago.setMercadoPagoPreferenceId(body.get("id").toString());
        pago.setCheckoutUrl(initPoint);
        pago = guardarPagoEnBaseDatos(pago, usuario.getId(), periodo);

        log.info("Checkout creado: pagoId={}, referencia={}", pago.getId(), referenciaExterna);
        return construirSalidaCheckout(pago, body.get("id").toString(), initPoint, periodo, periodoPago);
    }

    private PagoAdministracion prepararPagoPendienteEnBaseDatos(
            Usuario usuario, String periodo, BigDecimal monto, String concepto
    ) {
        List<PagoAdministracion> pendientes = pagoAdministracionRepository
                .findByUsuarioIdAndPeriodoAndEstadoPagoOrderByIdDesc(
                        usuario.getId(), periodo, PagoAdministracion.EstadoPago.PENDIENTE);

        for (int i = 1; i < pendientes.size(); i++) {
            PagoAdministracion duplicado = pendientes.get(i);
            duplicado.setEstadoPago(PagoAdministracion.EstadoPago.RECHAZADO);
            try {
                pagoAdministracionRepository.save(duplicado);
                log.info("Pago PENDIENTE duplicado marcado RECHAZADO: id={}, periodo={}", duplicado.getId(), periodo);
            } catch (DataAccessException ex) {
                log.warn("No se pudo marcar duplicado RECHAZADO id={}", duplicado.getId(), ex);
            }
        }

        PagoAdministracion pago;
        if (!pendientes.isEmpty()) {
            pago = pendientes.getFirst();
            log.info("Reutilizando pago PENDIENTE id={} para periodo={}", pago.getId(), periodo);
        } else {
            pago = new PagoAdministracion();
            pago.setUsuario(usuario);
            pago.setReferenciaExterna(generarReferenciaExterna(usuario.getId()));
            log.info("Nuevo pago PENDIENTE para usuario={} periodo={}", usuario.getId(), periodo);
        }

        if (pago.getReferenciaExterna() == null || pago.getReferenciaExterna().isBlank()) {
            pago.setReferenciaExterna(generarReferenciaExterna(usuario.getId()));
        }

        pago.setMonto(monto);
        pago.setMetodoPago(PagoAdministracion.MetodoPago.PSE);
        pago.setEstadoPago(PagoAdministracion.EstadoPago.PENDIENTE);
        pago.setConcepto(concepto);
        pago.setPeriodo(periodo);
        pago.setMercadoPagoPreferenceId(null);
        pago.setCheckoutUrl(null);
        pago.setMercadoPagoPaymentId(null);
        pago.setFechaPago(null);

        return guardarPagoEnBaseDatos(pago, usuario.getId(), periodo);
    }

    private String generarReferenciaExterna(Long usuarioId) {
        return "ADM-" + usuarioId + "-" + UUID.randomUUID();
    }

    private PagoAdministracion guardarPagoEnBaseDatos(PagoAdministracion pago, Long usuarioId, String periodo) {
        try {
            return pagoAdministracionRepository.save(pago);
        } catch (DataAccessException ex) {
            String causa = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            log.error(
                    "Error guardando pago en BD (usuario={}, periodo={}, metodoPago={}, estadoPago={}, causa={})",
                    usuarioId, periodo, pago.getMetodoPago(), pago.getEstadoPago(), causa, ex
            );
            throw new IllegalArgumentException(
                    "No se pudo registrar el pago en la base de datos. Contacta al administrador o intenta más tarde."
            );
        }
    }

    private boolean existePagoAprobadoEnPeriodo(Long usuarioId, String periodo) {
        try {
            return pagoAdministracionRepository.existsByUsuarioIdAndPeriodoAndEstadoPago(
                    usuarioId, periodo, PagoAdministracion.EstadoPago.APROBADO);
        } catch (DataAccessException ex) {
            log.warn("Consulta existsBy falló, usando historial en memoria (usuario={}, periodo={})", usuarioId, periodo, ex);
            return pagoAdministracionRepository.findByUsuarioId(usuarioId).stream()
                    .anyMatch(p -> periodo.equals(p.getPeriodo())
                            && p.getEstadoPago() == PagoAdministracion.EstadoPago.APROBADO);
        }
    }

    @Override
    public Map<String, Object> procesarNotificacionMercadoPago(Map<String, Object> payload, String topic, String paymentIdQuery) {
        try {
            String paymentId = resolverPaymentId(payload, topic, paymentIdQuery);
            Map<?, ?> paymentData = consultarPagoMercadoPago(paymentId);
            Map<String, Object> resultado = actualizarPagoDesdeDatosMercadoPago(paymentData);
            resultado.put("procesado", true);
            return resultado;
        } catch (IllegalArgumentException e) {
            log.debug("Notificación Mercado Pago sin procesar: {}", e.getMessage());
            Map<String, Object> ignorada = new LinkedHashMap<>();
            ignorada.put("procesado", false);
            ignorada.put("mensaje", e.getMessage());
            return ignorada;
        }
    }

    @Override
    public Map<String, Object> confirmarPagoPorReferencia(String referenciaExterna, String usernameAutenticado) {
        if (referenciaExterna == null || referenciaExterna.isBlank()) {
            throw new IllegalArgumentException("referenciaExterna es obligatoria");
        }

        Usuario usuario = usuarioRepository.findByUsuario(usernameAutenticado)
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));

        PagoAdministracion pago = pagoAdministracionRepository.findByReferenciaExterna(referenciaExterna)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado para la referencia enviada"));

        if (pago.getUsuario() == null || !usuario.getId().equals(pago.getUsuario().getId())) {
            throw new IllegalArgumentException("No autorizado para confirmar este pago");
        }

        if (pago.getEstadoPago() == PagoAdministracion.EstadoPago.APROBADO) {
            return construirRespuestaPago(pago);
        }

        Map<?, ?> paymentData = buscarPagoMercadoPagoPorReferencia(referenciaExterna);
        if (paymentData == null) {
            Map<String, Object> salida = construirRespuestaPago(pago);
            salida.put("mensaje", "El pago aún no fue confirmado por Mercado Pago");
            return salida;
        }

        return actualizarPagoDesdeDatosMercadoPago(paymentData);
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

    private String resolverPaymentId(Map<String, Object> payload, String topic, String paymentIdQuery) {
        if ("payment".equalsIgnoreCase(topic) && paymentIdQuery != null && !paymentIdQuery.isBlank()) {
            return paymentIdQuery.trim();
        }

        if (payload != null) {
            String type = extraerTexto(payload.get("type"));
            if ("payment".equalsIgnoreCase(type)) {
                Object data = payload.get("data");
                if (data instanceof Map<?, ?> dataMap) {
                    String idFromData = extraerTexto(dataMap.get("id"));
                    if (idFromData != null) {
                        return idFromData;
                    }
                }
            }

            String paymentId = extraerTexto(payload.get("payment_id"));
            if (paymentId != null) {
                return paymentId;
            }

            String dataId = extraerTexto(payload.get("data.id"));
            if (dataId != null) {
                return dataId;
            }
        }

        throw new IllegalArgumentException("No se recibió identificador de pago de Mercado Pago");
    }

    private Map<?, ?> consultarPagoMercadoPago(String paymentId) {
        Map<?, ?> paymentData = invocarMercadoPago(
                "https://api.mercadopago.com/v1/payments/" + paymentId,
                HttpMethod.GET,
                null
        );
        if (paymentData == null || paymentData.get("id") == null) {
            throw new IllegalArgumentException("Mercado Pago no devolvió información del pago " + paymentId);
        }
        return paymentData;
    }

    private Map<?, ?> buscarPagoMercadoPagoPorReferencia(String referenciaExterna) {
        String encodedRef = URLEncoder.encode(referenciaExterna, StandardCharsets.UTF_8);
        String url = "https://api.mercadopago.com/v1/payments/search"
                + "?sort=date_created&criteria=desc"
                + "&external_reference=" + encodedRef;

        Map<?, ?> searchResult = invocarMercadoPago(url, HttpMethod.GET, null);
        if (searchResult == null) {
            return null;
        }

        Object resultsObj = searchResult.get("results");
        if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
            return null;
        }

        Object first = results.get(0);
        return first instanceof Map<?, ?> paymentMap ? paymentMap : null;
    }

    private Map<String, Object> actualizarPagoDesdeDatosMercadoPago(Map<?, ?> paymentData) {
        String referenciaExterna = extraerTexto(paymentData.get("external_reference"));
        String estado = extraerTexto(paymentData.get("status"));
        String paymentId = extraerTexto(paymentData.get("id"));

        if (referenciaExterna == null || referenciaExterna.isBlank()) {
            throw new IllegalArgumentException("Mercado Pago no devolvió external_reference");
        }

        PagoAdministracion pago = pagoAdministracionRepository.findByReferenciaExterna(referenciaExterna)
                .orElseThrow(() -> new IllegalArgumentException("Pago no encontrado para la referencia enviada"));

        PagoAdministracion.EstadoPago nuevoEstado = mapearEstado(estado);
        pago.setMercadoPagoPaymentId(paymentId);
        pago.setEstadoPago(nuevoEstado);
        if (nuevoEstado == PagoAdministracion.EstadoPago.APROBADO) {
            pago.setFechaPago(LocalDateTime.now());
        }
        pagoAdministracionRepository.save(pago);

        return construirRespuestaPago(pago);
    }

    private Map<String, Object> construirSalidaCheckout(
            PagoAdministracion pago,
            String preferenceId,
            String initPoint,
            String periodo,
            YearMonth periodoPago
    ) {
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("pagoId", pago.getId());
        salida.put("estado", pago.getEstadoPago() != null ? pago.getEstadoPago().name() : "PENDIENTE");
        salida.put("estadoPago", pago.getEstadoPago() != null ? pago.getEstadoPago().name() : "PENDIENTE");
        salida.put("metodoPago", "Pago en linea");
        salida.put("referenciaExterna", pago.getReferenciaExterna());
        salida.put("mercadoPagoPreferenceId", preferenceId);
        salida.put("initPoint", initPoint);
        salida.put("checkoutUrl", initPoint);
        salida.put("sandboxInitPoint", initPoint);
        salida.put("periodo", periodo);
        salida.put("diaLimitePago", DIA_LIMITE_PAGO_ADMINISTRACION);
        salida.put("fechaLimitePago", periodoPago.atDay(DIA_LIMITE_PAGO_ADMINISTRACION).toString());
        salida.put("estadoAdministracion", LocalDate.now().isAfter(periodoPago.atDay(DIA_LIMITE_PAGO_ADMINISTRACION))
                ? "EN_MORA"
                : "PENDIENTE_EN_PLAZO");
        salida.put("mensaje", "Pago creado. Redirige al usuario a Mercado Pago.");
        return salida;
    }

    private Map<String, Object> construirRespuestaPago(PagoAdministracion pago) {
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("pagoId", pago.getId());
        salida.put("referenciaExterna", pago.getReferenciaExterna());
        salida.put("estadoPago", pago.getEstadoPago() != null ? pago.getEstadoPago().name() : null);
        salida.put("fechaPago", pago.getFechaPago() != null ? pago.getFechaPago().toString() : null);
        salida.put("mercadoPagoPaymentId", pago.getMercadoPagoPaymentId());
        salida.put("mensaje", mensajeEstado(pago.getEstadoPago()));
        return salida;
    }

    private Map<?, ?> invocarMercadoPago(String url, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(mercadoPagoAccessToken);

        HttpEntity<?> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, method, entity, Map.class);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            String detalle = ex.getResponseBodyAsString();
            if (detalle != null && detalle.length() > 300) {
                detalle = detalle.substring(0, 300) + "...";
            }
            if (ex.getStatusCode().value() == 401) {
                throw new IllegalArgumentException(
                        "Mercado Pago rechazó el Access Token (401). En Render usa MERCADOPAGO_ACCESS_TOKEN "
                                + "con el Access Token TEST-7403276532353229-... (no la Public Key TEST-7f88...). "
                                + "Detalle: " + detalle
                );
            }
            throw new IllegalArgumentException("Error de Mercado Pago (" + ex.getStatusCode().value() + "): " + detalle);
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("No fue posible comunicarse con Mercado Pago: " + ex.getMessage());
        }
    }

    private String resolverInitPointCheckout(Object sandboxInitPoint, Object initPointNormal) {
        if (esModoSandbox() && sandboxInitPoint != null) {
            return sandboxInitPoint.toString();
        }
        if (sandboxInitPoint != null) {
            return sandboxInitPoint.toString();
        }
        if (initPointNormal != null) {
            return initPointNormal.toString();
        }
        throw new IllegalArgumentException("Mercado Pago no devolvió URL de checkout");
    }

    private String resolverUrlBackend() {
        return normalizarBaseUrl(resolverUrlEfectiva(backendBaseUrl, "BACKEND_BASE_URL"));
    }

    private String resolverUrlRetorno(Map<String, Object> payload) {
        String desdePayload = extraerTexto(payload.get("urlRetorno"));
        if (desdePayload == null) {
            desdePayload = extraerTexto(payload.get("returnBaseUrl"));
        }
        if (desdePayload == null) {
            desdePayload = extraerTexto(payload.get("frontendBaseUrl"));
        }
        if (desdePayload != null && !desdePayload.isBlank()) {
            return normalizarBaseUrl(desdePayload);
        }
        return normalizarBaseUrl(resolverUrlEfectiva(frontendBaseUrl, "FRONTEND_BASE_URL"));
    }

    private String resolverUrlEfectiva(String configurada, String envKey) {
        String envExplicita = System.getenv(envKey);
        if (envExplicita != null && !envExplicita.isBlank()) {
            return envExplicita.trim();
        }
        String renderUrl = System.getenv("RENDER_EXTERNAL_URL");
        if (renderUrl != null && !renderUrl.isBlank()) {
            return renderUrl.trim();
        }
        if (configurada != null && !configurada.isBlank()) {
            return configurada.trim();
        }
        return "https://conjuntoback.onrender.com";
    }

    private String normalizarBaseUrl(String url) {
        String valor = url.trim();
        if (valor.endsWith("/")) {
            valor = valor.substring(0, valor.length() - 1);
        }
        return valor;
    }

    private BigDecimal extraerMontoDePayload(Map<String, Object> payload) {
        Object montoRaw = payload.get("monto");
        if (montoRaw == null) {
            montoRaw = payload.get("valor");
        }
        if (montoRaw == null) {
            throw new IllegalArgumentException("El campo monto o valor es obligatorio");
        }
        return extraerMonto(montoRaw);
    }

    /**
     * Mercado Pago rechaza back_urls y notification_url con HTTP (error 400 invalid_back_urls).
     */
    private String asegurarUrlHttpsParaMercadoPago(String url) {
        String valor = normalizarBaseUrl(url);
        if (valor.startsWith("https://")) {
            return valor;
        }
        if (valor.startsWith("http://")) {
            log.warn("URL HTTP detectada para Mercado Pago ({}). Se usará URL pública HTTPS.", valor);
            return normalizarBaseUrl(resolverUrlEfectiva("", "RENDER_EXTERNAL_URL"));
        }
        return "https://conjuntoback.onrender.com";
    }

    private String extraerConceptoDePayload(Map<String, Object> payload) {
        String concepto = extraerTexto(payload.get("concepto"));
        if (concepto == null || concepto.isBlank()) {
            concepto = extraerTexto(payload.get("descripcion"));
        }
        if (concepto == null || concepto.isBlank()) {
            concepto = "Pago administración";
        }
        return concepto;
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

    /** PSE en BD = pago en línea (Mercado Pago); la API expone MERCADO_PAGO para el cliente. */
    private String metodoPagoParaApi(PagoAdministracion.MetodoPago metodoPago) {
        if (metodoPago == null) {
            return null;
        }
        return metodoPago == PagoAdministracion.MetodoPago.PSE ? "MERCADO_PAGO" : metodoPago.name();
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
        item.put("fechaPago", pago.getFechaPago() != null ? pago.getFechaPago().toString() : null);
        item.put("metodoPago", metodoPagoParaApi(pago.getMetodoPago()));
        item.put("estadoPago", pago.getEstadoPago() != null ? pago.getEstadoPago().name() : null);
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
            item.put("fechaLimitePago", fechaLimite.toString());
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
        item.put("fechaLimitePago", fechaLimite.toString());
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
