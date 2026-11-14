package com.example.APP.IA;

import com.example.APP.IA.LlmClient.DefinicionHerramienta;
import com.example.APP.IA.LlmClient.LlamadaHerramienta;
import com.example.APP.Model.*;
import com.example.APP.Repository.*;
import com.example.APP.Service.ConocimientoIAService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class HerramientasIA {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final Logger log = LoggerFactory.getLogger(HerramientasIA.class);
    private static final int LIMITE_DEFECTO = 15;
    private static final int LIMITE_MAXIMO = 50;

    private final UsuarioRepository usuarioRepository;
    private final QuejaRepository quejaRepository;
    private final PagoAdministracionRepository pagoRepository;
    private final PaqueteriaRepository paqueteriaRepository;
    private final ReservaZonaComunRepository reservaRepository;
    private final AccesoVehicularRepository accesoVehicularRepository;
    private final AccesoPeatonalRepository accesoPeatonalRepository;
    private final MascotaRepository mascotaRepository;
    private final ConocimientoIAService conocimientoService;
    private final ObjectMapper json;

    public HerramientasIA(UsuarioRepository usuarioRepository, QuejaRepository quejaRepository,
                          PagoAdministracionRepository pagoRepository, PaqueteriaRepository paqueteriaRepository,
                          ReservaZonaComunRepository reservaRepository,
                          AccesoVehicularRepository accesoVehicularRepository,
                          AccesoPeatonalRepository accesoPeatonalRepository, MascotaRepository mascotaRepository,
                          ConocimientoIAService conocimientoService) {
        this.usuarioRepository = usuarioRepository;
        this.quejaRepository = quejaRepository;
        this.pagoRepository = pagoRepository;
        this.paqueteriaRepository = paqueteriaRepository;
        this.reservaRepository = reservaRepository;
        this.accesoVehicularRepository = accesoVehicularRepository;
        this.accesoPeatonalRepository = accesoPeatonalRepository;
        this.mascotaRepository = mascotaRepository;
        this.conocimientoService = conocimientoService;
        this.json = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public List<DefinicionHerramienta> disponiblesPara(Usuario usuario) {
        boolean admin = usuario.getRol() == Usuario.Rol.ADMINISTRADOR;
        boolean staff = admin || usuario.getRol() == Usuario.Rol.CELADOR;
        String alcance = staff ? "de todo el conjunto" : "del residente actual";

        List<DefinicionHerramienta> lista = new ArrayList<>();
        lista.add(new DefinicionHerramienta("buscar_conocimiento",
                "Busca en la base de conocimiento del conjunto (reglamento, horarios, tarifas, procedimientos y respuestas aprendidas).",
                esquema(Map.of("texto", prop("string", "Palabras clave a buscar")), "texto")));
        lista.add(new DefinicionHerramienta("registrar_conocimiento",
                "Guarda un dato GENERAL y reutilizable del conjunto que el usuario acaba de enseñar (norma, horario, tarifa, procedimiento). "
                        + "Nunca guardes datos personales, montos individuales, nombres de residentes ni información privada.",
                esquema(Map.of(
                        "titulo", prop("string", "Título corto"),
                        "contenido", prop("string", "Dato a recordar redactado de forma clara y general"),
                        "categoria", propEnum("Categoría", "RESERVAS", "PAGOS", "QUEJAS", "PAQUETERIA", "ACCESOS", "MASCOTAS", "CONVIVENCIA", "GENERAL")
                ), "titulo", "contenido")));
        lista.add(new DefinicionHerramienta("consultar_quejas",
                "Consulta quejas " + alcance + ". Permite filtrar por estado y categoría.",
                esquema(Map.of(
                        "estado", propEnum("Estado", "PENDIENTE", "EN_PROCESO", "RESUELTO"),
                        "categoria", prop("string", "Tipo de queja, por ejemplo RUIDO o MASCOTA"),
                        "limite", prop("integer", "Máximo de registros")
                ))));
        lista.add(new DefinicionHerramienta("consultar_paqueteria",
                "Consulta paquetes " + alcance + ".",
                esquema(Map.of(
                        "estado", propEnum("Estado", "PENDIENTE", "ENTREGADO"),
                        "torre", prop("string", "Torre (solo personal)"),
                        "limite", prop("integer", "Máximo de registros")
                ))));
        lista.add(new DefinicionHerramienta("consultar_reservas",
                "Consulta reservas de zonas comunes en un rango de fechas para conocer ocupación y disponibilidad.",
                esquema(Map.of(
                        "zona", propEnum("Zona común", "piscina", "gimnasio", "salon comunal", "zona bbq"),
                        "desde", prop("string", "Fecha inicial yyyy-MM-dd (por defecto hoy)"),
                        "hasta", prop("string", "Fecha final yyyy-MM-dd (por defecto 7 días después)")
                ))));
        lista.add(new DefinicionHerramienta("consultar_accesos",
                "Consulta ingresos peatonales y vehiculares " + alcance + ".",
                esquema(Map.of(
                        "fecha", prop("string", "Fecha yyyy-MM-dd (por defecto hoy)"),
                        "tipo", propEnum("Tipo de acceso", "PEATONAL", "VEHICULAR", "TODOS"),
                        "placa", prop("string", "Placa del vehículo (opcional)")
                ))));
        lista.add(new DefinicionHerramienta("consultar_mascotas",
                "Consulta mascotas registradas " + alcance + ".",
                esquema(Map.of("torre", prop("string", "Torre (solo personal)")))));

        if (usuario.getRol() != Usuario.Rol.CELADOR) {
            lista.add(new DefinicionHerramienta("consultar_pagos",
                    "Consulta pagos de administración " + alcance + ".",
                    esquema(Map.of(
                            "periodo", prop("string", "Periodo yyyy-MM"),
                            "estado", propEnum("Estado", "PENDIENTE", "APROBADO", "RECHAZADO"),
                            "limite", prop("integer", "Máximo de registros")
                    ))));
        }
        if (staff) {
            lista.add(new DefinicionHerramienta("resumen_conjunto",
                    "Devuelve indicadores generales del conjunto: residentes, quejas por estado, paquetes pendientes, reservas próximas, accesos de hoy y recaudo del mes.",
                    esquema(Map.of())));
            lista.add(new DefinicionHerramienta("buscar_residente",
                    "Busca residentes por nombre, torre o apartamento.",
                    esquema(Map.of("texto", prop("string", "Nombre, torre o apartamento")), "texto")));
        }
        if (admin) {
            lista.add(new DefinicionHerramienta("residentes_en_mora",
                    "Lista residentes sin pago aprobado de administración en un periodo.",
                    esquema(Map.of("periodo", prop("string", "Periodo yyyy-MM (por defecto el mes actual)")))));
        }
        return lista;
    }

    public String ejecutar(LlamadaHerramienta llamada, Usuario usuario) {
        Map<String, Object> a = llamada.argumentos() != null ? llamada.argumentos() : Map.of();
        boolean permitida = disponiblesPara(usuario).stream().anyMatch(h -> h.nombre().equals(llamada.nombre()));
        if (!permitida) {
            return aJson(Map.of("error", "Herramienta no permitida para el rol " + usuario.getRol()));
        }
        try {
            Object resultado = switch (llamada.nombre()) {
                case "buscar_conocimiento" -> buscarConocimiento(a);
                case "registrar_conocimiento" -> registrarConocimiento(a, usuario);
                case "consultar_quejas" -> consultarQuejas(a, usuario);
                case "consultar_paqueteria" -> consultarPaqueteria(a, usuario);
                case "consultar_reservas" -> consultarReservas(a, usuario);
                case "consultar_accesos" -> consultarAccesos(a, usuario);
                case "consultar_mascotas" -> consultarMascotas(a, usuario);
                case "consultar_pagos" -> consultarPagos(a, usuario);
                case "resumen_conjunto" -> metricasConjunto();
                case "buscar_residente" -> buscarResidente(a);
                case "residentes_en_mora" -> residentesEnMora(texto(a, "periodo"));
                default -> Map.of("error", "Herramienta desconocida");
            };
            return aJson(resultado);
        } catch (Exception e) {
            log.warn("Error ejecutando herramienta {}: {}", llamada.nombre(), e.getMessage());
            return aJson(Map.of("error", "No se pudo consultar: " + e.getMessage()));
        }
    }

    public Map<String, Object> metricasConjunto() {
        LocalDate hoy = LocalDate.now(ZONA);
        String periodo = YearMonth.now(ZONA).toString();
        List<Queja> quejas = quejaRepository.findAll();
        List<PagoAdministracion> pagosMes = pagoRepository.findAll().stream()
                .filter(p -> periodo.equals(p.getPeriodo())).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fecha", hoy.toString());
        m.put("periodoActual", periodo);
        m.put("residentes", usuarioRepository.findByRolOrderByNombreAsc(Usuario.Rol.RESIDENTE).size());
        m.put("quejasPorEstado", contar(quejas.stream().map(q -> Objects.toString(q.getEstado(), "SIN_ESTADO"))));
        m.put("quejasAbiertasPorCategoria", contar(quejas.stream()
                .filter(q -> q.getEstado() != Queja.Estado.RESUELTO)
                .map(q -> Objects.toString(q.getTipoQueja(), "GENERAL"))));
        m.put("quejasAbiertasPrioridadAlta", quejas.stream()
                .filter(q -> q.getEstado() != Queja.Estado.RESUELTO && "ALTA".equalsIgnoreCase(q.getPrioridadIA())).count());
        m.put("paquetesPendientes", paqueteriaRepository.findAll().stream()
                .filter(p -> p.getEstado() == Paqueteria.Estado.PENDIENTE).count());
        m.put("reservasProximos7Dias", contar(reservaRepository.findAll().stream()
                .filter(r -> r.getFechaReserva() != null && !r.getFechaReserva().isBefore(hoy)
                        && !r.getFechaReserva().isAfter(hoy.plusDays(7)))
                .map(r -> Objects.toString(r.getZonaComun(), "otra"))));
        m.put("accesosVehicularesHoy", accesoVehicularRepository.findAll().stream().filter(x -> hoy.equals(x.getFecha())).count());
        m.put("accesosPeatonalesHoy", accesoPeatonalRepository.findAll().stream().filter(x -> hoy.equals(x.getFecha())).count());
        m.put("pagosAprobadosMes", pagosMes.stream().filter(p -> p.getEstadoPago() == PagoAdministracion.EstadoPago.APROBADO).count());
        m.put("recaudoMes", pagosMes.stream()
                .filter(p -> p.getEstadoPago() == PagoAdministracion.EstadoPago.APROBADO && p.getMonto() != null)
                .map(PagoAdministracion::getMonto).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        m.put("residentesEnMoraMes", ((List<?>) residentesEnMora(periodo).get("residentes")).size());
        return m;
    }

    public Map<String, Object> residentesEnMora(String periodo) {
        String p = (periodo == null || periodo.isBlank()) ? YearMonth.now(ZONA).toString() : YearMonth.parse(periodo.trim()).toString();
        Set<Long> alDia = pagoRepository.findAll().stream()
                .filter(x -> p.equals(x.getPeriodo()) && x.getEstadoPago() == PagoAdministracion.EstadoPago.APROBADO && x.getUsuario() != null)
                .map(x -> x.getUsuario().getId()).collect(Collectors.toSet());
        List<Map<String, Object>> mora = usuarioRepository.findByRolOrderByNombreAsc(Usuario.Rol.RESIDENTE).stream()
                .filter(u -> !alDia.contains(u.getId()))
                .map(this::residenteResumen).toList();
        return Map.of("periodo", p, "total", mora.size(), "residentes", mora);
    }

    private Object buscarConocimiento(Map<String, Object> a) {
        List<ConocimientoIA> encontrados = conocimientoService.buscarRelevantes(texto(a, "texto"), 5);
        if (encontrados.isEmpty()) {
            return Map.of("resultados", List.of(), "nota", "No hay información registrada sobre ese tema");
        }
        conocimientoService.registrarUso(encontrados);
        return Map.of("resultados", encontrados.stream()
                .map(c -> Map.of("titulo", c.getTitulo(), "contenido", c.getContenido())).toList());
    }

    private Object registrarConocimiento(Map<String, Object> a, Usuario usuario) {
        ConocimientoIA c = conocimientoService.registrar(texto(a, "titulo"), texto(a, "contenido"),
                texto(a, "categoria"), usuario, ConocimientoIA.Origen.CONVERSACION);
        String nota = c.getEstado() == ConocimientoIA.Estado.APROBADO
                ? "Guardado y disponible de inmediato"
                : "Guardado como pendiente; quedará disponible cuando la administración lo apruebe";
        return Map.of("estado", c.getEstado().name(), "nota", nota);
    }

    private Object consultarQuejas(Map<String, Object> a, Usuario u) {
        String estado = texto(a, "estado");
        String categoria = texto(a, "categoria");
        Stream<Queja> s = quejaRepository.findAll().stream();
        if (!esStaff(u)) {
            s = s.filter(q -> q.getUsuario() != null && u.getId().equals(q.getUsuario().getId()));
        }
        List<Map<String, Object>> lista = s
                .filter(q -> estado == null || (q.getEstado() != null && q.getEstado().name().equalsIgnoreCase(estado)))
                .filter(q -> categoria == null || (q.getTipoQueja() != null && q.getTipoQueja().equalsIgnoreCase(categoria))
                        || (q.getCategoriaIA() != null && q.getCategoriaIA().equalsIgnoreCase(categoria)))
                .sorted(Comparator.comparing(Queja::getFechaCreacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limite(a))
                .map(q -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", q.getId());
                    m.put("tipo", q.getTipoQueja());
                    m.put("descripcion", TextoIA.recortar(q.getDescripcion(), 300));
                    m.put("estado", q.getEstado());
                    m.put("fecha", q.getFechaCreacion());
                    m.put("torreReportada", q.getTorre());
                    m.put("apartamentoReportado", q.getApartamento());
                    m.put("prioridadIA", q.getPrioridadIA());
                    if (esStaff(u) && q.getUsuario() != null) {
                        m.put("reportadoPor", q.getUsuario().getNombre());
                    }
                    return m;
                }).toList();
        return Map.of("total", lista.size(), "quejas", lista);
    }

    private Object consultarPaqueteria(Map<String, Object> a, Usuario u) {
        String estado = texto(a, "estado");
        String torre = texto(a, "torre");
        List<Paqueteria> base = esStaff(u) ? paqueteriaRepository.findAll() : paqueteriaRepository.findByUsuarioId(u.getId());
        List<Map<String, Object>> lista = base.stream()
                .filter(p -> estado == null || (p.getEstado() != null && p.getEstado().name().equalsIgnoreCase(estado)))
                .filter(p -> torre == null || !esStaff(u) || (p.getUsuario() != null && torre.equalsIgnoreCase(p.getUsuario().getTorre())))
                .sorted(Comparator.comparing(Paqueteria::getFechaRecepcion, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limite(a))
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("transportadora", p.getTransportadora());
                    m.put("fechaRecepcion", p.getFechaRecepcion());
                    m.put("estado", p.getEstado());
                    if (esStaff(u) && p.getUsuario() != null) {
                        m.put("destinatario", residenteResumen(p.getUsuario()));
                    }
                    return m;
                }).toList();
        return Map.of("total", lista.size(), "paquetes", lista);
    }

    private Object consultarReservas(Map<String, Object> a, Usuario u) {
        LocalDate desde = fecha(texto(a, "desde"), LocalDate.now(ZONA));
        LocalDate hasta = fecha(texto(a, "hasta"), desde.plusDays(7));
        String zona = texto(a, "zona");
        List<Map<String, Object>> lista = reservaRepository.findAll().stream()
                .filter(r -> r.getFechaReserva() != null && !r.getFechaReserva().isBefore(desde) && !r.getFechaReserva().isAfter(hasta))
                .filter(r -> zona == null || TextoIA.normalizar(zona).equals(TextoIA.normalizar(r.getZonaComun())))
                .sorted(Comparator.comparing(ReservaZonaComun::getFechaReserva)
                        .thenComparing(ReservaZonaComun::getHoraInicio, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(LIMITE_MAXIMO)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("zona", r.getZonaComun());
                    m.put("fecha", r.getFechaReserva());
                    m.put("horaInicio", r.getHoraInicio());
                    m.put("horaFin", r.getHoraFin());
                    boolean propia = r.getUsuario() != null && u.getId().equals(r.getUsuario().getId());
                    if (esStaff(u) && r.getUsuario() != null) {
                        m.put("reservadoPor", residenteResumen(r.getUsuario()));
                    } else {
                        m.put("esMiReserva", propia);
                    }
                    return m;
                }).toList();
        return Map.of("desde", desde, "hasta", hasta, "total", lista.size(), "reservas", lista);
    }

    private Object consultarAccesos(Map<String, Object> a, Usuario u) {
        LocalDate dia = fecha(texto(a, "fecha"), LocalDate.now(ZONA));
        String tipo = Optional.ofNullable(texto(a, "tipo")).orElse("TODOS").toUpperCase();
        String placa = texto(a, "placa");
        List<Map<String, Object>> lista = new ArrayList<>();

        if (!tipo.equals("PEATONAL")) {
            accesoVehicularRepository.findAll().stream()
                    .filter(x -> placa != null ? placa.equalsIgnoreCase(x.getPlacaVehiculo()) : dia.equals(x.getFecha()))
                    .filter(x -> esStaff(u) || mismoApartamento(u, x.getTorre(), x.getApartamento()))
                    .limit(LIMITE_MAXIMO)
                    .forEach(x -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("tipo", "VEHICULAR");
                        m.put("placa", x.getPlacaVehiculo());
                        m.put("vehiculo", x.getTipoVehiculo());
                        m.put("persona", x.getQuienIngresa());
                        m.put("destino", x.getTorre() + "-" + x.getApartamento());
                        m.put("fecha", x.getFecha());
                        m.put("horaEntrada", x.getHoraEntrada());
                        m.put("horaSalida", x.getHoraSalida());
                        lista.add(m);
                    });
        }
        if (!tipo.equals("VEHICULAR") && placa == null) {
            accesoPeatonalRepository.findAll().stream()
                    .filter(x -> dia.equals(x.getFecha()))
                    .filter(x -> esStaff(u) || mismoApartamento(u, x.getTorre(), x.getApartamento()))
                    .limit(LIMITE_MAXIMO)
                    .forEach(x -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("tipo", "PEATONAL");
                        m.put("visitante", x.getNombreVisitante());
                        m.put("destino", x.getTorre() + "-" + x.getApartamento());
                        m.put("fecha", x.getFecha());
                        m.put("horaEntrada", x.getHoraEntrada());
                        m.put("horaSalida", x.getHoraSalida());
                        lista.add(m);
                    });
        }
        return Map.of("fecha", dia, "total", lista.size(), "accesos", lista);
    }

    private Object consultarMascotas(Map<String, Object> a, Usuario u) {
        String torre = texto(a, "torre");
        List<Mascota> base = esStaff(u) ? mascotaRepository.findAll() : mascotaRepository.findByUsuarioId(u.getId());
        List<Map<String, Object>> lista = base.stream()
                .filter(m -> torre == null || !esStaff(u) || (m.getUsuario() != null && torre.equalsIgnoreCase(m.getUsuario().getTorre())))
                .limit(LIMITE_MAXIMO)
                .map(m -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("nombre", m.getNombre());
                    r.put("tipo", m.getTipo());
                    r.put("raza", m.getRaza());
                    if (esStaff(u) && m.getUsuario() != null) {
                        r.put("propietario", residenteResumen(m.getUsuario()));
                    }
                    return r;
                }).toList();
        return Map.of("total", lista.size(), "mascotas", lista);
    }

    private Object consultarPagos(Map<String, Object> a, Usuario u) {
        String periodo = texto(a, "periodo");
        String estado = texto(a, "estado");
        List<PagoAdministracion> base = u.getRol() == Usuario.Rol.ADMINISTRADOR
                ? pagoRepository.findAllByOrderByIdDesc()
                : pagoRepository.findByUsuarioIdOrderByIdDesc(u.getId());
        List<Map<String, Object>> lista = base.stream()
                .filter(p -> periodo == null || periodo.equals(p.getPeriodo()))
                .filter(p -> estado == null || (p.getEstadoPago() != null && p.getEstadoPago().name().equalsIgnoreCase(estado)))
                .limit(limite(a))
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("periodo", p.getPeriodo());
                    m.put("monto", p.getMonto());
                    m.put("estado", p.getEstadoPago());
                    m.put("metodo", p.getMetodoPago());
                    m.put("fechaPago", p.getFechaPago());
                    m.put("concepto", p.getConcepto());
                    if (u.getRol() == Usuario.Rol.ADMINISTRADOR && p.getUsuario() != null) {
                        m.put("residente", residenteResumen(p.getUsuario()));
                    }
                    return m;
                }).toList();
        return Map.of("total", lista.size(), "pagos", lista);
    }

    private Object buscarResidente(Map<String, Object> a) {
        String filtro = TextoIA.normalizar(texto(a, "texto"));
        if (filtro.isBlank()) {
            return Map.of("residentes", List.of());
        }
        List<Map<String, Object>> lista = usuarioRepository.findByRolOrderByNombreAsc(Usuario.Rol.RESIDENTE).stream()
                .filter(u -> TextoIA.normalizar(u.getNombre() + " torre " + u.getTorre() + " apto " + u.getApartamento()
                        + " " + u.getTorre() + "-" + u.getApartamento()).contains(filtro))
                .limit(20)
                .map(u -> {
                    Map<String, Object> m = residenteResumen(u);
                    m.put("telefono", u.getTelefono());
                    return m;
                }).toList();
        return Map.of("total", lista.size(), "residentes", lista);
    }

    private Map<String, Object> residenteResumen(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", u.getNombre());
        m.put("torre", u.getTorre());
        m.put("apartamento", u.getApartamento());
        return m;
    }

    private boolean esStaff(Usuario u) {
        return u.getRol() == Usuario.Rol.ADMINISTRADOR || u.getRol() == Usuario.Rol.CELADOR;
    }

    private boolean mismoApartamento(Usuario u, String torre, String apto) {
        return u.getTorre() != null && u.getApartamento() != null
                && u.getTorre().equalsIgnoreCase(Objects.toString(torre, "").trim())
                && u.getApartamento().equalsIgnoreCase(Objects.toString(apto, "").trim());
    }

    private Map<String, Long> contar(Stream<String> valores) {
        return valores.collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));
    }

    private String texto(Map<String, Object> a, String clave) {
        Object v = a.get(clave);
        return v == null || v.toString().isBlank() ? null : v.toString().trim();
    }

    private int limite(Map<String, Object> a) {
        Object v = a.get("limite");
        try {
            int n = v == null ? LIMITE_DEFECTO : (int) Double.parseDouble(v.toString());
            return Math.max(1, Math.min(n, LIMITE_MAXIMO));
        } catch (NumberFormatException e) {
            return LIMITE_DEFECTO;
        }
    }

    private LocalDate fecha(String valor, LocalDate defecto) {
        if (valor == null) {
            return defecto;
        }
        try {
            return LocalDate.parse(valor.length() > 10 ? valor.substring(0, 10) : valor);
        } catch (Exception e) {
            return defecto;
        }
    }

    private String aJson(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (Exception e) {
            return "{\"error\":\"No se pudo serializar el resultado\"}";
        }
    }

    private static Map<String, Object> esquema(Map<String, Object> propiedades, String... requeridos) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", propiedades);
        if (requeridos.length > 0) {
            s.put("required", List.of(requeridos));
        }
        return s;
    }

    private static Map<String, Object> prop(String tipo, String descripcion) {
        return Map.of("type", tipo, "description", descripcion);
    }

    private static Map<String, Object> propEnum(String descripcion, String... valores) {
        return Map.of("type", "string", "description", descripcion, "enum", List.of(valores));
    }
}
