-- Migración única: planilla_detalle (tabla) -> planilla_detalle (vista)
-- Ejecutar una sola vez contra una base que tenga la tabla antigua:
--   psql -h 192.168.1.171 -U postgres -d planillas_db -f migrate_planilla_detalle_vista.sql

ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS planilla_id BIGINT;
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS empleado_id BIGINT;
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS item INTEGER;
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS horas_trabajadas NUMERIC(8,2);
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS jornal_hora NUMERIC(12,4);
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS dias_antiguedad INTEGER;
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS bono_antig_pct NUMERIC(8,4);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'planilla_detalle' AND table_type = 'BASE TABLE'
    ) THEN
        UPDATE planilla_detalle_concepto pdc
        SET planilla_id = pd.planilla_id,
            empleado_id = pd.empleado_id,
            item = pd.item,
            horas_trabajadas = pd.horas_trabajadas,
            jornal_hora = pd.jornal_hora,
            dias_antiguedad = pd.dias_antiguedad,
            bono_antig_pct = pd.bono_antig_pct
        FROM planilla_detalle pd
        WHERE pdc.planilla_detalle_id = pd.id;

        ALTER TABLE planilla_detalle_concepto
            DROP CONSTRAINT IF EXISTS planilla_detalle_concepto_planilla_detalle_id_fkey;

        DROP TABLE planilla_detalle;
    END IF;
END $$;
