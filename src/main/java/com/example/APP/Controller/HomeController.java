package com.example.APP.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> inicio() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("servicio", "ConjuntoBack");
        info.put("estado", "ok");
        info.put("documentacion", "/swagger-ui/index.html");
        info.put("salud", "/actuator/health");
        return info;
    }
}
