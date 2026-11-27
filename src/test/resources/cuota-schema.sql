CREATE TABLE apartamentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    numero VARCHAR(255),
    torre VARCHAR(255),
    area DOUBLE PRECISION,
    propietario_id VARCHAR(255),
    torre_id BIGINT
);

CREATE TABLE cuotas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    apartamento_id BIGINT NOT NULL,
    importe DECIMAL(12,2) NOT NULL CHECK (importe > 0),
    moneda CHAR(3) NOT NULL,
    CONSTRAINT fk_cuotas_apartamento FOREIGN KEY (apartamento_id)
        REFERENCES apartamentos (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE pagos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cuota_id BIGINT NOT NULL,
    importe DECIMAL(12,2) NOT NULL CHECK (importe > 0),
    referencia VARCHAR(64) NOT NULL,
    CONSTRAINT uq_pagos_cuota_referencia UNIQUE (cuota_id, referencia),
    CONSTRAINT fk_pagos_cuota FOREIGN KEY (cuota_id)
        REFERENCES cuotas (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
