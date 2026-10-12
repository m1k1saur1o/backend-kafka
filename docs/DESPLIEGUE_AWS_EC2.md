# Despliegue en AWS EC2 con Docker Compose

Guía para desplegar **toda la plataforma integrada** (sumativa3-backend3 + sumativa2) en una
instancia EC2 usando los mismos `docker-compose.yml` del repositorio. No se usa Terraform,
CloudFormation ni scripts de aprovisionamiento: todos los pasos son manuales y reproducibles.

> Para un despliegue distribuido (un broker por instancia) ver la sección *Despliegue en EC2* del
> `README.md`; esta guía cubre el escenario de **una sola instancia**, adecuado para demo/QA.

## 1. Dimensionamiento

| Recurso | Recomendado | Motivo |
|---|---|---|
| Tipo de instancia | `t3.xlarge` (4 vCPU, 16 GiB) — mínimo `t3.large` (8 GiB) + swap | 3 Zookeeper + 3 brokers (heap 256–512 MB c/u) + 7 JVM Spring Boot (~300–450 MB c/u) + Kafka UI. |
| AMI | Amazon Linux 2023 | Docker en repos oficiales, SSM Agent preinstalado. |
| Disco raíz | EBS `gp3` 30 GiB | Imágenes Docker y builds Maven. |
| Datos Kafka/ZK | EBS `gp3` adicional 50 GiB montado en `/var/lib/docker` (o en `/data`) | Retención: `Notificaciones` 12 h y `bff-call-events` 7 días × RF 3 (las 3 réplicas viven en el mismo disco en esta topología). |
| IP | Elastic IP o nombre DNS (Route 53) | `KAFKA_EXTERNAL_HOST` y los clientes necesitan una dirección estable. |
| Rol IAM | `AmazonSSMManagedInstanceCore` (+ `ssm:GetParameter` si usas Parameter Store) | Acceso por Session Manager sin abrir SSH y lectura de secretos. |

Con `t3.large` agrega swap para evitar el OOM killer durante los builds:

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=4096 && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile && echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
```

## 2. Crear y preparar la instancia

1. EC2 → *Launch instance*: AMI Amazon Linux 2023, tipo `t3.xlarge`, par de llaves (o solo SSM),
   VPC/subred pública (o privada detrás de un ALB), security group `sg-sumativa` (sección 5),
   volumen raíz 30 GiB gp3 y un volumen adicional de 50 GiB gp3.
2. Asocia una **Elastic IP** (o crea un registro DNS `api.midominio.cl` en Route 53).
3. Conéctate (`aws ssm start-session --target <instance-id>` o SSH desde tu IP).

### Volumen persistente para Docker (datos de Kafka y Zookeeper)

```bash
lsblk                                     # identifica el disco adicional, p. ej. /dev/nvme1n1
sudo mkfs -t xfs /dev/nvme1n1
sudo mkdir -p /var/lib/docker
echo "UUID=$(sudo blkid -s UUID -o value /dev/nvme1n1) /var/lib/docker xfs defaults,nofail 0 2" | sudo tee -a /etc/fstab
sudo mount -a && df -h /var/lib/docker
```

Así los volúmenes con nombre del compose (`zk1-data`, `kafka1-data`, ...) quedan en EBS y sobreviven
a reinicios y a `docker compose down` (no uses `down -v` en producción). Activa **snapshots de EBS**
(Data Lifecycle Manager) para respaldo.

### Instalar Docker y Docker Compose

```bash
sudo dnf install -y docker git jq
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user && newgrp docker

