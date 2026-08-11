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
-- EMPLEADO: información constante de cada dependiente
-- (origen: columnas B a AW de la hoja auxiliar + campos de "Formato propuesto")
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS empleado (
    id                    BIGSERIAL PRIMARY KEY,
    tipo_documento        VARCHAR(30)  NOT NULL DEFAULT 'CI',
    nro_documento         VARCHAR(50)  NOT NULL,
    apellido_paterno      VARCHAR(100),
    apellido_materno      VARCHAR(100),
    apellido_casada       VARCHAR(100),
    nombre1               VARCHAR(100),
    otros_nombres         VARCHAR(100),
    sexo                  VARCHAR(1),
    fecha_nacimiento      DATE,
    pais_nacionalidad     VARCHAR(60)  NOT NULL DEFAULT 'Bolivia',
    afp                   VARCHAR(60),
    nua_cua               VARCHAR(50),
    fecha_ingreso         DATE,
    fecha_seguro          DATE,
    origen                VARCHAR(10),
    cargo                 VARCHAR(150),
    clasificacion_laboral VARCHAR(150),
    jubilado              BOOLEAN NOT NULL DEFAULT FALSE,
    direccion             VARCHAR(255),
    telefono              VARCHAR(50),
    jornal_hora           NUMERIC(12,4) NOT NULL DEFAULT 0,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_empleado_documento UNIQUE (tipo_documento, nro_documento)
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
    )
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
    total_haberes    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_descuentos NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_liquido    NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_planilla_periodo UNIQUE (periodo_anio, periodo_mes)
);

-- ----------------------------------------------------------------------------
-- PLANILLA_DETALLE: línea de planilla por empleado (hoja auxiliar)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS planilla_detalle (
    id                  BIGSERIAL PRIMARY KEY,
    planilla_id         BIGINT NOT NULL REFERENCES planilla(id),
    empleado_id         BIGINT NOT NULL REFERENCES empleado(id),
    item                INTEGER,
    horas_trabajadas    NUMERIC(8,2)  NOT NULL DEFAULT 0,
    jornal_hora         NUMERIC(12,4) NOT NULL DEFAULT 0,
    haber_basico        NUMERIC(14,2) NOT NULL DEFAULT 0,
    dias_antiguedad     INTEGER       NOT NULL DEFAULT 0,
    bono_antig_pct      NUMERIC(8,4)  NOT NULL DEFAULT 0,
    salario_dominical   NUMERIC(14,2) NOT NULL DEFAULT 0,
    bono_antig_monto    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_ganado        NUMERIC(14,2) NOT NULL DEFAULT 0,
    aporte_solidario    NUMERIC(14,2) NOT NULL DEFAULT 0,
    aporte_nacional     NUMERIC(14,2) NOT NULL DEFAULT 0,
    aporte_afp          NUMERIC(14,2) NOT NULL DEFAULT 0,
    aporte_riesgo_comun NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_aportes       NUMERIC(14,2) NOT NULL DEFAULT 0,
    descuentos_varios   NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_descuentos    NUMERIC(14,2) NOT NULL DEFAULT 0,
    liquido_pagable     NUMERIC(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_detalle_empleado UNIQUE (planilla_id, empleado_id)
);

-- ----------------------------------------------------------------------------
-- PLANILLA_DETALLE_CONCEPTO: desglose por concepto de haberes/descuentos
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS planilla_detalle_concepto (
    id                  BIGSERIAL PRIMARY KEY,
    planilla_detalle_id BIGINT NOT NULL REFERENCES planilla_detalle(id),
    concepto_id         BIGINT NOT NULL REFERENCES concepto(id),
    tipo                VARCHAR(20),
    monto               NUMERIC(14,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_detalle_concepto UNIQUE (planilla_detalle_id, concepto_id)
);

-- ----------------------------------------------------------------------------
-- ÍNDICES
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_empleado_activo        ON empleado (activo);
CREATE INDEX IF NOT EXISTS idx_detalle_planilla       ON planilla_detalle (planilla_id);
CREATE INDEX IF NOT EXISTS idx_detalle_empleado       ON planilla_detalle (empleado_id);
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
    ('DIA_ADMIN_RCIVA',     'Día tope RC-IVA',                    0.000000,  'DIAS',       'Reservado para futuros cálculos de RC-IVA')
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
