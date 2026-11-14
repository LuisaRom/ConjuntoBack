package com.example.APP.Service;

import com.example.APP.IA.HerramientasIA;
import com.example.APP.IA.LlmClient;
import com.example.APP.IA.TextoIA;
import com.example.APP.Model.Queja;
import com.example.APP.Repository.QuejaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class IaGestionService {

    private static final Logger log = LoggerFactory.getLogger(IaGestionService.class);
    private static final List<String> CATEGORIAS = List.of(
            "RUIDO", "MASCOTA", "VIOLENCIA", "ZONAS_COMUNES", "PARQUEADERO", "SEGURIDAD", "ASEO", "INFRAESTRUCTURA", "GENERAL");

    private final LlmClient llm;
    private final QuejaRepository quejaRepository;
    private final HerramientasIA herramientas;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public IaGestionService(LlmClient llm, QuejaRepository quejaRepository, HerramientasIA herramientas) {
        this.llm = llm;
        this.quejaRepository = quejaRepository;
        this.herramientas = herramientas;
    }

    @Async
    public void analizarQuejaAsync(Long quejaId) {
        try {
            analizarQueja(quejaId);
        } catch (Exception e) {
            log.warn("No se pudo analizar la queja {}: {}", quejaId, e.getMessage());
        }
    }

    public Queja analizarQueja(Long quejaId) {
        Queja q = quejaRepository.findById(quejaId)
                .orElseThrow(() -> new IllegalArgumentException("Queja no encontrada: " + quejaId));
        Map<String, Object> analisis = null;

        if (llm.disponible()) {
            try {
                String sistema = "Eres un analista de convivencia de un conjunto residencial en Colombia. "
                        + "Respondes únicamente con un objeto JSON válido, sin texto adicional ni bloques de código.";
                String prompt = "Analiza esta queja de un residente.\n"
                        + "Tipo indicado: " + q.getTipoQueja() + "\n"
                        + "Torre/apto reportado: " + q.getTorre() + "-" + q.getApartamento() + "\n"
                        + "Descripción: " + TextoIA.recortar(q.getDescripcion(), 1500) + "\n\n"
                        + "Devuelve JSON con las claves:\n"
                        + "categoria: una de " + CATEGORIAS + "\n"
                        + "prioridad: ALTA (riesgo para personas o bienes, violencia, emergencias), MEDIA (afecta la convivencia de forma reiterada) o BAJA\n"
                        + "resumen: una frase de máximo 25 palabras\n"
                        + "respuestaSugerida: respuesta cordial de la administración al residente, máximo 70 palabras";
                analisis = leerJson(llm.completar(sistema, prompt));
            } catch (Exception e) {
                log.warn("Fallo análisis IA de queja {}, se usa heurística: {}", quejaId, e.getMessage());
            }
        }
        if (analisis == null) {
            analisis = analisisHeuristico(q);
        }

        String categoria = Objects.toString(analisis.get("categoria"), "GENERAL").toUpperCase().trim();
        String prioridad = Objects.toString(analisis.get("prioridad"), "MEDIA").toUpperCase().trim();
        q.setCategoriaIA(CATEGORIAS.contains(categoria) ? categoria : "GENERAL");
        q.setPrioridadIA(Set.of("ALTA", "MEDIA", "BAJA").contains(prioridad) ? prioridad : "MEDIA");
        q.setResumenIA(TextoIA.recortar(Objects.toString(analisis.get("resumen"), ""), 600));
        q.setRespuestaSugeridaIA(TextoIA.recortar(Objects.toString(analisis.get("respuestaSugerida"), ""), 1500));
        q.setFechaAnalisisIA(LocalDateTime.now());
        return quejaRepository.save(q);
    }

    public Map<String, Object> resumenGestion() {
        Map<String, Object> metricas = herramientas.metricasConjunto();
        List<Map<String, Object>> quejasAbiertas = quejaRepository.findAll().stream()
                .filter(q -> q.getEstado() != Queja.Estado.RESUELTO)
                .sorted(Comparator.comparing(Queja::getFechaCreacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(15)
                .map(q -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", q.getId());
                    m.put("tipo", q.getTipoQueja());
                    m.put("prioridad", q.getPrioridadIA());
                    m.put("estado", q.getEstado());
                    m.put("descripcion", TextoIA.recortar(q.getDescripcion(), 200));
                    m.put("fecha", q.getFechaCreacion());
                    return m;
                }).toList();

        String analisis = null;
        boolean generadoPorIA = false;
        if (llm.disponible()) {
            try {
                String sistema = "Eres un asesor experto en administración de propiedad horizontal en Colombia. "
                        + "Escribes en español, en tono profesional y directo.";
                String prompt = "Indicadores actuales del conjunto (JSON):\n" + json.writeValueAsString(metricas)
                        + "\n\nQuejas abiertas más recientes (JSON):\n" + json.writeValueAsString(quejasAbiertas)
                        + "\n\nRedacta un informe breve para la administración con estas secciones: "
                        + "1) Situación general (2-3 frases), 2) Alertas prioritarias, 3) Patrones detectados, "
                        + "4) Acciones recomendadas para esta semana (máximo 5, concretas). "
                        + "Usa solo los datos entregados; no inventes cifras. Máximo 250 palabras.";
                analisis = llm.completar(sistema, prompt);
                generadoPorIA = analisis != null && !analisis.isBlank();
            } catch (Exception e) {
                log.warn("Fallo resumen de gestión con IA: {}", e.getMessage());
            }
        }
        if (!generadoPorIA) {
            analisis = resumenHeuristico(metricas);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("metricas", metricas);
        r.put("quejasAbiertas", quejasAbiertas);
        r.put("analisis", analisis);
        r.put("generadoPorIA", generadoPorIA);
        r.put("fecha", LocalDateTime.now(HerramientasIA.ZONA));
        return r;
    }

    public Map<String, Object> redactarComunicado(String tema, String tono, String detalles) {
        if (tema == null || tema.isBlank()) {
            throw new IllegalArgumentException("El tema es obligatorio");
        }
        String tonoFinal = tono == null || tono.isBlank() ? "cordial" : tono.trim();
        String texto = null;
        if (llm.disponible()) {
            try {
                texto = llm.completar(
                        "Eres la administración de un conjunto residencial en Colombia y redactas comunicados para los residentes.",
                        "Redacta un comunicado en tono " + tonoFinal + " sobre: " + tema.trim()
                                + (detalles != null && !detalles.isBlank() ? "\nDetalles a incluir: " + detalles.trim() : "")
                                + "\nMáximo 120 palabras, sin firma ni placeholders entre corchetes, listo para publicar.");
            } catch (Exception e) {
                log.warn("Fallo redacción de comunicado: {}", e.getMessage());
            }
        }
        boolean porIA = texto != null && !texto.isBlank();
        if (!porIA) {
            texto = "Estimados residentes:\n\nLes informamos sobre " + tema.trim() + "."
                    + (detalles != null && !detalles.isBlank() ? " " + detalles.trim() : "")
                    + "\n\nAgradecemos su atención y colaboración.\n\nAdministración";
        }
        return Map.of("texto", texto.trim(), "generadoPorIA", porIA);
    }

    private Map<String, Object> leerJson(String texto) throws Exception {
        if (texto == null) {
            return null;
        }
        int inicio = texto.indexOf('{');
        int fin = texto.lastIndexOf('}');
        if (inicio < 0 || fin <= inicio) {
            return null;
        }
        return json.readValue(texto.substring(inicio, fin + 1), new TypeReference<Map<String, Object>>() {
        });
    }

    private Map<String, Object> analisisHeuristico(Queja q) {
        String d = TextoIA.normalizar(q.getTipoQueja() + " " + q.getDescripcion());
        String categoria = "GENERAL";
        if (contiene(d, "violen", "agres", "golpe", "amenaz", "pelea")) categoria = "VIOLENCIA";
        else if (contiene(d, "robo", "hurto", "sospech", "inseguri", "intrus")) categoria = "SEGURIDAD";
        else if (contiene(d, "ruido", "musica", "fiesta", "bulla", "grito")) categoria = "RUIDO";
        else if (contiene(d, "mascota", "perro", "gato", "excremento", "ladra")) categoria = "MASCOTA";
        else if (contiene(d, "parque", "carro", "moto", "vehicul")) categoria = "PARQUEADERO";
        else if (contiene(d, "basura", "aseo", "sucio", "olor")) categoria = "ASEO";
        else if (contiene(d, "fuga", "gotera", "ascensor", "luz", "daño", "dano", "humedad")) categoria = "INFRAESTRUCTURA";
        else if (contiene(d, "piscina", "gimnasio", "salon", "bbq")) categoria = "ZONAS_COMUNES";

        String prioridad = "BAJA";
        if (Set.of("VIOLENCIA", "SEGURIDAD").contains(categoria) || contiene(d, "gas", "incendio", "herido", "emergencia", "urgente")) {
            prioridad = "ALTA";
        } else if (Set.of("RUIDO", "MASCOTA", "PARQUEADERO", "INFRAESTRUCTURA").contains(categoria) || contiene(d, "siempre", "todos los dias", "reiterad")) {
            prioridad = "MEDIA";
        }
        Map<String, Object> m = new HashMap<>();
        m.put("categoria", categoria);
        m.put("prioridad", prioridad);
        m.put("resumen", TextoIA.recortar(q.getDescripcion(), 140));
        m.put("respuestaSugerida", "Hemos recibido su reporte y la administración lo está revisando. "
                + "Le informaremos sobre las acciones tomadas. Gracias por contribuir a la sana convivencia.");
        return m;
    }

    @SuppressWarnings("unchecked")
    private String resumenHeuristico(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("Situación general:\n");
        sb.append("- Residentes registrados: ").append(m.get("residentes")).append("\n");
        sb.append("- Quejas por estado: ").append(m.get("quejasPorEstado")).append("\n");
        sb.append("- Paquetes pendientes por entregar: ").append(m.get("paquetesPendientes")).append("\n");
        sb.append("- Residentes sin pago aprobado en ").append(m.get("periodoActual")).append(": ").append(m.get("residentesEnMoraMes")).append("\n");
        sb.append("\nAcciones sugeridas:\n");
        Object alta = m.get("quejasAbiertasPrioridadAlta");
        if (alta instanceof Number n && n.longValue() > 0) {
            sb.append("- Atender primero las ").append(n).append(" quejas de prioridad alta.\n");
        }
        Map<String, Long> porCategoria = (Map<String, Long>) m.get("quejasAbiertasPorCategoria");
        if (porCategoria != null && !porCategoria.isEmpty()) {
            String top = porCategoria.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
            sb.append("- Publicar un comunicado preventivo sobre ").append(top.toLowerCase().replace("_", " ")).append(", la categoría con más quejas abiertas.\n");
        }
        sb.append("- Enviar recordatorio de pago a los residentes pendientes del periodo.\n");
        sb.append("\n(Resumen automático sin IA: configure IA_API_KEY para un análisis más completo.)");
        return sb.toString();
    }

    private boolean contiene(String texto, String... claves) {
        for (String c : claves) {
            if (texto.contains(c)) {
                return true;
            }
        }
        return false;
    }
}
