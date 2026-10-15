# Farmacia Simple — Etapas 4, 5 y 7 — v0.5.0-rc.1

> **Estado: ENTREGA DE CÓDIGO PARA REVISIÓN Y PRUEBAS LOCALES. NO DESPLEGADA en AWS.**  
> Código basado en los archivos de `FarmaciaSimple_Etapas2y3_v0.4.0-rc.1.zip` y la corrección de tests Cart.  
> El ZIP **contiene archivos nuevos y modificados**, no reemplaza el repositorio completo. Aplicar sobre `main` actualizado.

## 1. Decisiones funcionales

- Crear una orden desde el carrito cotizado, con opción `CHEAPEST` o `FASTEST`, zona de entrega y **correo de notificación ingresado por el cliente**, incluso si es distinto al de Microsoft Entra ID.
- Orders solicita **nuevamente** `GET /api/cart/quote` con las credenciales de la sesión; no confía en precios ni sucursales proporcionados por el navegador.
- Persistir la orden y su cotización detallada (snapshot) en PostgreSQL: estado `PENDING_PAYMENT`, sin cobro, sin reserva y sin descuento de stock.
- Registrar **en la misma transacción** un evento `order.created` en la tabla `orders.event_outbox`. Publicador periódico envía a RabbitMQ y marca publicado tras publisher confirm.
- Exchange durable `orders.events` (`direct`), routing key `order.created`, cola durable `notifications.order-created`.
- Notifications envía email SMTP y almacena el ID del evento enviado en `notifications.sent_notifications` para omitir redeliveries observados.
- Tras tres intentos fallidos de correo, RabbitMQ rechaza el evento hacia la DLQ `notifications.order-created.dlq` por `orders.dlx`.
- **Sin PayPal, checkout, stock reservado, admin REST de RabbitMQ ni clúster de dos nodos** en esta entrega. El dashboard RabbitMQ Management es suficiente para observación inicial; administración REST/topic puede añadirse después, si la pauta exige esas funciones.

**Límites explícitos:** el patrón outbox ofrece recuperación ante fallos antes de publicar, pero una caída entre envío y anotación puede duplicar mensajes. La tabla de emails enviados minimiza duplicados, pero una caída tras SMTP y antes del insert puede duplicar el correo. La versión usa un solo Orders Publisher; para varias réplicas se requiere locking de registros pendientes (`SKIP LOCKED`) y control de concurrencia. El carrito no se vacía automáticamente, porque aún no existe checkout.

## 2. Endpoints

| Método | Ruta | Función | Acceso |
|---|---|---|---|
| POST | `/api/orders` | Recalcular cotización y crear pedido | JWT (local: X-Demo-User) |
| GET | `/api/orders` | Historial del usuario | JWT |
| GET | `/api/orders/{id}` | Detalle solo del propietario | JWT |
| GET | `/actuator/health` | ALB / salud técnica | Interno |

`POST /api/orders` requiere `Idempotency-Key` alfanumérico `[-_A-Za-z0-9]`, entre 8 y 80 caracteres, y JSON:

```json
{"zoneCode":"PM","mode":"CHEAPEST","recipientEmail":"destinatario@ejemplo.cl"}
```

El campo `recipientEmail` es **obligatorio tanto en Docker local como en AWS**. El backend valida su formato, lo guarda en el pedido y lo publica en el evento `order.created` para que Notifications envíe la confirmación a esa dirección. En AWS, el JWT validado identifica exclusivamente al **propietario autenticado** del pedido; su correo de inicio de sesión no reemplaza el destinatario indicado por el cliente.

El JSON de respuesta contiene `id`, `recipientEmail`, `zoneCode`, `mode`, `status`, `totalAmount`, `createdAt`, `quote` (snapshot con despachos y líneas).

## 3. Ejecución local en Windows

### Archivos

Copiar el contenido del ZIP al **directorio raíz del repositorio existente**, conservando su `docker-compose.yml`, Auth, Cart, Products y workflows actuales. No reemplazar el repositorio por el ZIP: es un paquete de cambios.

### Arranque

```powershell
docker compose -f docker-compose.yml -f docker-compose.override.yml up --build -d
```

El override incluye Products existente más:

