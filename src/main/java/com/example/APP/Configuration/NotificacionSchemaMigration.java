package com.example.APP.Configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Asegura columnas de publicaciones/novedades en PostgreSQL (Render no siempre aplica ddl-auto).
 */
@Component
@Order(1)
public class NotificacionSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificacionSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public NotificacionSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE notificacion ADD COLUMN IF NOT EXISTS imagen_url VARCHAR(512)");
            jdbcTemplate.execute("ALTER TABLE notificacion ADD COLUMN IF NOT EXISTS video_url VARCHAR(512)");
            jdbcTemplate.execute("ALTER TABLE notificacion ADD COLUMN IF NOT EXISTS usuarios_etiquetados TEXT");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS historial_notificacion (
                        id BIGSERIAL PRIMARY KEY,
                        notificacion_original_id BIGINT,
                        mensaje TEXT,
                        fecha_envio TIMESTAMP,
                        imagen_url VARCHAR(512),
                        video_url VARCHAR(512),
                        usuarios_etiquetados TEXT,
                        usuario_id BIGINT,
                        fecha_archivado TIMESTAMP
                    )
                    """);
            log.info("Esquema de notificaciones/novedades verificado");
        } catch (Exception ex) {
            log.error("No se pudo actualizar el esquema de notificaciones: {}", ex.getMessage(), ex);
        }
    }
}
