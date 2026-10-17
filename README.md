# DigitalFix — Rabbit Admin

API REST de administración RabbitMQ con Java 21, Spring Boot 4.1.1, Spring AMQP y `RabbitAdmin`. No necesita base de datos ni declara la topología funcional al arrancar.

## Ejecución

Requisitos: Docker Compose, clúster del Paso 1 operativo, vhost `digitalfix` y red externa `digitalfix-messaging`.

```powershell
Copy-Item .env.example .env
# Completar .env con valores propios antes de continuar.
docker compose up -d --build
curl.exe http://127.0.0.1:8085/healthz
docker compose logs --tail 100 rabbit-admin
```

| Variable | Valor / finalidad |
|---|---|
| `RABBITMQ_ADDRESSES` | `rabbitmq1:5672,rabbitmq2:5672` por defecto |
| `RABBITMQ_USERNAME` | Usuario exclusivo del servicio, por ejemplo `digitalfix_rabbit_admin` |
| `RABBITMQ_PASSWORD` | Contraseña del usuario propio |
| `ENTRA_ISSUER` | Issuer exacto del tenant, normalmente `https://login.microsoftonline.com/<tenant>/v2.0` |
| `ENTRA_AUDIENCE` | Audiencia exacta del access token de la API |

Compose fija el vhost `digitalfix` y publica únicamente `127.0.0.1:8085`. La imagen ejecuta Java como usuario sin privilegios. `.env` está excluido de Git y del contexto Docker. No guardar tokens, contraseñas ni capturas con credenciales en evidencias versionadas.

### Usuario del broker

Un administrador de la infraestructura debe crear el usuario propio, sin etiquetas administrativas y sin permisos en otros vhosts. En Management UI: **Admin → Users → Add a user**, luego permisos únicamente para `digitalfix`. Usar esta expresión en **Configure**, **Write** y **Read**:

```text
^(?!(amq\.|rabbitmq\.|cmd\.|q\.cmd\.))[A-Za-z0-9][A-Za-z0-9._-]*$
```

La cuenta puede administrar recursos no protegidos del vhost; no es el administrador inicial ni la cuenta de Notify. La protección se aplica también en la API antes de acceder al broker. El alta de la cuenta es una operación de despliegue, no un cambio en `digitalfix-infra` ni una declaración automática al arrancar.

## Seguridad

Todos los endpoints `/api/rabbit/**` exigen firma JWT válida, issuer y audiencia configurados, vigencia temporal, scope `access_as_user` y rol `Admin` en el claim `roles`. `Cliente` y `Operador` sin `Admin` reciben 403. La aplicación utiliza el descubrimiento/JWKS de Entra mediante OAuth2 Resource Server; requiere conectividad al issuer. No hay perfil de desarrollo que deshabilite la seguridad.

`GET /healthz` es público y verifica que el proceso responde; **no certifica conectividad con RabbitMQ ni Entra**.

## Endpoints

Enviar `Authorization: Bearer <access_token>` y, para cuerpos JSON, `Content-Type: application/json`.

| Método | Ruta | Resultado |
|---|---|---|
| POST | `/api/rabbit/queues` | 201: declaración confirmada |
| GET | `/api/rabbit/queues/{name}` | 200: nombre, mensajes listos y consumidores |
| DELETE | `/api/rabbit/queues/{name}` | 204: elimina solo si está vacía y sin consumidores |
| POST | `/api/rabbit/exchanges` | 201: declaración confirmada |
| GET | `/api/rabbit/exchanges/{name}` | 200: nombre y existencia |
| DELETE | `/api/rabbit/exchanges/{name}` | 204: elimina solo si no tiene bindings |
| POST | `/api/rabbit/bindings` | 201: enlace confirmado |
| DELETE | `/api/rabbit/bindings` | 204: desvinculación confirmada; cuerpo JSON requerido |
| GET | `/healthz` | 200: proceso activo |

Cola (`POST /api/rabbit/queues`):

```json
{"name":"demo.admin.cola","durable":true,"exclusive":false,"autoDelete":false,"type":"classic"}
```

Exchange (`POST /api/rabbit/exchanges`):

```json
{"name":"demo.admin.direct","type":"direct","durable":true,"autoDelete":false}
```

También se admite `type: "topic"`, por ejemplo con nombre `demo.admin.topic`.

Binding (mismo cuerpo para POST y DELETE):

```json
{"queue":"demo.admin.cola","exchange":"demo.admin.direct","routingKey":"demo.creado"}
```

Para topic se permiten patrones como `demo.*` y `demo.#`. En direct los comodines son caracteres literales. Los nombres admiten 1–255 caracteres ASCII alfanuméricos, puntos, guiones y guiones bajos, comenzando por alfanumérico. Las routing keys son segmentos no vacíos separados por puntos, con alfanuméricos, guiones o guiones bajos; `*` y `#` ocupan un segmento completo. No se admiten campos JSON desconocidos ni argumentos arbitrarios.

Ejemplo PowerShell, con un token obtenido por el flujo existente de DigitalFix y almacenado en `$env:ENTRA_ACCESS_TOKEN`:

