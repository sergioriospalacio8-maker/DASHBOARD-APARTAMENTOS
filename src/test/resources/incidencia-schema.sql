CREATE TABLE apartamentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero VARCHAR(255),
    torre VARCHAR(255),
    area DOUBLE PRECISION,
    propietario_id VARCHAR(255),
    torre_id BIGINT
);

CREATE TABLE incidencias_mantenimiento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    apartamento_id BIGINT NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    descripcion VARCHAR(2000) NOT NULL,
    estado VARCHAR(16) NOT NULL CHECK (estado IN ('ABIERTA', 'EN_PROGRESO', 'RESUELTA')),
    creada_en TIMESTAMP(6) NOT NULL,
    actualizada_en TIMESTAMP(6) NOT NULL,
    resuelta_en TIMESTAMP(6),
    CONSTRAINT fk_incidencias_apartamento FOREIGN KEY (apartamento_id)
        REFERENCES apartamentos (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
