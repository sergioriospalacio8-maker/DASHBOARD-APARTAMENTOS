# Incidencias de mantenimiento

## Instalación manual (MySQL)

[`05-incidencias-mantenimiento.sql`](../../src/main/resources/db/manual/05-incidencias-mantenimiento.sql) requiere MySQL 8.0.16 o posterior en modo SQL estricto. Ejecuta `SHOW CREATE TABLE apartamentos` en el esquema de destino y confirma que usa InnoDB y que `apartamentos.id` es una PK `BIGINT` **con signo**. No necesita las tablas de los otros módulos ni modifica datos existentes. Verifica que `incidencias_mantenimiento` no exista, respalda el esquema y prueba primero en un esquema desechable. El script es aditivo, se aplica una sola vez y no carga datos de muestra. Usa UTC para interpretar las fechas `DATETIME(6)` almacenadas por la aplicación.

## API

Todas las rutas exigen un principal autenticado de confianza en la solicitud servlet y el rol `ADMIN` asignado en el servidor (`isUserInRole("ADMIN")`). Si falta cualquiera, responden `403` sin cuerpo, antes de procesar los datos. Las cabeceras del cliente, incluido `X-User-Role`, no conceden acceso. El módulo no instala un proveedor de autenticación; sin uno del lado del servidor las rutas quedan cerradas.

| Método y ruta | Datos | Éxito |
| --- | --- | --- |
| `POST /api/incidencias` | `{"apartamentoId":12,"titulo":"Fuga","descripcion":"Goteo en la cocina"}` | `201` con la incidencia en estado `ABIERTA` |
| `GET /api/incidencias` | Filtros opcionales combinables, p. ej. `?apartamentoId=12&estado=ABIERTA` | `200` con incidencias ordenadas por `id` ascendente; `[]` si no hay coincidencias |
| `GET /api/incidencias/{id}` | — | `200` con la incidencia |
| `PATCH /api/incidencias/{id}/estado` | `{"estado":"EN_PROGRESO"}` o `{"estado":"RESUELTA"}` según el estado actual | `200` con la incidencia actualizada |

Cada incidencia incluye `id`, `apartamentoId`, `titulo`, `descripcion`, `estado`, `creadaEn`, `actualizadaEn` y `resueltaEn`. Las fechas son instantes UTC en formato ISO 8601, con precisión de microsegundos. Al crear, `creadaEn` y `actualizadaEn` coinciden. Una transición válida avanza `actualizadaEn` y al resolver asigna ese mismo instante a `resueltaEn`, que antes es `null`. Los demás campos no cambian.

- `400` sin cuerpo: JSON no legible, campos requeridos ausentes o campos adicionales en el cuerpo, ID que no es un entero positivo con signo de 64 bits, textos de tipo incorrecto o vacíos tras quitar el espacio en los extremos, título de más de 120 unidades UTF-16, descripción de más de 2000, o estado desconocido. Los estados distinguen mayúsculas y minúsculas; `apartamentoId` en el JSON debe ser un número entero, no una cadena.
- `404` sin cuerpo: el apartamento indicado al crear o filtrar no existe, o no existe la incidencia indicada al consultar o actualizar.
- `409` sin cuerpo: se intentó una transición no permitida. Sólo se permite `ABIERTA` → `EN_PROGRESO` → `RESUELTA`; no se puede saltar un estado, retroceder ni volver a solicitar el mismo estado. La transición se valida bajo bloqueo de la fila y una solicitud rechazada no modifica el registro ni sus fechas.

No hay borrado de incidencias ni edición de los datos del reporte. Si la FK `fk_incidencias_apartamento` impide el borrado, `DELETE /api/apartamentos/{id}` devuelve `409` sin cuerpo y conserva el apartamento y sus incidencias, incluso las resueltas. Se conservan las respuestas de conflicto por historial de ocupación o cuotas; otros errores de base de datos no se traducen a `409`.
