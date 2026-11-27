CREATE TABLE IF NOT EXISTS usuarios (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    nombre CLOB,
    email CLOB,
    edad INT NOT NULL
);
