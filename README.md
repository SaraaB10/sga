# SGA - Sistema de Gestión de Alojamiento

Proyecto final de Programación Avanzada (Ingeniería de Sistemas y Computación, 2026-2).

El SGA es el backend que maneja la operación del Apartahotel Coral, en Bocagrande (Cartagena): apartamentos, temporadas y tarifas, disponibilidad, cotizaciones, reservas, la llegada y la salida de los huéspedes, el folio de cada reserva y las reservas que llegan desde un canal externo.

**Equipo LoopDivas:** Juliana, Sara y Laura.

---

## Tecnologías

- Java 25
- Spring Boot 4 con Gradle
- Spring Data JPA y H2 en archivo (en memoria con el perfil `memoria` y en las pruebas)
- Bean Validation
- MapStruct (DTO ↔ dominio)
- springdoc-openapi (Swagger UI)
- JUnit 5 y MockMvc para las pruebas

## Arquitectura

Usamos **arquitectura hexagonal** (puertos y adaptadores):

```
domain/           El negocio. No conoce Spring, JPA ni HTTP.
  entity/           Agregados y entidades (Reserva, Apartamento, Folio, Alojamiento...)
  valueobject/      Objetos de valor como record o enum (Estancia, Dinero, Ocupante...)
  service/          Servicios de dominio (disponibilidad, cotización, creación de reservas...)
  repository/       Puertos: las interfaces que necesita el dominio
  exception/        ReglaDominioException
application/      Casos de uso (@Service, @Transactional), DTO y excepciones de aplicación
infrastructure/   Lo que conecta el dominio con el mundo
  rest/             Controllers, mappers de MapStruct y manejo global de errores
  persistence/      Adaptadores JPA (entidades, mappers y repositorios Spring Data) y en memoria
  config/           Registro de beans, datos iniciales y parámetros
  scheduler/        Tarea programada que vence las reservas pendientes
```

Algunas decisiones que tomamos:

- Todas las reglas de negocio (RN, RP y L) se validan en el dominio. El DTO solo valida la forma del mensaje.
- El dominio no tiene setters ni anotaciones. Para guardarlo en H2 usamos entidades JPA aparte y un mapper de persistencia.
- Los parámetros del alojamiento (horarios, umbral de edad, plazo de confirmación, depósito, política de cancelación, tamaño de página...) están en `application.properties`, no quemados en el código.
- Las eliminaciones son lógicas: un apartamento retirado deja de salir en los listados, pero las reservas viejas lo siguen referenciando.

## Cómo ejecutarlo

Necesitas tener instalado el **JDK 25**.

```bash
git clone https://github.com/julianaVL07/sga.git
cd sga/sga
./gradlew bootRun          # en Windows (PowerShell): .\gradlew.bat bootRun
```

