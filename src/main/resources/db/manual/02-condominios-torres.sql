-- Manual MySQL migration. Select and verify the target schema before applying.
CREATE TABLE condominios (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE torres (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    condominio_id BIGINT NOT NULL,
    INDEX idx_torres_condominio (condominio_id),
    CONSTRAINT fk_torres_condominio FOREIGN KEY (condominio_id)
        REFERENCES condominios (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

ALTER TABLE apartamentos
    ADD COLUMN torre_id BIGINT NULL,
    ADD INDEX idx_apartamentos_torre (torre_id),
    ADD CONSTRAINT fk_apartamentos_torre FOREIGN KEY (torre_id)
        REFERENCES torres (id) ON DELETE SET NULL;
