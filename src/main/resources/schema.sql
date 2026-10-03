-- ============================================================================
--  SUCRE SUREÑA - Sistema de Control de Planillas Salariales
--  Esquema de base de datos para PostgreSQL
--  Diseñado a partir del análisis de la hoja "auxiliar" (Planilla Julio 2026)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- EMPRESA: datos de la empresa (Sucre Sureña / SIDS S.A.)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS empresa (
    id            BIGSERIAL PRIMARY KEY,
    nombre        VARCHAR(200) NOT NULL,
    nit           VARCHAR(50),
    cnss          VARCHAR(50),
    zona          VARCHAR(150),
    calle         VARCHAR(200),
    ciudad        VARCHAR(100),
    telefono      VARCHAR(50),
    activo        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------------------------------------------------------
-- USUARIO: usuarios del sistema (autenticación JWT)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuario (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(60)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    nombre     VARCHAR(150),
    rol        VARCHAR(50)  NOT NULL DEFAULT 'ADMIN',
    activo     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------------------------------------------------------
-- PERSONA: datos básicos de cada dependiente (identificación y datos personales)
-- (origen: columnas B a AW de la hoja auxiliar)
-- Los campos nombre1 y otros_nombres se unificaron en un único campo "nombres".
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS persona (
    id                    BIGSERIAL PRIMARY KEY,
    tipo_documento        VARCHAR(30)  NOT NULL DEFAULT 'CI',
    nro_documento         VARCHAR(50)  NOT NULL,
    apellido_paterno      VARCHAR(100),
    apellido_materno      VARCHAR(100),
    apellido_casada       VARCHAR(100),
    nombres               VARCHAR(200),
    sexo                  VARCHAR(1),
    fecha_nacimiento      DATE,
    pais_nacionalidad     VARCHAR(60)  NOT NULL DEFAULT 'Bolivia',
    direccion             VARCHAR(255),
    telefono              VARCHAR(50),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_persona_documento UNIQUE (tipo_documento, nro_documento)
);

-- ----------------------------------------------------------------------------
-- EMPLEADO: datos propios del contexto laboral del dependiente
-- La información básica de cada dependiente vive en la tabla "persona".
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS empleado (
    id                    BIGSERIAL PRIMARY KEY,
    persona_id            BIGINT NOT NULL REFERENCES persona(id),
    afp                   VARCHAR(60),
    nua_cua               VARCHAR(50),
    cuenta_bancaria       VARCHAR(50),
    fecha_ingreso         DATE,
    fecha_seguro          DATE,
    origen                VARCHAR(10),
    cargo                 VARCHAR(150),
    clasificacion_laboral VARCHAR(150),
    jubilado              BOOLEAN NOT NULL DEFAULT FALSE,
    jornal_hora           NUMERIC(12,4) NOT NULL DEFAULT 0,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_empleado_persona UNIQUE (persona_id)
);

-- ----------------------------------------------------------------------------
-- PARAMETRO: parámetros del sistema (tasas de aportes, horas, días, etc.)
-- Las tasas fueron extraídas de las filas T, U, V, W de la hoja auxiliar:
--   T = 0.005  -> APORTE_SOLIDARIO   (0,5%)
--   U = 0.0115 -> APORTE_NACIONAL    (1,15%)
--   V = 0.10   -> APORTE_AFP         (10%)
--   W = 0.0221 -> RIESGO_COMUN       (2,21%)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS parametro (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(50)  NOT NULL UNIQUE,
    nombre      VARCHAR(150) NOT NULL,
    valor       NUMERIC(12,6),
    unidad      VARCHAR(30)  NOT NULL DEFAULT 'PORCENTAJE',
    descripcion VARCHAR(255),
    activo      BOOLEAN NOT NULL DEFAULT TRUE
);

-- ----------------------------------------------------------------------------
-- BONO_ANTIGUEDAD: tabla de factor de antigüedad (hoja "FactorAntiguedad")
--   En años: 2-4 -> 5%, 5+1d-7 -> 11%, 8-10 -> 18%, 11-14 -> 26%,
--            15-19 -> 34%, 20-24 -> 42%, 25+ -> 50%
--   En días : 720-1800 -> 5%, 1801-2880 -> 11%, ... 9001+ -> 50%
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bono_antiguedad (
    id           BIGSERIAL PRIMARY KEY,
    desde_anios  INTEGER,
    hasta_anios  INTEGER,
    desde_dias   INTEGER,
    hasta_dias   INTEGER,
    porcentaje   NUMERIC(8,4) NOT NULL,
    descripcion  VARCHAR(150),
    activo       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_bono_rango CHECK (
        (desde_anios IS NOT NULL AND desde_anios >= 0) OR
        (desde_dias  IS NOT NULL AND desde_dias  >= 0)
    ),
    CONSTRAINT uq_bono_antiguedad UNIQUE (desde_dias)
);

-- ----------------------------------------------------------------------------
-- CONCEPTO: conceptos de haberes, descuentos y aportes
-- (los descuentos provienen de la hoja "papeletas": cuota sindic., celular,
--  refrigerio, coop., cerveza, queso, etc.)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS concepto (
    id                 BIGSERIAL PRIMARY KEY,
    codigo             VARCHAR(30) NOT NULL UNIQUE,
    nombre             VARCHAR(150) NOT NULL,
    tipo               VARCHAR(20) NOT NULL,           -- HABER | DESCUENTO | APORTE
    aplica_porcentaje  BOOLEAN NOT NULL DEFAULT FALSE,
    porcentaje         NUMERIC(8,4),
    orden              INTEGER NOT NULL DEFAULT 0,
    activo             BOOLEAN NOT NULL DEFAULT TRUE
);

-- ----------------------------------------------------------------------------
-- PLANILLA: cabecera de cada planilla mensual
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS planilla (
    id               BIGSERIAL PRIMARY KEY,
    periodo_anio     INTEGER NOT NULL,
    periodo_mes      INTEGER NOT NULL,
    nombre           VARCHAR(150),
    empresa_id       BIGINT REFERENCES empresa(id),
    estado           VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
    fecha_liquidacion DATE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_planilla_periodo UNIQUE (periodo_anio, periodo_mes)
);

-- ----------------------------------------------------------------------------
-- PLANILLA_DETALLE_CONCEPTO: desglose por concepto de haberes/descuentos
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS planilla_detalle_concepto (
    id                  BIGSERIAL PRIMARY KEY,
    planilla_detalle_id BIGINT NOT NULL,
    planilla_id         BIGINT,
    empleado_id         BIGINT,
    item                INTEGER,
    horas_trabajadas    NUMERIC(8,2),
    jornal_hora         NUMERIC(12,4),
    dias_antiguedad     INTEGER,
    bono_antig_pct      NUMERIC(8,4),
    concepto_id         BIGINT NOT NULL REFERENCES concepto(id),
    tipo                VARCHAR(20),
    monto               NUMERIC(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_detalle_concepto UNIQUE (planilla_detalle_id, concepto_id)
);

-- ----------------------------------------------------------------------------
-- ÍNDICES
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_empleado_activo        ON empleado (activo);
CREATE INDEX IF NOT EXISTS idx_persona_apellidos      ON persona (apellido_paterno, apellido_materno);
CREATE INDEX IF NOT EXISTS idx_detalle_concepto_det   ON planilla_detalle_concepto (planilla_detalle_id);
CREATE INDEX IF NOT EXISTS idx_concepto_tipo          ON concepto (tipo, activo);

-- ============================================================================
-- DATOS INICIALES
-- ============================================================================

-- Empresa (Sucre Sureña / Sociedad Industrial del Sur S.A.)
INSERT INTO empresa (id, nombre, nit, cnss, zona, calle, ciudad, telefono)
VALUES (1, 'Sucre Sureña - Sociedad Industrial del Sur S.A.',
        '1016257022', '06-213-0003', 'Quirpinchaca', 'Mauro Nuñez # 16',
        'Sucre - Bolivia', '64-41112')
ON CONFLICT (id) DO NOTHING;

-- Usuario administrador por defecto: admin / admin123 (cifrado BCrypt)
INSERT INTO usuario (username, password, nombre, rol)
VALUES ('admin', '$2b$10$6qUqDtKO7C76hzvItHLKHe9nBjLOKEQFU5QUEdHf0OE7mOGH0.J5m', 'Administrador', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- Parámetros del sistema
INSERT INTO parametro (codigo, nombre, valor, unidad, descripcion) VALUES
    ('APORTE_AFP',          'Aporte a las AFPs (10%)',            0.100000, 'PORCENTAJE', 'Descuento por aporte obligatorio a las AFPs'),
    ('RIESGO_COMUN',        'Aporte Riesgo Común + Comisión (2.21%)', 0.022100, 'PORCENTAJE', 'Aporte de riesgo común y comisión de la AFP'),
    ('APORTE_SOLIDARIO',    'Aporte Solidario del Asegurado (0.5%)', 0.005000, 'PORCENTAJE', 'Aporte solidario del asegurado'),
    ('APORTE_NACIONAL',     'Aporte Nacional del Asegurado (1.15%)', 0.011500, 'PORCENTAJE', 'Aporte nacional del asegurado'),
    ('HORAS_DIA',           'Horas de trabajo por día',           8.000000, 'HORAS',      'Jornada laboral diaria'),
    ('DIAS_MES',            'Días de haber básico por mes',       30.000000, 'DIAS',       'Días que se pagan en un mes'),
    ('DOMINICALES',         'Número de dominicales por mes',      4.000000,  'DIAS',       'Cantidad de domingos trabajados en el mes'),
    ('DIA_ADMIN_RCIVA',     'Día tope RC-IVA',                    0.000000,  'DIAS',       'Reservado para futuros cálculos de RC-IVA'),
    ('TOPE_APORTE_NACIONAL','Tope Aporte Nacional (Bs)',          13000.000000, 'MONTO',   'Si el total ganado es >= a este tope, el aporte nacional es (total ganado - tope) * tasa')
ON CONFLICT (codigo) DO NOTHING;

-- Tabla de bono de antigüedad (según hoja "FactorAntiguedad")
INSERT INTO bono_antiguedad (desde_anios, hasta_anios, desde_dias, hasta_dias, porcentaje, descripcion) VALUES
    (2,  4,  720,  1800, 0.05, 'De 2 a 4 años'),
    (5,  7,  1801, 2880, 0.11, 'De 5 años + 1 día a 7 años'),
    (8,  10, 2881, 3960, 0.18, 'De 8 a 10 años'),
    (11, 14, 3961, 5400, 0.26, 'De 11 a 14 años'),
    (15, 19, 5401, 7200, 0.34, 'De 15 a 19 años'),
    (20, 24, 7201, 9000, 0.42, 'De 20 a 24 años'),
    (25, NULL, 9001, NULL, 0.50, 'De 25 años + 1 día en adelante')
ON CONFLICT DO NOTHING;

-- Conceptos de haberes
INSERT INTO concepto (codigo, nombre, tipo, aplica_porcentaje, porcentaje, orden) VALUES
    ('HABER_BASICO',    'Haber Básico',                 'HABER',     FALSE, NULL, 10),
    ('BONO_ANTIGUEDAD', 'Bono de Antigüedad',           'HABER',     FALSE, NULL, 20),
    ('SALARIO_DOMINICAL','Salario Dominical',           'HABER',     FALSE, NULL, 30),
    ('HORAS_EXTRA',     'Horas Extra',                  'HABER',     FALSE, NULL, 40),
    ('AFP_10',          'Aporte a las AFPs 10%',        'APORTE',    TRUE,  0.1000, 10),
    ('AFP_2_21',        'Aporte AFP Riesgo Común 2.21%','APORTE',    TRUE,  0.0221, 20),
    ('APORTE_SOLIDARIO','Aporte Solidario del Asegurado 0.5%','APORTE', TRUE, 0.0050, 30),
    ('APORTE_NACIONAL', 'Aporte Nacional del Asegurado 1.15%','APORTE', TRUE, 0.0115, 40),
    ('RC_IVA',          'RC-IVA',                       'DESCUENTO', FALSE, NULL, 50),
    ('CUOTA_SINDICAL',  'Cuota Sindical',               'DESCUENTO', FALSE, NULL, 60),
    ('CUOTA_CONF_FABR', 'Cuota Confederación Fabril',   'DESCUENTO', FALSE, NULL, 70),
    ('PRO_DEPORTE',     'Pro-Deporte',                  'DESCUENTO', FALSE, NULL, 80),
    ('CELULAR',         'Descuento Celular',            'DESCUENTO', FALSE, NULL, 90),
    ('COMIDA',          'Refrigerio / Comida',          'DESCUENTO', FALSE, NULL, 100),
    ('COOP',            'Cooperativa SIDS',             'DESCUENTO', FALSE, NULL, 110),
    ('ASIS_COOP',       'Asignación Cooperativa',       'DESCUENTO', FALSE, NULL, 120),
    ('CERVEZA',         'Descuento Cerveza',            'DESCUENTO', FALSE, NULL, 130),
    ('QUESO',           'Descuento Queso',              'DESCUENTO', FALSE, NULL, 140),
    ('APOYO',           'Apoyo',                        'DESCUENTO', FALSE, NULL, 150),
    ('DCTO_VIRGEN',     'Descuento Virgen',             'DESCUENTO', FALSE, NULL, 160),
    ('VARIOS',          'Descuentos Varios',            'DESCUENTO', FALSE, NULL, 200)
ON CONFLICT (codigo) DO NOTHING;

-- ----------------------------------------------------------------------------
-- Personas y empleados de ejemplo (DESACTIVADO: los datos reales se cargan
-- desde los archivos Excel - ver cargar_datos.py y cargar_datos_complementarios.py)
-- ----------------------------------------------------------------------------
-- INSERT INTO persona (id, tipo_documento, nro_documento, apellido_paterno, apellido_materno, apellido_casada, nombres, sexo, fecha_nacimiento, pais_nacionalidad, direccion, telefono) VALUES
--     (1, 'CI', '4105285',  'Gutiérrez', 'Rojas',  NULL,     'José Luis',     'M', '1985-06-12', 'Bolivia', 'Av. Ostria Gutiérrez # 250', '64-445211'),
--     (2, 'CI', '5123456',  'Fernández', 'Quispe', 'García', 'María Elena',   'F', '1990-02-25', 'Bolivia', 'Calle La Plata # 118',       '64-423540'),
--     (3, 'CI', '3890123',  'Mamani',    'Choque', NULL,     'Carlos Alberto','M', '1982-11-03', 'Bolivia', 'Zona Central, Calle Arenales # 74', '64-412983'),
--     (4, 'CI', '4509876',  'Vargas',    'Soliz',  NULL,     'Ana María',     'F', '1995-08-19', 'Bolivia', 'Barrio Petrolero, Pasaje 2', '64-438771'),
--     (5, 'CI', '4781234',  'Cáceres',   'Mendieta', NULL,   'Pedro Raúl',    'M', '1978-01-30', 'Bolivia', 'Av. Las Américas # 612',     '64-451200'),
--     (6, 'CI', '5312468',  'Condori',   'Huanca', 'Pérez', 'Rosario',       'F', '1998-12-07', 'Bolivia', 'Calle Colón # 330',          '64-465517')
-- ON CONFLICT (id) DO NOTHING;

-- INSERT INTO empleado (id, persona_id, afp, nua_cua, fecha_ingreso, fecha_seguro, origen, cargo, clasificacion_laboral, jubilado, jornal_hora) VALUES
--     (1, 1, 'Gestora',           '1584701234', '2018-03-15', '2018-03-20', 'CH', 'Operador de Planta',          'Operario',       FALSE, 12.5000),
--     (2, 2, 'Futuro de Bolivia', '1594236781', '2019-07-01', '2019-07-05', 'LP', 'Control de Calidad',          'Técnico',        FALSE, 18.7500),
--     (3, 3, 'Previsión',         '1509876543', '2016-11-20', '2016-11-25', 'CB', 'Mantenimiento',               'Técnico',        FALSE, 15.0000),
--     (4, 4, 'Gestora',           '1540987612', '2021-02-10', '2021-02-12', 'CH', 'Asistente Administrativa',   'Administrativo', FALSE, 20.0000),
--     (5, 5, 'Futuro de Bolivia', '1512345698', '2014-05-05', '2014-05-08', 'PT', 'Supervisor de Producción',   'Supervisor',     FALSE, 25.0000),
--     (6, 6, 'Gestora',           '1576543210', '2022-09-12', '2022-09-14', 'CH', 'Operadora de Envasado',      'Operaria',       FALSE, 11.0000)
-- ON CONFLICT (id) DO NOTHING;

-- Ajustar secuencias para que las inserciones posteriores no colisionen con los ids de ejemplo
-- SELECT setval('persona_id_seq',  (SELECT COALESCE(MAX(id), 0) FROM persona) + 1,  false);
-- SELECT setval('empleado_id_seq', (SELECT COALESCE(MAX(id), 0) FROM empleado) + 1, false);

-- ============================================================================
-- MODIFICACIONES DE LA BASE DE DATOS
-- Se registran SIEMPRE al final del archivo, sin alterar la definición
-- original de las tablas.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Modificación 1: descuentos fijos y variables
--   - concepto.tipo_descuento: FIJO (monto igual para todos en "monto")
--                              | VARIABLE (monto propio por empleado)
--   - nueva tabla empleado_descuento: montos variables por empleado
-- ----------------------------------------------------------------------------
ALTER TABLE concepto ADD COLUMN IF NOT EXISTS tipo_descuento VARCHAR(20);
ALTER TABLE concepto ADD COLUMN IF NOT EXISTS monto NUMERIC(14,2);

CREATE TABLE IF NOT EXISTS empleado_descuento (
    id           BIGSERIAL PRIMARY KEY,
    empleado_id  BIGINT NOT NULL REFERENCES empleado(id),
    concepto_id  BIGINT NOT NULL REFERENCES concepto(id),
    monto        NUMERIC(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_empleado_descuento UNIQUE (empleado_id, concepto_id)
);

-- Clasificación de descuentos: FIJO (monto igual para todos) o VARIABLE (por empleado)
UPDATE concepto SET tipo_descuento = 'FIJO',     monto = 20.00 WHERE codigo = 'CUOTA_SINDICAL';
UPDATE concepto SET tipo_descuento = 'FIJO',     monto = 5.00  WHERE codigo = 'CUOTA_CONF_FABR';
UPDATE concepto SET tipo_descuento = 'FIJO',     monto = 5.00  WHERE codigo = 'PRO_DEPORTE';
UPDATE concepto SET tipo_descuento = 'VARIABLE' WHERE codigo IN
    ('RC_IVA', 'CELULAR', 'COMIDA', 'COOP', 'ASIS_COOP', 'CERVEZA', 'QUESO', 'APOYO', 'DCTO_VIRGEN', 'VARIOS');

-- Descuentos variables de ejemplo por empleado (DESACTIVADO: ver comentario
-- sobre personas y empleados de ejemplo)
-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 1, e.id, c.id, 15.00 FROM empleado e, concepto c
-- WHERE e.id = 1 AND c.codigo = 'CELULAR'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 2, e.id, c.id, 50.00 FROM empleado e, concepto c
-- WHERE e.id = 1 AND c.codigo = 'COOP'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 3, e.id, c.id, 20.00 FROM empleado e, concepto c
-- WHERE e.id = 2 AND c.codigo = 'CELULAR'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 4, e.id, c.id, 10.00 FROM empleado e, concepto c
-- WHERE e.id = 2 AND c.codigo = 'QUESO'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 5, e.id, c.id, 35.00 FROM empleado e, concepto c
-- WHERE e.id = 3 AND c.codigo = 'COOP'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 6, e.id, c.id, 12.00 FROM empleado e, concepto c
-- WHERE e.id = 4 AND c.codigo = 'CELULAR'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 7, e.id, c.id, 18.00 FROM empleado e, concepto c
-- WHERE e.id = 4 AND c.codigo = 'COMIDA'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 8, e.id, c.id, 45.00 FROM empleado e, concepto c
-- WHERE e.id = 5 AND c.codigo = 'COOP'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- INSERT INTO empleado_descuento (id, empleado_id, concepto_id, monto)
-- SELECT 9, e.id, c.id, 8.00 FROM empleado e, concepto c
-- WHERE e.id = 6 AND c.codigo = 'CERVEZA'
-- ON CONFLICT (empleado_id, concepto_id) DO NOTHING;

-- SELECT setval('empleado_descuento_id_seq', (SELECT COALESCE(MAX(id), 0) FROM empleado_descuento) + 1, false);

-- ----------------------------------------------------------------------------
-- Modificación 2: cuenta bancaria del empleado
--   Se agrega la columna "cuenta_bancaria" con el Nro de cuenta (Banco Económico)
--   de cada beneficiario para el pago de planillas por transferencia.
-- ----------------------------------------------------------------------------
ALTER TABLE empleado ADD COLUMN IF NOT EXISTS cuenta_bancaria VARCHAR(50);

-- ----------------------------------------------------------------------------
-- Modificación 3: optimizar bono_antiguedad
--   El INSERT inicial no tenía constraint de unicidad, por lo que en cada
--   arranque se acumulaban registros duplicados. Se eliminan los duplicados y
--   se agrega un índice único para evitar nuevos (equivalente a UNIQUE).
-- ----------------------------------------------------------------------------
DELETE FROM bono_antiguedad a
USING bono_antiguedad b
WHERE a.desde_dias IS NOT DISTINCT FROM b.desde_dias
  AND a.id > b.id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bono_antiguedad ON bono_antiguedad (desde_dias);

-- ----------------------------------------------------------------------------
-- Modificación 4: horas trabajadas por empleado
--   Cada empleado tiene su propia carga de horas mensual (no todos trabajan
--   las 208 horas por defecto). PlanillaService usa este valor por empleado
--   y solo recurre a (DIAS_MES - DOMINICALES) * HORAS_DIA cuando es NULO/<=0.
-- ----------------------------------------------------------------------------
ALTER TABLE empleado ADD COLUMN IF NOT EXISTS horas_trabajadas NUMERIC(8,2) NOT NULL DEFAULT 208.00;

-- ----------------------------------------------------------------------------
-- Modificación 5: haber_basico por empleado y tabla de configuración
--   - empleado.haber_basico: importe del haber básico tomado de la última
--     planilla generada. Al generar, PlanillaService usa este valor (si está
--     informado) en lugar de jornal x horas, y vuelve a guardarlo.
--   - configuracion: fila única con minimo_nacional y cantidad_minimo_nacional
--     usados para el bono de antigüedad:
--     bono_antig_monto = minimo_nacional x cantidad_minimo_nacional x bono_antig_pct
-- ----------------------------------------------------------------------------
ALTER TABLE empleado ADD COLUMN IF NOT EXISTS haber_basico NUMERIC(14,2);

CREATE TABLE IF NOT EXISTS configuracion (
    id                         BIGSERIAL PRIMARY KEY,
    minimo_nacional            NUMERIC(14,2) NOT NULL DEFAULT 0,
    cantidad_minimo_nacional   NUMERIC(14,2) NOT NULL DEFAULT 1,
    created_at                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO configuracion (minimo_nacional, cantidad_minimo_nacional)
SELECT 0.00, 1.00
WHERE NOT EXISTS (SELECT 1 FROM configuracion);

-- ----------------------------------------------------------------------------
-- Modificación 6: planilla_detalle pasa a ser una VISTA calculada a partir de
-- planilla_detalle_concepto. Cada movimiento (concepto) repite las columnas
-- base del detalle (planilla_id, empleado_id, item, horas, jornal, días y
-- porcentaje de antigüedad); la vista agrupa por planilla_detalle_id y
-- resume los montos por tipo de concepto.
-- ----------------------------------------------------------------------------
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS planilla_id BIGINT;
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS empleado_id BIGINT;
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS item INTEGER;
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS horas_trabajadas NUMERIC(8,2);
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS jornal_hora NUMERIC(12,4);
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS dias_antiguedad INTEGER;
ALTER TABLE planilla_detalle_concepto ADD COLUMN IF NOT EXISTS bono_antig_pct NUMERIC(8,4);

CREATE INDEX IF NOT EXISTS idx_detalle_concepto_plan ON planilla_detalle_concepto (planilla_id);

CREATE SEQUENCE IF NOT EXISTS planilla_detalle_id_seq;

SELECT setval('planilla_detalle_id_seq',
              COALESCE((SELECT MAX(planilla_detalle_id) FROM planilla_detalle_concepto), 0) + 1,
              false);

DROP VIEW IF EXISTS planilla_detalle;

CREATE OR REPLACE VIEW v_planilla_detalle AS
SELECT
    pdc.planilla_detalle_id                       AS id,
    MAX(pdc.planilla_id)                          AS planilla_id,
    MAX(pdc.empleado_id)                          AS empleado_id,
    MAX(pdc.item)                                 AS item,
    MAX(pdc.horas_trabajadas)                     AS horas_trabajadas,
    MAX(pdc.jornal_hora)                          AS jornal_hora,
    MAX(pdc.dias_antiguedad)                      AS dias_antiguedad,
    MAX(pdc.bono_antig_pct)                       AS bono_antig_pct,
    COALESCE(SUM(CASE WHEN c.codigo = 'HABER_BASICO'     THEN pdc.monto END), 0) AS haber_basico,
    COALESCE(SUM(CASE WHEN c.codigo = 'SALARIO_DOMINICAL' THEN pdc.monto END), 0) AS salario_dominical,
    COALESCE(SUM(CASE WHEN c.codigo = 'BONO_ANTIGUEDAD'  THEN pdc.monto END), 0) AS bono_antig_monto,
    COALESCE(SUM(CASE WHEN c.codigo = 'APORTE_SOLIDARIO' THEN pdc.monto END), 0) AS aporte_solidario,
    COALESCE(SUM(CASE WHEN c.codigo = 'APORTE_NACIONAL'  THEN pdc.monto END), 0) AS aporte_nacional,
    COALESCE(SUM(CASE WHEN c.codigo = 'AFP_10'           THEN pdc.monto END), 0) AS aporte_afp,
    COALESCE(SUM(CASE WHEN c.codigo = 'AFP_2_21'         THEN pdc.monto END), 0) AS aporte_riesgo_comun,
    COALESCE(SUM(CASE WHEN COALESCE(pdc.tipo, c.tipo) = 'HABER'     THEN pdc.monto END), 0) AS total_ganado,
    COALESCE(SUM(CASE WHEN COALESCE(pdc.tipo, c.tipo) = 'APORTE'    THEN pdc.monto END), 0) AS total_aportes,
    COALESCE(SUM(CASE WHEN COALESCE(pdc.tipo, c.tipo) = 'DESCUENTO' THEN pdc.monto END), 0) AS descuentos_varios,
    COALESCE(SUM(CASE WHEN COALESCE(pdc.tipo, c.tipo) = 'APORTE'    THEN pdc.monto END), 0)
        + COALESCE(SUM(CASE WHEN COALESCE(pdc.tipo, c.tipo) = 'DESCUENTO' THEN pdc.monto END), 0) AS total_descuentos,
    COALESCE(SUM(CASE WHEN COALESCE(pdc.tipo, c.tipo) = 'HABER' THEN pdc.monto END), 0)
        - COALESCE(SUM(CASE WHEN COALESCE(pdc.tipo, c.tipo) = 'APORTE' THEN pdc.monto END), 0)
        - COALESCE(SUM(CASE WHEN COALESCE(pdc.tipo, c.tipo) = 'DESCUENTO' THEN pdc.monto END), 0) AS liquido_pagable
FROM planilla_detalle_concepto pdc
JOIN concepto c ON c.id = pdc.concepto_id
GROUP BY pdc.planilla_detalle_id;

-- Backfill: copia el haber_basico de la planilla más reciente generada
UPDATE empleado e SET haber_basico = sub.haber_basico
FROM (
    SELECT DISTINCT ON (pd.empleado_id) pd.empleado_id, pd.haber_basico
    FROM v_planilla_detalle pd
    WHERE pd.haber_basico IS NOT NULL
    ORDER BY pd.empleado_id, pd.planilla_id DESC
) sub
WHERE sub.empleado_id = e.id AND e.haber_basico IS NULL;

-- ----------------------------------------------------------------------------
-- Modificación 7: v_planilla, vista con los totales calculados
--   Los campos total_haberes, total_descuentos y total_liquido se eliminan de
--   la tabla planilla y se calculan en la vista v_planilla a partir de
--   v_planilla_detalle.
-- ----------------------------------------------------------------------------
ALTER TABLE planilla DROP COLUMN IF EXISTS total_haberes;
ALTER TABLE planilla DROP COLUMN IF EXISTS total_descuentos;
ALTER TABLE planilla DROP COLUMN IF EXISTS total_liquido;

CREATE OR REPLACE VIEW v_planilla AS
SELECT
    p.id,
    p.periodo_anio,
    p.periodo_mes,
    p.nombre,
    p.empresa_id,
    p.estado,
    p.fecha_liquidacion,
    COALESCE((SELECT SUM(d.total_ganado)     FROM v_planilla_detalle d WHERE d.planilla_id = p.id), 0) AS total_haberes,
    COALESCE((SELECT SUM(d.total_descuentos) FROM v_planilla_detalle d WHERE d.planilla_id = p.id), 0) AS total_descuentos,
    COALESCE((SELECT SUM(d.total_ganado)     FROM v_planilla_detalle d WHERE d.planilla_id = p.id), 0)
        - COALESCE((SELECT SUM(d.total_descuentos) FROM v_planilla_detalle d WHERE d.planilla_id = p.id), 0) AS total_liquido,
    p.created_at,
    p.updated_at
FROM planilla p;

-- ----------------------------------------------------------------------------
-- Modificación 8: edad de riesgo común en configuración
-- ----------------------------------------------------------------------------
ALTER TABLE configuracion ADD COLUMN IF NOT EXISTS edad_riesgo_comun INTEGER;
ALTER TABLE configuracion ADD COLUMN IF NOT EXISTS edad_riesgo_comun_pct NUMERIC(8,4);

-- ----------------------------------------------------------------------------
-- Modificación 9: v_persona_empleado
-- Vista con los datos básicos de la persona junto con los datos laborales del
-- empleado y su porcentaje de antigüedad vigente.
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_persona_empleado AS
SELECT
    pe.id AS persona_id,
    TRIM(CONCAT_WS(' ', pe.apellido_paterno, pe.apellido_materno, pe.nombres)) AS persona_nombre_completo,
    e.id AS empleado_id,
    e.cargo,
    e.jornal_hora,
    e.fecha_ingreso,
    e.fecha_seguro,
    (SELECT b.porcentaje
       FROM bono_antiguedad b
      WHERE b.activo
        AND e.fecha_ingreso IS NOT NULL
        AND b.desde_dias <= (CURRENT_DATE - e.fecha_ingreso)
      ORDER BY b.desde_dias DESC
      LIMIT 1) AS porcentaje_antiguedad
FROM empleado e
JOIN persona pe ON pe.id = e.persona_id;

-- ----------------------------------------------------------------------------
-- Modificación 10: empleado.sindicato
-- ----------------------------------------------------------------------------
ALTER TABLE empleado ADD COLUMN IF NOT EXISTS sindicato BOOLEAN NOT NULL DEFAULT FALSE;
