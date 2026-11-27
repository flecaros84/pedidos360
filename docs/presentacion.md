# Pedidos360 / Farmacia Simple — Guía de presentación

**Parciales 2 y 3 · AWS Academy (`us-east-1`) · Rama `feature/integracion-final`**  
**Objetivo:** encender el laboratorio, mostrar cada componente y comprobar su funcionamiento sin modificar la infraestructura.

> **Lectura rápida:** `[x]` = funcionalidad implementada; las comprobaciones indicadas se realizan durante la presentación. Ya verificamos en AWS el **registro y bloqueo antifraude** y la **recepción de mensajes WebSocket**. Algunas otras evidencias deben mostrarse nuevamente en vivo.

## 0. Preparar y encender el laboratorio

### 0.1. AWS Academy y credenciales (PowerShell de Windows)

1. En **AWS Academy**, abrir el laboratorio y pulsar **Start Lab**.
2. En **AWS Details → AWS CLI → Show**, copiar las **credenciales temporales vigentes**.
3. Desde la carpeta raíz de `pedidos360-git`, abrir el archivo local de credenciales:

```powershell
notepad "$HOME\.aws\credentials"
```

Debe contener el perfil `[default]` y las tres variables `aws_access_key_id`, `aws_secret_access_key` y `aws_session_token`. **No compartirlas ni mostrarlas en capturas.**

```powershell
aws configure set region us-east-1
aws sts get-caller-identity
```

**Cuenta del laboratorio utilizado:** `465737120857`. Verificar que corresponda al laboratorio abierto.

### 0.2. Encender y comprobar

```powershell
.\scripts\lab\lab-start.ps1
.\scripts\lab\lab-status.ps1
```

Esperado: las EC2 en `running` y RDS `available`. **El estado `running` no garantiza por sí solo que los contenedores respondan**; abrir el frontend y verificar el catálogo.

### 0.3. Enlaces principales

