# Pedidos360 — Correcciones finales de Kafka y API Gateway

**Rama de trabajo:** `feature/correcciones-kafka-gateway`  
**Rama de destino:** `feature/integracion-final`  
**Región:** AWS Academy `us-east-1`

## Alcance implementado

| ID | Corrección | Solución |
|----|------------|----------|
| R01 | Interfaz gráfica Kafka | Kafbat UI v1.5.1, Docker en la EC2 Kafka existente; acceso únicamente por túnel SSM |
| R02 | El backend API Gateway no debe tener proxy | Rutas HTTP explícitas en backend, sin `ANY /api/{proxy+}`, sin `ANY /{proxy+}` y sin frontend en la API backend |
| R03a | Recomendaciones con Kafka | `shopping.interactions` → outbox SQL → `recommendations.updated` → consumidor que materializa `analytics.user_recommendations` |
| R03b | Antifraude con Kafka y bloqueo de tarjeta | `payments.fraud.requests` → motor Kafka → outbox → `payments.fraud.decisions` → aplicación de decisión → bloqueo persistente por huella HMAC |
| R04 | Auditoría con Kafka | Publicación desde outboxes de órdenes, pagos e inventario al tópico `audit.events`; auditoría consume Kafka y persiste PostgreSQL |

**No se modifica el proxy del frontend.** El API Gateway de frontend conserva `ANY /{proxy+}`.

**Aclaración AWS:** La integración privada entre un HTTP API y ALB/VPC Link debe usar tipo de integración `HTTP_PROXY`, distinto de las rutas comodín; por eso se elimina el proxy de *rutas* del backend pero se conserva el tipo de integración privada. Referencia: https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html

## Arquitectura de eventos

```text
Products / Cart --> shopping.interactions --> AnalyticsConsumer
                                               |
                                               +--> PostgreSQL: metrics / product_views
                                               +--> DynamoDB: pedidos360-events
                                               +--> recommendation_requests (outbox)
                                                      |
                                                      v
                                              recommendations.updated
                                                      |
                                                      v
                                             RecommendationConsumer
                                                      |
                                                      v
                                            analytics.user_recommendations
                                                      |
                                                      v
                                             GET /api/analytics/recommendations

POST /api/payments -> FRAUD_PENDING -> payments.fraud.requests
                                           |
                                           v
                                   FraudRequestConsumer
                                           |
                                           v
                                  PostgreSQL: fraud_decisions
                                           |
                                           v
                                  payments.fraud.decisions
                                           |
                                           v
                                   FraudDecisionConsumer
                                           |
                              +------------+-------------+
                              |                          |
                       CREATED / ALLOW             BLOCKED / REJECT
                              |                          |
                         Pago normal               fraud_alerts
                                                         |
                                                  fraud_blocked_cards

Orders / Payments / Inventory --> RabbitMQ (operaciones, sin cambios)
                    \
                     +--> outboxes independientes --> audit.events --> audit-service
                                                                    --> audit.audit_events
```

### Tópicos de negocio (5)

- `shopping.interactions` — visualización de productos y altas al carrito (ya existente).
- `recommendations.updated` — solicitudes de recomputar y actualizar las recomendaciones materializadas.
- `payments.fraud.requests` — solicita decisión previa al pago.
- `payments.fraud.decisions` — aplica decisiones del motor antifraude a los pagos pendientes.
- `audit.events` — eventos del negocio para auditoría. Audita por Kafka y conserva en RDS.

Todos los tópicos usan **una partición y una réplica** en este laboratorio de un único broker. Se mantiene RabbitMQ para notificaciones, inventario y despachos.

## Antifraude: semántica y seguridad

- Se conserva la regla de cuatro intentos con la misma referencia simulada en 10 segundos.
- El **cuarto intento** marca la huella HMAC de la tarjeta como bloqueada en `payments.fraud_blocked_cards`.
- Los intentos posteriores con la misma referencia se bloquean **indefinidamente**, incluso pasados 10 segundos, mientras exista la fila.
- Solo se envía a Kafka la huella HMAC, **nunca el número ni la referencia original de tarjeta**.
- Ante pérdida de Kafka, la solicitud queda `FRAUD_PENDING` y devuelve **503** tras el tiempo máximo. No reserva inventario ni confirma pago.
- Reintentar con la **misma** `Idempotency-Key`; una nueva llave crea un intento nuevo.
- Los eventos son idempotentes: `fraud_decisions(payment_id)` y estado de pago evitan alertas duplicadas.
- Se conservan `payment.blocked` y `fraud_alerts`; los eventos llegan a Kafka audit desde el outbox.
- Esta regla se aplica a las **referencias de pago SIMULATED**. Webpay real no entrega números de tarjeta ni debe suponerse que el bloqueo cubre tarjetas de Transbank.

