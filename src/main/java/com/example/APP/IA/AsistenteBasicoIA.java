package com.example.APP.IA;

import com.example.APP.Model.ConocimientoIA;
import com.example.APP.Model.Usuario;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AsistenteBasicoIA {

    private static final Locale ES = Locale.of("es", "CO");
    private static final Pattern TORRE = Pattern.compile("\\b(?:torre|t)\\s*(\\d{1,3})\\b");
    private static final Pattern APTO = Pattern.compile("\\b(?:apto|apartamento|apt|ap)\\s*(\\d{1,4})\\b");
    private static final Pattern PLACA = Pattern.compile("\\b([A-Z]{3})\\s?-?\\s?(\\d{2}[A-Z0-9]?)\\b");
    private static final Pattern ANIO = Pattern.compile("\\b(20\\d{2})\\b");
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};
    private static final Map<String, DayOfWeek> DIAS = Map.of(
            "lunes", DayOfWeek.MONDAY, "martes", DayOfWeek.TUESDAY, "miercoles", DayOfWeek.WEDNESDAY,
            "jueves", DayOfWeek.THURSDAY, "viernes", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY);

    private final HerramientasIA herramientas;

    public AsistenteBasicoIA(HerramientasIA herramientas) {
        this.herramientas = herramientas;
    }

    private enum Intencion {
        AYUDA, SALUDO, MORA, MI_PAGO, PAGOS, PAQUETES, QUEJAS, RESERVAS, ACCESOS, MASCOTAS, VEHICULOS, RESIDENTE, RESUMEN, CONOCIMIENTO
    }

    private record Rango(LocalDate desde, LocalDate hasta, String etiqueta) {
    }

    public LlmClient.ResultadoLlm responder(Usuario usuario, String mensaje, List<ConocimientoIA> relevantes) {
        String n = " " + TextoIA.normalizar(mensaje) + " ";
        List<String> usadas = new ArrayList<>();
        Intencion intencion = detectar(n, usuario, relevantes);
        String texto = switch (intencion) {
            case SALUDO -> saludo(usuario);
            case AYUDA -> ayuda(usuario);
            case MORA -> mora(usuario, n, usadas);
            case MI_PAGO -> miPago(usuario, n, usadas);
            case PAGOS -> pagos(usuario, n, usadas);
            case PAQUETES -> paquetes(usuario, n, usadas);
            case QUEJAS -> quejas(usuario, n, usadas);
            case RESERVAS -> reservas(usuario, n, relevantes, usadas);
            case ACCESOS -> accesos(usuario, mensaje, n, usadas);
            case MASCOTAS -> mascotas(usuario, n, usadas);
            case VEHICULOS -> vehiculos(usuario, mensaje, n, usadas);
            case RESIDENTE -> residente(usuario, mensaje, n, usadas);
            case RESUMEN -> resumen(usuario, usadas);
            case CONOCIMIENTO -> conocimiento(usuario, relevantes);
        };
        return new LlmClient.ResultadoLlm(texto, usadas);
    }

    private Intencion detectar(String n, Usuario u, List<ConocimientoIA> relevantes) {
        boolean staff = esStaff(u);
        boolean admin = u.getRol() == Usuario.Rol.ADMINISTRADOR;
        boolean preguntaNorma = tiene(n, "horario", "a que hora", "cuanto cuesta", "cuanto vale", "costo", "precio", "tarifa",
                "como reservo", "como puedo", "como hago", "como se", "reglas", "norma", "reglamento", "requisitos", "se puede", "esta permitido");
        boolean preguntaDatos = tiene(n, " hay ", " tengo ", " mis ", " mi ", "quien", "quienes", "cuantos", "cuantas", " hoy ",
                "manana", "esta semana", "disponible", "ocupad", "estado de", "en que va", "listado", "lista de", "muestrame", "dame");

        if (tiene(n, "que puedes hacer", "que sabes hacer", "ayuda", "como funcionas", "que me puedes", "en que me ayudas")) {
            return Intencion.AYUDA;
        }
        if (tiene(n, "vehiculo", "carro", " moto", "placa", "parqueadero", "parquear", "garaje", "camioneta", "automovil")
                && !tiene(n, "ingres", "entro", "entrado", "acceso")) {
            return preguntaNorma && !preguntaDatos && !relevantes.isEmpty() ? Intencion.CONOCIMIENTO : Intencion.VEHICULOS;
        }
        if (tiene(n, "no han pagado", "no ha pagado", "moros", " mora ", "en mora", "deben ", "deudor", "sin pagar",
                "atrasad", "pendientes de pago", "no estan al dia", "no esta al dia", "falta pagar", "faltan por pagar")) {
            return admin ? Intencion.MORA : Intencion.MI_PAGO;
        }
        if (tiene(n, "pago", "pague", "pagar", "administracion", "cuota", "recaudo", "recaud", "al dia", "debo ")) {
            if (preguntaNorma && !preguntaDatos && !relevantes.isEmpty()) {
                return Intencion.CONOCIMIENTO;
            }
            if (u.getRol() == Usuario.Rol.CELADOR) {
                return relevantes.isEmpty() ? Intencion.AYUDA : Intencion.CONOCIMIENTO;
            }
            return admin ? Intencion.PAGOS : Intencion.MI_PAGO;
        }
        if (tiene(n, "paquete", "encomienda", "correspondencia", "envio", "pedido", "domicilio", "llego algo", "me llego")) {
            return preguntaNorma && !preguntaDatos && !relevantes.isEmpty() ? Intencion.CONOCIMIENTO : Intencion.PAQUETES;
        }
        if (tiene(n, "queja", "reclamo", "denuncia", "reporte", "ruido", "convivencia")) {
            return preguntaNorma && !preguntaDatos && !relevantes.isEmpty() ? Intencion.CONOCIMIENTO : Intencion.QUEJAS;
        }
        if (tiene(n, "ingres", "entro", "entrado", "visita", "visitante", "acceso", "entradas", "salidas", "quien vino", "han venido")) {
            return Intencion.ACCESOS;
        }
        if (tiene(n, "reserva", "piscina", "gimnasio", " gym", "salon", "bbq", "asado", "zona comun", "agendar", "apartar")) {
            return preguntaNorma && !preguntaDatos && !relevantes.isEmpty() ? Intencion.CONOCIMIENTO : Intencion.RESERVAS;
        }
        if (tiene(n, "mascota", "perro", "gato", "animal")) {
            return preguntaNorma && !preguntaDatos && !relevantes.isEmpty() ? Intencion.CONOCIMIENTO : Intencion.MASCOTAS;
        }
        if (staff && (tiene(n, "quien vive", "residente de", "residentes de", "telefono de", "buscar residente", "datos de")
                || (TORRE.matcher(n).find() && APTO.matcher(n).find()))) {
            return Intencion.RESIDENTE;
        }
        if (staff && tiene(n, "resumen", "como va", "como esta el conjunto", "estado general", "indicador", "estadistica",
                "informe", "panorama", "novedades", "que hay pendiente", "que esta pendiente")) {
            return Intencion.RESUMEN;
        }
        if (!relevantes.isEmpty()) {
            return Intencion.CONOCIMIENTO;
        }
        if (tiene(n, "hola", "buenas", "buenos dias", "buenas tardes", "buenas noches", "que tal", "saludos") && n.trim().split(" ").length <= 5) {
            return Intencion.SALUDO;
        }
        return Intencion.AYUDA;
    }

    private String saludo(Usuario u) {
        return "¡Hola, " + primerNombre(u) + "! ¿En qué te ayudo hoy? " + ejemplos(u);
    }

    private String ayuda(Usuario u) {
        String base = switch (u.getRol()) {
            case ADMINISTRADOR -> "Puedo analizar la información del conjunto por ti: quiénes no han pagado la administración, "
                    + "el recaudo del mes, quejas pendientes y prioritarias, paquetes sin entregar, reservas, accesos, mascotas, "
                    + "vehículos sin parqueadero y un resumen general con alertas.";
            case CELADOR -> "Puedo ayudarte con lo operativo: paquetes pendientes por entregar, ingresos del día, "
                    + "a quién pertenece una placa, reservas de hoy, quejas abiertas y datos de residentes por torre y apartamento.";
            default -> "Puedo decirte si estás al día con la administración, si tienes paquetes en portería, "
                    + "en qué va tu queja, tus reservas y la disponibilidad de las zonas comunes, tus vehículos y parqueadero, "
                    + "tus visitas y las normas del conjunto.";
        };
        return base + "\n\n" + ejemplos(u);
    }

    private String ejemplos(Usuario u) {
        return switch (u.getRol()) {
            case ADMINISTRADOR -> "Prueba con: \"¿Quiénes no han pagado este mes?\", \"¿Cómo va el conjunto?\" o \"¿Qué vehículos no tienen parqueadero?\"";
            case CELADOR -> "Prueba con: \"¿Qué paquetes hay pendientes?\", \"¿De quién es la placa ABC123?\" o \"¿Quién ha ingresado hoy?\"";
            default -> "Prueba con: \"¿Estoy al día con la administración?\", \"¿Tengo paquetes?\" o \"¿Está libre la piscina el sábado?\"";
        };
    }

    private String mora(Usuario u, String n, List<String> usadas) {
        YearMonth periodo = periodo(n);
        Map<String, Object> datos = usar("residentes_en_mora", Map.of("periodo", periodo.toString()), u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> lista = lista(datos, "residentes");
        Map<String, Object> metricas = usar("resumen_conjunto", Map.of(), u, usadas);
        int totalResidentes = entero(metricas.get("residentes"));
        String nombrePeriodo = nombreMes(periodo);

        if (lista.isEmpty()) {
            return "Todos los residentes están al día con la administración de " + nombrePeriodo
                    + ". No hay pagos pendientes de aprobar para ese periodo.";
        }
        int alDia = Math.max(totalResidentes - lista.size(), 0);
        StringBuilder sb = new StringBuilder();
        sb.append("En ").append(nombrePeriodo).append(", ").append(lista.size())
                .append(lista.size() == 1 ? " residente no tiene" : " residentes no tienen").append(" pago de administración aprobado");
        if (totalResidentes > 0) {
            sb.append(" (").append(porcentaje(lista.size(), totalResidentes)).append(" de ").append(totalResidentes)
                    .append("). ").append(alDia).append(alDia == 1 ? " está al día." : " están al día.");
        } else {
            sb.append(".");
        }
        sb.append("\n\n").append(listaResidentes(lista, 15));

        Map<String, Long> porTorre = lista.stream()
                .collect(Collectors.groupingBy(r -> Objects.toString(r.get("torre"), "?"), Collectors.counting()));
        porTorre.entrySet().stream().max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 1 && porTorre.size() > 1)
                .ifPresent(e -> sb.append("\n\nLa torre ").append(e.getKey()).append(" concentra la mayor cantidad de pendientes (")
                        .append(e.getValue()).append(")."));

        if (periodo.equals(YearMonth.now(HerramientasIA.ZONA)) && LocalDate.now(HerramientasIA.ZONA).getDayOfMonth() <= 10) {
            sb.append("\n\nTen en cuenta que el mes apenas comienza, así que varios pagos pueden estar en camino.");
        }
        sb.append("\n\nTe sugiero enviar un recordatorio de pago desde Notificaciones a estos residentes.");
        return sb.toString();
    }

    private String miPago(Usuario u, String n, List<String> usadas) {
        YearMonth periodo = periodo(n);
        Map<String, Object> datos = usar("consultar_pagos", Map.of("limite", 24), u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> pagos = lista(datos, "pagos");
        String nombrePeriodo = nombreMes(periodo);
        List<Map<String, Object>> delPeriodo = pagos.stream()
                .filter(p -> periodo.toString().equals(Objects.toString(p.get("periodo"), ""))).toList();

        Optional<Map<String, Object>> aprobado = delPeriodo.stream().filter(p -> "APROBADO".equals(Objects.toString(p.get("estado")))).findFirst();
        if (aprobado.isPresent()) {
            Map<String, Object> p = aprobado.get();
            StringBuilder sb = new StringBuilder("Sí, " + primerNombre(u) + ", estás al día. Tu pago de " + nombrePeriodo);
            if (p.get("monto") != null) {
                sb.append(" por ").append(dinero(p.get("monto")));
            }
            sb.append(" está aprobado");
            if (p.get("fechaPago") != null) {
                sb.append(" desde el ").append(fecha(p.get("fechaPago")));
            }
            sb.append(".");
            return sb.toString();
        }
        Optional<Map<String, Object>> pendiente = delPeriodo.stream().filter(p -> "PENDIENTE".equals(Objects.toString(p.get("estado")))).findFirst();
        if (pendiente.isPresent()) {
            return "Tu pago de " + nombrePeriodo + " está registrado pero todavía en revisión. "
                    + "Cuando la administración lo apruebe quedarás al día; si pagaste por PSE suele confirmarse en pocos minutos.";
        }
        StringBuilder sb = new StringBuilder("No encuentro un pago aprobado de la administración de " + nombrePeriodo + ".");
        pagos.stream().filter(p -> "APROBADO".equals(Objects.toString(p.get("estado")))).findFirst().ifPresent(p ->
                sb.append(" Tu último pago aprobado fue el de ").append(nombreMes(Objects.toString(p.get("periodo"), "")))
                        .append(p.get("monto") != null ? " por " + dinero(p.get("monto")) : "").append("."));
        sb.append("\n\nPuedes pagar en línea por PSE desde la sección Pagos de la app, o en efectivo en la administración.");
        return sb.toString();
    }

    private String pagos(Usuario u, String n, List<String> usadas) {
        YearMonth periodo = periodo(n);
        Map<String, Object> mora = usar("residentes_en_mora", Map.of("periodo", periodo.toString()), u, usadas);
        Map<String, Object> totales = herramientas.resumenPagos(periodo.toString());
        Object recaudo = totales.get("recaudo");
        int aprobados = entero(totales.get("aprobados"));
        int pendientes = entero(totales.get("pendientes"));
        int rechazados = entero(totales.get("rechazados"));
        StringBuilder sb = new StringBuilder("Pagos de administración de " + nombreMes(periodo) + ":\n");
        sb.append("• Recaudo aprobado: ").append(dinero(recaudo)).append(" en ").append(aprobados).append(aprobados == 1 ? " pago" : " pagos").append("\n");
        sb.append("• En revisión: ").append(pendientes).append("\n");
        if (rechazados > 0) {
            sb.append("• Rechazados: ").append(rechazados).append("\n");
        }
        int enMora = entero(mora.get("total"));
        sb.append("• Residentes sin pago aprobado: ").append(enMora);
        if (pendientes > 0) {
            sb.append("\n\nHay ").append(pendientes).append(pendientes == 1 ? " pago esperando" : " pagos esperando")
                    .append(" aprobación; revisarlos reduce la cifra de pendientes.");
        }
        if (enMora > 0) {
            sb.append("\n\nSi quieres el listado, pregúntame \"¿quiénes no han pagado ").append(periodo.equals(YearMonth.now(HerramientasIA.ZONA)) ? "este mes" : "en " + MESES[periodo.getMonthValue() - 1]).append("?\"");
        }
        return sb.toString();
    }

    private String paquetes(Usuario u, String n, List<String> usadas) {
        boolean entregados = tiene(n, "entregad", "recogi", "historial");
        Map<String, Object> args = new HashMap<>();
        if (!entregados) {
            args.put("estado", "PENDIENTE");
        }
        args.put("limite", 50);
        torre(n).ifPresent(t -> args.put("torre", t));
        Map<String, Object> datos = usar("consultar_paqueteria", args, u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> lista = lista(datos, "paquetes");

        if (!esStaff(u)) {
            if (lista.isEmpty()) {
                return entregados ? "No tienes paquetes registrados." : "No tienes paquetes pendientes en portería. Te avisaremos cuando llegue alguno.";
            }
            StringBuilder sb = new StringBuilder(entregados ? "Estos son tus paquetes más recientes:\n" :
                    "Sí, tienes " + lista.size() + (lista.size() == 1 ? " paquete esperándote" : " paquetes esperándote") + " en portería:\n");
            lista.stream().limit(10).forEach(p -> sb.append("• ").append(Objects.toString(p.get("transportadora"), "Paquete"))
                    .append(", recibido ").append(cuando(p.get("fechaRecepcion")))
                    .append(entregados ? " (" + estadoLegible(p.get("estado")) + ")" : "").append("\n"));
            if (!entregados) {
                sb.append("\nPuedes recogerlo en portería presentando tu identificación.");
            }
            return sb.toString().trim();
        }

        if (lista.isEmpty()) {
            return entregados ? "No hay paquetes registrados." : "No hay paquetes pendientes por entregar. Todo está al día en portería.";
        }
        int totalPendientes = lista.size();
        if (!entregados && !args.containsKey("torre") && lista.size() >= 50) {
            totalPendientes = Math.max(totalPendientes, entero(usar("resumen_conjunto", Map.of(), u, usadas).get("paquetesPendientes")));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Hay ").append(totalPendientes).append(totalPendientes == 1 ? " paquete pendiente" : " paquetes pendientes").append(" por entregar");
        Map<String, Long> porTorre = lista.stream().collect(Collectors.groupingBy(
                p -> Objects.toString(mapa(p.get("destinatario")).get("torre"), "?"), TreeMap::new, Collectors.counting()));
        if (porTorre.size() > 1 && lista.size() == totalPendientes) {
            sb.append(", repartidos así: ").append(porTorre.entrySet().stream()
                    .map(e -> "torre " + e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining(", ")));
        }
        sb.append(".\n\n");
        lista.stream().limit(12).forEach(p -> {
            Map<String, Object> d = mapa(p.get("destinatario"));
            sb.append("• ").append(Objects.toString(d.get("nombre"), "Residente"))
                    .append(" (").append(torreApto(d)).append("): ").append(Objects.toString(p.get("transportadora"), "paquete"))
                    .append(", ").append(fecha(p.get("fechaRecepcion"))).append("\n");
        });
        long antiguos = lista.stream().filter(p -> diasDesde(p.get("fechaRecepcion")) > 3).count();
        if (antiguos > 0) {
            sb.append("\nAtención: ").append(antiguos).append(antiguos == 1 ? " paquete lleva" : " paquetes llevan")
                    .append(" más de 3 días en portería. Conviene recordarles a esos residentes que pasen a recogerlos.");
        }
        return sb.toString().trim();
    }

    private String quejas(Usuario u, String n, List<String> usadas) {
        Map<String, Object> args = new HashMap<>();
        args.put("limite", 50);
        String estado = tiene(n, "resuelt", "cerrad", "finalizad") ? "RESUELTO"
                : tiene(n, "en proceso", "en tramite", "atendiendo") ? "EN_PROCESO" : null;
        if (estado != null) {
            args.put("estado", estado);
        }
        String categoria = categoriaQueja(n);
        if (categoria != null) {
            args.put("categoria", categoria);
        }
        Map<String, Object> datos = usar("consultar_quejas", args, u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> lista = lista(datos, "quejas");
        boolean soloAbiertas = estado == null && tiene(n, "pendiente", "abierta", "sin resolver", "prioritari", "urgente", "activa");
        if (soloAbiertas) {
            lista = lista.stream().filter(q -> !"RESUELTO".equals(Objects.toString(q.get("estado")))).toList();
        }

        if (!esStaff(u)) {
            if (lista.isEmpty()) {
                return "No tienes quejas registradas" + (estado != null ? " en estado " + estadoLegible(estado) : "") + ". "
                        + "Si necesitas reportar algo, puedes hacerlo desde la sección Quejas.";
            }
            StringBuilder sb = new StringBuilder(lista.size() == 1 ? "Esta es tu queja:\n" : "Estas son tus quejas más recientes:\n");
            lista.stream().limit(8).forEach(q -> sb.append("• #").append(q.get("id")).append(" ")
                    .append(Objects.toString(q.get("tipo"), "Queja")).append(" del ").append(fecha(q.get("fecha")))
                    .append(": ").append(estadoLegible(q.get("estado"))).append("\n"));
            long abiertas = lista.stream().filter(q -> !"RESUELTO".equals(Objects.toString(q.get("estado")))).count();
            sb.append(abiertas == 0 ? "\nTodas tus quejas están resueltas."
                    : "\nLa administración te notificará cuando " + (abiertas == 1 ? "se resuelva." : "se resuelvan."));
            return sb.toString().trim();
        }

        if (lista.isEmpty()) {
            return "No hay quejas" + (categoria != null ? " de " + categoria.toLowerCase().replace("_", " ") : "")
                    + (soloAbiertas ? " abiertas" : estado != null ? " en estado " + estadoLegible(estado) : "") + ". ";
        }
        Map<String, Long> porEstado = lista.stream().collect(Collectors.groupingBy(q -> Objects.toString(q.get("estado"), "SIN_ESTADO"), Collectors.counting()));
        if (categoria == null && estado == null && !soloAbiertas) {
            Map<String, Object> reales = mapa(usar("resumen_conjunto", Map.of(), u, usadas).get("quejasPorEstado"));
            if (!reales.isEmpty()) {
                porEstado = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : reales.entrySet()) {
                    porEstado.put(e.getKey(), (long) entero(e.getValue()));
                }
            }
        }
        long totalQuejas = porEstado.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Long> porTipo = lista.stream().filter(q -> !"RESUELTO".equals(Objects.toString(q.get("estado"))))
                .collect(Collectors.groupingBy(q -> Objects.toString(q.get("tipo"), "General"), Collectors.counting()));
        List<Map<String, Object>> altas = lista.stream()
                .filter(q -> "ALTA".equalsIgnoreCase(Objects.toString(q.get("prioridadIA"), "")) && !"RESUELTO".equals(Objects.toString(q.get("estado"))))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Encontré ").append(totalQuejas).append(totalQuejas == 1 ? " queja" : " quejas")
                .append(categoria != null ? " de " + categoria.toLowerCase().replace("_", " ") : "").append(": ")
                .append(porEstado.entrySet().stream().map(e -> e.getValue() + " " + plural(estadoLegible(e.getKey()).toLowerCase(), e.getValue()))
                        .collect(Collectors.joining(", "))).append(".");
        if (!altas.isEmpty()) {
            sb.append("\n\nPrioridad alta (").append(altas.size()).append("):\n");
            altas.stream().limit(5).forEach(q -> sb.append("• #").append(q.get("id")).append(" ")
                    .append(TextoIA.recortar(Objects.toString(q.get("descripcion"), ""), 90)).append("\n"));
        }
        List<Map<String, Object>> abiertas = lista.stream().filter(q -> !"RESUELTO".equals(Objects.toString(q.get("estado")))).toList();
        if (!abiertas.isEmpty()) {
            sb.append(altas.isEmpty() ? "\n\nQuejas abiertas más recientes:\n" : "\nOtras abiertas:\n");
            abiertas.stream().filter(q -> !altas.contains(q)).limit(6).forEach(q -> sb.append("• #").append(q.get("id")).append(" ")
                    .append(Objects.toString(q.get("tipo"), "General")).append(" (").append(fecha(q.get("fecha"))).append(", ")
                    .append(Objects.toString(q.get("reportadoPor"), "residente")).append(")\n"));
            Map<String, Object> masAntigua = abiertas.get(abiertas.size() - 1);
            long dias = diasDesde(masAntigua.get("fecha"));
            if (dias > 7) {
                sb.append("\nLa queja abierta más antigua (#").append(masAntigua.get("id")).append(") lleva ").append(dias).append(" días sin cerrarse.");
            }
        }
        porTipo.entrySet().stream().max(Map.Entry.comparingByValue()).filter(e -> e.getValue() > 1 && categoria == null)
                .ifPresent(e -> sb.append("\nEl motivo más frecuente entre las abiertas es ").append(e.getKey().toLowerCase())
                        .append(" (").append(e.getValue()).append("); un comunicado preventivo podría ayudar."));
        return sb.toString().trim();
    }

    private String reservas(Usuario u, String n, List<ConocimientoIA> relevantes, List<String> usadas) {
        Rango rango = rango(n);
        String zona = zona(n);
        Map<String, Object> args = new HashMap<>();
        args.put("desde", rango.desde().toString());
        args.put("hasta", rango.hasta().toString());
        if (zona != null) {
            args.put("zona", zona);
        }
        Map<String, Object> datos = usar("consultar_reservas", args, u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> lista = lista(datos, "reservas");
        boolean mias = !esStaff(u) && tiene(n, "mis reservas", "mi reserva", "tengo reserva", "reserve", "tengo alguna");
        if (mias) {
            lista = lista.stream().filter(r -> Boolean.TRUE.equals(r.get("esMiReserva"))).toList();
        }
        String nombreZona = zona != null ? "la " + zona : "las zonas comunes";
        StringBuilder sb = new StringBuilder();
        if (lista.isEmpty()) {
            if (mias) {
                sb.append("No tienes reservas ").append(rango.etiqueta()).append(".");
            } else {
                sb.append(capitalizar(nombreZona)).append(zona != null ? " está" : " están").append(" libre")
                        .append(zona != null ? "" : "s").append(" ").append(rango.etiqueta()).append(": no hay reservas registradas.");
            }
        } else {
            sb.append(mias ? "Tus reservas " : "Reservas de " + nombreZona + " ").append(rango.etiqueta()).append(":\n");
            Map<String, List<Map<String, Object>>> porDia = lista.stream().collect(Collectors.groupingBy(
                    r -> Objects.toString(r.get("fecha")), TreeMap::new, Collectors.toList()));
            boolean variosDias = porDia.size() > 1 || !rango.desde().equals(rango.hasta());
            porDia.forEach((dia, reservas) -> {
                if (variosDias) {
                    sb.append("\n").append(capitalizar(fecha(dia))).append(":\n");
                }
                reservas.forEach(r -> {
                    sb.append("• ").append(capitalizar(Objects.toString(r.get("zona"), ""))).append(" de ")
                            .append(hora(r.get("horaInicio"))).append(" a ").append(hora(r.get("horaFin")));
                    if (r.get("reservadoPor") != null) {
                        Map<String, Object> d = mapa(r.get("reservadoPor"));
                        sb.append(" (").append(Objects.toString(d.get("nombre"), "")).append(", ").append(torreApto(d)).append(")");
                    } else if (Boolean.TRUE.equals(r.get("esMiReserva"))) {
                        sb.append(" (tu reserva)");
                    } else {
                        sb.append(" (ocupado)");
                    }
                    sb.append("\n");
                });
            });
            if (!mias && zona != null) {
                sb.append("\nEl resto del horario permitido está disponible.");
            }
        }
        if (zona != null) {
            relevantes.stream().filter(c -> TextoIA.normalizar(c.getTitulo()).contains(TextoIA.normalizar(zona).split(" ")[0]))
                    .findFirst().ifPresent(c -> sb.append("\n\nRecuerda: ").append(c.getContenido()));
        }
        if (!esStaff(u) && !mias) {
            sb.append("\n\nPuedes reservar desde la sección Reservas de la app.");
        }
        return sb.toString().trim();
    }

    private String accesos(Usuario u, String original, String n, List<String> usadas) {
        Rango rango = rango(n, true);
        Map<String, Object> args = new HashMap<>();
        args.put("fecha", rango.desde().toString());
        placa(original).ifPresent(p -> args.put("placa", p));
        if (tiene(n, "peatonal", "a pie", "visitante", "visita")) {
            args.put("tipo", "PEATONAL");
        } else if (tiene(n, "vehicul", "carro", "moto")) {
            args.put("tipo", "VEHICULAR");
        }
        Map<String, Object> datos = usar("consultar_accesos", args, u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> lista = lista(datos, "accesos");
        String cuando = args.containsKey("placa") ? "de la placa " + args.get("placa") : rango.etiqueta();
        if (lista.isEmpty()) {
            return "No hay ingresos registrados " + cuando + (esStaff(u) ? "." : " para tu apartamento.");
        }
        long peatonales = lista.stream().filter(a -> "PEATONAL".equals(a.get("tipo"))).count();
        long vehiculares = lista.size() - peatonales;
        long dentro = lista.stream().filter(a -> a.get("horaEntrada") != null && a.get("horaSalida") == null).count();
        StringBuilder sb = new StringBuilder();
        if (args.containsKey("placa")) {
            sb.append("La placa ").append(args.get("placa")).append(" tiene ").append(lista.size()).append(lista.size() == 1 ? " ingreso registrado" : " ingresos registrados");
        } else {
            sb.append(capitalizar(cuando)).append(lista.size() == 1 ? " se registró 1 ingreso" : " se registraron " + lista.size() + " ingresos");
        }
        if (peatonales > 0 && vehiculares > 0) {
            sb.append(" (").append(peatonales).append(" peatonales y ").append(vehiculares).append(" vehiculares)");
        }
        sb.append(".");
        if (dentro > 0) {
            sb.append(" ").append(dentro).append(dentro == 1 ? " persona sigue" : " personas siguen").append(" dentro (sin salida registrada).");
        }
        sb.append("\n\n");
        lista.stream().limit(12).forEach(a -> {
            sb.append("• ");
            if ("VEHICULAR".equals(a.get("tipo"))) {
                sb.append("Vehículo ").append(Objects.toString(a.get("placa"), "")).append(a.get("persona") != null ? " (" + a.get("persona") + ")" : "");
            } else {
                sb.append(Objects.toString(a.get("visitante"), "Visitante"));
            }
            sb.append(" hacia ").append(Objects.toString(a.get("destino"), "")).append(", entrada ").append(hora(a.get("horaEntrada")));
            if (a.get("horaSalida") != null) {
                sb.append(", salida ").append(hora(a.get("horaSalida")));
            }
            sb.append("\n");
        });
        if (lista.size() > 12) {
            sb.append("y ").append(lista.size() - 12).append(" más.");
        }
        return sb.toString().trim();
    }

    private String mascotas(Usuario u, String n, List<String> usadas) {
        Map<String, Object> args = new HashMap<>();
        torre(n).ifPresent(t -> args.put("torre", t));
        Map<String, Object> datos = usar("consultar_mascotas", args, u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> lista = lista(datos, "mascotas");
        if (!esStaff(u)) {
            if (lista.isEmpty()) {
                return "No tienes mascotas registradas. Puedes registrarlas en la sección Mascotas.";
            }
            return "Tienes " + lista.size() + (lista.size() == 1 ? " mascota registrada: " : " mascotas registradas: ")
                    + lista.stream().map(m -> m.get("nombre") + " (" + Objects.toString(m.get("tipo"), "").toLowerCase()
                    + (m.get("raza") != null ? ", " + m.get("raza") : "") + ")").collect(Collectors.joining(", ")) + ".";
        }
        if (lista.isEmpty()) {
            return "No hay mascotas registradas" + (args.containsKey("torre") ? " en la torre " + args.get("torre") : "") + ".";
        }
        Map<String, Long> porTipo = lista.stream().collect(Collectors.groupingBy(m -> Objects.toString(m.get("tipo"), "otro").toLowerCase(), Collectors.counting()));
        StringBuilder sb = new StringBuilder("Hay " + lista.size() + (lista.size() == 1 ? " mascota registrada" : " mascotas registradas")
                + (args.containsKey("torre") ? " en la torre " + args.get("torre") : "") + ": "
                + porTipo.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).collect(Collectors.joining(", ")) + ".\n\n");
        lista.stream().limit(12).forEach(m -> {
            Map<String, Object> d = mapa(m.get("propietario"));
            sb.append("• ").append(m.get("nombre")).append(" (").append(Objects.toString(m.get("tipo"), "").toLowerCase()).append(") de ")
                    .append(Objects.toString(d.get("nombre"), "residente")).append(", ").append(torreApto(d)).append("\n");
        });
        return sb.toString().trim();
    }

    private String vehiculos(Usuario u, String original, String n, List<String> usadas) {
        Map<String, Object> args = new HashMap<>();
        Optional<String> placa = placa(original);
        placa.ifPresent(p -> args.put("placa", p));
        torre(n).ifPresent(t -> args.put("torre", t));
        boolean sinParqueadero = tiene(n, "sin parqueadero", "no tienen parqueadero", "no tiene parqueadero", "sin asignar", "falta asignar", "pendientes de parqueadero");
        if (sinParqueadero) {
            args.put("sinParqueadero", true);
        }
        Map<String, Object> datos = usar("consultar_vehiculos", args, u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> lista = lista(datos, "vehiculos");

        if (!esStaff(u)) {
            if (lista.isEmpty()) {
                return "No tienes vehículos registrados. Puedes agregarlos en tu Perfil, en \"Mis vehículos\", "
                        + "con la marca, el modelo y la placa; luego la administración te asignará el parqueadero.";
            }
            StringBuilder sb = new StringBuilder(lista.size() == 1 ? "Tienes un vehículo registrado:\n" : "Tienes " + lista.size() + " vehículos registrados:\n");
            lista.forEach(v -> sb.append("• ").append(capitalizar(descripcionVehiculo(v))).append(", placa ").append(v.get("placa")).append(": ")
                    .append(v.get("parqueadero") != null ? "parqueadero " + v.get("parqueadero") : "pendiente de asignación de parqueadero").append("\n"));
            return sb.toString().trim();
        }

        if (placa.isPresent()) {
            if (lista.isEmpty()) {
                return "La placa " + placa.get() + " no está registrada como vehículo de ningún residente. Si está ingresando, trátalo como visitante.";
            }
            StringBuilder sb = new StringBuilder();
            lista.forEach(v -> {
                Map<String, Object> d = mapa(v.get("propietario"));
                sb.append("La placa ").append(v.get("placa")).append(" es un ").append(descripcionVehiculo(v))
                        .append(" de ").append(Objects.toString(d.get("nombre"), "un residente")).append(" (").append(torreApto(d)).append(")")
                        .append(v.get("parqueadero") != null ? ", con parqueadero " + v.get("parqueadero") : ", aún sin parqueadero asignado").append(".\n");
            });
            return sb.toString().trim();
        }

        Map<String, Object> metricas = usar("resumen_conjunto", Map.of(), u, usadas);
        int total = entero(metricas.get("vehiculosRegistrados"));
        int sinAsignar = entero(metricas.get("vehiculosSinParqueadero"));
        StringBuilder sb = new StringBuilder();
        if (sinParqueadero) {
            if (lista.isEmpty()) {
                return "Todos los vehículos registrados tienen parqueadero asignado.";
            }
            sb.append(lista.size()).append(lista.size() == 1 ? " vehículo está" : " vehículos están").append(" esperando parqueadero:\n");
        } else {
            sb.append("Hay ").append(total).append(total == 1 ? " vehículo registrado" : " vehículos registrados")
                    .append(args.containsKey("torre") ? " (mostrando la torre " + args.get("torre") + ")" : "")
                    .append("; ").append(total - sinAsignar).append(" con parqueadero y ").append(sinAsignar).append(" sin asignar.\n");
        }
        lista.stream().limit(15).forEach(v -> {
            Map<String, Object> d = mapa(v.get("propietario"));
            sb.append("• ").append(v.get("placa")).append(" ").append(descripcionVehiculo(v)).append(", ")
                    .append(Objects.toString(d.get("nombre"), "")).append(" (").append(torreApto(d)).append(")")
                    .append(v.get("parqueadero") != null ? ": parqueadero " + v.get("parqueadero") : ": sin parqueadero").append("\n");
        });
        if (sinAsignar > 0 && u.getRol() == Usuario.Rol.ADMINISTRADOR) {
            sb.append("\nPuedes asignar los parqueaderos desde Menú > Vehículos.");
        }
        return sb.toString().trim();
    }

    private String residente(Usuario u, String original, String n, List<String> usadas) {
        String filtro;
        Optional<String> t = torre(n);
        Optional<String> a = apto(n);
        if (t.isPresent() && a.isPresent()) {
            filtro = t.get() + "-" + a.get();
        } else if (t.isPresent()) {
            filtro = "torre " + t.get();
        } else {
            filtro = n.replaceAll("\\b(quien|vive|en|el|la|de|del|residente|residentes|telefono|buscar|datos|dame|los|las)\\b", " ").trim();
        }
        Map<String, Object> datos = usar("buscar_residente", Map.of("texto", filtro), u, usadas);
        if (datos.containsKey("error")) {
            return error(datos);
        }
        List<Map<String, Object>> lista = lista(datos, "residentes");
        if (lista.isEmpty()) {
            return "No encontré residentes que coincidan con \"" + filtro + "\".";
        }
        StringBuilder sb = new StringBuilder(lista.size() == 1 ? "Encontré a:\n" : "Encontré " + lista.size() + " residentes:\n");
        lista.stream().limit(15).forEach(r -> sb.append("• ").append(r.get("nombre")).append(", ").append(torreApto(r))
                .append(r.get("telefono") != null ? ", tel. " + r.get("telefono") : "").append("\n"));
        return sb.toString().trim();
    }

    private String resumen(Usuario u, List<String> usadas) {
        Map<String, Object> m = usar("resumen_conjunto", Map.of(), u, usadas);
        if (m.containsKey("error")) {
            return error(m);
        }
        Map<String, Object> quejas = mapa(m.get("quejasPorEstado"));
        int pendientes = entero(quejas.get("PENDIENTE")) + entero(quejas.get("EN_PROCESO"));
        int altas = entero(m.get("quejasAbiertasPrioridadAlta"));
        int paquetes = entero(m.get("paquetesPendientes"));
        int residentes = entero(m.get("residentes"));
        int mora = entero(m.get("residentesEnMoraMes"));
        int sinParq = entero(m.get("vehiculosSinParqueadero"));
        int accesos = entero(m.get("accesosVehicularesHoy")) + entero(m.get("accesosPeatonalesHoy"));
        Map<String, Object> reservas = mapa(m.get("reservasProximos7Dias"));
        int totalReservas = reservas.values().stream().mapToInt(this::entero).sum();

        LocalDate hoy = LocalDate.now(HerramientasIA.ZONA);
        StringBuilder sb = new StringBuilder("Así va el conjunto hoy, " + hoy.getDayOfWeek().getDisplayName(TextStyle.FULL, ES) + " "
                + hoy.getDayOfMonth() + " de " + MESES[hoy.getMonthValue() - 1] + ":\n\n");
        sb.append("• Quejas abiertas: ").append(pendientes).append(altas > 0 ? " (" + altas + " de prioridad alta)" : "").append("\n");
        sb.append("• Paquetes por entregar: ").append(paquetes).append("\n");
        sb.append("• Reservas en los próximos 7 días: ").append(totalReservas).append("\n");
        sb.append("• Ingresos registrados hoy: ").append(accesos).append("\n");
        sb.append("• Vehículos sin parqueadero: ").append(sinParq).append("\n");
        if (u.getRol() == Usuario.Rol.ADMINISTRADOR) {
            sb.append("• Recaudo del mes: ").append(dinero(m.get("recaudoMes"))).append("\n");
            sb.append("• Residentes sin pago aprobado este mes: ").append(mora)
                    .append(residentes > 0 ? " de " + residentes + " (" + porcentaje(mora, residentes) + ")" : "").append("\n");
        }
        List<String> alertas = new ArrayList<>();
        if (altas > 0) {
            alertas.add(altas == 1 ? "atender primero la queja de prioridad alta" : "atender primero las " + altas + " quejas de prioridad alta");
        }
        if (paquetes > 5) {
            alertas.add("hay bastantes paquetes acumulados en portería");
        }
        if (u.getRol() == Usuario.Rol.ADMINISTRADOR && residentes > 0 && mora * 100 / residentes >= 30
                && LocalDate.now(HerramientasIA.ZONA).getDayOfMonth() > 10) {
            alertas.add("la mora supera el 30%, conviene enviar recordatorios de pago");
        }
        if (sinParq > 0 && u.getRol() == Usuario.Rol.ADMINISTRADOR) {
            alertas.add("asignar parqueadero a " + sinParq + (sinParq == 1 ? " vehículo" : " vehículos"));
        }
        if (!alertas.isEmpty()) {
            sb.append("\nPrioridades: ").append(String.join("; ", alertas)).append(".");
        } else {
            sb.append("\nNo veo alertas importantes en este momento.");
        }
        return sb.toString().trim();
    }

    private String conocimiento(Usuario u, List<ConocimientoIA> relevantes) {
        if (relevantes.isEmpty()) {
            return "No tengo información sobre eso todavía. Puedes consultarlo con la administración. " + ejemplos(u);
        }
        ConocimientoIA mejor = relevantes.get(0);
        StringBuilder sb = new StringBuilder(mejor.getContenido());
        if (relevantes.size() > 1) {
            sb.append("\n\nTambién te puede servir: ").append(relevantes.get(1).getTitulo().toLowerCase()).append(".");
        }
        return sb.toString();
    }

    private Map<String, Object> usar(String herramienta, Map<String, Object> args, Usuario u, List<String> usadas) {
        usadas.add(herramienta);
        return herramientas.ejecutarDatos(herramienta, args, u);
    }

    private YearMonth periodo(String n) {
        YearMonth actual = YearMonth.now(HerramientasIA.ZONA);
        if (tiene(n, "mes pasado", "mes anterior")) {
            return actual.minusMonths(1);
        }
        for (int i = 0; i < MESES.length; i++) {
            if (n.contains(" " + MESES[i] + " ")) {
                Matcher m = ANIO.matcher(n);
                boolean conAnio = m.find();
                YearMonth ym = YearMonth.of(conAnio ? Integer.parseInt(m.group(1)) : actual.getYear(), i + 1);
                return !conAnio && ym.isAfter(actual) ? ym.minusYears(1) : ym;
            }
        }
        return actual;
    }

    private Rango rango(String n) {
        return rango(n, false);
    }

    private Rango rango(String n, boolean hoyPorDefecto) {
        LocalDate hoy = LocalDate.now(HerramientasIA.ZONA);
        if (tiene(n, "pasado manana")) {
            return new Rango(hoy.plusDays(2), hoy.plusDays(2), "pasado mañana");
        }
        if (tiene(n, "manana")) {
            return new Rango(hoy.plusDays(1), hoy.plusDays(1), "mañana");
        }
        if (tiene(n, "ayer")) {
            return new Rango(hoy.minusDays(1), hoy.minusDays(1), "ayer");
        }
        if (tiene(n, " hoy ", "hoy")) {
            return new Rango(hoy, hoy, "hoy");
        }
        if (tiene(n, "fin de semana")) {
            LocalDate sabado = hoy.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            return new Rango(sabado, sabado.plusDays(1), "este fin de semana");
        }
        if (tiene(n, "proxima semana", "siguiente semana", "semana que viene")) {
            LocalDate lunes = hoy.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            return new Rango(lunes, lunes.plusDays(6), "la próxima semana");
        }
        if (tiene(n, "este mes")) {
            return new Rango(hoy, hoy.with(TemporalAdjusters.lastDayOfMonth()), "en lo que queda del mes");
        }
        for (Map.Entry<String, DayOfWeek> e : DIAS.entrySet()) {
            if (n.contains(" " + e.getKey() + " ")) {
                LocalDate dia = hoy.with(TemporalAdjusters.nextOrSame(e.getValue()));
                return new Rango(dia, dia, "el " + fecha(dia));
            }
        }
        return hoyPorDefecto ? new Rango(hoy, hoy, "hoy") : new Rango(hoy, hoy.plusDays(7), "en los próximos 7 días");
    }

    private String zona(String n) {
        if (tiene(n, "piscina")) return "piscina";
        if (tiene(n, "gimnasio", " gym")) return "gimnasio";
        if (tiene(n, "salon")) return "salon comunal";
        if (tiene(n, "bbq", "asado", "parrilla")) return "zona bbq";
        return null;
    }

    private String categoriaQueja(String n) {
        if (tiene(n, "ruido", "musica", "fiesta", "bulla")) return "RUIDO";
        if (tiene(n, "mascota", "perro", "gato")) return "MASCOTA";
        if (tiene(n, "violen", "agresi", "pelea")) return "VIOLENCIA";
        if (tiene(n, "parqueadero", "vehiculo", "carro")) return "PARQUEADERO";
        return null;
    }

    private Optional<String> torre(String n) {
        Matcher m = TORRE.matcher(n);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private Optional<String> apto(String n) {
        Matcher m = APTO.matcher(n);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private Optional<String> placa(String original) {
        Matcher m = PLACA.matcher(original.toUpperCase());
        while (m.find()) {
            String p = m.group(1) + m.group(2);
            if (!Set.of("QUE", "CON", "LOS", "LAS", "DEL", "POR", "UNA", "HAY", "MES", "DIA", "APT", "LES", "SON", "MAS").contains(m.group(1))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private String listaResidentes(List<Map<String, Object>> lista, int max) {
        StringBuilder sb = new StringBuilder();
        lista.stream().limit(max).forEach(r -> sb.append("• ").append(r.get("nombre")).append(" (").append(torreApto(r)).append(")\n"));
        if (lista.size() > max) {
            sb.append("y ").append(lista.size() - max).append(" más.");
        }
        return sb.toString().trim();
    }

    private String descripcionVehiculo(Map<String, Object> v) {
        String tipo = Objects.toString(v.get("tipo"), "CARRO").toLowerCase();
        String desc = tipo + " " + Objects.toString(v.get("marca"), "") + " " + Objects.toString(v.get("modelo"), "");
        if (v.get("color") != null) {
            desc += " " + v.get("color").toString().toLowerCase();
        }
        return desc.replaceAll("\\s+", " ").trim();
    }

    private String torreApto(Map<String, Object> d) {
        Object t = d.get("torre");
        Object a = d.get("apartamento");
        if (t == null && a == null) return "sin torre";
        return "T" + Objects.toString(t, "?") + "-" + Objects.toString(a, "?");
    }

    private String nombreMes(YearMonth ym) {
        return MESES[ym.getMonthValue() - 1] + " de " + ym.getYear();
    }

    private String nombreMes(String periodo) {
        try {
            return nombreMes(YearMonth.parse(periodo));
        } catch (Exception e) {
            return periodo;
        }
    }

    private String fecha(Object valor) {
        LocalDate d = aFecha(valor);
        if (d == null) return Objects.toString(valor, "");
        LocalDate hoy = LocalDate.now(HerramientasIA.ZONA);
        if (d.equals(hoy)) return "hoy";
        if (d.equals(hoy.minusDays(1))) return "ayer";
        if (d.equals(hoy.plusDays(1))) return "mañana";
        String texto = d.getDayOfWeek().getDisplayName(TextStyle.FULL, ES) + " " + d.getDayOfMonth() + " de " + MESES[d.getMonthValue() - 1];
        return d.getYear() != hoy.getYear() ? texto + " de " + d.getYear() : texto;
    }

    private LocalDate aFecha(Object valor) {
        if (valor instanceof LocalDate d) return d;
        if (valor instanceof LocalDateTime dt) return dt.toLocalDate();
        if (valor == null) return null;
        try {
            String s = valor.toString();
            return LocalDate.parse(s.length() >= 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private long diasDesde(Object valor) {
        LocalDate d = aFecha(valor);
        return d == null ? 0 : ChronoUnit.DAYS.between(d, LocalDate.now(HerramientasIA.ZONA));
    }

    private String hora(Object valor) {
        if (valor == null) return "";
        String s = valor.toString();
        return s.length() >= 5 ? s.substring(0, 5) : s;
    }

    private String dinero(Object valor) {
        double d;
        if (valor instanceof Number num) {
            d = num.doubleValue();
        } else {
            try {
                d = Double.parseDouble(Objects.toString(valor, "0"));
            } catch (NumberFormatException e) {
                return Objects.toString(valor, "$0");
            }
        }
        return "$" + String.format(ES, "%,.0f", d);
    }

    private String plural(String palabra, long cantidad) {
        if (cantidad == 1 || palabra.endsWith("s") || palabra.contains(" ")) return palabra;
        return palabra + "s";
    }

    private String cuando(Object valor) {
        String f = fecha(valor);
        return Set.of("hoy", "ayer", "mañana").contains(f) ? f : "el " + f;
    }

    private String porcentaje(int parte, int total) {
        return total == 0 ? "0%" : Math.round(parte * 100.0 / total) + "%";
    }

    private String estadoLegible(Object estado) {
        return switch (Objects.toString(estado, "")) {
            case "PENDIENTE" -> "Pendiente";
            case "EN_PROCESO" -> "En proceso";
            case "RESUELTO" -> "Resuelta";
            case "ENTREGADO" -> "Entregado";
            case "APROBADO" -> "Aprobado";
            case "RECHAZADO" -> "Rechazado";
            default -> Objects.toString(estado, "");
        };
    }

    private String error(Map<String, Object> datos) {
        return "No pude consultar esa información en este momento (" + datos.get("error") + "). Intenta de nuevo en unos minutos.";
    }

    private String primerNombre(Usuario u) {
        String nombre = u.getNombre();
        return nombre == null || nombre.isBlank() ? "vecino" : nombre.trim().split("\\s+")[0];
    }

    private String capitalizar(String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private boolean esStaff(Usuario u) {
        return u.getRol() == Usuario.Rol.ADMINISTRADOR || u.getRol() == Usuario.Rol.CELADOR;
    }

    private boolean tiene(String texto, String... claves) {
        for (String c : claves) {
            if (texto.contains(c)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lista(Map<String, Object> datos, String clave) {
        Object v = datos.get(clave);
        return v instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private int entero(Object v) {
        if (v instanceof Number num) return num.intValue();
        try {
            return v == null ? 0 : Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
