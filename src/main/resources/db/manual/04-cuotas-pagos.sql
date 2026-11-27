-- Manual MySQL 8.0.16+ migration. Verify the selected schema and apartamentos.id first (see docs/database/cuotas-pagos.md).
CREATE TABLE cuotas (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    apartamento_id BIGINT NOT NULL,
    importe DECIMAL(12,2) NOT NULL,
    moneda CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT chk_cuotas_importe CHECK (importe > 0),
    INDEX idx_cuotas_apartamento (apartamento_id),
    CONSTRAINT fk_cuotas_apartamento FOREIGN KEY (apartamento_id)
        REFERENCES apartamentos (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE pagos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cuota_id BIGINT NOT NULL,
    importe DECIMAL(12,2) NOT NULL,
    referencia VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT chk_pagos_importe CHECK (importe > 0),
    CONSTRAINT uq_pagos_cuota_referencia UNIQUE (cuota_id, referencia),
    CONSTRAINT fk_pagos_cuota FOREIGN KEY (cuota_id)
        REFERENCES cuotas (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4;