Antes del primer arranque crea la clave del JWT (ver [Seguridad](#seguridad)): sin ella la aplicación no arranca.

Cuando en la consola salga `Started SgaApplication`, la API queda en `http://localhost:8080`.

| Qué | Dónde |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Consola H2 | http://localhost:8080/h2-console (URL `jdbc:h2:file:./data/sga`, usuario `sa`, sin contraseña; con el perfil `memoria`, `jdbc:h2:mem:sgadb`) |
| Contrato OpenAPI | `sga/docs/api/openapi.yaml` |

Para correr las pruebas:

```bash
./gradlew clean test       # en Windows (PowerShell): .\gradlew.bat clean test
```

El reporte queda en `sga/build/reports/tests/test/index.html`.

### Los datos se guardan entre reinicios

Al arrancar normal (`./gradlew bootRun`) la base H2 se guarda en el archivo `sga/data/sga.mv.db` (`jdbc:h2:file:./data/sga`, con `ddl-auto=update`): las cuentas, reservas y demás cambios siguen ahí al volver a arrancar. No hace falta ningún perfil.

- Los datos iniciales solo se cargan cuando la base está vacía (el primer arranque); en los siguientes no se vuelven a cargar ni se duplican.
- La carpeta `data/` está en `.gitignore`: la base nunca se sube al repositorio.
- Las pruebas (`./gradlew test`) usan H2 en memoria (`src/test/resources/config/application.properties`) y nunca tocan `data/`.

**Empezar en limpio.** Hay dos formas:

1. Detén la app, borra la carpeta `sga/data/` y vuelve a arrancar: se crea una base nueva con los datos iniciales.
2. Arranca con el perfil `memoria`, que usa H2 en memoria (`jdbc:h2:mem:sgadb`): cada arranque empieza de cero y al detener la app no queda nada guardado. Desde la carpeta `sga/`:

```bash
# Mac y Linux
./gradlew bootRun --args='--spring.profiles.active=memoria'
```

```powershell
# Windows (PowerShell)
.\gradlew.bat bootRun --args='--spring.profiles.active=memoria'
```

```bat
:: Windows (símbolo del sistema, cmd)
gradlew.bat bootRun --args="--spring.profiles.active=memoria"
```

## Despliegue

El sistema está desplegado: el frontend en **Vercel** y el backend en **Render**, con el perfil `prod` (`application-prod.properties`) y el `Dockerfile` de `sga/`.

| Qué | URL |
|---|---|
| Aplicación | https://coral-pi-nine.vercel.app |
| API | https://coral-snjd.onrender.com |
| Estado del API | https://coral-snjd.onrender.com/actuator/health |

> **Arranque en frío y disco efímero.** El plan gratuito de Render apaga el servicio tras unos 15 minutos sin tráfico: la primera petición después de eso tarda alrededor de un minuto mientras arranca (el frontend espera hasta 70 segundos). Conviene abrir `/actuator/health` antes de una demostración. Además el disco es efímero: la base H2 (`./data/sga`) y cualquier archivo guardado en el servidor se pierden en cada reinicio o despliegue, y el sistema vuelve a los datos iniciales. Por eso en Render las fotos van a Cloudinary y no al almacén local.

**Variables de entorno de Render.** Los valores no están en el repositorio; se configuran en **Environment** del servicio:

- `SPRING_PROFILES_ACTIVE` (`prod`)
- `SGA_JWT_SECRET`
- `SGA_CORS_ALLOWED_ORIGINS`
- `SGA_ADMIN_CORREO`, `SGA_ADMIN_CONTRASENA`, `SGA_ADMIN_TELEFONO`, `SGA_ADMIN_DOCUMENTO`, `SGA_ADMIN_FECHA_NACIMIENTO` (el nombre del administrador es fijo: "Administrador SGA")
- `SGA_RECEPCION_NOMBRE`, `SGA_RECEPCION_CORREO`, `SGA_RECEPCION_CONTRASENA`, `SGA_RECEPCION_TELEFONO`, `SGA_RECEPCION_DOCUMENTO`, `SGA_RECEPCION_FECHA_NACIMIENTO`
- `SGA_SERVICIO_NOMBRE`, `SGA_SERVICIO_CORREO`, `SGA_SERVICIO_CONTRASENA`, `SGA_SERVICIO_TELEFONO`, `SGA_SERVICIO_DOCUMENTO`, `SGA_SERVICIO_FECHA_NACIMIENTO`
- `SGA_CANALES_CREDENCIAL_DEMO`
- `SGA_IA_PROVEEDOR` (`gemini`) y `SGA_IA_GEMINI_CLAVE`
- `SGA_IMAGENES_PROVEEDOR` (`cloudinary`), `SGA_IMAGENES_CLOUDINARY_CLOUD_NAME`, `SGA_IMAGENES_CLOUDINARY_API_KEY` y `SGA_IMAGENES_CLOUDINARY_API_SECRET`

`PORT` la pone Render. Las fechas de nacimiento van en formato `AAAA-MM-DD`. Los datos de los tres usuarios iniciales (administrador, recepción y personal de servicio) no tienen valores por defecto y se tratan igual: hay que definirlos todos antes de desplegar. En Vercel, `VITE_API_URL` apunta a la URL del API.

**Notificaciones en Render.** En Render las notificaciones usan el adaptador **local** (los correos se escriben en el log del servicio), porque el plan gratuito bloquea las conexiones SMTP salientes. El envío real con Gmail (`sga.notificaciones.proveedor=smtp`) se demuestra en local; ver [Notificaciones](#notificaciones).

## Datos iniciales

Cuando la base está vacía (el primer arranque, después de borrar `sga/data/` o en cada arranque con el perfil `memoria`) se cargan solos estos datos. Las fechas de los bloqueos y de las reservas son relativas al día en que se cargan (hoy en Colombia), así que con una base guardada de días anteriores esas fechas ya no son las de hoy:

- El alojamiento Coral, con sus horarios (entrada 15:00, salida 11:00), normas, parámetros y la política de mascotas (máximo 2 de hasta 10 kg, aseo de $110.000) (Anexo A).
- Tres servicios adicionales: recepción nocturna ($70.000, RP-03), paseo privado por la bahía ($420.000) y cena en balcón ($260.000).
- Las temporadas Base, Media y Alta, con sus rangos de 2026 y comienzos de 2027.
- La versión 1 de la política de cancelación, con los tramos de `application.properties` (16 días o más 0 %, de 7 a 15 días 25 %, menos de 7 días o no-show 50 %).
- Los 5 apartamentos, con tarifa en todas las temporadas:

| Apartamento | Nombre | Capacidad | Estado al arrancar |
|---|---|---|---|
| APT-101 | Coral Luminoso | 2 | PREPARADO, con un bloqueo de hoy+4 a hoy+7 |
| APT-102 | Arrecife Rosado | 2 | PREPARADO |
| APT-201 | Marea Dorada | 4 | OCUPADO (tiene la reserva EN_CURSO) |
| APT-202 | Bahía Cristal | 4 | FUERA_DE_SERVICIO, con un bloqueo de hoy a hoy+15 |
| APT-301 | Gran Coral Imperial | 6 | PREPARADO, con un bloqueo de hoy+20 a hoy+25 |

- 7 reservas en todos los estados y por los tres canales, cada una con su folio:

| Estado | Apartamento | Estancia | Canal |
|---|---|---|---|
| FINALIZADA | APT-101 | hoy-10 a hoy-7 | DIRECTO |
| NO_SHOW | APT-102 | hoy-5 a hoy-3 | PORTAL |
| EN_CURSO | APT-201 | hoy-1 a hoy+2 | DIRECTO |
| PENDIENTE | APT-101 | hoy+10 a hoy+12 | PORTAL |
| CONFIRMADA | APT-102 | hoy+12 a hoy+15 | DIRECTO |
| CANCELADA | APT-101 | hoy+20 a hoy+22 | PORTAL |
| PENDIENTE | APT-201 | hoy+15 a hoy+18 | EXTERNO (sin hora de llegada) |

- Al menos un usuario por rol (administrador, recepción, personal de servicio y dos huéspedes).

Si la base ya existe, los cargadores no duplican nada y solo completan lo mínimo que le falte: los apartamentos del inventario y sus tarifas, un usuario por cada rol sin ninguno, un apartamento FUERA_DE_SERVICIO con su bloqueo, un bloqueo vigente y una reserva por cada estado y canal que no tenga ninguna, siempre en noches libres.

## Endpoints

Son 83 operaciones: las 67 de esta lista, las 12 de autenticación y usuarios (ver [Seguridad](#seguridad)), la sugerencia de gravedad (ver [Modelo de IA](#modelo-de-ia)) y los 3 reportes del administrador (`GET /api/reportes/ocupacion`, `/ingresos` y `/reservas-por-canal`). Todos los listados devuelven 10 resultados por página y las páginas empiezan en 0 (`?pagina=`).

**Reservas**
- `POST /api/reservas` crear una reserva (201 con `Location`); acepta mascotas opcionales (`cantidadMascotas`, `pesoMascotaMasPesadaKg`)
- `GET /api/reservas` listar con filtros (estado, apartamento, canal, documento del titular, fechas y `buscar`, que encuentra por una parte del código o del nombre del titular), de la más reciente a la más antigua. El buscador del encabezado del panel abre **Reservas** con esa búsqueda aplicada
- `GET /api/reservas/{codigo}` consultar una reserva
- `PUT /api/reservas/{codigo}` modificar fechas, ocupantes, apartamento o mascotas
- `PUT /api/reservas/{codigo}/confirmar` confirmar (el cuerpo con la hora de llegada es opcional)
- `PUT /api/reservas/{codigo}/cancelar` cancelar con la retención de la política congelada
- `PUT /api/reservas/{codigo}/no-show` declarar no-show
- `GET /api/reservas/llegadas` y `GET /api/reservas/salidas` llegadas y salidas previstas de un día (hoy si no se manda `fecha`)
- `GET /api/reservas/mias` historial del huésped: solo sus reservas, con filtro opcional de estado

**Operación en recepción**
- `PUT /api/reservas/{codigo}/registrar-llegada` registrar la llegada (el apartamento pasa a OCUPADO)
- `PUT /api/reservas/{codigo}/registrar-salida` registrar la salida (el apartamento pasa a PENDIENTE_PREPARACION)
- `GET /api/reservas/calendario` calendario de ocupación de un apartamento noche por noche (máximo 62 noches)

**Apartamentos**
- `GET /api/apartamentos/disponibles` buscar disponibles por fechas y grupo, con filtros (capacidad, característica, servicios, precio y fechas de nacimiento). `servicios` acepta varios valores (`servicios=hidromasaje&servicios=vista al mar` o separados por coma) y el apartamento debe ofrecerlos todos según su dotación y su descripción
- `GET /api/apartamentos` listado de los no retirados
- `GET /api/apartamentos/{id}` detalle con dotación, imágenes, tarifas y bloqueos vigentes
- `GET /api/apartamentos/{id}/disponibilidad?desde=&hasta=` calendario público: por cada noche dice si está libre, sin datos de las reservas (máximo 62 noches). Lo usa el calendario mensual del detalle del apartamento
- `POST /api/apartamentos` registrar un apartamento (nace inactivo)
- `POST /api/imagenes` subir una foto (multipart, parte `archivo`) y recibir su URL; `GET /api/imagenes/{nombre}` sirve las fotos del almacén local (ver [Imágenes](#imágenes))
- `PUT /api/apartamentos/{id}` actualizar; si baja la capacidad, avisa qué reservas la superan
- `PUT /api/apartamentos/{id}/tarifas` definir tarifas por temporada
- `GET /api/apartamentos/{id}/tarifas/historico` histórico de tarifas
- `PUT /api/apartamentos/{id}/activar` ponerlo a la venta (exige tarifa en todas las temporadas)
- `PUT /api/apartamentos/{id}/retirar` retirar de la venta (eliminación lógica)
- `PUT /api/apartamentos/{id}/iniciar-preparacion`, `/terminar-preparacion`, `/poner-fuera-de-servicio` y `/restablecer-servicio` operaciones del estado operativo
- `GET /api/apartamentos/pendientes-preparacion` los que están en PENDIENTE_PREPARACION o EN_PREPARACION
- `POST /api/apartamentos/{id}/bloqueos` registrar un bloqueo (201 con `Location`)
- `GET /api/apartamentos/{id}/bloqueos/{idBloqueo}` consultar un bloqueo, también si ya se levantó (es el recurso del `Location`)
- `PUT /api/apartamentos/{id}/bloqueos/{idBloqueo}/levantar` levantar un bloqueo vigente: sus noches vuelven a la venta y el bloqueo se conserva como levantado (solo administrador)
- `POST /api/apartamentos/{id}/novedades` registrar una novedad (201 con `Location`)
- `GET /api/apartamentos/{id}/novedades/{idNovedad}` consultar una novedad (es el recurso del `Location`)
- `GET /api/apartamentos/{id}/novedades` historial de novedades, de la más reciente a la más antigua

**Cotizaciones**
- `POST /api/cotizaciones` valor de la estancia noche por noche

**Folio**
- `GET /api/reservas/{codigo}/folio` consultar el folio con sus cargos, pagos y saldo
- `POST /api/reservas/{codigo}/folio/pagos` registrar un pago (201 con `Location` al folio)
- `POST /api/reservas/{codigo}/folio/cargos` cargar un servicio adicional (201 con `Location` al folio)
- `POST /api/reservas/{codigo}/folio/correcciones` corregir un cargo con un movimiento inverso (201 con `Location` al folio)
- `PUT /api/reservas/{codigo}/folio/cerrar` cerrar con saldo cero
- `POST /api/reservas/{codigo}/folio/autorizacion-cierre` el administrador autoriza cerrar con saldo

**Canal externo** (con la credencial del canal en `X-Canal-Credencial`, sin JWT; ver [Canal externo](#canal-externo))
- `GET /api/canales/{canal}/disponibilidad` disponibilidad paginada para el canal
- `POST /api/canales/{canal}/reservas` recibir una reserva del canal
- `PUT /api/canales/{canal}/reservas/{identificadorExterno}/cancelar` cancelar una reserva del canal

**Canales de venta** (solo el administrador)
- `POST /api/canales` crear un canal (201 con la credencial en claro, una sola vez) y `GET /api/canales` listarlos sin credenciales
- `PUT /api/canales/{nombre}/regenerar-credencial` emitir una credencial nueva y `PUT /api/canales/{nombre}/desactivar` eliminación lógica
- `GET /api/canales/conflictos?estado=` conflictos de canal (RN-18) y `PUT /api/canales/conflictos/{id}/resolver` resolver uno con su motivo
- `GET /api/canales/eventos?canal=&desde=&hasta=` bitácora de mensajes con los canales (7.8)

**Configuración del alojamiento**
- `GET /api/alojamiento` y `PUT /api/alojamiento` consultar y actualizar los datos del alojamiento
- `GET /api/medios-pago` medios de pago aceptados y activos (público); el administrador los gestiona con `GET /api/medios-pago/todos`, `POST /api/medios-pago` y `PUT /api/medios-pago/{codigo}/activar` y `/desactivar`. Solo con un medio activo se registra un pago
- `PUT /api/alojamiento/parametros` el administrador cambia los parámetros (umbral de edad facturable, tiempo de preparación, plazo de confirmación y del anticipo, hora límite de no-show, hora y recargo de la recepción nocturna, estancia mínima, anticipo, depósito de garantía, mascotas y edad mínima de la cuenta). Se guardan en la base, nacen con los valores de `application.properties` y rigen de inmediato, sin reiniciar
- `POST /api/alojamiento/servicios` crear un servicio adicional (201 con `Location`), `GET` y `PUT /api/alojamiento/servicios/{id}` consultarlo y editar su nombre, descripción, si genera cargo y su valor, y `PUT /api/alojamiento/servicios/{id}/desactivar` y `/activar` (eliminación lógica). La recepción nocturna no se puede desactivar ni dejar sin cargo, porque RP-03 la cobra a las llegadas desde la hora configurada; la respuesta lo explica en `motivoObligatorio` y el panel **Administración → Alojamiento** lo muestra
- `GET /api/temporadas`, `GET /api/temporadas/{id}` y `POST /api/temporadas` listar, consultar y crear temporadas (201 con `Location`)
- `POST /api/temporadas/{id}/rangos` agregar un rango a una temporada (201 con `Location`)
- `GET /api/politica-cancelacion` política vigente
- `GET /api/politica-cancelacion/versiones` versiones de la política
- `PUT /api/politica-cancelacion` publicar una versión nueva

El detalle de cada endpoint (parámetros, respuestas y errores) está en Swagger y en el `openapi.yaml`. Los dos describen las 82 operaciones, y en cada operación protegida el `openapi.yaml` dice qué roles la pueden usar.

Los errores siempre vuelven con el mismo formato (`ErrorResponse`): **400** cuando el mensaje está mal escrito, **401** cuando falta el token o no es válido, **403** cuando el rol, el propietario o el canal no lo permiten, **404** cuando el recurso no existe y **409** cuando una regla del negocio no lo permite.

## Probar la API

En `sga/docs/api/sga.http` dejamos una colección de 267 peticiones que recorre el flujo completo de una reserva y prueba un 409 por cada regla que expone la API, además de los 400 y 404, los casos de seguridad (401, 403 por rol, por propietario y por canal), la gestión de usuarios, el orden de las validaciones de una reserva, el calendario de disponibilidad, el filtro por servicios, los servicios adicionales y la subida de fotos. Con las 6 del simulador `canal-externo.http` son 273 peticiones. Cada petición valida su respuesta con `client.test`: las de éxito, el código y uno o dos campos clave; las de error, el código y todo el `ErrorResponse` (`status`, `error`, `mensaje`, `path`, `timestamp` y los `errores` por campo de los 400).

1. Arranca la app en limpio, con el perfil `memoria` (`./gradlew bootRun --args='--spring.profiles.active=memoria'`) o después de borrar `sga/data/`. Con una base guardada de antes, las peticiones que esperan los datos iniciales fallan.
2. Abre `sga.http` en IntelliJ y dale a **Run All Requests in File**. En VS Code se puede usar la extensión REST Client.
3. Si la quieres volver a correr, reinicia la app primero (con el perfil `memoria`, o borrando `sga/data/`): la colección cambia los datos.

## Documentación

- `sga/docs/api/openapi.yaml` contrato de la API
- `sga/docs/trazabilidad/matriz-trazabilidad.md` qué regla se protege dónde y qué endpoint la expone
- `sga/docs/entrega-2/cambios-frente-entrega-1.md` qué agregamos o cambiamos frente a la Entrega 1

## Estado del proyecto

- **Entrega 1:** dominio con sus reglas y pruebas unitarias.
- **Entrega 2:** casos de uso, API REST (50 operaciones), persistencia en H2 con datos iniciales, consultas paginadas y documentación. Además, frente a la Entrega 1 hicimos estos cambios (el detalle está en `sga/docs/entrega-2/cambios-frente-entrega-1.md`):
    - Reservas: reglas de canal en el dominio, mascotas en la reserva (L-16) y pruebas unitarias de RN-04, RN-10, RN-12, RN-14, RN-21, RN-22, RP-03, L-15 y L-16.
    - Apartamentos y operación: operaciones del estado operativo en lugar del cambio genérico, cada una con su estado de origen, `Location` en bloqueos y novedades con su consulta por id, historial de novedades y apartamentos pendientes de preparación.
    - Folio y transversal: depósito como `AJUSTE_MODIFICACION`, consulta del folio, cargos de servicios adicionales y correcciones (RN-16), `Location` en pagos y temporadas, consulta de una temporada y disponibilidad del canal paginada.
    - Pruebas unitarias de las reglas de configuración (L-03, L-06, L-09, L-12, L-13, L-14 y L-17), del titular facturable (3.2), del apartamento activo (3.3) y del formato del código de reserva.
- **Verificación:** las 472 pruebas pasan (`./gradlew clean test`), las 261 peticiones de `sga.http` y las 6 de `canal-externo.http` pasan contra la app recién arrancada en limpio, las pruebas de integración de cada módulo comprueban el cuerpo de error de un 400, 401, 403, 404 y 409 y la matriz de trazabilidad dice qué prueba unitaria y de integración cubre cada regla.

## Seguridad

Los endpoints exigen un token JWT, salvo el login, el registro, las consultas públicas del visitante y las herramientas de desarrollo (Swagger y consola H2). Cada endpoint define qué roles lo usan y, en reservas y folios, el huésped solo accede a lo suyo: **401** es que falta el token o no sirve, **403** que el rol no lo permite o la reserva no es suya.

**Crear la clave.** La aplicación no arranca sin ella. En `sga/`:

```bash
echo "sga.jwt.secret=$(openssl rand -base64 48)" > src/main/resources/application-secrets.properties
```

> **`application-secrets.properties` nunca se sube al repositorio.** Está en `.gitignore` y cada integrante genera su propia clave con el comando de arriba; no se comparte por chat ni por correo. Si una clave llega a GitHub se considera comprometida: hay que sacar el archivo (`git rm --cached`) y generar una nueva, porque borrar el commit no basta.

La plantilla es `application-secrets.properties.example`. En despliegue y en GitHub Actions se usa la variable de entorno `SGA_JWT_SECRET`. El archivo nunca entra al jar: `build.gradle` lo excluye de `processResources`, y `./gradlew bootRun` lo lee directamente de `src/main/resources`.

**Usuarios iniciales.** Son datos de prueba de H2, no credenciales reales:

| Rol | Correo | Contraseña |
|---|---|---|
| ADMINISTRADOR | `admin@sga.local` | `Admin2026` |
| RECEPCIONISTA | `recepcion@sga.local` | `Recepcion2026` |
| PERSONAL_SERVICIO | `servicio@sga.local` | `Servicio2026` |
| HUESPED (Laura Gomez) | `huesped1@sga.local` | `Huesped2026` |
| HUESPED (Valentina Rios) | `huesped2@sga.local` | `Huesped2026` |

**Login y registro**, ambos públicos:

- `POST /api/auth/login` recibe `correo` y `contrasena` y devuelve el token, que dura 30 minutos.
- `POST /api/auth/registro` crea un huésped (201) con `nombre`, `correo`, `contrasena`, `telefono`, `documento` y `fechaNacimiento`. El teléfono debe tener al menos 7 dígitos y solo admite números, espacios, guiones, paréntesis y el signo + al inicio (la misma regla rige en el perfil, en los usuarios del panel y en el titular de una reserva, y el frontend la valida igual). Responde 409 si el correo o el documento ya existen.

```bash
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"recepcion@sga.local","contrasena":"Recepcion2026"}'
```

**Consultas públicas del visitante.** Responden sin token para que cualquiera pueda ver la oferta antes de iniciar sesión. Son solo lecturas y la cotización; los `PUT` y `POST` de esas mismas rutas siguen siendo del administrador:

- `GET /api/apartamentos`, `GET /api/apartamentos/disponibles`, `GET /api/apartamentos/{id}` y `GET /api/apartamentos/{id}/disponibilidad`.
- `GET /api/alojamiento` y `GET /api/politica-cancelacion`.
- `GET /api/temporadas` y `GET /api/temporadas/{id}`.
- `GET /api/imagenes/{nombre}` (las fotos del almacén local).
- `POST /api/cotizaciones` (calcula el valor, no crea la reserva).

Reservar, consultar reservas y cualquier otra operación sí piden el token.

**Usar el token.** Envíelo en el encabezado `Authorization: Bearer <token>`. En Swagger UI, péguelo en el botón **Authorize**. Sin token, o con un token mal firmado o vencido, la respuesta es 401 con el `ErrorResponse` y el encabezado `WWW-Authenticate: Bearer`.

**Canal por rol al crear una reserva** (`POST /api/reservas`): el huésped solo reserva por `PORTAL` y a su nombre (el documento del titular debe ser el del token), la recepción solo por `DIRECTO` y el administrador por cualquiera de los dos. Si no se cumple, responde 403. Es una regla de acceso, no de negocio: la verifica `CanalPorRolVerificador`, fuera del dominio.

**Usuarios** (solo el administrador). Aquí se asignan los roles internos; el registro público siempre crea huéspedes. No hay `DELETE`:

- `POST /api/usuarios` crear un usuario de cualquier rol (201 con `Location`). Valida lo mismo que el registro y responde 409 si el correo o el documento ya existen.
- `GET /api/usuarios` listar 10 por página, con filtros opcionales `rol` y `activo`.
- `GET /api/usuarios/{id}` consultar un usuario (404 si no existe).
- `PUT /api/usuarios/{id}/rol` cambiar el rol.
- `PUT /api/usuarios/{id}/desactivar` eliminación lógica. Responde 409 si el usuario es titular de reservas PENDIENTE, CONFIRMADA o EN_CURSO, o si el administrador intenta desactivarse a sí mismo. No se borran el titular ni sus reservas, y un usuario inactivo ya no inicia sesión (401).

**Mi perfil** (cualquier usuario autenticado, siempre sobre el usuario del token):

- `GET /api/usuarios/me` consultar mi perfil.
- `PUT /api/usuarios/me` actualizar nombre, teléfono y foto (`fotoUrl`). El correo no es editable.
- `PUT /api/usuarios/me/contrasena` cambiar la contraseña con `contrasenaActual` y `contrasenaNueva` (204). Si la actual no coincide, responde 400.

**Mis reservas** (solo el huésped): `GET /api/reservas/mias` lista las reservas cuyo titular tiene el documento del token. Un huésped nunca ve reservas de otros.

Ninguna respuesta incluye la contraseña.

**CORS.** El frontend puede llamar a la API desde los orígenes de `sga.cors.origenes-permitidos` en `application.properties` (por defecto `http://localhost:5173` y `http://localhost:3000`). Se permiten `GET`, `POST`, `PUT` y `OPTIONS`, los encabezados `Authorization` y `Content-Type`, y el navegador puede leer `Location`. Para otro origen, agréguelo a esa propiedad separado por comas.

## Modelo de IA

Al registrar una novedad, el SGA sugiere su gravedad (BAJA, MEDIA o ALTA) con una explicación corta (`POST /api/apartamentos/novedades/sugerir-gravedad`, para personal de servicio y administración). Es solo una ayuda: la gravedad que se guarda la elige siempre la persona. La pantalla dice de dónde salió: **"Sugerencia de IA"** si la dio Gemini o **"Sugerencia local"** si la dieron las reglas por palabras clave (la respuesta lo trae en `origen`: `IA` o `LOCAL`).

Con Gemini, si responde **429** (límite de peticiones) o **503** (saturado), el SGA reintenta **una sola vez tras 1 segundo**. Si vuelve a fallar, o hay cualquier otro error (sin clave, sin red, más de 15 segundos o una respuesta inválida), responde la sugerencia local como respaldo y el motivo queda en el log (`IA: se usa la sugerencia local porque ...`). El formulario nunca se bloquea.

El proveedor se elige con `sga.ia.proveedor` en `application.properties`:

| Valor | Qué hace |
|---|---|
| `local` (por defecto) | Reglas por palabras clave, sin internet ni clave. Por ejemplo, gas, fuga, eléctrico o inundación → ALTA. Es el que usan las pruebas. |
| `gemini` | Le pregunta a Gemini por la API REST de Google AI Studio. Necesita la clave. Si Gemini falla, responde el local. |
| `deshabilitado` | Nunca sugiere nada, ni siquiera la local; en el formulario sale "Sugerencia no disponible". |

**Sacar la clave gratis de Google AI Studio:**

1. Entra a https://aistudio.google.com/apikey con una cuenta de Google.
2. Pulsa **Create API key** (Crear clave de API) y cópiala. El plan gratuito no pide tarjeta; tiene un límite de peticiones por minuto y por día, de sobra para probar.
3. Pégala en `src/main/resources/application-secrets.properties` (el mismo archivo de la clave del JWT, que nunca se sube al repositorio):

   ```properties
   sga.ia.gemini.clave=la-clave-que-copiaste
   ```

4. Arranca con Gemini: cambia `sga.ia.proveedor=gemini` en `application.properties` o, sin tocar el archivo, `./gradlew bootRun --args='--sga.ia.proveedor=gemini'`.

El modelo (`sga.ia.gemini.modelo`, por defecto `gemini-3.8-flash`), la URL de la API y el tiempo de espera de cada consulta (`sga.ia.gemini.tiempo-espera=PT15S`) también están en `application.properties`. La clave solo va en `application-secrets.properties`; en despliegue se puede pasar con la variable de entorno `SGA_IA_GEMINI_CLAVE`.

**Apagar la IA:** pon `sga.ia.proveedor=deshabilitado` en `application.properties` (o arranca con `--sga.ia.proveedor=deshabilitado`). No hace falta borrar la clave.

## Notificaciones

El SGA le escribe al titular de la reserva cuando la reserva se **crea**, se **confirma** o se **cancela** (con la retención), y le manda un **recordatorio** el día anterior a su llegada o a su salida. Cada correo, en español, lleva el código de la reserva, el apartamento, las fechas y el valor. Por el mismo canal sale el **código para recuperar la contraseña**.

Los correos salen después de que la operación quedó guardada y en segundo plano: si el correo falla o tarda más de 5 segundos, la reserva sigue igual, el usuario no ve ningún error y el fallo queda en el log. Si la reserva no tiene correo del titular, no se envía nada y también queda en el log.

El proveedor se elige con `sga.notificaciones.proveedor` en `application.properties`:

| Valor | Qué hace |
|---|---|
| `local` (por defecto) | No envía nada por internet: guarda los mensajes en memoria y los escribe completos en el log (`Notificaciones (local): correo a ...`). Es el que usan las pruebas. En desarrollo, el código de recuperación de contraseña se lee ahí. |
| `smtp` | Envía correos de verdad por SMTP. Necesita la cuenta y la clave. |

**Enviar con Gmail usando una clave de aplicación.** Gmail no deja usar la contraseña normal de la cuenta desde una aplicación; hay que crear una "contraseña de aplicación":

1. Entra a https://myaccount.google.com/security con la cuenta que va a enviar los correos y activa la **Verificación en dos pasos** (sin ella no aparece la opción del paso 2).
2. Entra a https://myaccount.google.com/apppasswords, escribe un nombre (por ejemplo `SGA Coral`) y pulsa **Crear**. Google muestra una clave de 16 letras: cópiala sin los espacios. Solo se muestra una vez.
3. Pega la cuenta y la clave en `src/main/resources/application-secrets.properties` (el mismo archivo de la clave del JWT, que nunca se sube al repositorio):

   ```properties
   sga.notificaciones.smtp.usuario=tu-cuenta@gmail.com
   sga.notificaciones.smtp.clave=laclavedeaplicacion
   ```

4. Arranca con SMTP: cambia `sga.notificaciones.proveedor=smtp` en `application.properties` o, sin tocar el archivo, `./gradlew bootRun --args='--sga.notificaciones.proveedor=smtp'`.

El servidor (`smtp.gmail.com`), el puerto (`587` con STARTTLS) y el tiempo de espera (`sga.notificaciones.smtp.tiempo-espera=PT5S`) ya están en `application.properties`. Con Gmail el remitente es la misma cuenta; `sga.notificaciones.remitente` vacío usa esa. La cuenta y la clave solo van en `application-secrets.properties`; en despliegue se pueden pasar con las variables de entorno `SGA_NOTIFICACIONES_SMTP_USUARIO` y `SGA_NOTIFICACIONES_SMTP_CLAVE`. Si la clave se filtra, bórrala en https://myaccount.google.com/apppasswords y crea otra.

**Recuperar la contraseña.** En el login, el enlace **¿Olvidaste tu contraseña?** lleva a `/recuperar`, donde se escribe el correo, y luego a `/recuperar/codigo`, donde se escriben el código y la contraseña nueva. Por la API:

- `POST /api/auth/recuperacion/solicitar` con `correo`: responde **202 siempre**, exista o no la cuenta, para no revelar qué correos están registrados. Si la cuenta existe y está activa, le envía un código de 6 dígitos.
- `POST /api/auth/recuperacion/restablecer` con `correo`, `codigo` y `nuevaContrasena` (204). La contraseña nueva sigue la regla del registro. Si el código no sirve, responde 400.

El código se guarda cifrado, vence a los 15 minutos (`sga.recuperacion.vigencia`), sirve una sola vez y se anula tras 5 intentos fallidos (`sga.recuperacion.intentos-maximos`). Pedir otro anula el anterior. Las dos rutas son públicas.

**Recordatorios automáticos.** Todos los días a las 9:00 (hora de Colombia) el SGA avisa a los titulares de las reservas CONFIRMADAS que llegan mañana y de las EN_CURSO que salen mañana. Un mismo recordatorio no se envía dos veces: se guarda el último envío por reserva y motivo. Se configura en `application.properties`:

```properties
# false los apaga
sga.recordatorios.activo=true
# segundo minuto hora día mes día-de-la-semana
sga.recordatorios.cron=0 0 9 * * *
```

## Canal externo

Las plataformas de terceros (por ejemplo Booking) venden el mismo inventario por las tres operaciones de `/api/canales/{canal}/...`. No usan el JWT: cada canal se autentica con **su propia credencial** en el encabezado `X-Canal-Credencial`.

| Caso | Respuesta |
|---|---|
| Sin credencial, credencial incorrecta, de otro canal o canal que no existe | **401** con `ErrorResponse` |
| Credencial correcta pero canal desactivado | **403** con `ErrorResponse` |
| Reserva aceptada / el mismo identificador externo repetido por el mismo canal (RN-19) | 201 `ACEPTADO` / 200 `DUPLICADO_IGNORADO` |
| Noches ya vendidas o bloqueadas (RN-18) | 409 `RECHAZADO_CONFLICTO`, y el conflicto queda registrado |

La idempotencia es por canal: el mismo identificador externo enviado por dos canales distintos son dos reservas, y cada canal solo puede cancelar las reservas que él creó. Todo mensaje, también los rechazados por credencial, queda en la bitácora. El administrador gestiona los canales, los conflictos y la bitácora en el panel, en **Administración → Canales de venta** (`/panel/canales`): al crear un canal o regenerar su credencial la ve **una sola vez**, con un botón para copiarla; el SGA solo guarda su hash (BCrypt).

**Credencial del canal de prueba.** Los datos iniciales crean el canal `booking`, el de la reserva externa `BK-DEMO-000123`. Su credencial va en `src/main/resources/application-secrets.properties`, junto a la clave del JWT:

```bash
echo "sga.canales.credencial-demo=$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')" >> src/main/resources/application-secrets.properties
```

En producción va en la variable de entorno `SGA_CANALES_CREDENCIAL_DEMO`. Si no la configuras, la app genera una al azar al arrancar, pero **no la escribe en el log**: solo avisa que falta y cómo definirla:

```
WARN ... DatosInicialesCanalesConfig : Falta sga.canales.credencial-demo: el canal externo 'booking' se creó con una credencial aleatoria que no se muestra. Defínala en application-secrets.properties (sga.canales.credencial-demo=...) o, en producción, con la variable de entorno SGA_CANALES_CREDENCIAL_DEMO, y arranque con la base vacía; o emita una nueva desde Administración → Canales de venta (Regenerar credencial).
```

Como el canal ya quedó creado, definir la propiedad después solo surte efecto con la base vacía; si no, se emite una credencial nueva desde **Canales de venta**.

**Correr el simulador.** `sga/docs/api/canal-externo.http` hace de plataforma externa y es independiente de `sga.http`:

1. Arranca la app en limpio (`./gradlew bootRun --args='--spring.profiles.active=memoria'`, o después de borrar `sga/data/`).
2. Abre `canal-externo.http` y pon en `@credencial` el valor de `sga.canales.credencial-demo` (o la que emitas con **Regenerar credencial** en el panel). `@canal` ya es `booking`.
3. En IntelliJ dale a **Run All Requests in File**. Corre en orden y cada petición dice arriba qué debe responder:
   1. consultar disponibilidad (200);
   2. reserva externa aceptada (201 `ACEPTADO`);
   3. el mismo mensaje repetido, con el mismo identificador externo (200 `DUPLICADO_IGNORADO`, no se crea otra reserva);
   4. una reserva sobre las noches de la reserva CONFIRMADA de APT-102 (409 `RECHAZADO_CONFLICTO`, queda el conflicto);
   5. cancelar la reserva externa (200 `ACEPTADO`);
   6. una consulta sin credencial (401).
4. Para ver el resultado como administrador, entra a **Canales de venta**: en **Bitácora** salen los seis mensajes y en **Conflictos** el del paso 4, listo para resolverlo con un motivo (la reserva vigente no cambia).

Si lo corres dos veces sin reiniciar, el paso 2 responde `DUPLICADO_IGNORADO` y el 5 `RECHAZADO_INVALIDO`, porque esa reserva ya se aceptó y se canceló.

## Imágenes

Las fotos de los apartamentos se guardan a través del puerto `AlmacenImagenes`. En **Administración → Apartamentos → Nuevo apartamento**, el botón **Subir fotos** agrega fotos del equipo (JPG, PNG o WebP de hasta 5 MB) y **Agregar URL** sigue dejando pegar la dirección de una imagen; entre las dos van de 1 a 10 imágenes y se marca cuál es la principal. Las fotos se suben al guardar (`POST /api/imagenes`, solo el administrador) y su URL viaja en el registro como cualquier otra. Si alguna no se puede subir, el apartamento se guarda igual con las demás imágenes y la pantalla avisa cuántas fallaron; si no queda ninguna, el formulario lo dice y deja pegar una URL.

El proveedor se elige con `sga.imagenes.proveedor` en `application.properties`:

| Valor | Qué hace |
|---|---|
| `local` (por defecto) | Guarda la foto en `sga.imagenes.local.directorio` (`./data/imagenes`, fuera del repositorio) y la sirve el backend en `{sga.imagenes.local.url-publica}/api/imagenes/{nombre}`, sin token. No usa internet ni credenciales. Es el que usan las pruebas. |
| `cloudinary` | Subida firmada a Cloudinary (la firma SHA-1 se calcula en el backend y el secreto nunca viaja). Devuelve una URL `https://res.cloudinary.com/...`. Necesita las credenciales. |

**Credenciales de Cloudinary.** Están en el panel de Cloudinary, en **Settings → API Keys**. Van en `src/main/resources/application-secrets.properties`, nunca en el repositorio:

```properties
sga.imagenes.cloudinary.cloud-name=tu-cloud-name
sga.imagenes.cloudinary.api-key=tu-api-key
sga.imagenes.cloudinary.api-secret=tu-api-secret
```

Luego arranca con `--sga.imagenes.proveedor=cloudinary`. La carpeta de Cloudinary (`sga.imagenes.cloudinary.carpeta=sga/apartamentos`) y el tiempo de espera (`sga.imagenes.cloudinary.tiempo-espera=PT20S`) están en `application.properties`. En despliegue las credenciales van en las variables de entorno `SGA_IMAGENES_CLOUDINARY_CLOUD_NAME`, `SGA_IMAGENES_CLOUDINARY_API_KEY` y `SGA_IMAGENES_CLOUDINARY_API_SECRET`.

**En Render** se usa Cloudinary (`SGA_IMAGENES_PROVEEDOR=cloudinary`): el disco del servidor es efímero y las fotos del almacén local se perderían en cada reinicio o despliegue (ver [Despliegue](#despliegue)).

Las URL de las imágenes deben empezar por `https://`; la única excepción es `http://localhost` o `http://127.0.0.1`, que son las del almacén local con el backend en el propio equipo. Las fotos de los datos iniciales son URL fijas y no dependen del proveedor.

## Integraciones

Cada servicio externo entra por un puerto y tiene un adaptador real y uno local, de modo que si el servicio falla o no hay red el sistema sigue funcionando.

| Integración | Puerto | Adaptador real | Adaptador local | Propiedad que la elige o la apaga | Tiempo de espera | Qué pasa si falla |
|---|---|---|---|---|---|---|
| Canal externo | `AutenticarCanalUseCase` y `RecibirReservaExternaUseCase` (entrada) | `CanalExternoController` con `CanalCredencialFilter` (credencial en `X-Canal-Credencial`) | El simulador `sga/docs/api/canal-externo.http` y el canal `booking` de los datos iniciales (`sga.canales.credencial-demo`) | No tiene propiedad: el administrador apaga cada canal con `PUT /api/canales/{nombre}/desactivar` | No espera a nadie: el canal llama al SGA y la respuesta es inmediata | 401 o 403 con `ErrorResponse`, 409 `RECHAZADO_CONFLICTO` con el conflicto registrado; todo queda en la bitácora |
| Notificaciones por correo | `NotificadorTitular` | `NotificadorTitularSmtp` (SMTP de Gmail) | `NotificadorTitularLocal` (en memoria y en el log) | `sga.notificaciones.proveedor` (`smtp` o `local`); los recordatorios se apagan con `sga.recordatorios.activo=false` | `sga.notificaciones.smtp.tiempo-espera=PT5S` | La operación ya quedó guardada: el usuario no ve ningún error y el fallo queda en el log |
| Modelo de IA | `SugeridorGravedad` | `SugeridorGravedadGemini` (Google AI Studio) | `SugeridorGravedadLocal` (palabras clave); `SugeridorGravedadDeshabilitado` la apaga | `sga.ia.proveedor` (`gemini`, `local` o `deshabilitado`) | `sga.ia.gemini.tiempo-espera=PT15S`, con un reintento tras 1 s ante 429 o 503 | Responde la sugerencia local ("Sugerencia local") y el motivo queda en el log; con `deshabilitado` sale "Sugerencia no disponible". La novedad se guarda igual |
| Subida de fotos | `AlmacenImagenes` | `AlmacenImagenesCloudinary` (subida firmada a Cloudinary) | `AlmacenImagenesLocal` (disco del servidor, servido en `/api/imagenes/{nombre}`) | `sga.imagenes.proveedor` (`cloudinary` o `local`) | `sga.imagenes.cloudinary.tiempo-espera=PT20S` | `POST /api/imagenes` responde 502; el apartamento se guarda con las demás imágenes y la pantalla avisa |
| Mostrar las fotos | URL de cada imagen del apartamento (`imagenes`) y de la portada (`frontend/src/lib/imagenes.ts`) | `FotoApartamento` carga la imagen de `res.cloudinary.com` o del backend | El fondo crema con ondas de `FotoApartamento` | No tiene propiedad: basta con que la imagen no cargue | El del navegador | Se muestra el fondo con ondas en lugar de la foto, sin errores en pantalla |
| Mapa con OpenStreetMap | Latitud y longitud de `GET /api/alojamiento` | `MapaAlojamiento` con Leaflet y los mosaicos de `tile.openstreetmap.org` | La dirección con las coordenadas y el enlace a OpenStreetMap | No tiene propiedad: basta con que el mapa no cargue | 8 segundos (`ESPERA_MAXIMA_MS` en `MapaAlojamiento.tsx`) | Se muestra "El mapa no está disponible en este momento" con la dirección y las coordenadas |

**Funcionar sin internet.** Para la demostración sin red, arranca con la IA deshabilitada, las notificaciones locales y los recordatorios apagados:

```bash
./gradlew bootRun --args='--sga.ia.proveedor=deshabilitado --sga.notificaciones.proveedor=local --sga.recordatorios.activo=false'
```

El sistema funciona completo: se reserva, se paga, se registran llegadas y salidas, se cierran folios y se generan los reportes y su PDF. Los correos (también el código para recuperar la contraseña) se leen en el log, el formulario de novedades muestra "Sugerencia no disponible" y deja elegir la gravedad, las fotos subidas quedan en el disco del servidor (almacén local, el de por defecto), las de internet se reemplazan por el fondo con ondas y el mapa por la dirección con sus coordenadas.

## Funcionalidades opcionales

- **Exportación de reportes a PDF.** En **Administración → Reportes** (`/panel/reportes`), el botón **Descargar PDF** genera `reporte-coral-<desde>-<hasta>.pdf` con el nombre del alojamiento, el rango de fechas y las tres tablas (ocupación, ingresos y reservas por canal) con los mismos datos de la pantalla. Las librerías `jspdf` y `jspdf-autotable` se descargan solo al pulsar el botón.
- **Recordatorios automáticos.** Un día antes de la llegada o de la salida el SGA le escribe al titular (ver [Notificaciones](#notificaciones)). Se encienden o apagan con `sga.recordatorios.activo`.

## Forma de trabajo

Trabajamos sobre `develop` con una rama por integrante (`feature/sara-reservas`, `feature/laura-apartamentos` y `feature/juliana-folio`) y Pull Requests para integrar los cambios. `master` guarda las versiones entregadas.

| Integrante | Módulo |
|---|---|
| Sara | Reservas: creación, ciclo de vida, listados y datos de prueba de reservas |
| Laura | Apartamentos y operación: disponibilidad, cotización, bloqueos, estado operativo y datos del Coral |
| Juliana | Folio, canal externo y transversal: errores, configuración y documentación |