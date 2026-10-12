# Evento de auditoría `BffCallEvent` v1

Cada solicitud que recibe `ms-bff` en `/api/**` genera **un** evento de auditoría, también cuando
termina en error (400, 401, 403, 404, 500, 503). `/actuator/health` no se audita.

| Ítem | Valor |
|---|---|
| Tópico | `bff-call-events` (`BFF_EVENTS_TOPIC`) |
| Particiones / RF / `min.insync.replicas` | 3 / 3 / 2 |
| Retención | 7 días (`BFF_EVENTS_RETENTION_MS=604800000`) |
| Clave del mensaje | `eventId` |
| Formato | JSON UTF-8, sin cabeceras de tipo Java (`spring.json.add.type.headers=false`) |
| Productor HTTP | `POST http://producer-service/auditoria/eventos` (rol `AUDIT_PUBLISHER`) |
| Consumer group | `bff-auditoria-consumer` (`BFF_EVENTS_CONSUMER_GROUP_ID`) |
| Esquema JSON | [`bff-call-event.v1.schema.json`](bff-call-event.v1.schema.json) |

## Clases (DTO)

| Servicio | Clase | Rol |
|---|---|---|
| ms-bff | `cl.duoc.sumativa2.bff.auditoria.BffCallEvent` | Construye el evento (`AuditoriaFilter`). |
| producer-service | `com.example.producer_service.dto.BffCallEventDTO` | Valida (Bean Validation) y publica sin modificar. |
| consumer-service | `com.example.consumer_service.dto.BffCallEventDTO` | Deserializa (`ignoreUnknown`), deduplica y registra. |

Es un contrato **distinto** de `NotificacionDTO` (`id`, `fecha`, `usuario`), que no puede representar
una llamada HTTP. Los dos flujos no se mezclan: tópicos, endpoints, DTOs, consumer groups, listeners y
circuit breakers son independientes.

## Ejemplo

```json
{
  "schemaVersion": "1.0",
  "eventType": "BFF_HTTP_CALL",
  "eventId": "754bfb13-0343-4295-985b-39fb9b7386b4",
  "timestamp": "2026-10-11T23:24:32.294116549Z",
  "requestId": "e2e-404-1791761212",
  "sourceService": "ms-bff",
  "sourceInstance": "ms-bff:172.22.0.8:8080",
  "method": "GET",
  "path": "/api/cajeros/cuentas/99999",
  "routePattern": "/api/cajeros/cuentas/{cuentaId}",
  "endpoint": "cajeros.cuentas",
  "channel": "cajeros",
  "user": "admin",
  "authenticated": true,
  "status": 404,
  "outcome": "CLIENT_ERROR",
  "durationMs": 23,
  "errorCode": "DATOS_NO_ENCONTRADOS",
  "errorMessage": "No se encontraron registros de cuentas anuales para la cuenta 99999"
}
```

## Campos

| Campo | Tipo | Obligatorio | Descripción |
|---|---|---|---|
| `schemaVersion` | string | sí | `"1.0"`. Ver versionado. |
| `eventType` | string | sí | Siempre `BFF_HTTP_CALL`. |
| `eventId` | string (UUID) | sí | Único por evento. Clave Kafka y de deduplicación. |
| `timestamp` | string ISO-8601 UTC | sí | Instante en que el BFF recibió la solicitud. |
| `requestId` | string | sí | Correlación. Se acepta `X-Request-Id` del cliente si cumple `[A-Za-z0-9._-]{8,64}`; si no, se genera un UUID. Se devuelve en la respuesta y se propaga a `ms-backend` y `producer-service`. |
| `sourceService` / `sourceInstance` | string | sí / no | `ms-bff` y su instance-id de Eureka. |
| `method` | string | sí | Método HTTP. |
| `path` | string | sí | Ruta solicitada, **sin query string**. |
| `routePattern` | string \| null | no | Patrón del controlador; `null` si la solicitud no llegó a uno (401/403). |
| `endpoint` | string | sí | Endpoint lógico `canal.recurso` (`movil.cuentas`, `cajeros.intereses`...). |
| `channel` | string | sí | `movil`, `cajeros`, `web` o `desconocido`. |
| `user` | string \| null | no | `sub` del JWT **ya validado**. `null` en 401 (nunca se toma de un token no verificado). |
| `authenticated` | boolean | sí | `true` si el JWT fue válido (incluye los 403). |
| `status` | int | sí | Status HTTP final de la respuesta. |
| `outcome` | string | sí | `SUCCESS` (<400), `CLIENT_ERROR` (4xx), `SERVER_ERROR` (5xx). |
| `durationMs` | long | sí | Duración total en el BFF (incluye seguridad y llamada a ms-backend). |
| `errorCode` / `errorMessage` | string \| null | no | Solo con status ≥ 400. Mensaje = el mismo que recibe el cliente (máx. 500 caracteres). |

### Códigos de error

