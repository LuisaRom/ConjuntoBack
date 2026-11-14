package com.example.APP.IA;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.Function;

public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final int MAX_ITERACIONES = 6;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String modelo;
    private final String baseUrl;
    private final int maxTokens;

    public OpenAiCompatibleLlmClient(RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey,
                                     String modelo, String baseUrl, int maxTokens) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.openai.com/v1" : baseUrl.replaceAll("/+$", "");
        this.maxTokens = maxTokens;
    }

    @Override
    public boolean disponible() {
        return (apiKey != null && !apiKey.isBlank()) || baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1");
    }

    @Override
    public String proveedor() {
        return "openai-compatible";
    }

    @Override
    public String modelo() {
        return modelo;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResultadoLlm conversar(String sistema, List<MensajeLlm> historial, List<DefinicionHerramienta> herramientas,
                                  Function<LlamadaHerramienta, String> ejecutor) {
        List<Map<String, Object>> mensajes = new ArrayList<>();
        mensajes.add(Map.of("role", "system", "content", sistema));
        for (MensajeLlm m : historial) {
            mensajes.add(Map.of("role", m.rol(), "content", m.contenido()));
        }

        List<Map<String, Object>> tools = new ArrayList<>();
        for (DefinicionHerramienta h : herramientas) {
            tools.add(Map.of("type", "function", "function",
                    Map.of("name", h.nombre(), "description", h.descripcion(), "parameters", h.parametros())));
        }

        List<String> usadas = new ArrayList<>();
        for (int i = 0; i < MAX_ITERACIONES; i++) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("model", modelo);
            cuerpo.put("max_tokens", maxTokens);
            cuerpo.put("messages", mensajes);
            if (!tools.isEmpty()) {
                cuerpo.put("tools", tools);
            }

            Map<String, Object> respuesta = restTemplate.postForObject(baseUrl + "/chat/completions",
                    new HttpEntity<>(cuerpo, headers()), Map.class);
            List<Map<String, Object>> choices = respuesta != null
                    ? (List<Map<String, Object>>) respuesta.getOrDefault("choices", List.of()) : List.of();
            if (choices.isEmpty()) {
                throw new IllegalStateException("Respuesta vacía del proveedor de IA");
            }

            Map<String, Object> mensaje = (Map<String, Object>) choices.get(0).get("message");
            List<Map<String, Object>> llamadas = mensaje.get("tool_calls") instanceof List<?> l
                    ? (List<Map<String, Object>>) l : List.of();

            if (llamadas.isEmpty()) {
                Object contenido = mensaje.get("content");
                return new ResultadoLlm(contenido != null ? contenido.toString().trim() : "", usadas);
            }

            Map<String, Object> asistente = new LinkedHashMap<>();
            asistente.put("role", "assistant");
            asistente.put("content", mensaje.get("content"));
            asistente.put("tool_calls", llamadas);
            mensajes.add(asistente);

            for (Map<String, Object> llamada : llamadas) {
                Map<String, Object> funcion = (Map<String, Object>) llamada.get("function");
                String nombre = String.valueOf(funcion.get("name"));
                usadas.add(nombre);
                String salida = ejecutor.apply(new LlamadaHerramienta(nombre, leerArgumentos(funcion.get("arguments"))));
                mensajes.add(Map.of("role", "tool", "tool_call_id", String.valueOf(llamada.get("id")), "content", salida));
            }
        }
        return new ResultadoLlm("No pude completar la consulta en este momento. Intenta formularla de otra manera.", usadas);
    }

    private Map<String, Object> leerArgumentos(Object argumentos) {
        if (argumentos instanceof Map<?, ?> m) {
            Map<String, Object> copia = new HashMap<>();
            m.forEach((k, v) -> copia.put(String.valueOf(k), v));
            return copia;
        }
        if (argumentos == null || argumentos.toString().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentos.toString(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }
}
