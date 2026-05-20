package com.example.APP.Configuration;

import com.example.APP.Security.CustomUserDetailsService;
import com.example.APP.Security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService customUserDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, CustomUserDetailsService customUserDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pagos/mercadopago/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pagos/mercadopago/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/pagos/resultado").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/crear").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/residentes").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.POST, "/api/usuarios/residentes").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.GET, "/api/residentes").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.POST, "/api/residentes").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/mensajeria").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.GET, "/api/mensajes/contactos").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.GET, "/api/mensajes/**").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.GET, "/api/notificaciones/novedades").hasAnyRole("ADMINISTRADOR", "CELADOR", "RESIDENTE")
                        .requestMatchers(HttpMethod.GET, "/api/notificaciones/chat/**").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.POST, "/api/notificaciones/chat/enviar").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.POST, "/api/notificaciones/recibos/enviar").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.GET, "/api/notificaciones/**").hasAnyRole("ADMINISTRADOR", "CELADOR", "RESIDENTE")
                        .requestMatchers(HttpMethod.POST, "/api/notificaciones").hasAnyRole("ADMINISTRADOR", "CELADOR", "RESIDENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/notificaciones/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/notificaciones/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/quejas/crear").hasRole("RESIDENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/quejas/*/en-proceso", "/api/quejas/*/finalizar")
                        .hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.GET, "/api/reservas/todas").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.POST, "/api/reservas/crear").hasRole("RESIDENTE")
                        .requestMatchers(HttpMethod.GET, "/api/mascotas/**").hasAnyRole("ADMINISTRADOR", "CELADOR", "RESIDENTE")
                        .requestMatchers(HttpMethod.POST, "/api/mascotas", "/api/mascotas/crear").hasRole("RESIDENTE")
                        .requestMatchers(HttpMethod.DELETE, "/api/mascotas/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/accesos/validar-qr").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/accesos/*").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers(HttpMethod.GET, "/api/quejas/admin/tarjetas").hasAnyRole("ADMINISTRADOR", "CELADOR")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