| Contenedor | Puerto local | Uso |
|---|---|---|
| `orders-service` | 8084 | API de pedidos |
| `notifications-service` | sin exposición pública | Consumidor AMQP |
| `rabbitmq` | 15672 solo 127.0.0.1 | Dashboard Management; AMQP solo Docker |
| `mailpit` | 8025 solo 127.0.0.1 | Bandeja de correos simulados |

**Nota:** La autenticación local se conserva vía `X-Demo-User`. Evitar introducir credenciales Entra en los contenedores locales.

### Primera prueba funcional

1. Abrir Frontend (`http://localhost`, u origen local configurado), iniciar sesión de prueba y agregar al menos un producto disponible.
2. Seleccionar una zona, **Mejor precio** o **Entrega más rápida**.
3. Ingresar el correo de notificación (por ejemplo `prueba@example.cl`), que puede ser distinto al correo de inicio de sesión, y presionar **Generar pedido (sin pago)**.
4. Comprobar el ID en **Mis pedidos**, el destinatario mostrado y el total desglosado (recotizado en backend).
5. Abrir **http://localhost:8025** para ver el mensaje enviado por Notifications.
6. Abrir **http://localhost:15672** (usuario local `farmacia`, contraseña local `farmacia-local`) y comprobar los exchanges/colas `orders.events`, `notifications.order-created` y `.dlq`.
7. Consultar `docker compose ... logs -f orders-service notifications-service rabbitmq` si hay falla.
8. Repetir `POST /api/orders` con la misma `Idempotency-Key` y comprobar que no aparecen pedidos/eventos nuevos.

**Prueba de tolerancia a fallos:** detener RabbitMQ después de crear una orden; verificar que Orders la guarda con outbox pendiente; reiniciar Rabbit y esperar el envío. No hacerlo con cargas reales.

### Pruebas automatizadas

```powershell
cd orders-service
mvn test
cd ../notifications-service
mvn test
```

La presente entrega incluye pruebas unitarias; validación Docker real y AWS **requieren ejecución por parte del usuario**. No se afirma que se hayan ejecutado en este entorno.

---

## 4. Guía AWS completa ANTES de tocar infraestructura

No ejecutar hasta aprobar Docker local. **Crear únicamente tres EC2 nuevas privadas** (Orders, Notifications y RabbitMQ). Reutilizar VPC, NAT, RDS, ALB, VPC Link, API Gateway y JWT existente. **No modificar ni reiniciar Auth, Cart o Products**; excepción: regla SG Cart que autoriza Orders en `8082` sin retirar las reglas actuales.

### 4.1 Inventario y elecciones fijas

| Recurso | Nombre propuesto | Configuración |
|---|---|---|
| VPC | `pedidos360-vpc` | `vpc-06f422a8b206c315c` |
| Subred EC2 | `pedidos360-private-b` | `subnet-0a205bb35d0802b09`, AZ `us-east-1b`; sin IP pública |
| NAT | `pedidos360-nat-b` | Reutilizar, salida Internet desde subnet B |
| IAM | `LabInstanceProfile` | Para las tres EC2; agente SSM habilitado |
| EC2 Orders | `pedidos360-orders` | Amazon Linux 2023, Docker, puerto 8084 |
| EC2 Notifications | `pedidos360-notifications` | Amazon Linux 2023, Docker, puerto 8085 solo localhost |
| EC2 RabbitMQ | `pedidos360-rabbitmq` | Amazon Linux 2023, Docker, AMQP 5672, Management 15672 loopback |
| RDS | PostgreSQL existente | BD `pedidos360`, esquemas nuevos `orders` y `notifications` |
| ALB interno | `pedidos360-internal-alb` | Reutilizar listener HTTP 80 |
| Target group | `pedidos360-orders-tg` | instance / HTTP:8084; health `/actuator/health` |
| API Gateway | `m5cup771ni` | HTTP API; stage `desarrollo`, JWT Microsoft existente |
| VPC Link | `pedidos360-vpclink` | Reutilizar integración privada listener ALB 80 |
| Rabbit dashboard | Sin endpoint público | SSM port forwarding o SSH-over-SSM |