| Recurso | Acceso |
|---|---|
| **Aplicación web (React)** | [Abrir frontend](https://rqe2am6670.execute-api.us-east-1.amazonaws.com/) |
| **API backend** | `https://46lmamgixk.execute-api.us-east-1.amazonaws.com` |
| **EC2 / terminales** | [Consola EC2](https://us-east-1.console.aws.amazon.com/ec2/home?region=us-east-1#Instances:) |
| **RabbitMQ Management** | URL indicada por `lab-status.ps1` (su IP puede cambiar) |
| **DynamoDB** | [Tablas DynamoDB](https://us-east-1.console.aws.amazon.com/dynamodbv2/home?region=us-east-1#tables) |
| **RDS PostgreSQL** | [Consola RDS](https://us-east-1.console.aws.amazon.com/rds/home?region=us-east-1#databases:) |
| **API Gateway** | [Consola API Gateway](https://us-east-1.console.aws.amazon.com/apigateway/main/apis?region=us-east-1) |
| **AWS Lambda** | [Funciones Lambda](https://us-east-1.console.aws.amazon.com/lambda/home?region=us-east-1#/functions) |
| **Microsoft Entra ID** | [Consola Entra](https://entra.microsoft.com/) |

## 1. Cómo abrir terminales y consultar registros

**Forma más simple (sin instalar nada):** AWS → **EC2 → Instances** → seleccionar instancia → **Connect → Session Manager → Connect**. Se abre una terminal Linux en el navegador.

| Instancia EC2 | ID | Contenedor(es) |
|---|---|---|
| `pedidos360-kafka` | `i-033f772036b7ac49c` | `pedidos360-kafka` |
| `pedidos360-rabbitmq` | `i-0867820c4e9368dbc` | `pedidos360-rabbitmq` |
| `pedidos360-analytics` | `i-0c7318ae02746d1cb` | `pedidos360-analytics` |
| `pedidos360-audit` | `i-0c1a1db6713b9e868` | `pedidos360-audit`, `pedidos360-notifications` |
| `pedidos360-dispatch` | `i-0c240c7c0217e2886` | `pedidos360-dispatch`, `pedidos360-payment` |
| `pedidos360-products` | `i-059998c9e4488ada4` | `pedidos360-products`, `pedidos360-orders` |
| `pedidos360-cart` | `i-038d26e9e13a3ed0b` | `pedidos360-cart`, `pedidos360-auth` |
| `pedidos360-frontend` | `i-0ad4a6c56a65a2c8d` | `pedidos360-frontend` |

**Alternativa desde PowerShell** (necesita *Session Manager Plugin* instalado):

```powershell
# Ejemplo: terminal de Kafka
aws ssm start-session --target i-033f772036b7ac49c
```

Si Windows dice `SessionManagerPlugin is not found`, comprueba la instalación y, si corresponde, añade su carpeta al PATH de esa ventana:

```powershell
$env:Path += ";C:\Program Files\Amazon\SessionManagerPlugin\bin"
```

**Una vez dentro de Linux**, los comandos habituales son:

```bash
sudo docker ps
sudo docker logs --since 15m --tail 100 pedidos360-analytics
# Cambiar el nombre del contenedor por el que se quiera inspeccionar.
```

> **Importante:** `sudo docker ...` se ejecuta **dentro de la terminal Linux de EC2**, no en PowerShell de Windows.

### 1.1. PostgreSQL con Laragon / HeidiSQL

RDS es **privado**. Para acceder desde Laragon se necesita mantener abierto un túnel SSM; **no abrir el puerto 5432 públicamente**.

**Ventana 1 — PowerShell (dejar abierta):**

```powershell
aws ssm start-session `
  --target i-0c240c7c0217e2886 `
  --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=pedidos360-postgres.ctpxhqpkibne.us-east-1.rds.amazonaws.com,portNumber=5432,localPortNumber=15432"
```

Esperado: `Port 15432 opened ... Waiting for connections...`.

**Ventana 2 — Laragon → Database → HeidiSQL:** crear una conexión **PostgreSQL (TCP/IP)**.

| Campo | Valor |
|---|---|
| Host | `127.0.0.1` |
| Puerto | `15432` |
| Usuario | `pedidos360` |
| Base de datos | `pedidos360` |
| Contraseña | `DATABASE_PASSWORD` del archivo local `scripts\lab\master.env` |

Abrir el archivo privado cuando haga falta:

```powershell
notepad .\scripts\lab\master.env
```

**No mostrar la contraseña en la presentación.** En HeidiSQL, abrir una pestaña **Query**, pegar la consulta SQL y ejecutarla.

> Si aparece `remaining connection slots are reserved ...`, RDS alcanzó el máximo de conexiones. No es un problema de Laragon ni del túnel; cerrar conexiones que no se utilicen y revisar los pools de los microservicios antes de reintentar.

### 1.2. RabbitMQ Management

```powershell
.\scripts\lab\lab-status.ps1
```

Copiar la dirección `RabbitMQ console` (formato `http://IP_PUBLICA:15672`). Se utiliza **HTTP**, con acceso restringido por IP. Usuario y contraseña: `RABBITMQ_USERNAME` y `RABBITMQ_PASSWORD` en `scripts\lab\master.env`.

En el panel usar **Exchanges → `orders.events`** y **Queues →** `notifications.order-paid`, `audit.all-events`, `inventory.payment-results` y `dispatch.order-paid`. Una cola con `Ready = 0` puede significar que el mensaje ya fue consumido: revisar **consumidores, tasas y registros SQL**.

---

# Parcial 2

## 2. Microservicio de Notificaciones (`notifications-service`)

- [x] **Consume eventos** de RabbitMQ y envía correos mediante SMTP.
- [x] **Dispone de una prueba directa desde Postman.**

**Demostración A — correo después del pago**

1. Crear una orden y aprobar el pago desde el frontend.
2. Abrir RabbitMQ → cola `notifications.order-paid` y revisar consumidor/actividad.
3. Mostrar el **correo real recibido** por el destinatario utilizado en la prueba.

**Demostración B — Postman**

| Campo | Valor |
|---|---|
| Método | `POST` |
| URL | `https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/notifications/test` |
| Authorization | **Bearer Token** (JWT de acceso vigente) |
| Header | `Content-Type: application/json` |
| Body → raw → JSON | Ver ejemplo |

```json
{
  "recipient": "correo-de-prueba@ejemplo.com",
  "message": "Prueba de notificaciones de Pedidos360"
}
```

Usar **un correo propio de prueba**. Esperado: **HTTP 202**, respuesta con `result: "SENT"`, `eventId`, `sentAt`, y recepción del email. El JWT de acceso se puede consultar desde **Mi cuenta → Ver detalles técnicos → Ver JWT**; no mostrarlo completo.

**Evidencia:** Postman `202` + email recibido + cola RabbitMQ.

## 3. Microservicio adicional: Auditoría (`audit-service`)

- [x] **Microservicio propio e independiente**, integrado mediante RabbitMQ.
- [x] Consume la cola `audit.all-events` del exchange `orders.events`.
- [x] Guarda eventos en PostgreSQL (`audit.audit_events`).

**Demostración:** crear/pagar una orden → RabbitMQ **Queues → `audit.all-events`** → HeidiSQL:

```sql
SELECT event_type, source, received_at
FROM audit.audit_events
ORDER BY received_at DESC
LIMIT 30;
```

Se pueden encontrar eventos como `order.created`, `payment.approved` y `order.paid` (dependiendo de los generados).

**Alternativa:** Postman con JWT → `GET https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/audit/recent`.

**Evidencia:** eventos SQL recientes + cola/bindings RabbitMQ. **Extra:** también existe `dispatch-service`, que consume `dispatch.order-paid`.

---

# Parcial 3

## 4. Microservicio de Pagos y Antifraude (`payment-service` / `ms-pago`)

- [x] **Pago simulado** aprobado/rechazado, registro en PostgreSQL y eventos RabbitMQ.
- [x] **Regla antifraude:** más de tres usos de una misma referencia de tarjeta en diez segundos → cuarto intento bloqueado.
- [x] **Alerta y bloqueo demostrados en AWS** mediante consultas en HeidiSQL.
- [ ] **Webpay real (+5 puntos):** integración preparada, transacción real no acreditada; **bonus opcional**.

**Demostración del pago:** frontend → crear orden → `PENDING_PAYMENT` → pago simulado aprobado → `PAID`. En RabbitMQ revisar `payment.approved` en `orders.events`.

**Demostración antifraude:** cuatro órdenes diferentes pendientes; intentar pagarlas dentro de **10 segundos**, con la **misma referencia simulada de tarjeta**. Usar claves `Idempotency-Key` distintas. El cuarto intento debe devolver **HTTP 429**. Nunca usar tarjetas reales.

**HeidiSQL — alerta:**

```sql
SELECT id, reason, created_at
FROM payments.fraud_alerts
ORDER BY created_at DESC
LIMIT 10;
```

**HeidiSQL — transacción bloqueada:**

```sql
SELECT id, order_id, status, created_at
FROM payments.payment_attempts
WHERE status = 'BLOCKED'
ORDER BY created_at DESC
LIMIT 10;
```

**Ya comprobado:** `MORE_THAN_3_IN_10_SECONDS` y un pago con estado `BLOCKED` en el mismo instante. **Evidencia:** guardar ambas capturas SQL (y respuesta `429`, si se repite la prueba).

## 5. Kafka: ingesta de eventos (`ms-analytics-recommendations`)

- [x] **Products publica** `product.view` al abrir un detalle de producto.
- [x] **Cart publica** `cart.add` al agregar al carrito.
- [x] **Tópico de negocio:** `shopping.interactions`; consumidor `analytics`.

**Demostración:** abrir detalles de productos y agregar un artículo al carrito. En AWS → EC2 → `pedidos360-kafka` → **Connect → Session Manager**:

```bash
# Listar tópicos
sudo docker exec pedidos360-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# Mostrar hasta 10 eventos del tópico
sudo docker exec pedidos360-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic shopping.interactions \
  --from-beginning --max-messages 10 --timeout-ms 15000

# Ver el grupo consumidor Analytics
sudo docker exec pedidos360-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group analytics
```

**Evidencia:** mensajes JSON `product.view` / `cart.add` y grupo `analytics`. Puede haber tópicos internos adicionales; un único tópico de negocio es suficiente para la pauta.

## 6. Procesamiento y recomendaciones (`analytics-service`)

- [x] **Microservicio propio Spring Boot**, en EC2 `pedidos360-analytics` (puerto interno `8088`).
- [x] Consume Kafka, registra historial y métricas en PostgreSQL.
- [x] Genera recomendaciones por **categoría más consultada**, prefiriendo productos aún no vistos.

**Demostración:**

1. En la web, abrir varias veces productos de una categoría.
2. En HeidiSQL, comprobar el historial y los contadores:

```sql
SELECT name, value
FROM analytics.analytics_metrics;

SELECT user_id, product_sku, category, views, last_view
FROM analytics.product_views
ORDER BY views DESC
LIMIT 20;
```

3. En la web → **Analítica en tiempo real → Recomendaciones para ti → Actualizar recomendaciones**.
4. Mostrar los SKU recomendados y repetir con otra categoría, verificando si cambia la categoría dominante y la recomendación.

**Comprobación por API (Postman + JWT):**

- `GET https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/analytics/metrics`
- `GET https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/analytics/recommendations`

**Importante:** el algoritmo actual se basa en **frecuencia histórica por categoría**, no en una ventana estricta de las consultas más recientes. La lista puede no cambiar inmediatamente si otra categoría mantiene mayor frecuencia.

## 7. DynamoDB: almacenamiento NoSQL (**+2 puntos**)

- [x] `analytics-service` persiste en **Amazon DynamoDB** los eventos Kafka procesados.
- [x] Tabla creada: `pedidos360-events`; campos como `eventId`, `type`, `userId`, `createdAt`, `payload`.
- [ ] **Evidencia a capturar:** comprobar registros reales recientes desde la consola.

**Demostración:** [DynamoDB](https://us-east-1.console.aws.amazon.com/dynamodbv2/home?region=us-east-1#tables) → tabla `pedidos360-events` → **Explore table items → Scan/Run**. Mostrar eventos `product.view` y `cart.add` con fecha y contenido. Generar primero una interacción nueva si es necesario.

**Evidencia:** captura de los ítems persistidos. Crear la tabla sin registros no basta para demostrar el bonus.

## 8. Dashboard WebSocket en tiempo real (**+3 puntos**)

- [x] Dashboard React con **tres métricas**: vistas de productos, interacciones de carrito y productos visualizados (distintos).
- [x] API WebSocket en AWS: `wss://rnh8pxz6m2.execute-api.us-east-1.amazonaws.com/lab`.
- [x] **Mensajes `analytics.metrics` recibidos en Chrome (verificado en AWS).**

**Demostración:**

1. Abrir frontend → **Analítica en tiempo real** (mostrar las tres cifras).
2. Presionar **F12 → Network → Socket** (en otras versiones se llama **WS**).
3. **Ctrl+R** para recargar con Network abierto.
4. Seleccionar la conexión `lab?ticket=...` → pestaña **Messages**.
5. Mostrar mensajes repetidos con `"type":"analytics.metrics"`.
6. En otra pestaña, abrir un producto o agregarlo al carrito; comprobar que la métrica cambia **sin recargar**.

**Evidencia ya obtenida:** conexión WebSocket y mensajes `analytics.metrics` recibidos. Para la demostración final, mostrar además el cambio de contador en vivo.

### 8.1. AWS Lambda: administración de conexiones WebSocket

- [x] **Función desplegada:** `pedidos360-ws-routes` (AWS Lambda, región `us-east-1`).
- [x] **Integración:** API Gateway WebSocket invoca la función al conectar y desconectar clientes, mediante las rutas `$connect` y `$disconnect`.
- [x] **Persistencia auxiliar en DynamoDB:** `pedidos360-ws-tickets` para tickets de conexión y `pedidos360-ws-connections` para conexiones registradas.
- [ ] **Evidencia a mostrar en vivo:** integración API Gateway → Lambda y ejecución reciente en CloudWatch Logs.

**Demostración — mostrar Lambda en AWS:**

1. Abrir [AWS Lambda → Functions](https://us-east-1.console.aws.amazon.com/lambda/home?region=us-east-1#/functions) y seleccionar **`pedidos360-ws-routes`**.
2. En **Code**, mostrar el código de la función y explicar que atiende las solicitudes de conexión/desconexión WebSocket y la validación del acceso. No mostrar valores secretos ni tickets completos.
3. Abrir [API Gateway](https://us-east-1.console.aws.amazon.com/apigateway/main/apis?region=us-east-1), localizar la API **WebSocket** de Pedidos360 y abrir **Routes**. Seleccionar `$connect` y `$disconnect` y mostrar sus integraciones con Lambda.
4. En otra pestaña, abrir el **frontend** autenticado. En Chrome → **F12 → Network → WS/Socket**, recargar con **Ctrl+R** y verificar que se establece la conexión `.../lab?ticket=...`. No compartir el ticket en capturas.
5. Volver a **Lambda → `pedidos360-ws-routes` → Monitor → View CloudWatch logs**. Abrir el flujo de registros más reciente y mostrar que existen invocaciones coincidentes con la prueba. Según cómo estén instrumentados los logs, puede que no se vea una línea específica de `$connect`; la actividad/invocación reciente también sirve como evidencia.
6. Opcional: en [DynamoDB](https://us-east-1.console.aws.amazon.com/dynamodbv2/home?region=us-east-1#tables), abrir `pedidos360-ws-connections` → **Explore table items**, para comprobar las conexiones registradas (pueden desaparecer al desconectar el navegador).

**Comprobación alternativa desde PowerShell** (no modifica recursos):

```powershell
aws lambda get-function-configuration `
  --function-name pedidos360-ws-routes `
  --region us-east-1 `
  --query "{Nombre:FunctionName,Runtime:Runtime,Estado:State,UltimaModificacion:LastModified}" `
  --output table

# Show the most recent log streams for this function.
aws logs describe-log-streams `
  --log-group-name "/aws/lambda/pedidos360-ws-routes" `
  --order-by LastEventTime `
  --descending `
  --limit 5 `
  --region us-east-1
```

**Qué explicar al evaluador:** React se conecta a **API Gateway WebSocket**; **Lambda gestiona el ciclo de vida y la autorización de las conexiones**, y utiliza DynamoDB para registrar su estado. **`analytics-service`**, ejecutándose en EC2, calcula y publica los mensajes `analytics.metrics`; Lambda **no** realiza ese cálculo ni envía las métricas periódicamente.

**Evidencia recomendada:** una captura de la función Lambda, una de la integración `$connect` en API Gateway y otra de las invocaciones/logs recientes, junto con los mensajes WebSocket ya mostrados en el paso 8.

---

## 9. Comprobación adicional: frontend, backend, CORS y JWT

Las dos API son **independientes**: frontend `rqe2am6670` y backend `46lmamgixk`. **Es normal que el API del frontend no tenga CORS configurado**: es el backend el que debe permitir explícitamente al origen del frontend.

En **API Gateway → backend → CORS**, mostrar que `Access-Control-Allow-Origin` incluye el dominio del frontend (y `http://localhost` para desarrollo), sin comodín `*`.

**Pruebas PowerShell:**

```powershell
$front = "https://rqe2am6670.execute-api.us-east-1.amazonaws.com"
$api   = "https://46lmamgixk.execute-api.us-east-1.amazonaws.com"

# Preflight desde el frontend autorizado: esperado HTTP 204 y Allow-Origin=$front.
curl.exe -i -X OPTIONS "$api/api/cart/items" `
  -H "Origin: $front" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: authorization,content-type"

# Sin JWT, API protegida: esperado HTTP 401.
curl.exe -i "$api/api/cart/items"
```

**Opcional:** repetir OPTIONS con `Origin: https://sitio-no-autorizado.example`. No debe aparecer un `Access-Control-Allow-Origin` que autorice ese sitio, aunque la respuesta sea `204`.

---

## 10. Orden rápido para la presentación

| Orden | Mostrar | Evidencia principal |
|---|---|---|
| 1 | AWS EC2 + RDS + aplicación | Instancias encendidas, frontend operativo |
| 2 | RabbitMQ + Notifications | Cola, petición Postman `202`, correo recibido |
| 3 | Audit Service | Cola `audit.all-events`, eventos en HeidiSQL |
| 4 | Pago y antifraude | Pago normal, alerta SQL y `BLOCKED` |
| 5 | Kafka | Tópico `shopping.interactions` y mensajes |
| 6 | Analytics | Métricas, historial SQL y recomendaciones |
| 7 | DynamoDB (**+2**) | Ítems de eventos en `pedidos360-events` |
| 8 | WebSocket (**+3**) | Tres indicadores y mensajes `analytics.metrics` en Network |
| 9 | **AWS Lambda (WebSocket)** | Función `pedidos360-ws-routes`, integración `$connect`/`$disconnect`, ejecución en CloudWatch |
| 10 | CORS / JWT | Backend autoriza origen frontend; `401` sin token |

### Checklist de evidencias antes de presentar

- [x] Antifraude: alerta `MORE_THAN_3_IN_10_SECONDS` registrada en PostgreSQL.
- [x] Antifraude: intento `BLOCKED` registrado en PostgreSQL.
- [x] WebSocket: mensajes `analytics.metrics` recibidos en Chrome.
- [x] Dashboard: tres métricas y recomendaciones visibles.
- [ ] Notifications: Postman `202` y correo recibido en esta demostración.
- [ ] Audit: registro SQL reciente y RabbitMQ.
- [ ] Kafka: mensajes y consumidor mostrados en terminal.
- [ ] Analytics: historial y cambio de recomendaciones mostrado en vivo.
- [ ] DynamoDB: captura de eventos reales para los **+2 puntos**.
- [ ] WebSocket: aumento de alguna métrica en vivo, sin recargar.
- [ ] Lambda: mostrar `pedidos360-ws-routes` en AWS Console.
- [ ] Lambda: mostrar integración de `$connect` y `$disconnect` con API Gateway WebSocket.
- [ ] Lambda: comprobar invocaciones recientes en CloudWatch Logs tras abrir el frontend.
- [ ] CORS: preflight desde origen autorizado y `401` sin JWT.

## 11. Apagar el laboratorio al terminar (ahorrar créditos)

Desde la carpeta `pedidos360-git` en **PowerShell de Windows**:

```powershell
.\scripts\lab\lab-stop.ps1
.\scripts\lab\lab-status.ps1
```

El script **detiene las EC2 y solicita detener RDS**, sin hacer `terraform destroy`. Continúan posibles cargos por EBS, ALB, IP y almacenamiento RDS; además, **RDS puede reiniciarse automáticamente después de siete días**. Para la próxima demostración, volver al **paso 0** y renovar las credenciales temporales de AWS Academy cuando corresponda.

---

**Repositorio:** [Pedidos360 — `feature/integracion-final`](https://github.com/flecaros84/pedidos360/tree/feature/integracion-final).  
**Seguridad de las capturas:** ocultar contraseñas, tokens JWT completos, direcciones personales, datos de clientes y secretos de `master.env`.