## Desarrollo local

Desde la raíz del monorepo:

```powershell
docker compose -f docker-compose.full.yml up --build -d
docker compose -f docker-compose.full.yml ps
```

Kafbat UI local: **http://127.0.0.1:8080**. RabbitMQ: http://127.0.0.1:15672.

Crear una orden, visualizar un producto y pagarlo para generar tráfico real. En Kafbat UI revisar los cinco tópicos, sus mensajes, sus particiones y los grupos `analytics`, `recommendations`, `fraud-engine`, `fraud-actions` y `audit`.

### Consultas SQL de comprobación

```sql
-- Auditoría actual exclusivamente desde Kafka
SELECT event_type,source,received_at
  FROM audit.audit_events WHERE source='kafka'
  ORDER BY received_at DESC LIMIT 30;

-- Huellas permanentemente bloqueadas, sin revelar datos sensibles
SELECT LEFT(card_fingerprint,12) AS fingerprint_prefix,reason,blocked_at
  FROM payments.fraud_blocked_cards ORDER BY blocked_at DESC LIMIT 10;

-- Decisiones originadas en Kafka
SELECT payment_id,decision,reason,created_at,published_at
  FROM payments.fraud_decisions ORDER BY created_at DESC LIMIT 20;

-- Intentos efectivamente bloqueados
SELECT id,status,created_at FROM payments.payment_attempts
 WHERE status='BLOCKED' ORDER BY created_at DESC LIMIT 10;

-- Recomendaciones persistidas por el consumidor Kafka
SELECT user_id,rank,product_sku,score,updated_at
  FROM analytics.user_recommendations ORDER BY updated_at DESC LIMIT 30;
```

**Prueba antifraude:** realizar cuatro pagos simulados con la misma referencia, en órdenes distintas y antes de 10 s. El cuarto debe devolver HTTP 429. Luego esperar más de 10 s y probar otra orden: la tarjeta debe seguir bloqueada. La alerta inicial debe tener `MORE_THAN_3_IN_10_SECONDS`. Un pago con una referencia nueva debe poder aprobarse.

**Prueba de resiliencia:** apagar solo Kafka temporalmente en desarrollo e intentar un pago SIMULATED. El pago debe quedar pendiente y la API devolver 503, **nunca APPROVED**. Reiniciar Kafka, reintentar la operación con la misma llave y recuperar el flujo.

## Despliegue en AWS Academy (sin instancias adicionales)

1. Validar GitHub Actions; fusionar la rama únicamente cuando las pruebas estén verdes.
2. Encender laboratorio, configurar credenciales AWS Academy y revisar identidad.
3. Ejecutar en `terraform/lab` `terraform init`, `terraform plan` y revisar cuidadosamente las acciones antes de `terraform apply`: **no aplicar un plan que destruya RDS, ALB o las EC2 existentes**. Solo se esperan cambios de rutas del backend; el frontend conserva sus rutas.
4. Con las imágenes ya publicadas por CI y un SHA válido, regenerar los archivos env y cargarlos a SSM:
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
   aws sts get-caller-identity
   .\scripts\lab\generate-env.ps1 -MasterEnv .\scripts\lab\master.env -TerraformDir terraform/lab -ImageTag <SHA_DE_CI>
   .\scripts\lab\lab-configure-all.ps1
   ```
5. Desplegar primero broker con UI y después los servicios cambiados:
   ```powershell
   .\scripts\lab\lab-deploy.ps1 -ImageTag <SHA_DE_CI> -Services kafka
   .\scripts\lab\lab-deploy.ps1 -ImageTag <SHA_DE_CI> -Services products,orders,payment,audit,analytics
   ```
6. Esperar que Flyway complete las migraciones `payment V2/V3`, `analytics V2` y revisar contenedores con `sudo docker ps` y `sudo docker logs --tail 100 pedidos360-payment`.
7. Probar frontend, JWT, pagos, notificaciones y despachos, además de las nuevas funciones Kafka.

**Nota:** los scripts de EC2 están en cinco hosts backend compartidos por restricciones del laboratorio. No se crea otra instancia para la UI. Los pools Hikari se limitaron por defecto a cuatro conexiones por microservicio para proteger RDS `db.t3.micro`.

### Kafka UI en AWS, sin puertos públicos

Desde PowerShell en el directorio del repositorio:

```powershell
.\scripts\lab\kafka-ui-tunnel.ps1
```

Mantener la consola abierta y acceder a **http://127.0.0.1:18080**. Requiere Session Manager Plugin y permiso SSM. La EC2 Kafka debe estar encendida; el Security Group **no abre 8080**.

Alternativa manual (el ID puede cambiar si se recrea la instancia):

```powershell
aws ssm start-session `
  --target i-033f772036b7ac49c `
  --document-name AWS-StartPortForwardingSession `
  --parameters "portNumber=8080,localPortNumber=18080" `
  --region us-east-1
