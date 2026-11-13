package com.example.APP.IA;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TextoIA {

    private static final Set<String> STOPWORDS = Set.of(
            "que", "como", "cual", "cuales", "cuando", "donde", "para", "por", "con", "sin", "los", "las",
            "del", "una", "uno", "unos", "unas", "este", "esta", "estos", "estas", "ese", "esa", "hay",
            "mis", "tus", "sus", "mas", "pero", "porque", "puedo", "puede", "tengo", "tiene", "quiero",
            "sobre", "entre", "desde", "hasta", "muy", "ser", "son", "fue", "era", "estan", "the",
            "hola", "favor", "gracias", "me", "mi", "se", "lo", "le", "al", "el", "la", "de", "en", "y", "o"
    );

    private TextoIA() {
    }

    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase().replaceAll("[^a-z0-9ñ ]", " ").replaceAll("\\s+", " ").trim();
    }

    public static Set<String> tokens(String texto) {
        Set<String> resultado = new LinkedHashSet<>();
        for (String palabra : normalizar(texto).split(" ")) {
            if (palabra.length() < 3 || STOPWORDS.contains(palabra)) {
                continue;
            }
            resultado.add(raiz(palabra));
        }
        return resultado;
    }

    public static String raiz(String palabra) {
        return palabra.length() > 6 ? palabra.substring(0, 6) : palabra;
    }

    public static String recortar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max) + "...";
    }
}