```powershell
$cabeceras = @{Authorization = "Bearer $env:ENTRA_ACCESS_TOKEN"}
$base = 'http://127.0.0.1:8085/api/rabbit'
Invoke-RestMethod -Method Post -Uri "$base/queues" -Headers $cabeceras -ContentType 'application/json' -Body '{"name":"demo.admin.cola","durable":true,"exclusive":false,"autoDelete":false,"type":"classic"}'
Invoke-RestMethod -Uri "$base/queues/demo.admin.cola" -Headers $cabeceras
Invoke-RestMethod -Method Delete -Uri "$base/queues/demo.admin.cola" -Headers $cabeceras
```

### Errores y protección

Errores de aplicación: `{"status":409,"codigo":"CONFLICTO_RECURSO","mensaje":"..."}`. No se devuelven credenciales ni trazas internas.

| HTTP | Significado |
|---|---|
| 400 | Nombre, JSON, propiedades o routing key inválidos |
| 401 | JWT ausente o inválido |
| 403 | Sin rol/scope, recurso protegido o permiso denegado por el broker |
| 404 | Cola o exchange inexistente |
| 409 | Declaración incompatible, cola ocupada o exchange con bindings |
| 503 | No se pudo confirmar la operación por indisponibilidad/error del broker |

Se bloquean los prefijos completos `cmd.`, `q.cmd.`, `amq.` y `rabbitmq.` en creación, consulta, eliminación y ambos extremos de bindings. Esto protege `cmd.direct`, `cmd.topic`, `cmd.dead.dlx`, las tres colas funcionales y sus DLQ, incluyendo futuras ampliaciones de esos espacios de nombres. El exchange predeterminado de nombre vacío queda excluido por validación.

### Propiedades y límites AMQP

- Solo colas **classic durables, no exclusivas y sin auto-delete**, y exchanges **direct/topic durables sin auto-delete**. Cambiar tipo, durabilidad u otras propiedades incompatibles exige recreación explícita fuera de una declaración normal. La API responde 409 y nunca borra/recrea automáticamente.
- Repetir una declaración compatible devuelve 201: acredita la declaración, no que el recurso fuera nuevo. Los bindings también admiten declaración repetida.
- La consulta pasiva de cola entrega únicamente nombre, mensajes listos y consumidores; no informa tipo ni todos los argumentos. La consulta pasiva de exchange verifica existencia, no devuelve propiedades inventadas. No hay listado global ni inventario en memoria.
- Eliminar un binding con extremos existentes es idempotente (204 incluso si no existía). AMQP no permite demostrar con esta operación que existía anteriormente. Si falta un extremo, se devuelve 404.
- La eliminación de colas exige `ifUnused` e `ifEmpty` en el broker. Para exchanges se usa `exchangeDelete(name, true)` mediante el canal del `RabbitTemplate` del `RabbitAdmin`: su método simplificado no expone `ifUnused` y puede ocultar errores. Las consultas pasivas usan también ese template para distinguir inexistencia de indisponibilidad.
- Un 503 puede representar resultado incierto tras una desconexión: consultar antes de reintentar una eliminación. No hay transacción distribuida entre llamadas REST.
- Dos direcciones permiten reconexión; las colas classic no se replican por estar en un clúster de dos nodos. No se promete alta disponibilidad.

## Pruebas y demostración

Pruebas unitarias y de seguridad, con Java 21 (las dos integraciones se omiten sin variable del broker):

```powershell
.\mvnw.cmd -B test
```

Suite completa en la red Docker, con broker real y `.env` configurado:

```powershell
docker build --target pruebas -t digitalfix-rabbit-admin:pruebas .
python scripts/verificar_integracion.py
```

El script necesita Python 3, el repositorio hermano `digitalfix-infra` (o `--infra <ruta>`) y acceso local a Management API en 15672. Lee credenciales de infraestructura en memoria para comparar la topología funcional antes/después; no modifica sus archivos. Guarda resultados locales ignorados en `evidencias-locales/`. Las operaciones bajo prueba se ejecutan por Spring MVC/Security y AMQP real con el usuario propio. Management API solo observa la topología.

Las pruebas generan claves RSA y JWT exclusivamente en `src/test`; validan firma, issuer, audiencia, expiración, roles y scope usando el decoder real. **Esto no equivale a autenticación real con Entra ID.**

Para la demo con Entra, crear recursos `demo.admin.*` por REST, revisar en Management UI el vhost `digitalfix`, tipo/durabilidad de la cola, tipo del exchange y bindings. Publicar un mensaje temporal, comprobar enrutamiento y que DELETE de cola ocupada devuelve 409. Consumir solo ese mensaje de prueba, eliminar binding, cola y exchange. No purgar colas funcionales. La suite automatizada usa nombres UUID y limpia sus recursos al finalizar.

Resultados y matriz EP3/EP4: [docs/EVIDENCIAS.md](docs/EVIDENCIAS.md).

## Pendientes de despliegue

Validar un access token real de Entra con Admin/scope y rechazo de Cliente/Operador en el tenant; no se dispone de esos tokens en esta entrega. En AWS configurar secretos, resolución/conectividad privada con los dos nodos, salida a Entra y acceso interno autorizado. No abrir 8085 a Internet; para una demo remota usar túnel SSH (`ssh -L 8085:127.0.0.1:8085 usuario@host`) o acceso privado. Evaluar TLS de transporte según la red de despliegue. La prueba SMTP de Notify sigue fuera de este paso.
