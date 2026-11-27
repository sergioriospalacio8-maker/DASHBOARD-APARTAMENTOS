# Tabla `usuarios` (MySQL)

La aplicación raíz persiste `/api/usuarios` en MySQL. Antes de desplegar, un administrador debe seleccionar el esquema de destino y ejecutar manualmente [`01-usuarios.sql`](../../src/main/resources/db/manual/01-usuarios.sql) si falta la tabla. El script requiere MySQL 8.0.17 o posterior para la colación binaria de `id`: los identificadores se guardan como texto y se comparan distinguiendo mayúsculas y minúsculas.

El script solo crea la tabla si no existe; no modifica una tabla existente ni carga datos de ejemplo. Si la tabla ya existe, comprueba que sea compatible antes de desplegar. Mantén desactivada la generación automática del esquema de Hibernate (`spring.jpa.hibernate.ddl-auto=none`). Los datos del antiguo mapa en memoria no se migran automáticamente.
