CREATE TABLE apartamentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero VARCHAR(255),
    torre VARCHAR(255),
    area DOUBLE PRECISION,
    propietario_id VARCHAR(255),
    torre_id BIGINT
);

CREATE TABLE residentes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL
);

CREATE TABLE ocupaciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    apartamento_id BIGINT NOT NULL,
    residente_id BIGINT NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE,
    activa TINYINT GENERATED ALWAYS AS (CASE WHEN fecha_fin IS NULL THEN 1 ELSE NULL END),
    CONSTRAINT chk_ocupaciones_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    CONSTRAINT uq_ocupaciones_activa UNIQUE (apartamento_id, residente_id, activa),
    CONSTRAINT fk_ocupaciones_apartamento FOREIGN KEY (apartamento_id) REFERENCES apartamentos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ocupaciones_residente FOREIGN KEY (residente_id) REFERENCES residentes (id) ON DELETE RESTRICT
);