**Capacidad y costo:** mínimo 3 EC2 más, discos EBS y mayor tráfico de la NAT actual. Confirmar límites/cuotas de AWS Academy, tamaño de RAM para Rabbit y créditos disponibles antes de crear instancias. Para RabbitMQ, dimensionar memoria y disco según la carga; un broker único **no da alta disponibilidad**.

### 4.2 Orden de configuración de Security Groups

Nombres propuestos nuevos: `pedidos360-orders-sg`, `pedidos360-notifications-sg`, `pedidos360-rabbitmq-sg`. El principio es referenciar **SG origen**, no `0.0.0.0/0`.

| SG destino | Entrada TCP | Origen SG | Acción |
|---|---|---|---|
| `pedidos360-orders-sg` | 8084 | `pedidos360-alb-sg` | Nueva |
| `pedidos360-rabbitmq-sg` | 5672 | `pedidos360-orders-sg` | Nueva |
| `pedidos360-rabbitmq-sg` | 5672 | `pedidos360-notifications-sg` | Nueva |
| `pedidos360-rds-sg` | 5432 | `pedidos360-orders-sg` | **Agregar sin borrar reglas** |
| `pedidos360-rds-sg` | 5432 | `pedidos360-notifications-sg` | **Agregar sin borrar reglas** |
| `pedidos360-cart-sg` | 8082 | `pedidos360-orders-sg` | **Agregar**, mantener reglas históricas |
| `pedidos360-alb-sg` | 80 | `pedidos360-vpclink-sg` | Existente, mantener |
| `pedidos360-alb-sg` | 80 | `pedidos360-cart-sg` | Existente, mantener |

Además agregar **egreso del ALB** TCP 8084 hacia `pedidos360-orders-sg` si está restringido. En SG nuevos, permitir egresos necesarios: Orders → Cart :8082, Rabbit :5672, RDS :5432 y HTTPS :443 para Azure JWKS/GHCR/SSM via NAT; Notifications → Rabbit :5672, RDS :5432, SMTP :587 (u otro puerto configurado) hacia proveedor, salida 443 para despliegue. Rabbit puede necesitar 443 hacia repositorios/SSM durante instalación. **Sin entrada SSH pública**; el túnel SSH pasa por SSM. **No abrir 15672** en SG; el Docker de Rabbit lo expone solo a 127.0.0.1 en la EC2.

### 4.3 Preparar las EC2 privadas

1. Comprobar que la subnet privada B usa la tabla de rutas `pedidos360-private-b-rt` con `0.0.0.0/0 → pedidos360-nat-b`.
2. Crear los tres SG anteriores en la VPC correcta, aplicar reglas descritas.
3. Crear EC2 `pedidos360-rabbitmq`, `pedidos360-orders`, `pedidos360-notifications` en private-b, sin IP pública, con `LabInstanceProfile`, key pair vigente, Amazon Linux 2023; Docker instalado y `sudo systemctl enable --now docker`.
4. Comprobar **Session Manager** de las tres desde la consola AWS. Anotar IDs `i-...` y direcciones IPv4 privadas. Las IP privadas deberán permanecer estables durante esta prueba; usar DNS privado interno en futuras versiones para evitar roturas al reemplazar instancias.
5. Para cada máquina, verificar `sudo docker info` desde una sesión SSM. No cerrar puertos adicionales por intuición: respetar cuadro de SG anterior.

Si el usuario necesita SSH encapsulado mediante SSM, **no usar `ssh ec2-user@IP-privada` desde Windows sin túnel**. El procedimiento reproducible ya está en los workflows: `aws ssm start-session --target i-... --document-name AWS-StartSSHSession --parameters portNumber=22` y `ssh` con `ProxyCommand`. 

### 4.4 PostgreSQL

