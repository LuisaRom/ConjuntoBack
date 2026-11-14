package com.example.APP.IA;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.Function;

public class AnthropicLlmClient implements LlmClient {

    private static final int MAX_ITERACIONES = 6;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String modelo;
    private final String baseUrl;
    private final int maxTokens;

    public AnthropicLlmClient(RestTemplate restTemplate, String apiKey, String modelo, String baseUrl, int maxTokens) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.anthropic.com" : baseUrl.replaceAll("/+$", "");
        this.maxTokens = maxTokens;
    }

    @Override
    public boolean disponible() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String proveedor() {
        return "anthropic";
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
        for (MensajeLlm m : historial) {
            mensajes.add(new HashMap<>(Map.of("role", m.rol(), "content", m.contenido())));
        }

        List<Map<String, Object>> tools = new ArrayList<>();
        for (DefinicionHerramienta h : herramientas) {
            tools.add(Map.of("name", h.nombre(), "description", h.descripcion(), "input_schema", h.parametros()));
        }

        List<String> usadas = new ArrayList<>();
        for (int i = 0; i < MAX_ITERACIONES; i++) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("model", modelo);
            cuerpo.put("max_tokens", maxTokens);
            cuerpo.put("system", sistema);
            cuerpo.put("messages", mensajes);
            if (!tools.isEmpty()) {
                cuerpo.put("tools", tools);
            }

            Map<String, Object> respuesta = restTemplate.postForObject(baseUrl + "/v1/messages",
                    new HttpEntity<>(cuerpo, headers()), Map.class);
            if (respuesta == null) {
                throw new IllegalStateException("Respuesta vacía del proveedor de IA");
            }

            List<Map<String, Object>> contenido = (List<Map<String, Object>>) respuesta.getOrDefault("content", List.of());
            String stopReason = String.valueOf(respuesta.get("stop_reason"));

            if (!"tool_use".equals(stopReason)) {
                return new ResultadoLlm(extraerTexto(contenido), usadas);
            }

            mensajes.add(new HashMap<>(Map.of("role", "assistant", "content", contenido)));
            List<Map<String, Object>> resultados = new ArrayList<>();
            for (Map<String, Object> bloque : contenido) {
                if (!"tool_use".equals(bloque.get("type"))) {
                    continue;
                }
                String nombre = String.valueOf(bloque.get("name"));
                Map<String, Object> entrada = bloque.get("input") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();
                usadas.add(nombre);
                String salida = ejecutor.apply(new LlamadaHerramienta(nombre, entrada));
                resultados.add(Map.of("type", "tool_result", "tool_use_id", bloque.get("id"), "content", salida));
            }
            mensajes.add(new HashMap<>(Map.of("role", "user", "content", resultados)));
        }
        return new ResultadoLlm("No pude completar la consulta en este momento. Intenta formularla de otra manera.", usadas);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        return headers;
    }

    private String extraerTexto(List<Map<String, Object>> contenido) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> bloque : contenido) {
            if ("text".equals(bloque.get("type")) && bloque.get("text") != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(bloque.get("text"));
            }
        }
        return sb.toString().trim();
    }
}