| `errorCode` | Status | Origen |
|---|---|---|
| `TOKEN_AUSENTE` | 401 | Sin cabecera `Authorization`. |
| `TOKEN_INVALIDO` | 401 | Firma, issuer, audience, expiración o formato inválidos. |
| `ACCESO_DENEGADO` | 403 | JWT válido sin rol `USER` ni `ADMIN`. |
| `PARAMETRO_INVALIDO` | 400 | Identificador rechazado por ms-backend. |
| `DATOS_NO_ENCONTRADOS` | 404 | ms-backend sin registros. |
| `RUTA_NO_ENCONTRADA` / `METODO_NO_PERMITIDO` | 404 / 405 | Ruta o método inexistente bajo `/api/**`. |
| `SERVICIO_DATOS_NO_DISPONIBLE` | 503 | ms-backend caído, sin instancias, timeout, 5xx o circuito abierto. |
| `ERROR_INTERNO` | 500 | Error inesperado del BFF. |

## Datos que nunca se registran

Cabecera `Authorization`, JWT (del usuario o de servicio), contraseñas, `client_secret`, cuerpos,
query string y cabeceras en general. Como defensa adicional, `path` y `errorMessage` se sanean:
cualquier fragmento con forma de JWT (`eyJ...`) o `Bearer <valor>` se reemplaza por `[REDACTED]`.
Hay pruebas que lo verifican (`AuditoriaBffTest`).

## Versionado

- `1.x`: cambios **compatibles** (solo agregar campos opcionales). El consumidor ignora campos
  desconocidos (`@JsonIgnoreProperties(ignoreUnknown = true)`) y acepta cualquier `1.x`.
- `2.0`: cambios incompatibles (renombrar, quitar o cambiar el tipo de un campo). Requiere un
  `eventType` o tópico nuevo y desplegar consumidores antes que productores.
- `producer-service` rechaza con 400 una versión distinta de `1.x`; `consumer-service` descarta y
  confirma (sin reintentos) un evento con versión no soportada o sin `eventId`.

## Flujo y garantías

```
cliente ──HTTP──▶ ms-bff ──(asíncrono, token client_credentials)──▶ producer-service ──acks=all──▶ bff-call-events
                    │ AuditoriaFilter (antes de Spring Security)                                         │
                    └─ responde al cliente sin esperar la auditoría                    consumer-service ◀┘
                                                                                      (ack manual, dedupe)
```

1. `AuditoriaFilter` (orden `HIGHEST_PRECEDENCE+10`, antes de Spring Security) mide la solicitud y,
   al terminar, entrega el evento a `ProducerAuditoriaPublisher`.
2. El publicador lo pone en una cola acotada (`AUDIT_QUEUE_CAPACITY`, 1000) atendida por
   `AUDIT_THREADS` (2) hilos: **la respuesta al cliente no espera** a producer-service ni a Kafka.
3. El hilo obtiene (y cachea) un token de servicio en `POST http://auth-service/auth/token` y llama
   `POST http://producer-service/auditoria/eventos`, ambos por Eureka + LoadBalancer.
4. `producer-service` valida y publica con `acks=all`, idempotencia, `@Retry` y `@CircuitBreaker`
   (instancia `kafkaAuditoria`, independiente de la de notificaciones).
5. `consumer-service` consume en tiempo real con `AckMode.MANUAL_IMMEDIATE`: confirma el offset solo
   después de registrar el evento. Duplicados por `eventId` se confirman sin reprocesar.

### ¿Qué pasa si Kafka (o producer-service) no está disponible?

**Decisión: auditoría *best effort* que nunca degrada la API.** La respuesta al cliente es la misma
con o sin auditoría disponible.

| Situación | Comportamiento |
|---|---|
| Kafka caído | `producer-service` reintenta y responde 503; su circuit breaker se abre y responde 503 de inmediato. |
| `producer-service` / `auth-service` caído o 5xx | El circuit breaker `auditoria` del BFF se abre (≥ 50 % de fallos en 10 llamadas) y deja de intentar durante 30 s. |
| Cola llena | El evento se descarta sin bloquear. |
| Evento no publicado (cualquier causa) | Se escribe **completo** en el log del BFF con `WARN AUDITORIA_RESPALDO` (sin datos sensibles) y se cuenta en `bff.auditoria.eventos{resultado=fallido|descartado}`. |
| BFF se detiene | Intenta vaciar la cola durante 5 s. |

Garantía resultante: *at-most-once* hacia Kafka mientras el BFF está sano, más un respaldo en log
para los eventos perdidos. En el lado consumidor la entrega es *at-least-once* (commit manual) con
deduplicación por `eventId`, por lo que el efecto es *exactly-once* dentro de la ventana de
deduplicación.

**Evolución para auditoría obligatoria:** si una llamada no debe responderse sin quedar auditada,
usar el patrón *Transactional Outbox* (tabla local + relay a Kafka) o publicar desde el BFF con un
productor Kafka propio con buffer persistente. Se descartó aquí porque el BFF no tiene base de datos
propia y el requisito es no bloquear la respuesta.

## Consultar los eventos procesados

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8082/auditoria/eventos?limite=20&requestId=<id>&canal=movil&status=404"
```

Respuesta: `totalProcesados`, `duplicadosDescartados`, `porCanal`, `porResultado` y `eventos[]`
(cada uno con `evento`, `topic`, `particion`, `offset`, `consumidoEn`). Es un historial en memoria
de la instancia (últimos `AUDIT_HISTORY_SIZE` = 200), pensado para verificación.
