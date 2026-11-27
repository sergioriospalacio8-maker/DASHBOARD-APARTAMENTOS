-- Manual MySQL 8.0.16+ migration. Verify the selected schema and apartamentos.id first (see docs/database/residentes-ocupaciones.md).
CREATE TABLE residentes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE ocupaciones (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    apartamento_id BIGINT NOT NULL,
    residente_id BIGINT NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NULL,
    activa TINYINT GENERATED ALWAYS AS (CASE WHEN fecha_fin IS NULL THEN 1 ELSE NULL END) STORED,
    CONSTRAINT chk_ocupaciones_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    CONSTRAINT uq_ocupaciones_activa UNIQUE (apartamento_id, residente_id, activa),
    INDEX idx_ocupaciones_residente (residente_id),
    CONSTRAINT fk_ocupaciones_apartamento FOREIGN KEY (apartamento_id)
        REFERENCES apartamentos (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_ocupaciones_residente FOREIGN KEY (residente_id)
        REFERENCES residentes (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