# Plugin Docker Compose v2 y Buildx (necesario para "docker compose build")
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo curl -sSL "https://github.com/docker/buildx/releases/download/v0.17.1/buildx-v0.17.1.linux-amd64" \
  -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/*
docker compose version && docker buildx version
```

> Usa la variante `arm64` de Buildx si la instancia es Graviton (`t4g`).

## 3. Clonar o copiar el proyecto

```bash
mkdir -p ~/apps && cd ~/apps
git clone <url-repo-sumativa3-backend3> sumativa3-backend3
git clone <url-repo-sumativa2> sumativa2
# Alternativa sin git: scp -r o rsync desde tu equipo (excluyendo .env y target/)
```

Los dos directorios deben quedar **uno al lado del otro** (`~/apps/sumativa3-backend3` y `~/apps/sumativa2`).

**Base de datos:** `ms-backend` necesita MySQL. En AWS lo recomendable es **Amazon RDS for MySQL**
(subred privada, SG que solo acepte 3306 desde `sg-sumativa`). Define en `sumativa2/.env`
`MYSQL_HOST=<endpoint-rds>`, `MYSQL_DATABASE`, `MYSQL_USER` y `MYSQL_PASSWORD`. Si usas un contenedor
MySQL en la misma instancia, publícalo solo en `127.0.0.1:3306` y deja `MYSQL_HOST=host.docker.internal`.

## 4. Crear los archivos `.env`

Los `.env` **nunca se suben al repositorio** (están en `.gitignore`); créalos en la instancia con
permisos `600`. Los valores sensibles se generan ahí o se leen desde **SSM Parameter Store
(SecureString)** / **Secrets Manager**.

```bash
cd ~/apps/sumativa3-backend3 && cp .env.example .env && chmod 600 .env
cd ~/apps/sumativa2          && cp .env.example .env && chmod 600 .env
```

Ejemplo leyendo secretos desde Parameter Store (requiere `ssm:GetParameter` en el rol IAM):

```bash
p() { aws ssm get-parameter --name "$1" --with-decryption --query Parameter.Value --output text; }
JWT_SECRET=$(p /sumativa/jwt/secret)
CLIENT_SECRET=$(p /sumativa/auth/client-secret)
EUREKA_PASSWORD=$(p /sumativa/eureka/password)
for f in ~/apps/sumativa3-backend3/.env ~/apps/sumativa2/.env; do
  sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$JWT_SECRET|; s|^AUTH_CLIENT_SECRET=.*|AUTH_CLIENT_SECRET=$CLIENT_SECRET|; \
          s|^EUREKA_PASSWORD=.*|EUREKA_PASSWORD=$EUREKA_PASSWORD|" "$f"
done
unset JWT_SECRET CLIENT_SECRET EUREKA_PASSWORD
```

Valores que debes revisar en `sumativa3-backend3/.env`:

| Variable | Valor en EC2 |
|---|---|
| `AUTH_USERNAME` / `AUTH_PASSWORD` | Usuario real; la clave preferentemente como `{bcrypt}$$2a$$10$$...` |
| `JWT_SECRET` | `openssl rand -base64 48` (distinto por entorno) |
| `AUTH_CLIENT_SECRET` | `openssl rand -hex 24` |
| `EUREKA_PASSWORD` | `openssl rand -hex 20` |
| `KAFKA_EXTERNAL_HOST` | DNS público / Elastic IP de la instancia (ver sección 6) |
| `JWT_EXPIRATION_SECONDS` | 900–3600 |
| `KAFKA_UI_BIND`, `EUREKA_BIND`, `PRODUCER_BIND` | `127.0.0.1` (no exponer) |

En `sumativa2/.env`: los mismos `JWT_SECRET`, `JWT_ISSUER`, `BFF_JWT_AUDIENCE`, `AUTH_CLIENT_ID`,
`AUTH_CLIENT_SECRET`, `EUREKA_USERNAME`, `EUREKA_PASSWORD`; además MySQL y `MS_BACKEND_REPLICAS`.

## 5. Security Groups: exponer solo lo necesario

`sg-sumativa` (instancia):

| Puerto | Origen | Uso |
|---|---|---|
| 443 (o 18080 sin ALB) | `sg-alb` (o IPs de clientes) | **ms-bff**, único punto de entrada a los datos. |
| 8080 | `sg-alb` (o IPs de clientes) | `auth-service` (`POST /auth/login`). |
| 8082 | IP del administrador /32 | `consumer-service` (consulta de auditoría). Opcional. |
| 29092, 39092, 49092 | IP del administrador /32 | Listener EXTERNAL de Kafka. **Solo si** usas clientes Kafka fuera de la instancia; si no, no los abras y quita `ports:` de los brokers. |
| 22 | IP del administrador /32 | SSH. Mejor: SSM Session Manager y sin regla 22. |

**Nunca** abras a Internet: 2181/2888/3888 (Zookeeper), 9092 (INTERNAL), 8761 (Eureka), 8090
(Kafka UI), 8081 (producer-service), 3306 (MySQL). En el compose ya están sin publicar o ligados a
`127.0.0.1`. Para ver Eureka o Kafka UI usa un túnel:

```bash
aws ssm start-session --target <instance-id> --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["8090"],"localPortNumber":["8090"]}'
# o: ssh -L 8090:127.0.0.1:8090 -L 8761:127.0.0.1:8761 ec2-user@<ip>
```

## 6. `KAFKA_EXTERNAL_HOST`

Los brokers anuncian dos listeners:

- `INTERNAL://kafka-N:9092`: lo usan los microservicios dentro de Docker. No depende de la IP de EC2.
- `EXTERNAL://${KAFKA_EXTERNAL_HOST}:29092|39092|49092`: para herramientas fuera de la instancia.

Define `KAFKA_EXTERNAL_HOST` con un nombre **resoluble y alcanzable por el cliente externo**:
el DNS público de la instancia (`ec2-x-x-x-x.compute-1.amazonaws.com`), la Elastic IP o un registro
de Route 53. Si queda en `localhost`, un cliente remoto recibirá `localhost:29092` en los metadatos y
fallará con `Connection to node ... (localhost/127.0.0.1) could not be established`. Tras cambiarlo:

```bash
cd ~/apps/sumativa3-backend3 && docker compose up -d kafka-1 kafka-2 kafka-3
# Verificación desde tu equipo (con tu IP permitida en el SG):
kafka-topics --bootstrap-server <KAFKA_EXTERNAL_HOST>:29092 --list
```

## 7. Iniciar, detener, logs y healthchecks

```bash
# Iniciar (orden: sumativa3 crea la red sumativa-net y Eureka)
cd ~/apps/sumativa3-backend3 && docker compose up -d --build
cd ~/apps/sumativa2          && docker compose up -d --build

# Estado y healthchecks
docker compose ps                                   # en cada directorio: todo "healthy"
docker inspect --format '{{.State.Health.Status}}' sumativa2-ms-bff
curl -s localhost:18080/actuator/health             # {"status":"UP"}
curl -s localhost:8080/actuator/health
curl -s -u "$EUREKA_USERNAME:$EUREKA_PASSWORD" -H 'Accept: application/json' \
  127.0.0.1:8761/eureka/apps | jq -r '.applications.application[].name'

# Prueba end-to-end completa
cd ~/apps/sumativa3-backend3 && BFF_URL=http://localhost:18080 ./scripts/e2e-integracion.sh

# Logs
docker compose logs -f --tail=100 ms-bff ms-backend          # en sumativa2
docker compose logs -f auth-service producer-service consumer-service   # en sumativa3
docker logs sumativa2-ms-bff 2>&1 | grep AUDITORIA_RESPALDO  # eventos de auditoría no publicados

# Escalar ms-backend
cd ~/apps/sumativa2 && docker compose up -d --scale ms-backend=3

# Detener (conserva volúmenes)
cd ~/apps/sumativa2 && docker compose down
cd ~/apps/sumativa3-backend3 && docker compose down
```

Inicio automático tras reiniciar la instancia: todos los servicios usan `restart: unless-stopped`
y Docker está habilitado con `systemctl enable docker`.

Configura además los logs de Docker con rotación (`/etc/docker/daemon.json`):

```json
{ "log-driver": "json-file", "log-opts": { "max-size": "20m", "max-file": "5" } }
```

o el driver `awslogs` para enviarlos a CloudWatch Logs.

## 8. Qué cambiar para producción

| Tema | Laboratorio (este compose) | Producción |
|---|---|---|
| Tráfico externo | HTTP en 18080/8080 | **HTTPS**: Application Load Balancer con certificado de ACM (listener 443 → 18080 y `/auth/*` → 8080) o un proxy (Nginx/Caddy) con TLS. Redirigir 80 → 443. |
| Kafka | `PLAINTEXT`, sin autenticación | **SASL_SSL** (SCRAM-SHA-512) en los brokers y ACLs por tópico. Los clientes ya lo soportan con `KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM` y `KAFKA_SASL_JAAS_CONFIG`. O usar **Amazon MSK** con IAM/TLS. |
| Kafka/Zookeeper | 3 brokers en una instancia | Un broker por instancia en 3 AZ (ver README) o Amazon MSK. Migrar a KRaft. |
| Secretos | `.env` en disco | SSM Parameter Store / Secrets Manager, rotación periódica, `.env` con `chmod 600`. |
| JWT | HS256 con secreto compartido | RS256/ES256 con JWKS (o Cognito/Keycloak) para que solo auth-service pueda firmar. |
| Eureka | 1 nodo, sin self-preservation | `EUREKA_SELF_PRESERVATION=true` y 2 nodos en *peer replication* (o AWS Cloud Map). |
| Auditoría | best effort + log de respaldo | Si la auditoría es obligatoria: patrón *Transactional Outbox*; logs a CloudWatch con retención. |
| Kafka UI | sin login, 127.0.0.1 | Deshabilitar o configurar autenticación (`AUTH_TYPE`). |
| Imágenes | build en la instancia | Construir en CI, publicar en **Amazon ECR** con tags inmutables y desplegar con `docker compose pull`. |
| Credenciales de prueba | `INSTRUCCIONES_POSTMAN.txt` | Nunca reutilizar el usuario/clave de prueba. |

## 9. Checklist de seguridad

- [ ] `git status --ignored` muestra `.env` como ignorado en ambos repos; ningún secreto en el historial.
- [ ] SG sin 2181, 9092, 8761, 8090, 8081 ni 3306 abiertos a Internet.
- [ ] `JWT_SECRET`, `AUTH_CLIENT_SECRET` y `EUREKA_PASSWORD` distintos a los de desarrollo.
- [ ] HTTPS delante de ms-bff y auth-service; el JWT nunca viaja en texto plano.
- [ ] Kafka con TLS/SASL si los brokers son accesibles desde fuera de la instancia.
- [ ] Snapshots de EBS programados y alarmas de CloudWatch (CPU, memoria, disco, estado de la instancia).
