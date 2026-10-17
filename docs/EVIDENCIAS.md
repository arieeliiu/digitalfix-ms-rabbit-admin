# Evidencias del Paso 3 — Rabbit Admin

Fecha: 17 de octubre de 2026. Rama: `feature/rabbitmq-admin`.

## Resultado ejecutado

Suite Docker contra la infraestructura local de dos nodos, vhost `digitalfix`, con usuario propio `digitalfix_rabbit_admin`:

```text
SeguridadHttpTests:    9 pruebas, 0 fallos, 0 errores, 0 omitidas
ReglasServiceTests:   2 pruebas, 0 fallos, 0 errores, 0 omitidas
IntegracionRabbitTests: 2 pruebas, 0 fallos, 0 errores, 0 omitidas
Total:              13 pruebas, 0 fallos, 0 errores, 0 omitidas
BUILD SUCCESS
Topología funcional de Notify/Report sin cambios
```

Los tests de integración utilizan MockMvc con la cadena real de seguridad y el servicio real conectado al broker. No representan un inicio de sesión Entra ni una prueba de failover entre nodos.

Además se construyó y arrancó la imagen de producción con `docker compose up -d --build`. Verificación por HTTP sobre el contenedor:

| Solicitud | Resultado real |
|---|---|
| GET `/healthz` | 200, estado activo |
| GET `/api/rabbit/queues/demo` sin JWT | 401, `NO_AUTENTICADO` |
| GET `/api/rabbit/queues/demo` con JWT inválido | 401, `NO_AUTENTICADO` |

## Matriz de funcionalidades para EP3/EP4

| Funcionalidad | Evidencia reproducible | Estado |
|---|---|---|
| Crear/consultar/eliminar cola | Ciclo REST/AMQP con recurso UUID | Verificado en broker real |
| Crear/eliminar direct y topic | Ciclo para ambos tipos | Verificado en broker real |
| Crear/eliminar binding | Publicación confirmada llega antes de unbind y no llega después | Verificado en broker real |
| Declaración incompatible | Declarar topic sobre direct devuelve 409 | Verificado en broker real |
| Borrado seguro | Cola con mensaje y exchange enlazado rechazan borrado con 409 | Verificado en broker real |
| Recurso inexistente | Consultas tras borrar devuelven 404 | Verificado en broker real |
| Validación de entrada | JSON desconocido, nombre vacío/largo y propiedades no admitidas → 400 | Automatizado |
| Recursos protegidos | Prefijos funcionales/sistema rechazados antes de llamar al broker | Unitario y solicitudes de integración |
| JWT y autorización | Ausente, inválido, firma/issuer/audiencia incorrectos, expirado, roles Cliente/Operador, Admin sin scope | Automatizado, JWT de pruebas |
| Admin autorizado | JWT firmado con Admin + scope permite operaciones | Automatizado, JWT de pruebas |
| Broker no disponible | Conexión real rechazada en puerto local 1 → 503; mapeo HTTP probado | Automatizado |
| Conservación de Notify/Report | Comparación de colas, exchanges y bindings funcionales antes/después | Sin cambios de topología |
| Docker y acceso local | Imagen de producción arranca; health 200 y API 401 sin token | HTTP real |
| Entra real y AWS | Requiere token real y entorno de despliegue | Pendiente |

La comparación de topología no mide entrega SMTP ni constituye una prueba completa de Notify. Ninguna prueba consume, publica ni purga mensajes funcionales; el usuario propio tampoco tiene permisos sobre esos recursos. Se eliminaron únicamente recursos temporales de la suite. No se modificaron repositorios vecinos ni volúmenes Docker.

## Reproducción y revisión

Ejecutar los comandos de pruebas del README. El script guarda el log completo y la instantánea de topología en `evidencias-locales/` (ignorados en Git). Para la presentación, obtener capturas propias de las solicitudes autenticadas y de los recursos temporales en Management UI, ocultando tokens y credenciales. No se presentan capturas ni autenticación real que no se hayan realizado.

La entrega se publica como commit en la rama; el usuario crea el PR y decide el merge.
