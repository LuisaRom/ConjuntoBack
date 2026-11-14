package com.example.APP.IA;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableAsync
public class IaConfig {

    @Bean
    public LlmClient llmClient(
            @Value("${ia.proveedor:anthropic}") String proveedor,
            @Value("${ia.api-key:}") String apiKey,
            @Value("${ia.modelo:}") String modelo,
            @Value("${ia.base-url:}") String baseUrl,
            @Value("${ia.max-tokens:1024}") int maxTokens,
            ObjectMapper objectMapper
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(75_000);
        RestTemplate restTemplate = new RestTemplate(factory);

        if ("anthropic".equalsIgnoreCase(proveedor.trim())) {
            String modeloFinal = modelo.isBlank() ? "claude-haiku-5-5" : modelo;
            return new AnthropicLlmClient(restTemplate, apiKey, modeloFinal, baseUrl, maxTokens);
        }
        String modeloFinal = modelo.isBlank() ? "gpt-4o-mini" : modelo;
        return new OpenAiCompatibleLlmClient(restTemplate, objectMapper, apiKey, modeloFinal, baseUrl, maxTokens);
    }
}
