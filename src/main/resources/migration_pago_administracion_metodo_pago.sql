-- Ejecutar manualmente en PostgreSQL (Render / Neon) si quieres aceptar también MERCADO_PAGO y EN_LINEA.
-- Sin este script, el backend guarda pagos en línea como PSE (compatible con el CHECK actual).

ALTER TABLE pago_administracion DROP CONSTRAINT IF EXISTS pago_administracion_metodo_pago_check;

ALTER TABLE pago_administracion
    ADD CONSTRAINT pago_administracion_metodo_pago_check
        CHECK (metodo_pago IN ('PSE', 'EFECTIVO', 'MERCADO_PAGO', 'EN_LINEA'));