Orders y Notifications usan los secretos **existentes** `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (el mismo RDS, no un RDS nuevo). Cada servicio ejecuta `CREATE SCHEMA IF NOT EXISTS orders/notifications` y Hibernate `ddl-auto=update` en esta versión académica. Comprobar que el usuario de RDS tiene permiso `CREATE` sobre la base. Si no, ejecutar los `CREATE SCHEMA` como administrador desde Cloud SQL no aplica; usar **cliente PostgreSQL/RDS** con permisos apropiados. No conceder derechos de superusuario a los servicios. En producción, reemplazar `ddl-auto=update` por migraciones versionadas y permisos limitados.

### 4.5 RabbitMQ y correo

- Guardar nuevos Secrets GitHub: `RABBITMQ_EC2_INSTANCE_ID`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` (mínimo 12 caracteres) y otros secretos por servicio según tabla final.
- Ejecutar `Deploy RabbitMQ EC2` **primero**, sobre `main`. Persistencia Docker volume `pedidos360-rabbitmq-data` en el volumen EBS de la EC2; sobrevive a reinicio del contenedor/instancia, **no a eliminación de la EC2 sin conservar EBS**.
- En AWS, el broker no permite `guest` remoto; utilizar usuario/clave dedicados del laboratorio.
- Para acceso administrativo usar **SSM port forwarding** desde una máquina con AWS CLI + Session Manager Plugin:

```powershell
aws ssm start-session --target i-ID_RABBITMQ --document-name AWS-StartPortForwardingSession --parameters "portNumber=15672,localPortNumber=15672"
```

Abrir `http://localhost:15672` mientras la sesión esté activa. Sustituir `i-ID_RABBITMQ` por el ID real. **Este acceso no requiere hacer público 15672**.

- Correo SMTP: para Gmail, una cuenta habilitada para contraseñas de aplicación y conexión `smtp.gmail.com:587`, STARTTLS. Definir `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM` como Secrets; **no** introducir secretos en commits, capturas o chat. El remitente debe corresponder a una identidad autorizada por el servidor SMTP.
- Verificar desde la EC2 Notifications el acceso saliente al host/puerto SMTP. En AWS, restricciones de correo/salida pueden impedir la entrega incluso con credenciales correctas. Si ocurre, detenerse y diagnosticar; el mensaje no debe considerarse enviado sólo porque el pedido aparece en el historial.

### 4.6 GitHub variables/secrets nuevas

| Variable | Tipo | Valor esperado |
|---|---|---|
| `ORDERS_EC2_INSTANCE_ID` | Secret | ID EC2 Orders |
| `NOTIFICATIONS_EC2_INSTANCE_ID` | Secret | ID EC2 Notifications |
| `RABBITMQ_EC2_INSTANCE_ID` | Secret | ID EC2 RabbitMQ |
| `RABBITMQ_USERNAME` | Secret | usuario AMQP y del panel |
| `RABBITMQ_PASSWORD` | Secret | contraseña AMQP, fuerte |
| `SMTP_HOST` | Secret | host SMTP |
| `SMTP_PORT` | Secret | 587 (en Gmail) |
| `SMTP_USERNAME` | Secret | usuario SMTP |
| `SMTP_PASSWORD` | Secret | contraseña de aplicación |
| `SMTP_FROM` | Secret | remitente permitido |
| `CART_INTERNAL_URL` | Variable | `http://<IP-privada-actual-Cart>:8082` |
| `RABBITMQ_PRIVATE_IP` | Variable | IP privada EC2 Rabbit, sin `http://` |

**Reutilizar:** `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`, `EC2_SSH_PRIVATE_KEY`, `AZURE_ISSUER_URI`, `AZURE_AUDIENCE`, `FRONTEND_ORIGIN`. Reutilizar `API_GATEWAY_BASE_URL` y el workflow Frontend previo. **Sin necesidad de nueva variable `ORDERS_API_URL` en AWS:** la entrada Nginx usa la misma URL API Gateway que Products cuando `AUTH_MODE=azure`.

### 4.7 ALB y API Gateway

