# Pedidos360 / Farmacia Simple — Checklist de evaluación 2 y 3

**Guía de demostración y verificación en línea**  
**Proyecto:** [flecaros84/pedidos360](https://github.com/flecaros84/pedidos360), rama `feature/integracion-final`  
**Base:** los documentos entregados **«Enunciado 2»** y **«Enunciado Evaluación 3»**, conservando el orden de sus apartados y requisitos.  
**Entorno de referencia:** AWS Academy, región `us-east-1`, Microsoft Entra ID y servicios Docker en EC2.  
**Criterio de evidencia:** `CONFIRMADO` = probado con salidas compartidas; `CÓDIGO` = implementado o presente en repositorio, requiere captura de funcionamiento; `PENDIENTE` = por demostrar o aclarar con docente. No confundir existencia del código con funcionamiento validado en AWS.

## 0. Accesos de demostración: aplicación, consolas y terminales

### 0.1. Direcciones online del laboratorio

| Componente | Enlace / acceso | Qué mostrar |
|---|---|---|
| **Frontend — Farmacia Simple** | <https://rqe2am6670.execute-api.us-east-1.amazonaws.com/> | Login, catálogo, carrito, órdenes, pagos, métricas, recomendaciones y visor JWT. **HTTP 200 confirmado**. |
| **Backend — AWS API Gateway** | <https://46lmamgixk.execute-api.us-east-1.amazonaws.com> | Punto de entrada a `/api/...`; sin JWT responde `401` en rutas protegidas. **Confirmado**. |
| **RabbitMQ Management** | <http://13.221.209.111:15672/> | Exchanges, queues, bindings, conexiones y tasas de mensajes. URL del último `terraform output`; puede cambiar al detener/encender la EC2. Acceso restringido por IP. |
| **AWS EC2** | <https://us-east-1.console.aws.amazon.com/ec2/home?region=us-east-1#Instances:> | Estado de instancias, IPs, Security Groups. |
| **AWS API Gateway** | <https://us-east-1.console.aws.amazon.com/apigateway/main/apis?region=us-east-1> | API backend `46lmamgixk`, frontend `rqe2am6670` y WebSocket `rnh8pxz6m2`. |
| **AWS DynamoDB** | <https://us-east-1.console.aws.amazon.com/dynamodbv2/home?region=us-east-1#tables> | Tablas `pedidos360-events`, `pedidos360-ws-tickets`, `pedidos360-ws-connections`. |
| **AWS RDS** | <https://us-east-1.console.aws.amazon.com/rds/home?region=us-east-1#databases:> | PostgreSQL `pedidos360-postgres`. Para ver registros de auditoría se necesita una conexión SQL, no basta el panel de RDS. |
| **AWS Systems Manager** | <https://us-east-1.console.aws.amazon.com/systems-manager/home?region=us-east-1#sessions> | Session Manager para ingresar a EC2 y ejecutar `docker logs` / comandos Kafka. |
| **Microsoft Entra** | <https://entra.microsoft.com/> | Registro de aplicación, autenticación SPA, scopes y OAuth/OIDC. |
| **GitHub Actions** | <https://github.com/flecaros84/pedidos360/actions> | Jobs de tests, construcción de imágenes y validación Terraform. |
| **Postman** | Aplicación de escritorio o web | Prueba `POST /api/notifications/test` con JWT; requests autenticadas a Audit, Orders, etc. |

**RabbitMQ:** el panel público usa HTTP, no HTTPS. Para trabajar con credenciales, es preferible túnel cifrado por SSM, especialmente fuera de una red confiable:

```powershell
aws ssm start-session `
  --target i-0867820c4e9368dbc `
  --document-name AWS-StartPortForwardingSession `
  --parameters "portNumber=15672,localPortNumber=15672"
```

Mientras la sesión esté abierta, visitar **<http://localhost:15672/>**. Las credenciales son las variables privadas `RABBITMQ_USERNAME` y `RABBITMQ_PASSWORD`; **no** se incluyen en este documento. Se requiere AWS CLI, Session Manager Plugin, sesión vigente y permisos SSM. RabbitMQ está instalado **en EC2 mediante Docker**, no en Amazon MQ.

**Kafka:** **no se ha instalado una consola gráfica web**. Se observa con la consola de EC2/SSM y las herramientas CLI del contenedor Kafka. No confundir con Amazon MSK: Kafka también corre en una EC2 propia.

### 0.2. Dónde se encuentra cada servicio para revisar logs

| Instancia / ID | Contenedores a inspeccionar |
|---|---|
| `pedidos360-cart` — `i-038d26e9e13a3ed0b` | `pedidos360-cart`, `pedidos360-auth` |
| `pedidos360-products` — `i-059998c9e4488ada4` | `pedidos360-products`, `pedidos360-orders` |
| `pedidos360-audit` — `i-0c1a1db6713b9e868` | `pedidos360-audit`, `pedidos360-notifications` |
| `pedidos360-dispatch` — `i-0c240c7c0217e2886` | `pedidos360-dispatch`, `pedidos360-payment` |
| `pedidos360-analytics` — `i-0c7318ae02746d1cb` | `pedidos360-analytics` |
| Frontend — `i-0ad4a6c56a65a2c8d` | `pedidos360-frontend` |
| RabbitMQ — `i-0867820c4e9368dbc` | `pedidos360-rabbitmq` |
| Kafka — `i-033f772036b7ac49c` | `pedidos360-kafka` |
| NAT — `i-085279f5a4774c9e0` | Conectividad de subred privada; no aloja un microservicio funcional. |

Para abrir una **terminal online**: AWS Console → **EC2 → Instances → seleccionar instancia → Connect → Session Manager → Connect** (si el agente y permisos están habilitados). Alternativamente, en PowerShell:

```powershell
aws ssm start-session --target i-0c1a1db6713b9e868
```

Dentro de esa EC2, por ejemplo:

```bash
sudo docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
sudo docker logs --since 15m --tail 100 pedidos360-audit
sudo docker logs --since 15m --tail 100 pedidos360-notifications
```

Cambiar el ID y el contenedor según la tabla. Los logs de Docker no aparecen necesariamente en CloudWatch: en este despliegue no se ha demostrado una integración centralizada de logs. **No mostrar secretos, mensajes con datos personales ni JWT completos en las capturas.**

---

# ENUNCIADO 2 — «Pedidos360 – Arquitectura de Microservicios»

## 1. Instrucciones Generales y Contexto

- [ ] **Microsoft Azure IDaaS:** abrir [Entra](https://entra.microsoft.com/) → **App registrations / Registros de aplicaciones** → aplicación Pedidos360 → **Authentication** y **Expose an API**. Mostrar SPA, redirect URI HTTPS y scope `pedidos360.access`. **Estado:** CÓDIGO + demostración de login a capturar.
- [ ] **AWS infraestructura de contenedores y perímetro:** abrir **EC2** y **API Gateway** (enlaces sección 0). Mostrar containers en EC2, API backend `46lmamgixk` y rutas protegidas. **Estado:** infraestructura y backend observados en funcionamiento.
- [ ] **RabbitMQ para mensajería asíncrona:** ingresar al panel Management y mostrar exchange `orders.events`, colas y conexiones. Crear un pedido y completar un pago; comprobar tasas de mensajes. **Estado:** CÓDIGO, evidencia visual pendiente.

## 2. Componentes de la Arquitectura Target

### Frontend (Angular / React)

- [ ] **Login corporativo Azure AD con JWT:** abrir el [frontend](https://rqe2am6670.execute-api.us-east-1.amazonaws.com/), iniciar sesión y entrar a **Mi cuenta → Ver detalles técnicos**. Usar el visor JWT para mostrar `iss`, `aud`, `scp`, `exp` y cabecera. **Captura:** identidad autenticada y claims **sin mostrar token completo**. **Estado:** CÓDIGO, captura pendiente.
- [ ] **Adjuntar JWT a HTTP:** navegador → `F12` → **Network** → solicitar catálogo/carrito → inspeccionar la petición y confirmar que envía encabezado `Authorization: Bearer ...` (ocultar valor). Alternativamente, llamada autenticada desde Postman. **Estado:** CÓDIGO; verificar online.

### Microservicio de Identidad / Login (Tenant Azure IDaaS)

- [ ] Mostrar que **Microsoft Entra ID** emite el token y que el frontend usa OAuth 2.0/OIDC + PKCE (MSAL). El componente `auth-service` es un recurso de apoyo del sistema, **no sustituye al tenant**. Evidencia: registro Entra + inicio de sesión + JWT. **Estado:** CÓDIGO / pendiente captura.

### Microservicio de Productos (Catálogo)

- [ ] Abrir catálogo y consultar un producto y disponibilidad por sucursal; tomar captura de valores anteriores a la compra.
- [ ] Generar un pedido y un **pago aprobado**; comprobar en RabbitMQ el flujo `payment.approved` y la cola `inventory.payment-results`. Reconsultar stock del mismo SKU/sucursal y mostrar el cambio. **Importante:** antes del pago hay reserva; la confirmación descuenta definitivamente. **Estado:** CÓDIGO, comparación antes/después pendiente.
- [ ] Desde EC2 `i-059998c9e4488ada4`, revisar `sudo docker logs --since 15m pedidos360-products`. **No deducir descuento solo porque la cola está vacía**: los consumidores pueden procesar mensajes inmediatamente.

### Microservicio de Carrito / Compras

- [ ] Agregar, modificar o quitar ítems mediante el frontend; mostrar cantidades y cotización. Backend: `/api/cart`, `/api/cart/items`, `/api/cart/quote` con JWT.
- [ ] Tras pago aprobado, comprobar que se reconcilian los productos comprados y el carrito queda actualizado; tras pago rechazado no debe limpiarse. **Estado:** lógica incorporada en rc.2; validar sobre el entorno desplegado.

### Microservicio de Órdenes (`orders-service`)

- [ ] Crear pedido en el frontend, visualizar ID, monto, zona, dirección de flujo y estado `PENDING_PAYMENT`. Evidencia: pantalla **Mis pedidos** y respuesta autenticada de `GET /api/orders`.
- [ ] Después de pago aprobado, mostrar cambio a `PAID` y mensajes `order.paid`. **Estado:** CÓDIGO / pendiente evidencia simultánea.
- [ ] Revisar desde EC2 Products/Orders el contenedor `pedidos360-orders`; el pedido se persiste en PostgreSQL. Mostrar correlación por ID de orden (sin correos privados).

### Microservicio de Notificaciones (`notifications-service`)

- [ ] **Correo automático por eventos:** pagar un pedido correctamente → RabbitMQ cola `notifications.order-paid` → comprobar recepción del email real en la dirección indicada al crear la orden. La cola de errores es `notifications.order-paid.dlq`. **Estado:** envío SMTP confirmado en pruebas anteriores; guardar captura de esta evaluación.
- [ ] **Prueba directa desde Postman (exigencia expresa del enunciado):**
  - Método `POST`.
  - URL `https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/notifications/test`.
  - Authorization → **Bearer Token** (JWT vigente con scope `pedidos360.access`).
  - Header `Content-Type: application/json`.
  - Body raw JSON (reemplazar por un destinatario controlado):

    ```json
    {"recipient":"prueba@example.com","message":"Prueba académica de Notifications"}
    ```

  - Esperado: HTTP `202 Accepted`, JSON con `result: "SENT"`, `eventId` y `sentAt`, **más el correo recibido**. El endpoint requiere `NOTIFICATIONS_ALLOW_TEST=true`; el script de generación lo configura para laboratorio. Mostrar capturas de Postman y cliente de correo. No probar con direcciones ajenas.

### Nuevo Microservicio a Elección

- [ ] **Audit Service — elección propuesta para el enunciado 2:** consume mensajes RabbitMQ desde `audit.all-events` y guarda eventos en PostgreSQL (`audit.audit_events`). Explicar su valor: trazabilidad del pedido, pago y operaciones. **Estado:** CÓDIGO; demostración pendiente.
- [ ] **Prueba online de auditoría:** después de generar/pagar un pedido, usar Postman `GET https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/audit/recent` con JWT. Responde una lista con `event_id`, `event_type`, `source`, `received_at`, ordenada por fecha (máximo 100). Buscar `order.created`, `payment.approved`, `order.paid` y otros efectivamente generados. **No funciona abriendo la URL directamente en el navegador sin Bearer**.
- [ ] **Prueba RabbitMQ de Audit:** panel → **Queues** → `audit.all-events` → verificar consumidores y actividad; panel → **Exchanges** → `orders.events` → bindings del consumidor Audit. Conservar captura junto a respuesta de `/api/audit/recent`.
- [ ] **Prueba SQL de respaldo, opcional:** consultar mediante un cliente PostgreSQL con acceso privado a RDS:

  ```sql
  SELECT event_id, event_type, source, received_at
  FROM audit.audit_events
  ORDER BY received_at DESC
  LIMIT 30;
  ```

  **Nota:** el panel de RDS muestra instancia y métricas, no necesariamente ofrece una herramienta SQL para esta instancia. La consulta debe realizarse por conexión autorizada (por ejemplo, desde la red privada vía SSM). No abrir el puerto 5432 a Internet.
- [ ] **Servicio adicional de valor — Dispatch:** la solución también contiene `dispatch-service`, suscrito a `dispatch.order-paid`, con `GET /api/dispatch`. Puede presentarse como extensión, **pero Audit basta como microservicio elegido** para esta sección.

## 3. Capa Perimetral y Seguridad

- [x] **AWS API Gateway como entrada backend:** `46lmamgixk`; rutas `/api/*` dirigidas al ALB y servicios, con JWT Authorizer. Demostración: AWS → API Gateway → **APIs → Routes / Authorizers / Integrations**.
- [x] **Bloqueo sin JWT:** ya comprobado en el laboratorio:

  ```powershell
  curl.exe -i "https://46lmamgixk.execute-api.us-east-1.amazonaws.com/api/cart/items"
  # Esperado y observado: HTTP 401 Unauthorized.
  ```

- [ ] **Éxito con JWT válido:** obtener token vigente desde el visor JWT de la aplicación (mantenerlo privado), en Postman añadir Bearer a `GET /api/cart` o `GET /api/auth/me`. Esperado HTTP `200`. Tomar captura del código y respuesta **ocultando el token**.
- [x] **CORS / frontend separado (refuerzo arquitectónico):** la nueva SPA está en API HTTPS `rqe2am6670`, el backend en API `46lmamgixk`. Preflight desde el origen nuevo devuelve **204** y los encabezados adecuados; la SPA respondió **200**. Pruebas de noviembre de 2026.
- [ ] **Control adicional:** repetir peticiones con JWT expirado/firma inválida y confirmar `401/403` sin alterar seguridad. No publicar credenciales.

## 4. Entregables Esperados

### 4.1. Enlaces a repositorios separados por componente

- [ ] **Observación importante:** el enunciado solicita *repositorios separados por componente*. La implementación actual usa **un monorepositorio**. No marcar este requisito como cumplido sin aprobación del profesor o sin entregar repos separados. Enlaces a carpetas de código mientras se aclara:
  - [Frontend](https://github.com/flecaros84/pedidos360/tree/feature/integracion-final/frontend)
  - [Products](https://github.com/flecaros84/pedidos360/tree/feature/integracion-final/products-service)
  - [Cart](https://github.com/flecaros84/pedidos360/tree/feature/integracion-final/cart-service)
  - [Orders](https://github.com/flecaros84/pedidos360/tree/feature/integracion-final/orders-service)
  - [Notifications](https://github.com/flecaros84/pedidos360/tree/feature/integracion-final/notifications-service)
  - [Audit — elegido](https://github.com/flecaros84/pedidos360/tree/feature/integracion-final/audit-service)

### 4.2. Evidencias de Funcionamiento (orden del enunciado)

- [ ] **E2-01 — Login:** captura de Microsoft Entra + frontend autenticado + visor de claims JWT.
- [ ] **E2-02 — API Gateway:** captura de `401` sin token y `200` con Bearer JWT; pantalla de JWT Authorizer en AWS.
- [ ] **E2-03 — RabbitMQ + Products:** captura de exchange/cola y stock del mismo SKU antes/después de pagar.
- [ ] **E2-04 — Notifications:** captura de Postman (`202`) y del correo recibido; captura de `notifications.order-paid`.
- [ ] **E2-05 — Microservicio adicional:** respuesta `GET /api/audit/recent` + actividad/binding `audit.all-events`, breve explicación funcional.

---

# ENUNCIADO 3 — «Módulos de Pagos, Analítica con Kafka y Microservicio Libre»

## 1. Contexto de Evolución Arquitectónica: De la Transaccionalidad a la Analítica en Tiempo Real

> Este apartado describe el propósito arquitectónico y no enumera entregables adicionales verificables. Puede justificarse con el diagrama de eventos del laboratorio.

- [ ] **Desacoplamiento operativo/analítico:** explicar que Cart y Products publican eventos de interacción a Kafka `shopping.interactions`, mientras Analytics los consume sin bloquear el flujo de compra.
- [ ] **Detección temprana de fraude:** Payment analiza referencias de tarjeta simuladas mediante HMAC, sin conservar el número real, y registra intentos/alertas en PostgreSQL.
- [ ] **Inteligencia y recomendaciones:** Analytics consume `product.view` y `cart.add`, genera métricas y recomendaciones basadas en categorías y productos vistos. Mostrar actividad con el dashboard.

## 2. Componentes a Desarrollar

### 2.1. Microservicio de Pagos y Antifraude (`ms-pago`)

- [ ] **Gestionar pagos de órdenes:** en el frontend → Mis pedidos → **Pagar pedido** → pago simulado aprobado. Evidencia: estado `PENDING_PAYMENT → PAID`; respuesta de `GET /api/payments/{id}` con JWT; RabbitMQ `payment.approved`.
- [ ] **Pago rechazado:** repetir en un pedido de prueba diferente y mostrar `payment.rejected`; el stock reservado debe liberarse y la orden no quedar `PAID`.
- [ ] **Regla de más de 3 usos de la misma tarjeta en 10 segundos:** crear **cuatro órdenes de prueba diferentes todavía pagables**, lanzar **cuatro intentos simulados con la misma `cardReference`** y **`Idempotency-Key` distinta por intento**, dentro de los diez segundos. Para evitar falsas conclusiones, automatizar con Postman Collection Runner o un test controlado; pulsar manualmente puede superar el plazo. El **cuarto** debe ser bloqueado con `429 Too Many Requests`. No utilizar tarjetas reales. **Estado:** CÓDIGO, prueba real pendiente.
- [ ] **Registrar alerta antifraude:** en PostgreSQL, comprobar registro en `payments.fraud_alerts` (`reason = 'MORE_THAN_3_IN_10_SECONDS'`) y un intento `BLOCKED` en `payments.payment_attempts`:

  ```sql
  SELECT id, reason, created_at
  FROM payments.fraud_alerts
  ORDER BY created_at DESC LIMIT 10;

  SELECT id, order_id, status, created_at
  FROM payments.payment_attempts
  WHERE status = 'BLOCKED'
  ORDER BY created_at DESC LIMIT 10;
  ```

  **Precisión:** el código registra el bloqueo en la base de datos, pero **no se debe asumir un evento `payment.blocked` en Audit/RabbitMQ**: el camino de bloqueo devuelve `429` sin pasar por el publisher de eventos de pago. Si el profesor exige esa evidencia específica en RabbitMQ, sería un ajuste pendiente.
- [ ] **Webpay real, bonus +5 puntos:** existe código de integración Webpay y ruta de retorno, pero **no está acreditada una transacción real del ambiente de integración**. No reclamar el bonus por mostrar solo el botón o un pago simulado. Con credenciales de integración, demostrar ida/vuelta, confirmación del servidor y estado final. El enunciado permite simulación sin bonus.

### 2.2. Pipeline de Analítica y Recomendaciones con Kafka (`ms-analytics-recommendations`)

#### Ingesta de Eventos

- [ ] **Product → Kafka:** abrir varios detalles de producto desde el frontend (las consultas `GET /api/products/{id}` publican `product.view`).
- [ ] **Cart → Kafka:** añadir un producto al carrito (`POST /api/cart/items` publica `cart.add`).
- [ ] **Ver eventos realmente en Kafka:** AWS → **EC2 → instancia Kafka `i-033f772036b7ac49c` → Connect → Session Manager**; dentro ejecutar:

  ```bash
  sudo docker ps
  sudo docker exec pedidos360-kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:9092 --list
  sudo docker exec pedidos360-kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic shopping.interactions \
    --from-beginning --max-messages 5 --timeout-ms 10000
  sudo docker exec pedidos360-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 --describe --group analytics
  ```

  Esperado: tópico `shopping.interactions`, JSON con `type = product.view` o `cart.add`, `eventId`, `sku`, `category`, y grupo consumidor `analytics`. **Si hay pocos eventos**, la lectura con `--max-messages` puede agotar el plazo; eso no es automáticamente un fallo del broker. No capturar `userId` personal sin ocultarlo.

#### Procesamiento y Sugerencias

- [ ] **Analytics consume y consolida eventos:** EC2 `i-0c7318ae02746d1cb` → Session Manager → `sudo docker logs --since 15m --tail 100 pedidos360-analytics`. Comprobar ausencia de errores de Kafka/DynamoDB. Consultar con JWT `GET /api/analytics/metrics` desde Postman.
- [ ] **Recomendaciones dinámicas:** abrir varios productos de una categoría y luego revisar **Recomendaciones para ti**. Usar Postman con JWT en `GET /api/analytics/recommendations`; repetir tras generar nuevos eventos. **Comprobar que cambia realmente**: el código implementa recomendaciones por categoría visitada, no un modelo de IA predictivo. Si el frontend no muestra resultados aunque Analytics tenga eventos, revisar la coherencia del identificador de usuario entre Products y Analytics.

#### Almacenamiento NoSQL en la Nube (+2 puntos extra)

- [ ] **Tabla AWS DynamoDB `pedidos360-events`:** abrir [AWS DynamoDB](https://us-east-1.console.aws.amazon.com/dynamodbv2/home?region=us-east-1#tables) → seleccionar tabla → **Explore table items / Explorar elementos**. Buscar registros `product.view` y `cart.add` con `eventId`, `type`, `userId`, `createdAt` y `payload`. Capturar pantalla tras generar eventos nuevos.
- [ ] **Consulta AWS CLI de solo lectura:**

  ```powershell
  aws dynamodb describe-table --table-name pedidos360-events `
    --region us-east-1 --query "Table.TableStatus" --output text

  aws dynamodb scan --table-name pedidos360-events `
    --region us-east-1 --max-items 5 `
    --query "Items[*].{Evento:eventId.S,Tipo:type.S,Fecha:createdAt.S}" `
    --output table
  ```

  **Acreditación del bonus:** tener la tabla creada **no basta**; demostrar que el consumidor Kafka persiste eventos de interacción recientes.

#### Dashboard con WebSockets para Métricas en Tiempo Real (+3 puntos extra)

- [ ] Abrir frontend y sección **Analítica / Métricas**. Mostrar **tres indicadores** visibles: `Vistas de productos` (`product.view`), `Interacciones de carrito` (`cart.add`) y `Productos visualizados` (`activeProducts`); también existe listado de productos más vistos.
- [ ] Con `F12` → **Network → WS**, observar conexión WebSocket a `wss://rnh8pxz6m2.execute-api.us-east-1.amazonaws.com/lab`, tras obtener ticket por `POST /api/analytics/ws-ticket`. Abrir productos y agregar ítems en otra pestaña; verificar llegada de frames `analytics.metrics` sin recargar la página (el emisor intenta actualizaciones cada ~5 segundos). En este despliegue, **el polling REST es alternativa local**, pero para los **+3 puntos deben demostrarse frames WebSocket reales**.
- [ ] En [API Gateway](https://us-east-1.console.aws.amazon.com/apigateway/main/apis?region=us-east-1), ubicar WebSocket API `rnh8pxz6m2`. En DynamoDB se pueden observar `pedidos360-ws-tickets` (tickets de vida breve, expiran) y `pedidos360-ws-connections` (conexiones activas). Ambas tablas podrían estar vacías después de cerrar el navegador, lo cual **no prueba fallo**.

> **Nota sobre el título «Microservicio Libre»:** el enunciado 3 adjunto **solo desglosa formalmente los componentes Pagos/Antifraude y Pipeline Kafka/Analytics**. No contiene una tercera lista detallada de requisitos para otro microservicio. La solución incluye Audit y Dispatch, demostrables como extensiones, pero no se atribuyen requisitos inexistentes al texto entregado.

---

## 3. Panel de supervisión: RabbitMQ, Kafka, Audit, DynamoDB, RDS y estado

### RabbitMQ: cómo observar la interacción en tiempo real

1. Abrir RabbitMQ Management (preferiblemente por túnel SSM). Pestaña **Overview**: Connections, Channels y tasas generales de mensajes.
2. Pestaña **Exchanges**: abrir `orders.events` y revisar `Bindings` y `Message rates`.
3. Pestaña **Queues and Streams** (o **Queues**): comprobar al menos estas colas, según servicios activos:

   | Cola | Función |
   |---|---|
   | `notifications.order-paid` | Orders → Notifications → correo después del pago. |
   | `notifications.order-paid.dlq` | Fallos de envío tras reintentos. |
   | `inventory.payment-results` | Pago aprobado/rechazado → Products. |
   | `audit.all-events` | Copia de eventos de negocio consumida por Audit. |
   | `dispatch.order-paid` | Pago exitoso → creación de despacho. |

4. Ejecutar una compra de prueba mientras el panel está abierto. Observar picos en `Publish`, `Deliver/Get` y consumidores. **Cola en 0 Ready puede indicar consumo correcto**; para una evidencia robusta complementar con estados en el frontend y Audit.
5. No publicar manualmente eventos falsos ni purgar colas en el entorno de demostración.

### Kafka: terminal online de EC2 y datos

- Kafka usa **un tópico principal**: `shopping.interactions`, no un exchange RabbitMQ.
- En Kafka CLI observar eventos de `product.view` y `cart.add`, y el grupo `analytics`. El consumidor Analytics actualiza la base SQL y DynamoDB.
- Comparar el `eventId`/tipo de un mensaje con un ítem reciente de `pedidos360-events` en DynamoDB. Es la mejor evidencia de flujo de extremo a extremo.

### Auditoría: cómo verificar exactamente

- **Fuente:** `orders.events` en RabbitMQ.
- **Cola:** `audit.all-events`.
- **Destino:** tabla PostgreSQL `audit.audit_events`.
- **Consulta online autenticada:** `GET /api/audit/recent` con Bearer JWT válido.
- **Evidencia recomendable:** misma orden visible en **Mis pedidos**, evento en RabbitMQ y fila reciente en `/api/audit/recent` con el mismo tipo/fecha (y cuando corresponda, ID en cuerpo de evento mediante SQL, sin exponer datos privados).
- **Limitación:** `/api/audit/recent` expone metadatos (`event_id`, `event_type`, `source`, `received_at`), no el JSON completo del evento. El `body` está almacenado en PostgreSQL.

### RDS y PostgreSQL: dónde consultar

- AWS RDS → `pedidos360-postgres` → comprobar `Available`, endpoint y métricas.
- PostgreSQL aloja esquemas funcionales (`orders`, `payments`, `audit`, `analytics`, `dispatch`, `notifications`, etc.); conectarse por un cliente SQL autorizado desde la VPC o mediante túnel SSM, según permisos de laboratorio. No habilitar acceso público solo para tomar capturas.
- Consulta opcional para contar eventos de Audit:

  ```sql
  SELECT event_type, COUNT(*) AS cantidad
  FROM audit.audit_events
  GROUP BY event_type
  ORDER BY cantidad DESC;
  ```

### Estado rápido del laboratorio

```powershell
# Desde la raíz del repositorio y con credenciales AWS Academy vigentes.
.\scripts\lab\lab-status.ps1

# Salidas y URLs vigentes (incluye potenciales cambios de IP de RabbitMQ).
terraform -chdir=terraform/lab output

# Estado de DynamoDB.
aws dynamodb describe-table --table-name pedidos360-events `
  --region us-east-1 --query "Table.TableStatus" --output text
```

---

## 4. Guion sugerido de demostración (orden práctico, después de cubrir la pauta)

1. Abrir pestañas: **frontend**, **RabbitMQ Overview/Queues**, **DynamoDB `pedidos360-events`**, **API Gateway**, **Entra**, **Postman** y una **sesión SSM Kafka**.
2. Iniciar sesión Microsoft y mostrar claims JWT (token completo oculto). Probar `401` sin token y `200` con token.
3. Abrir detalle de producto y añadirlo al carrito. En Kafka observar `product.view` y `cart.add`; en DynamoDB ver el nuevo evento; en Dashboard ver las métricas.
4. Crear una orden, capturar `PENDING_PAYMENT`, aprobar pago, revisar `PAID` y carrito. En RabbitMQ observar `orders.events`, `inventory.payment-results`, `notifications.order-paid`, `audit.all-events`, `dispatch.order-paid`. Verificar stock posterior.
5. Mostrar correo real recibido; repetir prueba directa de Postman `/api/notifications/test` si se necesita evidenciar la exigencia literal.
6. Consultar `/api/audit/recent` (JWT) y `/api/dispatch` (JWT) para demostrar los dos microservicios extra.
7. Demostrar **WebSocket real** en Network → WS con tres métricas y cambios sin actualizar la página.
8. La prueba de cuatro intentos antifraude se hace **en entorno de pruebas y con automatización**, no improvisada durante una presentación: tener preparadas cuatro órdenes y capturas del `429` y `payments.fraud_alerts`.

## 5. Evidencias que conviene capturar para entregar

| Archivo sugerido | Evidencia | Estado |
|---|---|---|
| `E2-01_Entra_JWT.png` | Login Entra + visor de JWT sin token completo | Por capturar |
| `E2-02_Gateway_401_200.png` | Bloqueo sin token y autorización con token | `401` confirmado; `200` por capturar |
| `E2-03_Rabbit_Stock.png` | Exchange/colas y diferencia de stock pre/post pago | Por capturar |
| `E2-04_Postman_Email.png` | Notifications `202` + email | Por capturar |
| `E2-05_Audit.png` | `GET /api/audit/recent` + `audit.all-events` | Por capturar |
| `E3-01_Pagos_Antifraude.png` | Pago, bloqueo cuarto intento y alerta SQL | Por capturar |
| `E3-02_Kafka_Consumer.png` | Tópico `shopping.interactions`, mensajes y grupo `analytics` | Por capturar |
| `E3-03_DynamoDB.png` | Nuevos eventos en `pedidos360-events` | Por capturar |
| `E3-04_WebSockets.png` | Frames `analytics.metrics` + 3 métricas | Por capturar |
| `E3-05_Recomendaciones.png` | Sugerencias que reaccionan a vistas | Por capturar |

**Cierre:** código e infraestructura son una base válida para la demostración, pero el cumplimiento académico debe sustentarse con estas evidencias, no solo con el estado `HTTP 200` de la aplicación. En particular, confirmar la exigencia de repositorios separados del enunciado 2, el bloqueo antifraude real y las bonificaciones Webpay/DynamoDB/WebSocket.

### Opcional: detener el laboratorio al terminar la jornada

```powershell
.\scripts\lab\lab-stop.ps1
```

Esta acción **detiene EC2 y solicita detener RDS**, por lo que la aplicación deja de funcionar hasta volver a iniciar el laboratorio (`lab-start.ps1`). No elimina recursos. **No elimina todos los costos** (EBS, ALB, IP, almacenamiento RDS, etc.); además RDS detenido puede reiniciarse automáticamente después de varios días y la IP pública de RabbitMQ puede cambiar. Ejecutarlo solo si realmente se desea apagar la infraestructura, no simplemente terminar de trabajar.

## Referencias técnicas

- [Código de la solución](https://github.com/flecaros84/pedidos360/tree/feature/integracion-final).
- [Documentación de despliegue y CORS](https://github.com/flecaros84/pedidos360/blob/feature/integracion-final/docs/LAB_CORS_JWT_PAGOS.md).
- [RabbitMQ Management: documentación oficial](https://www.rabbitmq.com/docs/management).
- [AWS Session Manager: acceso a instancias](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-sessions-start.html).
- [AWS DynamoDB: consultar ítems](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/getting-started-step-3.html).
