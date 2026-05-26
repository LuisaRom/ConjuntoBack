-- Ejecutar en PostgreSQL (Render) si las publicaciones/novedades fallan al guardar.

ALTER TABLE notificacion ADD COLUMN IF NOT EXISTS imagen_url VARCHAR(512);
ALTER TABLE notificacion ADD COLUMN IF NOT EXISTS video_url VARCHAR(512);
ALTER TABLE notificacion ADD COLUMN IF NOT EXISTS usuarios_etiquetados TEXT;

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
);
