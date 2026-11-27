# Condominios y torres

## Migración manual (MySQL)

Antes de desplegar la aplicación raíz, un operador debe revisar y aplicar una sola vez
[`02-condominios-torres.sql`](../../src/main/resources/db/manual/02-condominios-torres.sql)
en el esquema de destino. El script no selecciona una base ni se ejecuta al iniciar la app.

- Hacer respaldo, probar primero en un clon y programar una ventana para el `ALTER TABLE`.
  MySQL hace commits implícitos en DDL: una ejecución interrumpida puede dejar pasos aplicados.
- Confirmar el esquema seleccionado y revisar `SHOW CREATE TABLE apartamentos`: debe ser InnoDB y
  corresponder al mapeo existente (`id` numérico autogenerado, `numero`, `torre`, `area`, `propietario_id`).
  No deben existir `condominios`, `torres`, `apartamentos.torre_id` ni los índices o restricciones nuevos.
- Verificar que las dos tablas, la columna nullable y las dos claves foráneas existan antes de desplegar.
  Todas las filas previas quedan sin asignar; no se infieren vínculos del texto de `apartamentos.torre`.

Borrar una torre deja `torre_id` en `NULL`, sin borrar apartamentos ni cambiar su texto legado.
Un condominio con torres no puede borrarse; hay que trasladarlas o eliminarlas primero.

## API

Las rutas nuevas, incluida la asociación de apartamentos, exigen un principal autenticado por el
servidor (`getUserPrincipal()`) y el rol asignado por el servidor (`isUserInRole("ADMIN")`). Si falta
cualquiera, devuelven `403`. Ninguna cabecera enviada por el cliente, incluido `X-User-Role`, concede acceso.
Esta entrega no incorpora un proveedor de autenticación: la aplicación actual por sí sola no concede
acceso a estas rutas hasta que el despliegue configure autenticación confiable y el rol `ADMIN`.

| Método | Ruta | Entrada | Éxito |
| --- | --- | --- | --- |
| GET | `/api/condominios`, `/api/torres` | — | `200`, lista ordenada por ID |
| GET | `/api/condominios/{id}`, `/api/torres/{id}` | — | `200`, recurso |
| POST | `/api/condominios`, `/api/torres` | JSON del recurso sin `id` | `201`, recurso con ID generado |
| PUT | `/api/condominios/{id}`, `/api/torres/{id}` | JSON completo del recurso sin `id` | `200`, recurso actualizado |
| DELETE | `/api/condominios/{id}`, `/api/torres/{id}` | — | `204`, sin cuerpo |
| GET | `/api/apartamentos/{id}/torre` | — | `200`, `{"torreId":7}` o `{"torreId":null}` |
| PUT | `/api/apartamentos/{id}/torre` | `{"torreId":7}` para asignar; `{"torreId":null}` o `{}` para desasignar | `200`, asociación resultante |

Ejemplos de cuerpo: condominio `{"nombre":"Central"}`; torre `{"nombre":"Norte","condominioId":1}`.
El nombre es obligatorio y admite de 1 a 120 caracteres después de recortar espacios en los extremos.
Los datos requeridos inválidos devuelven `400`, los IDs inexistentes `404`, y el intento de borrar un
condominio que todavía tiene torres `409`. Las rutas originales de apartamentos siguen usando el
mismo JSON, cabeceras y control de acceso legado; su campo `torre` sigue siendo texto independiente de la asociación.
