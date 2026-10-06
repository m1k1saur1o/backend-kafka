# Sistema de notificaciones con Kafka y JWT

Solución de microservicios con Spring Boot que implementa el flujo **autenticación → producción → consumo** de notificaciones sobre un clúster Kafka de tres brokers. Los endpoints HTTP están protegidos con **JWT Bearer** según el modelo OAuth2: un servicio emite los tokens y los demás los validan como *Resource Servers*.

La misma imagen de cada microservicio sirve para Docker Compose y para un despliegue distribuido en instancias EC2. Toda la configuración específica del entorno se entrega mediante variables de entorno.

---

## Contenido

1. [Arquitectura](#arquitectura)
2. [Microservicios](#microservicios)
3. [Flujo completo](#flujo-completo)
4. [Requisitos previos](#requisitos-previos)
5. [Configuración y secretos](#configuración-y-secretos)
6. [Variables de entorno](#variables-de-entorno)
7. [Compilación y pruebas](#compilación-y-pruebas)
8. [Imágenes Docker](#imágenes-docker)
9. [Ejecución con Docker Compose](#ejecución-con-docker-compose)
10. [Endpoints](#endpoints)
11. [Configuración de Kafka](#configuración-de-kafka)
12. [Despliegue en EC2](#despliegue-en-ec2)
13. [Operación: logs, estado y tópico](#operación-logs-estado-y-tópico)
14. [Decisiones técnicas y limitaciones](#decisiones-técnicas-y-limitaciones)

---

## Arquitectura

```
                     ┌────────────────────┐
  POST /auth/login   │    auth-service    │  Valida usuario/contraseña
 ───────────────────▶│      :8080         │  y emite un JWT (HS256)
   ◀── access_token  └────────────────────┘
                               │ (secreto de firma compartido, sin llamadas en runtime)
          Authorization: Bearer <JWT>
 ─────────────┬────────────────┴──────────────────────────┐
              ▼                                           ▼
   ┌────────────────────┐                     ┌────────────────────┐
   │  producer-service  │                     │  consumer-service  │
   │  :8081  (Resource  │                     │  :8082  (Resource  │
   │   Server OAuth2)   │                     │   Server OAuth2)   │
   └─────────┬──────────┘                     └─────────▲──────────┘
             │ JSON (acks=all)                          │ JSON + commit manual
             ▼                                          │
   ┌──────────────────────────────────────────────────────────────┐
   │   Tópico "Notificaciones": 3 particiones, RF 3, min.ISR 2    │
   │   kafka-1  ·  kafka-2  ·  kafka-3   (+ zookeeper-1/2/3)      │
   └──────────────────────────────────────────────────────────────┘
                     ▲
                     └── Kafka UI :8090 (solo 127.0.0.1 por defecto)
```

Cada microservicio es un proyecto Maven independiente, organizado por capas:

```
<servicio>/src/main/java/com/example/<servicio>/
├── config/        # @ConfigurationProperties validadas y beans de Kafka
├── controller/    # Endpoints REST
├── dto/           # Records de request/response y mensajes Kafka
├── exception/     # Excepciones y @RestControllerAdvice (errores JSON)
├── security/      # SecurityFilterChain, JwtEncoder/JwtDecoder, manejo 401/403
└── service/       # Interfaces + impl/ con la lógica de negocio
```

Las carpetas `E3S8 Microservicios/kafka-producer` y `E3S8 Microservicios/kafka-consumer` contienen los proyectos originales, que se usaron solo como referencia y no se modificaron.

## Microservicios

| Servicio | Puerto | Responsabilidad |
|---|---|---|
| `auth-service` | 8080 | Autentica con usuario y contraseña y emite un JWT firmado con `sub`, `roles`, `iat`, `nbf`, `exp`, `iss`, `aud` y `jti`. |
| `producer-service` | 8081 | Endpoint protegido `POST /notificaciones`. Genera `id` (UUID) y `fecha` (ISO-8601 UTC), publica en el tópico `Notificaciones` y espera la confirmación del broker (`acks=all`). |
| `consumer-service` | 8082 | Consume `Notificaciones` con `group.id` configurable, deserializa JSON, registra cada mensaje y hace commit del offset **solo después** de procesarlo. Expone endpoints protegidos para pausar, reanudar y consultar el listener, y para ver los últimos mensajes consumidos. |

### auth-service y OAuth2

`POST /auth/login` cumple el rol de *token endpoint*: recibe credenciales y devuelve una respuesta con el formato de OAuth2 (RFC 6749 §5.1): `access_token`, `token_type` y `expires_in`. El token es un JWT (RFC 7519) que se envía como `Authorization: Bearer <token>` (RFC 6750).

`producer-service` y `consumer-service` se configuran como **OAuth2 Resource Servers** (`spring-boot-starter-oauth2-resource-server`). En cada solicitud validan:

- la firma HS256 con el secreto compartido;
- `exp` y `nbf` (con 60 s de tolerancia de reloj);
- `iss`, que debe coincidir con `JWT_ISSUER`;
- `aud`, que debe contener `JWT_AUDIENCE`;
- los roles del claim `roles`, que se mapean a `ROLE_USER` y `ROLE_ADMIN`.

Un token ausente, mal formado, expirado, con otra firma o con otro issuer o audience recibe **401**. Un token válido sin el rol requerido recibe **403**.

## Flujo completo

1. El cliente envía `POST /auth/login` con `{"username","password"}` y recibe `access_token`.
2. El cliente envía `POST /notificaciones` al productor con `Authorization: Bearer <token>` y `{"usuario":"ana"}`.
3. El productor valida el JWT, genera `id` y `fecha`, publica el JSON en `Notificaciones` y responde **201** con la partición y el offset asignados.
4. El consumidor recibe el mensaje, lo registra en el log y en un historial en memoria, y hace `ack` (commit del offset).
5. El cliente verifica el consumo con `GET /notificaciones/consumidas` o con los logs, y administra el listener con `/kafka-listener/**`.

## Requisitos previos

- Docker 24 o superior y Docker Compose v2.
- Para compilar fuera de Docker: Java 21 (Maven es opcional; cada servicio incluye `./mvnw`).
- Para el script E2E: `curl`, `jq` y `openssl`.
- Unos 4 GB de RAM libres para los 3 Zookeeper, los 3 brokers y los 3 servicios.

## Configuración y secretos

- El repositorio **no contiene secretos**. Cada servicio versiona su configuración en `src/main/resources/<servicio>.properties`, que solo contiene placeholders `${VARIABLE:valor-por-defecto-no-sensible}`. Las variables obligatorias (`AUTH_USERNAME`, `AUTH_PASSWORD`, `JWT_SECRET`, `KAFKA_BOOTSTRAP_SERVERS`) no tienen valor por defecto: si faltan, el servicio **no arranca** y muestra un mensaje claro.
- `application.properties` y `application.yml` están en `.gitignore`. Úsalos solo para overrides locales: copia `<servicio>/application.properties.example` como `<servicio>/application.properties`, y Spring Boot lo cargará desde el directorio de trabajo.
- Docker Compose lee `.env` (ignorado por git). Créalo desde `.env.example`:

  ```bash
  cp .env.example .env
  # Completa AUTH_USERNAME, AUTH_PASSWORD y JWT_SECRET. Para el secreto:
  openssl rand -base64 48
  chmod 600 .env
  ```

- `AUTH_PASSWORD` puede ir en texto plano (se cifra con BCrypt en memoria al arrancar) o ya codificada con el prefijo del algoritmo (`{bcrypt}$2a$10$...`; en `.env` escribe cada `$` como `$$`).
- Las clases de propiedades ocultan los secretos en `toString()`, y la aplicación nunca registra contraseñas ni tokens.

Recomendaciones:

- En AWS, guarda `JWT_SECRET` y `AUTH_PASSWORD` en **SSM Parameter Store (SecureString)** o **Secrets Manager** e inyéctalos al iniciar el contenedor. No los pongas en *user data*, en la AMI ni en el repositorio.
- Usa un `JWT_SECRET` distinto por entorno y rótalo periódicamente. Al rotarlo, los tokens anteriores dejan de ser válidos.
- No publiques Kafka UI ni los puertos de Zookeeper o Kafka a Internet.
- Antes de hacer *push*, revisa con `git status --ignored` que `.env` y `POSTMAN_TEST_LOCAL.txt` aparezcan como ignorados.

## Variables de entorno

### Comunes a los tres servicios

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `JWT_SECRET` | sí | – | Secreto HS256, de al menos 32 caracteres. Debe ser **idéntico** en los tres servicios. |
| `JWT_ISSUER` | no | `sumativa3-auth-service` | Issuer (`iss`) que se emite y se valida. |
| `JWT_AUDIENCE` | no | `notificaciones-api` | Audience (`aud`) que se emite y se valida. |
| `SERVER_PORT` | no | 8080 / 8081 / 8082 | Puerto HTTP del servicio. |

### auth-service

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `AUTH_USERNAME` | sí | – | Usuario habilitado. |
| `AUTH_PASSWORD` | sí | – | Contraseña, en texto plano o como `{bcrypt}...`. |
| `AUTH_ROLES` | no | `USER,ADMIN` | Roles incluidos en el claim `roles`. |
| `JWT_EXPIRATION_SECONDS` | no | `3600` | Vigencia del token, en segundos. |

### producer-service

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | sí | – | Lista `host:puerto` de los brokers. |
| `KAFKA_TOPIC` | no | `Notificaciones` | Tópico de destino. |
| `KAFKA_TOPIC_AUTO_CREATE` | no | `true` | Crea el tópico si no existe, mediante KafkaAdmin. |
| `KAFKA_TOPIC_PARTITIONS` / `KAFKA_TOPIC_REPLICAS` | no | `3` / `3` | Particiones y réplicas usadas al crear el tópico. |
| `KAFKA_SEND_TIMEOUT_MS` | no | `15000` | Espera máxima por la confirmación del broker. Si se supera, responde 503. |
| `KAFKA_MAX_BLOCK_MS`, `KAFKA_REQUEST_TIMEOUT_MS`, `KAFKA_DELIVERY_TIMEOUT_MS` | no | 10000 / 10000 / 15000 | Timeouts del cliente productor. |

### consumer-service

| Variable | Obligatoria | Por defecto | Descripción |
|---|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | sí | – | Lista `host:puerto` de los brokers. |
| `KAFKA_TOPIC` | no | `Notificaciones` | Tópico que se consume. |
| `KAFKA_CONSUMER_GROUP_ID` | no | `notificaciones-consumer` | `group.id` del consumidor. |
| `KAFKA_AUTO_OFFSET_RESET` | no | `earliest` | Política cuando el grupo no tiene offsets confirmados. |
| `KAFKA_LISTENER_CONCURRENCY` | no | `3` | Hilos consumidores; conviene que coincida con el número de particiones. |
| `KAFKA_LISTENER_ID` | no | `notificacionListener` | Id del listener en los endpoints administrativos. |
| `KAFKA_LISTENER_AUTO_STARTUP` | no | `true` | Inicia el listener al arrancar el servicio. |
| `KAFKA_RETRY_ATTEMPTS` / `KAFKA_RETRY_BACKOFF_MS` | no | `3` / `1000` | Reintentos ante errores de procesamiento antes de descartar el mensaje. |
| `CONSUMER_HISTORY_SIZE` | no | `100` | Tamaño del historial en memoria. |

### Seguridad cliente–broker de Kafka (producer y consumer)

| Variable | Por defecto | Ejemplo |
|---|---|---|
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | `SASL_SSL` o `SASL_PLAINTEXT` |
| `KAFKA_SASL_MECHANISM` | `PLAIN` | `SCRAM-SHA-512` |
| `KAFKA_SASL_JAAS_CONFIG` | vacío | `org.apache.kafka.common.security.scram.ScramLoginModule required username="..." password="...";` |
| `SPRING_KAFKA_SSL_TRUST_STORE_LOCATION` / `SPRING_KAFKA_SSL_TRUST_STORE_PASSWORD` | – | `file:/certs/truststore.jks` |

> **Importante:** OAuth2/JWT protege **solo los endpoints HTTP**. La conexión entre los microservicios y los brokers es independiente: con el `docker-compose.yml` incluido usa `PLAINTEXT` dentro de una red privada. Para cifrar y autenticar el tráfico Kafka, habilita SASL_SSL (o SASL_PLAINTEXT) **en los brokers** y define las variables anteriores en los clientes. El JWT HTTP **no** protege Kafka.

### Solo para Docker Compose (`.env`)

| Variable | Por defecto | Descripción |
|---|---|---|
| `KAFKA_EXTERNAL_HOST` | `localhost` | Host anunciado en el listener EXTERNAL (29092/39092/49092). |
| `AUTH_PORT`, `PRODUCER_PORT`, `CONSUMER_PORT` | 8080 / 8081 / 8082 | Puertos publicados en el host. |
| `KAFKA_UI_BIND` | `127.0.0.1` | Interfaz donde se publica Kafka UI (8090). |

## Compilación y pruebas

Cada servicio se compila y se prueba por separado. `mvn clean verify` ejecuta:

- **auth-service** (10 pruebas): emisión y claims del JWT, token expirado, login válido, credenciales inválidas (401), cuerpo incompleto o malformado (400) y contexto.
- **producer-service** (13 pruebas): token válido (201), sin token, token mal formado, otra firma, expirado, otro issuer y otra audience (401), sin roles (403), cuerpo inválido (400), Kafka no disponible (503) y contexto.
- **consumer-service** (10 pruebas): seguridad de los endpoints administrativos (401/403/404), contexto e **integración con Kafka embebido**: consumo JSON, commit del offset tras el ack y descarte de un mensaje corrupto sin bloquear la partición.

```bash
# Con Java 21 instalado (el wrapper descarga Maven si hace falta)
(cd auth-service && ./mvnw clean verify)
(cd producer-service && ./mvnw clean verify)
(cd consumer-service && ./mvnw clean verify)

# Sin Java ni Maven locales, usando un contenedor Maven
for s in auth-service producer-service consumer-service; do
  docker run --rm -v "$PWD/$s":/app -v "$HOME/.m2":/root/.m2 -w /app \
    maven:3.9-eclipse-temurin-21 mvn -B clean verify
done
```

El JAR queda en `<servicio>/target/<servicio>.jar` y se ejecuta con `java -jar`, siempre que estén definidas las variables obligatorias.

## Imágenes Docker

Cada servicio tiene un `Dockerfile` *multi-stage*: una etapa Maven compila y una etapa JRE 21 Alpine ejecuta el JAR con un usuario no root, con `HEALTHCHECK` sobre `/actuator/health`. También incluye un `.dockerignore`.

```bash
docker build -t auth-service:latest     ./auth-service
docker build -t producer-service:latest ./producer-service
docker build -t consumer-service:latest ./consumer-service

# o las tres a la vez
docker compose build
```

## Ejecución con Docker Compose

```bash
cp .env.example .env                 # y completa los valores obligatorios
docker compose up -d --build         # infraestructura + microservicios
docker compose ps                    # espera a que todo esté "healthy"
./scripts/e2e-test.sh                # prueba end-to-end automatizada (19 verificaciones)
```

Orden de arranque, controlado con healthchecks y `depends_on`: Zookeeper ×3 → Kafka ×3 → `kafka-init` (crea el tópico) → Kafka UI, `producer-service` y `consumer-service`. `auth-service` no depende de Kafka.

Para iniciar solo la infraestructura Kafka, por ejemplo para ejecutar los servicios desde el IDE:

```bash
docker compose up -d kafka-init kafka-ui
# Los servicios fuera de Docker usan el listener EXTERNAL:
export KAFKA_BOOTSTRAP_SERVERS=localhost:29092,localhost:39092,localhost:49092
```

Para detener:

```bash
docker compose down        # conserva los volúmenes (datos de Kafka y Zookeeper)
docker compose down -v     # borra también los volúmenes
```

| Componente | URL / puerto en el host |
|---|---|
| auth-service | http://localhost:8080 |
| producer-service | http://localhost:8081 |
| consumer-service | http://localhost:8082 |
| Kafka UI | http://127.0.0.1:8090 |
| Kafka EXTERNAL | `localhost:29092`, `localhost:39092`, `localhost:49092` |

Dentro de la red `kafka-net`, los servicios usan nombres DNS internos (`kafka-1:9092`, `kafka-2:9092`, `kafka-3:9092`) y nunca `localhost`.

## Endpoints

### auth-service

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/auth/login` | pública | Obtiene el token. |
| GET | `/actuator/health` | pública | Salud del servicio. |

```http
POST /auth/login
Content-Type: application/json

{ "username": "<usuario>", "password": "<contraseña>" }
```

```json
200 OK
{ "access_token": "eyJhbGciOiJIUzI1NiIs...", "token_type": "Bearer", "expires_in": 3600 }
```

Claims del token decodificado:

```json
{ "sub": "<usuario>", "roles": ["USER","ADMIN"], "iss": "sumativa3-auth-service",
  "aud": "notificaciones-api", "iat": 1760000000, "nbf": 1760000000, "exp": 1760003600, "jti": "..." }
```

Errores:

```json
401 { "error": "invalid_grant", "error_description": "Usuario o contraseña incorrectos" }
400 { "error": "invalid_request", "error_description": "El cuerpo de la solicitud no es válido",
      "details": { "password": "password es obligatorio" } }
```

### producer-service

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/notificaciones` | Bearer, rol USER o ADMIN | Publica una notificación. |
| GET | `/actuator/health` | pública | Salud del servicio. |

```http
POST /notificaciones
Authorization: Bearer <JWT>
Content-Type: application/json

{ "usuario": "ana" }
```

```json
201 Created
{
  "estado": "PUBLICADA",
  "notificacion": { "id": "1e442572-127c-4bb5-bd33-cab8bac48eb3", "fecha": "2026-10-06T00:48:55.159Z", "usuario": "ana" },
  "topic": "Notificaciones", "particion": 2, "offset": 0
}
```

Mensaje JSON publicado en Kafka (clave = `id`, sin cabeceras de tipo Java):

```json
{ "id": "1e442572-127c-4bb5-bd33-cab8bac48eb3", "fecha": "2026-10-06T00:48:55.159Z", "usuario": "ana" }
```

| Código | Caso |
|---|---|
| 201 | Publicado y confirmado por Kafka. |
| 400 | JSON mal formado o `usuario` vacío o ausente (`details.usuario`). |
| 401 | Sin token, token inválido o expirado, otro issuer u otra audience (`WWW-Authenticate: Bearer ...`). |
| 403 | Token válido sin rol USER ni ADMIN. |
| 415 | `Content-Type` distinto de `application/json`. |
| 503 | Kafka no confirmó la publicación dentro de `KAFKA_SEND_TIMEOUT_MS`. |

### consumer-service

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/notificaciones/consumidas?limite=20` | Bearer, USER o ADMIN | Últimos mensajes procesados por la instancia. |
| GET | `/kafka-listener` | Bearer, USER o ADMIN | Estado de todos los listeners. |
| GET | `/kafka-listener/{id}/estado` | Bearer, USER o ADMIN | Estado de un listener. |
| POST | `/kafka-listener/{id}/pausar` | Bearer, **ADMIN** | Pausa el consumo. |
| POST | `/kafka-listener/{id}/reanudar` | Bearer, **ADMIN** | Reanuda el consumo. |
| GET | `/actuator/health` | pública | Salud del servicio. |

El id del listener por defecto es `notificacionListener`.

```json
GET /kafka-listener/notificacionListener/estado
200 { "listenerId": "notificacionListener", "estado": "ACTIVO", "running": true,
      "pauseRequested": false, "paused": false, "groupId": "notificaciones-consumer",
      "particionesAsignadas": ["Notificaciones-0","Notificaciones-1","Notificaciones-2"] }
```

`estado` puede ser `ACTIVO`, `PAUSANDO` (pausa solicitada, se aplica en el siguiente *poll*), `PAUSADO` o `DETENIDO`. Un id inexistente devuelve 404.

```json
GET /notificaciones/consumidas?limite=1
200 { "totalProcesados": 3,
      "mensajes": [ { "notificacion": { "id": "...", "fecha": "...", "usuario": "carla" },
                      "topic": "Notificaciones", "particion": 1, "offset": 1,
                      "consumidoEn": "2026-10-06T00:48:55.165Z" } ] }
```

## Configuración de Kafka

- **Tópico `Notificaciones`**: 3 particiones, replication factor 3, `min.insync.replicas=2` y retención de 12 h. Lo crea el contenedor `kafka-init` (idempotente, `--if-not-exists`). `producer-service` también lo declara mediante KafkaAdmin; puedes desactivarlo con `KAFKA_TOPIC_AUTO_CREATE=false`. La creación automática de tópicos en el broker está deshabilitada.
- **Productor**: `acks=all`, idempotencia habilitada, `StringSerializer` para la clave y `JsonSerializer` para el valor, sin cabeceras de tipo (el consumidor no depende de clases Java del productor).
- **Consumidor**: `ErrorHandlingDeserializer` envuelve un `JsonDeserializer` con tipo por defecto `NotificacionDTO` y paquetes de confianza restringidos. `enable.auto.commit=false` y `AckMode.MANUAL_IMMEDIATE`: el offset se confirma solo después de procesar el mensaje. Un `DefaultErrorHandler` reintenta los errores de procesamiento y, al agotar los reintentos (o ante un JSON no deserializable), registra el error y confirma el offset para no bloquear la partición.
- **Tolerancia a fallos**: con RF 3 y min ISR 2, el sistema sigue publicando y consumiendo si cae **un** broker (verificado deteniendo `kafka-2`).

### Listeners internos y externos

Cada broker declara dos listeners:

| Listener | Bind | Anunciado (Compose) | Uso |
|---|---|---|---|
| `INTERNAL` | `0.0.0.0:9092` | `kafka-N:9092` | Replicación entre brokers y clientes dentro de la red Docker o la VPC. |
| `EXTERNAL` | `0.0.0.0:29092/39092/49092` | `${KAFKA_EXTERNAL_HOST}:29092/39092/49092` | Clientes fuera de Docker (IDE, otra máquina). |

El cliente se conecta primero a cualquier broker del *bootstrap* y luego a la dirección **anunciada** (`advertised.listeners`) de cada broker. Esa dirección debe ser resoluble y alcanzable desde el cliente; de lo contrario, aparecen errores como `Connection to node -1 (localhost/127.0.0.1:9092) could not be established`.

## Despliegue en EC2

### Topología recomendada

| Instancia | Contenido | Ejemplo de DNS privado |
|---|---|---|
| `kafka-1`, `kafka-2`, `kafka-3` | 1 Zookeeper + 1 broker por instancia | `ip-10-0-1-11.ec2.internal`, `...-12`, `...-13` |
| `auth` | `auth-service` | `ip-10-0-1-21.ec2.internal` |
| `producer` | `producer-service` | `ip-10-0-1-22.ec2.internal` |
| `consumer` | `consumer-service` | `ip-10-0-1-23.ec2.internal` |

Todas las instancias deben estar en la **misma VPC**. Usa el **DNS privado** (o una zona privada de Route 53, por ejemplo `kafka-1.internal`) para comunicar las instancias. Las IP privadas cambian si recreas la instancia y las IP públicas cambian al detenerla y reiniciarla, salvo que uses Elastic IP.

### Security groups

| SG | Regla de entrada | Origen | Motivo |
|---|---|---|---|
| `sg-kafka` | TCP 2181 | `sg-kafka` | Clientes de Zookeeper (brokers). |
| `sg-kafka` | TCP 2888, 3888 | `sg-kafka` | Quórum y elección de líder de Zookeeper. |
| `sg-kafka` | TCP 9092 | `sg-kafka`, `sg-producer`, `sg-consumer` | Listener INTERNAL (replicación y microservicios). |
| `sg-kafka` | TCP 29092 (opcional) | IP del administrador (/32) | Listener EXTERNAL para herramientas fuera de la VPC. |
| `sg-auth` | TCP 8080 | Clientes de la API (IP del administrador o ALB) | Login. |
| `sg-producer` | TCP 8081 | Clientes de la API (IP del administrador o ALB) | Publicación. |
| `sg-consumer` | TCP 8082 | IP del administrador o ALB | Endpoints administrativos. |
| todos | TCP 22 | IP del administrador (/32) | SSH (o mejor, SSM Session Manager sin abrir el 22). |

Reglas de salida: por defecto, todo permitido. **No** abras 2181, 2888, 3888 ni 9092 a `0.0.0.0/0`. Para Kafka UI usa un túnel SSH en lugar de abrir 8090:

```bash
ssh -L 8090:localhost:8090 ec2-user@<ip-publica-kafka-1>
```

### Preparar cada instancia (Amazon Linux 2023)

```bash
sudo dnf install -y docker git && sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user && newgrp docker
```

### Brokers Kafka (una instancia por broker)

Ejemplo para `kafka-1` (cambia `ID`, `SELF` y el puerto externo en cada instancia). La red `host` hace que los puertos y nombres sean los de la propia instancia:

```bash
ID=1
SELF=ip-10-0-1-11.ec2.internal          # DNS privado de ESTA instancia
PUBLIC=ec2-x-x-x-x.compute-1.amazonaws.com   # DNS público (solo si usas EXTERNAL)
ZK=ip-10-0-1-11.ec2.internal,ip-10-0-1-12.ec2.internal,ip-10-0-1-13.ec2.internal
IFS=, read -r Z1 Z2 Z3 <<<"$ZK"

docker run -d --name zookeeper --network host --restart unless-stopped \
  -v zk-data:/var/lib/zookeeper/data -v zk-log:/var/lib/zookeeper/log \
  -e ZOOKEEPER_SERVER_ID=$ID -e ZOOKEEPER_CLIENT_PORT=2181 -e ZOOKEEPER_TICK_TIME=2000 \
  -e ZOOKEEPER_INIT_LIMIT=5 -e ZOOKEEPER_SYNC_LIMIT=2 \
  -e ZOOKEEPER_SERVERS="$Z1:2888:3888;$Z2:2888:3888;$Z3:2888:3888" \
  -e KAFKA_OPTS="-Dzookeeper.4lw.commands.whitelist=ruok,srvr" \
  confluentinc/cp-zookeeper:7.4.4

docker run -d --name kafka --network host --restart unless-stopped \
  -v kafka-data:/var/lib/kafka/data \
  -e KAFKA_BROKER_ID=$ID \
  -e KAFKA_ZOOKEEPER_CONNECT="$Z1:2181,$Z2:2181,$Z3:2181" \
  -e KAFKA_LISTENERS="INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:29092" \
  -e KAFKA_ADVERTISED_LISTENERS="INTERNAL://$SELF:9092,EXTERNAL://$PUBLIC:29092" \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP="INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT" \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=3 -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=3 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=2 -e KAFKA_DEFAULT_REPLICATION_FACTOR=3 \
  -e KAFKA_MIN_INSYNC_REPLICAS=2 -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=false \
  confluentinc/cp-kafka:7.4.4
```

Si no necesitas acceso externo, elimina el listener `EXTERNAL` de las tres variables. Después, crea el tópico desde cualquier broker (ver [Crear o verificar el tópico](#crear-o-verificar-el-tópico-notificaciones)).

### Microservicios (una instancia cada uno)

Construye la imagen en la instancia o súbela a un registro como Amazon ECR:

```bash
# Opción A: construir en la instancia
git clone <url-del-repositorio> app && cd app
docker build -t auth-service:latest ./auth-service

# Opción B: construir localmente y transferir la imagen
docker save auth-service:latest | gzip > auth-service.tar.gz
scp auth-service.tar.gz ec2-user@<ip>:~ && ssh ec2-user@<ip> 'docker load < auth-service.tar.gz'
```

Crea un archivo de entorno **fuera del repositorio**, con permisos restrictivos, o léelo desde SSM:

```bash
# Ejemplo leyendo secretos desde SSM Parameter Store (requiere un rol IAM con ssm:GetParameter)
cat > ~/auth.env <<EOF
AUTH_USERNAME=$(aws ssm get-parameter --name /sumativa3/auth/username --query Parameter.Value --output text)
AUTH_PASSWORD=$(aws ssm get-parameter --name /sumativa3/auth/password --with-decryption --query Parameter.Value --output text)
JWT_SECRET=$(aws ssm get-parameter --name /sumativa3/jwt/secret --with-decryption --query Parameter.Value --output text)
JWT_ISSUER=sumativa3-auth-service
JWT_AUDIENCE=notificaciones-api
EOF
chmod 600 ~/auth.env
```

```bash
# auth
docker run -d --name auth-service --restart unless-stopped -p 8080:8080 \
  --env-file ~/auth.env auth-service:latest

# producer (JWT_SECRET, JWT_ISSUER y JWT_AUDIENCE iguales a los de auth)
docker run -d --name producer-service --restart unless-stopped -p 8081:8081 \
  --env-file ~/producer.env \
  -e KAFKA_BOOTSTRAP_SERVERS=ip-10-0-1-11.ec2.internal:9092,ip-10-0-1-12.ec2.internal:9092,ip-10-0-1-13.ec2.internal:9092 \
  producer-service:latest

# consumer
docker run -d --name consumer-service --restart unless-stopped -p 8082:8082 \
  --env-file ~/consumer.env \
  -e KAFKA_BOOTSTRAP_SERVERS=ip-10-0-1-11.ec2.internal:9092,ip-10-0-1-12.ec2.internal:9092,ip-10-0-1-13.ec2.internal:9092 \
  -e KAFKA_CONSUMER_GROUP_ID=notificaciones-consumer \
  consumer-service:latest
```

Los microservicios usan el listener **INTERNAL** (9092) a través del DNS privado. No hace falta exponer Kafka a Internet. Para probar desde tu equipo, cambia `localhost` por la IP pública o el DNS de cada instancia en Postman, o ejecuta el script E2E:

```bash
AUTH_URL=http://<auth-publica>:8080 PRODUCER_URL=http://<producer-publica>:8081 \
CONSUMER_URL=http://<consumer-publica>:8082 ./scripts/e2e-test.sh
```

Para producción, coloca los servicios detrás de un **Application Load Balancer con HTTPS** (certificado de ACM), de modo que el JWT nunca viaje en texto plano.

## Operación: logs, estado y tópico

### Estado de los contenedores

```bash
docker compose ps                                    # estado + health
docker inspect --format '{{.State.Health.Status}}' producer-service
curl -s http://localhost:8081/actuator/health        # {"status":"UP"}
docker stats --no-stream
```

### Logs

```bash
docker compose logs -f auth-service producer-service consumer-service
docker compose logs --tail=100 consumer-service
docker compose logs consumer-service | grep -E "Mensaje consumido|Acknowledge"
docker logs -f kafka-1          # en EC2: docker logs -f kafka / auth-service / ...
```

### Crear o verificar el tópico `Notificaciones`

```bash
# Verificar (particiones, líderes, réplicas e ISR)
docker exec kafka-1 kafka-topics --bootstrap-server kafka-1:9092 --describe --topic Notificaciones

# Crear manualmente (idempotente)
docker exec kafka-1 kafka-topics --bootstrap-server kafka-1:9092 --create --if-not-exists \
  --topic Notificaciones --partitions 3 --replication-factor 3 \
  --config min.insync.replicas=2 --config retention.ms=43200000

# Listar tópicos y revisar el grupo consumidor (offsets y lag)
docker exec kafka-1 kafka-topics --bootstrap-server kafka-1:9092 --list
docker exec kafka-1 kafka-consumer-groups --bootstrap-server kafka-1:9092 \
  --describe --group notificaciones-consumer

# Leer los mensajes del tópico desde el inicio
docker exec kafka-1 kafka-console-consumer --bootstrap-server kafka-1:9092 \
  --topic Notificaciones --from-beginning --property print.key=true --timeout-ms 5000
```

En EC2, ejecuta los mismos comandos con `docker exec kafka ...` y `--bootstrap-server <dns-privado>:9092`.

## Decisiones técnicas y limitaciones

- **HS256 con secreto compartido.** Es simple de operar en EC2 y no crea una dependencia de red en runtime entre los servicios. Como contrapartida, cualquier servicio con el secreto podría emitir tokens. Si se necesita separar emisión y validación, la evolución natural es firmar con **RS256**, publicar un **JWKS** y configurar los Resource Servers con `issuer-uri` / `jwk-set-uri`. Esto incluye reemplazar `auth-service` por Spring Authorization Server, Keycloak o Amazon Cognito sin cambiar los controladores.
- **Usuario único configurado por entorno.** No hay base de datos de usuarios, *refresh tokens* ni revocación: un token es válido hasta su `exp`. El endpoint de login no aplica *rate limiting*; en producción conviene limitarlo en el ALB o con AWS WAF.
- **Kafka sin SASL por defecto.** El tráfico Kafka viaja en `PLAINTEXT` dentro de la red Docker o la VPC. Los clientes ya admiten SASL_SSL y SASL_PLAINTEXT por variables de entorno, pero los brokers del `docker-compose.yml` no traen SASL configurado.
- **Historial en memoria.** `GET /notificaciones/consumidas` refleja solo lo que procesó esa instancia desde su último arranque. Es una herramienta de verificación, no un almacenamiento.
- **Zookeeper.** Se mantiene la arquitectura de referencia (Confluent 7.4.4 con Zookeeper). En una versión nueva conviene migrar a KRaft.
- **Configuración versionada.** Cada servicio usa `<servicio>.properties` en lugar de `application.properties`, para cumplir la regla de no versionar archivos `application.*` sin perder la configuración por defecto (que solo contiene placeholders). Por la misma regla de `.gitignore`, los `application.properties` de los proyectos de referencia en `E3S8 Microservicios/` tampoco se versionan; solo contienen el nombre y el puerto de la aplicación.