1. Crear Target Group `pedidos360-orders-tg`, tipo `Instance`, HTTP :8084, VPC `pedidos360-vpc`, health check HTTP `/actuator/health`, matcher 200; registrar la **EC2 Orders**.
2. Listener HTTP :80 de `pedidos360-internal-alb`: **agregar regla explícita con prioridad disponible**: path `/api/orders` y `/api/orders/*` → `pedidos360-orders-tg`. Mantener comportamiento Products original. Comprobar que Products sigue apuntando al target healthy. No modificar acción por defecto hasta validar rutas.
3. API Gateway HTTP API `m5cup771ni`: agregar `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{id}`, **integración privada ya existente** `pedidos360-vpclink` → `HTTP:80` del ALB (misma integración que Products), y **JWT Authorizer** de Entra ID.
4. Aplicar **parameter mapping `overwrite:path = $request.path`** para evitar el prefijo del stage `desarrollo` hacia Spring Boot, como se hizo en Products. No eliminar ni modificar otras rutas.
5. Confirmar despliegue automático de stage `desarrollo` (o desplegar manualmente si no está habilitado). El frontend utilizará `https://...execute-api.us-east-1.amazonaws.com/desarrollo` de la variable Products existente.
6. No crear ninguna ruta API Gateway hacia Notifications o RabbitMQ. Sus puertos son privados.

### 4.8 Orden de CI/CD y pruebas en AWS

1. Aplicar el paquete de cambios en una rama `feature/farmacia-simple-orders-messaging` y validar Docker local.
2. PR y CI `CI Orders Notifications`, merge a `main`.
3. `Build Messaging Images` sobre `main`: dos imágenes `ghcr.io/flecaros84/pedidos360-orders`, `...-notifications`. El workflow histórico `Build Docker Images` sigue encargado de Auth, Cart, Products y Frontend; **no se sustituye ni se invalida**.
4. Configurar todas las Secrets/Variables, SG, EC2 y broker según los pasos anteriores.
5. `Deploy RabbitMQ EC2` → `Deploy Orders EC2` → comprobar `pedidos360-orders-tg` **Healthy** → `Deploy Notifications EC2` → **Deploy Frontend EC2** (la nueva imagen Frontend debe haberse construido mediante el pipeline histórico).
6. Prueba autenticada AWS: catálogo, carrito, cotización, `POST /api/orders` HTTP 201, historial `GET /api/orders`, evento recibido en Rabbit y correo real en destino. Revisar DLQ en caso de fallo.

**IMPORTANTE: la imagen Frontend** se crea mediante el workflow existente `Build Docker Images` de los cuatro servicios, en este punto. Si hay algún cambio al script de entrada, confirmar que este archivo se copie en el Dockerfile del Frontend vigente.

### 4.9 Diagnóstico

| Síntoma | Diagnóstico primero |
|---|---|
| 403 en `/api/orders` | JWT Authorizer o audience/issuer backend |
| 404/502 en API Gateway | regla listener Orders, Target Group, stage y `overwrite:path` |
| Cart `502` desde Orders | `CART_INTERNAL_URL`, SG Cart `8082` desde Orders, token propagado |
| 409 al crear | stock/cotización recalculados, carrito vacío o no disponible |
| Pedido 201 sin correo | revisar tabla outbox, Rabbit :5672, logs Notifications, DLQ, SMTP |
| Rabbit acceso denegado | Secrets AMQP, `5672` SG y reinicio del broker |
| SMTP falla | credenciales, remitente, TCP 587 saliente/STARTTLS; mensajes a DLQ tras reintentos |
| Pedido duplicado | asegurar reutilizar `Idempotency-Key` al reintentar; la clave no reemplaza checkout final |

---

## 5. Lista de verificación de cierre

- [ ] `mvn test` Orders y Notifications en entorno con Maven.
- [ ] `docker compose ... up --build -d` correcto.
- [ ] Carrito + cotización + pedido guardado en DB, idempotencia comprobada.
- [ ] Email aparece en Mailpit y Rabbit registra queue/binding.
- [ ] Prueba de fallo Rabbit antes de enviar y recuperación del outbox.
- [ ] Aprobación del diseño y costo AWS; creación recursos en orden documentado.
- [ ] CI/Build dos imágenes nuevas + Frontend; tres despliegues privados.
- [ ] API Gateway POST/GET protegido, ALB healthy, SMTP real verificado.
- [ ] No se modificó exposición pública anterior de Auth/Cart.
- [ ] Evaluar alcance académico pendiente: exchange topic, REST administración, clúster, auditoría.

## 6. Versionado

Rama sugerida: `feature/farmacia-simple-orders-messaging`  
Commit sugerido: `Versión 0.5.0-rc.1: agrega pedidos, RabbitMQ y notificaciones por correo`