```

## Retiro de la cola de auditoría antigua

Después de verificar que los mensajes nuevos llegan a `audit.events` y `audit.audit_events` con `source='kafka'`, retirar la cola durable antigua de RabbitMQ para evitar que acumule mensajes sin consumidores.

Conectarse por **EC2 → pedidos360-rabbitmq → Connect → Session Manager** y ejecutar:

```bash
sudo docker exec pedidos360-rabbitmq rabbitmqctl list_queues name messages consumers
sudo docker exec pedidos360-rabbitmq rabbitmqctl delete_queue audit.all-events
```

**No ejecutar la eliminación antes de verificar Kafka y el backfill de outboxes.** Los eventos ya almacenados en PostgreSQL permanecen; los eventos en los outboxes se pueden reproducir vía Kafka. RabbitMQ continúa atendiendo las demás colas.

## API Gateway y pruebas

En la API **backend** solo hay rutas explícitas como `GET /api/products`, `POST /api/orders`, `POST /api/payments`, `GET /api/audit/recent`. Las rutas Webpay de retorno se limitan a `GET` y `POST` públicos.

```powershell
# HTTP 401 sin JWT, ruta existente y protegida:
curl.exe -i https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/orders

# Ruta no declarada: 404 de API Gateway
curl.exe -i https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/ruta-inexistente
```

Mostrar **API Gateway → API backend → Routes**: no debe haber `ANY /api/{proxy+}` ni catch-all para el frontend. Mostrar después la API **frontend** separada: allí `ANY /{proxy+}` se mantiene.

Ejecutar el verificador existente:

```powershell
python scripts/validate/check_routes.py --frontend <URL_FRONTEND> --api <URL_BACKEND> --origin <ORIGEN_FRONTEND>
```

## Checklist final y recuperación

- [ ] Maven + Docker Compose + Terraform validate aprobados en GitHub Actions.
- [ ] Kafbat UI muestra los cinco tópicos y sus consumidores.
- [ ] GET recomendaciones entrega filas persistidas de `analytics.user_recommendations`.
- [ ] Cuarto pago simulado bloqueado; tarjeta permanece bloqueada después de 10 segundos.
- [ ] Pago con Kafka caído falla cerrado (503, nunca aprobado).
- [ ] Auditoría registra eventos nuevos con `source=kafka` y Rabbit continúa para notificaciones.
- [ ] API Gateway backend solo tiene rutas explícitas; frontend proxy sin cambios.
- [ ] No hay exposición pública de Kafka UI, Kafka 9092, RDS 5432 ni claves secretas.
- [ ] Los contenedores y RDS están sanos; frontend y WebSocket continúan funcionando.

**Rollback de aplicación:** redeplegar SHA anterior por `lab-deploy.ps1 -ImageTag <SHA_ANTERIOR>` para recuperar contenedores; sin embargo, **las nuevas tablas/columnas de Flyway permanecen** y el rollback del antifraude no debe borrar la blocklist sin autorización. Restaurar rutas explícitas de Terraform según plan revisado. No ejecutar `terraform destroy`.

**Límite de ejecución:** GitHub Actions publica las imágenes; el despliegue sobre AWS Academy exige credenciales temporales activas y una sesión de laboratorio con permisos suficientes. Este documento no implica que Terraform ya se haya aplicado en AWS.
