# Residentes y ocupaciones

La ficha del residente es un registro del directorio (`id`, `nombre`), no una cuenta de `Usuario`. Crear o editar una ficha no crea usuarios ni concede acceso. La ocupación sólo referencia el `id` existente de `Apartamento`: no modifica el propietario del apartamento ni requiere condominios, torres o la migración de T2.

## Despliegue manual (MySQL)

Antes de aplicar [`03-residentes-ocupaciones.sql`](../../src/main/resources/db/manual/03-residentes-ocupaciones.sql), verifica en el esquema de destino:

- MySQL 8.0.16 o posterior (con restricciones `CHECK` efectivas), InnoDB y modo SQL estricto sin fechas cero o inválidas. Revisa `SHOW CREATE TABLE apartamentos` antes de planificar el cambio: debe existir `apartamentos` en InnoDB y `id` debe ser su PK de tipo `BIGINT` **con signo**, acorde con el `Long` mapeado por JPA. Si el esquema real no coincide, no ejecutes el script tal cual.
- `residentes` y `ocupaciones` aún no deben existir. Usa primero un esquema desechable y un respaldo del esquema de destino. El script es aditivo y no repetible; MySQL puede confirmar cada DDL por separado. Si hay una aplicación parcial, revisa qué se creó antes de reintentar; no borres datos para volver a ejecutar el script.
- No delegues la creación de estas tablas a Hibernate: el esquema manual añade las restricciones e índices que no genera el mapeo. Las dos FK son restrictivas, incluso con ocupaciones cerradas. Conserva el nombre `fk_ocupaciones_apartamento`: el borrado de apartamentos reconoce esta restricción por nombre y por el código de fila padre referenciada (MySQL `1451`, H2 `23503`).

La columna generada `activa` vale `1` si `fecha_fin` es `NULL`, o `NULL` en caso contrario. El índice único impide dos filas activas del mismo par y permite varias filas cerradas; no impide que distintas personas ocupen el mismo apartamento. El alta serializa por residente con un bloqueo de escritura y comprueba el par en `READ COMMITTED`; también bloquea para lectura sólo el identificador del apartamento durante el alta. El cierre bloquea la fila de la ocupación para que no se sobrescriba un cierre anterior. El `CHECK` impide un fin anterior al inicio.

## API

Todas las rutas de esta sección requieren un principal autenticado en la solicitud servlet **y** que `isUserInRole("ADMIN")` sea verdadero. Sin cualquiera de los dos, devuelven `403` sin cuerpo antes de procesar los datos. `X-User-Role` y otros encabezados de identidad de cliente no conceden acceso. Este módulo no añade un proveedor de autenticación: el despliegue debe configurar uno de confianza en el servidor que exponga el principal y el rol. Sin él las rutas quedan cerradas. Esto no modifica la autorización de las rutas anteriores.

| Método y ruta | Datos | Respuesta de éxito |
| --- | --- | --- |
| `POST /api/residentes` | `{"nombre":"Ana López"}` | `201` con la ficha creada |
| `GET /api/residentes` | — | `200` con las fichas por `id` ascendente |
| `GET /api/residentes/{id}` | — | `200` con la ficha |
| `PUT /api/residentes/{id}` | `{"nombre":"Ana Pérez"}` | `200` con la ficha actualizada |
| `POST /api/ocupaciones` | `{"apartamentoId":12,"residenteId":7,"fechaInicio":"2026-01-02"}` | `201` con la ocupación y `fechaFin: null` |
| `PATCH /api/ocupaciones/{id}/cierre` | `{"fechaFin":"2026-01-02"}` | `200` con la ocupación cerrada |
| `GET /api/ocupaciones` | Filtros opcionales `apartamentoId`, `residenteId` | `200` con filas abiertas y cerradas por `id` ascendente |

La ficha contiene `id` y `nombre`. La ocupación contiene `id`, `apartamentoId`, `residenteId`, `fechaInicio` y `fechaFin`. Con ambos filtros sólo se devuelven las filas que cumplen ambos; sin filtros se devuelve todo el historial. Si no hay coincidencias, incluso con un filtro de ID desconocido, se devuelve `[]`.

- `400` sin cuerpo: datos requeridos ausentes, JSON no legible, identificadores no válidos (usa enteros positivos), nombre en blanco o de más de 120 puntos de código tras quitar espacios de los extremos, fecha inválida o fin anterior al inicio. Usa fechas de calendario `YYYY-MM-DD` entre `1000-01-01` y `9999-12-31`. Se permite cerrar el mismo día que inició la ocupación.
- `404` sin cuerpo: no existe la ficha consultada/editada, falta el apartamento o el residente del alta, o no existe la ocupación que se intenta cerrar.
- `409` sin cuerpo: ya hay una ocupación activa para ese par residente-apartamento, o la ocupación que se intenta cerrar ya estaba cerrada.

Fuera de estas rutas, `DELETE /api/apartamentos/{id}` conserva su autorización previa y los estados `204`, `403` y `404`. Si la FK `fk_ocupaciones_apartamento` impide la eliminación, devuelve `409` sin cuerpo y no cambia el apartamento ni su historial, incluso si todas las ocupaciones están cerradas. Si el controlador JDBC o Hibernate no identifica la restricción, o si ocurre otro error SQL, no se traduce a `409`. La FK de la base de datos protege el historial aunque el error no pueda traducirse.

Una ocupación es activa mientras `fechaFin` sea `null`, independientemente de la fecha actual. No hay restricciones de fechas pasadas o futuras ni de solapamiento con filas ya cerradas. Un nuevo ingreso crea otra fila y conserva la anterior. No hay operaciones para borrar fichas u ocupaciones, reabrir filas cerradas, cambiar el inicio ni reasignar sus referencias. Editar el nombre de una ficha no altera las ocupaciones.
