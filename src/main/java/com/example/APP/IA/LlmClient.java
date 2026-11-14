package com.example.APP.IA;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface LlmClient {

    boolean disponible();

    String proveedor();

    String modelo();

    ResultadoLlm conversar(String sistema,
                           List<MensajeLlm> historial,
                           List<DefinicionHerramienta> herramientas,
                           Function<LlamadaHerramienta, String> ejecutor);

    default String completar(String sistema, String prompt) {
        return conversar(sistema, List.of(new MensajeLlm("user", prompt)), List.of(), llamada -> "").texto();
    }

    record MensajeLlm(String rol, String contenido) {
    }

    record DefinicionHerramienta(String nombre, String descripcion, Map<String, Object> parametros) {
    }

    record LlamadaHerramienta(String nombre, Map<String, Object> argumentos) {
    }

    record ResultadoLlm(String texto, List<String> herramientasUsadas) {
    }
}
