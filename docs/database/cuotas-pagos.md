# Cuotas y pagos manuales

## Instalación manual (MySQL)

Revisa `SHOW CREATE TABLE apartamentos` en el esquema de destino. [`04-cuotas-pagos.sql`](../../src/main/resources/db/manual/04-cuotas-pagos.sql) requiere MySQL 8.0.16 o posterior, modo SQL estricto, InnoDB y una PK `apartamentos.id` de tipo `BIGINT` **con signo**. Si el esquema no coincide, no ejecutes el script tal cual. No necesita tablas de condominios, torres ni ocupaciones.

Verifica que `cuotas` y `pagos` no existen, respalda el esquema y prueba primero en un esquema desechable. La migración es aditiva, no es repetible y MySQL puede confirmar cada DDL por separado. Si se aplica parcialmente, revisa qué se creó antes de reintentar. No delegues la creación a Hibernate: el script define los `CHECK`, las FK restrictivas y la referencia única por cuota. No se insertan datos de muestra.

## API

Todas las rutas requieren un principal autenticado de confianza en la solicitud servlet y `isUserInRole("ADMIN")`. Sin cualquiera de los dos, devuelven `403` sin cuerpo antes de procesar los datos. `X-User-Role` y otros encabezados de cliente no conceden acceso. Este módulo no instala un proveedor de autenticación: el despliegue debe configurar uno del lado del servidor o las rutas quedarán cerradas.

| Método y ruta | Datos | Éxito |
| --- | --- | --- |
| `POST /api/cuotas` | `{"apartamentoId":12,"importe":100.00,"moneda":"COP"}` | `201` con la cuota |
| `GET /api/cuotas` | Filtro opcional `?apartamentoId=12` | `200` con las cuotas por `id` ascendente; `[]` si no hay cuotas |
| `GET /api/cuotas/{id}` | — | `200` con la cuota |
| `POST /api/cuotas/{id}/pagos` | `{"importe":25.50,"referencia":"REC-12"}` | `201` con el pago o `200` con el mismo pago si es un reintento idéntico |
| `GET /api/cuotas/{id}/pagos` | — | `200` con los pagos por `id` ascendente; `[]` si no hay pagos |

Una cuota contiene `id`, `apartamentoId`, `importe`, `moneda` y `saldo` (importe menos la suma de pagos). Un pago contiene `id`, `cuotaId`, `importe` y `referencia`; su moneda es la de la cuota, no un dato independiente. `moneda` es un código fijo de tres letras ASCII mayúsculas. No se procesa dinero externamente: no hay pasarela ni conversiones de moneda.

- `400` sin cuerpo: JSON no legible, campos requeridos ausentes, ID que no es un entero positivo de 64 bits, moneda inválida, importe fuera de `0.01`–`9999999999.99` o con más de dos decimales (no se redondea), o referencia que no sea una cadena de 1–64 caracteres `[A-Za-z0-9._-]`.
- `404` sin cuerpo: no existe el apartamento indicado al crear o filtrar, o no existe la cuota indicada al consultar o registrar/listar pagos.
- `409` sin cuerpo: el importe supera el saldo, o ya existe la misma referencia en esa cuota con otro importe. Las referencias distinguen mayúsculas y minúsculas. Un reintento con la misma referencia e importe devuelve el pago original incluso si la cuota ya quedó saldada. Reutilizar la referencia en otra cuota es válido.

Todas las altas de pagos bloquean la fila padre de la cuota antes de buscar la referencia y calcular el saldo, en una transacción `READ COMMITTED`. Un rechazo no modifica el saldo ni inserta un pago. Las escrituras directas en la base de datos no deben usarse para registrar pagos: el límite sobre la suma se aplica en la transacción de la API.

No hay operaciones para modificar o borrar cuotas y pagos. Si la FK `fk_cuotas_apartamento` impide el borrado, `DELETE /api/apartamentos/{id}` devuelve `409` sin cuerpo y conserva el apartamento, las cuotas y los pagos, incluso si todas las cuotas están saldadas. La traducción a `409` se limita a las FK de cuotas y ocupaciones identificadas por nombre y código de fila padre referenciada (MySQL `1451`, H2 `23503`); los demás errores SQL no se traducen.
