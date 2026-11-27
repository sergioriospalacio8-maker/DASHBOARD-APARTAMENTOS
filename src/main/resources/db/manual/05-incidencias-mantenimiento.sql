-- MySQL 8.0.16+: verificar esquema y apartamentos.id; ver docs/database/incidencias-mantenimiento.md.
CREATE TABLE incidencias_mantenimiento (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    apartamento_id BIGINT NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    descripcion VARCHAR(2000) NOT NULL,
    estado VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    creada_en DATETIME(6) NOT NULL,
    actualizada_en DATETIME(6) NOT NULL,
    resuelta_en DATETIME(6),
    CONSTRAINT chk_incidencias_estado CHECK (estado IN ('ABIERTA', 'EN_PROGRESO', 'RESUELTA')),
    INDEX idx_incidencias_apartamento_estado (apartamento_id, estado),
    INDEX idx_incidencias_estado (estado),
    CONSTRAINT fk_incidencias_apartamento FOREIGN KEY (apartamento_id)
        REFERENCES apartamentos (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
