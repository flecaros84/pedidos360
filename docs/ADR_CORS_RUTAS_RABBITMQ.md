# ADR — Rutas, CORS y acceso público a RabbitMQ

**Proyecto:** Pedidos360 / Farmacia Simple  
**Estado:** Requisito aprobado para la entrega integral (pendiente de implementación)  
**Rama:** `feature/integracion-final`  
**Motivo:** observaciones recibidas en la evaluación anterior.

## RQ-WEB-01 — Rutas y despliegues diferenciados

- El frontend React/Nginx y el backend deben publicarse por endpoints claramente independientes.
- El frontend servirá la SPA desde su origen (p. ej., `https://frontend-host/` o `http://frontend-ip/` en el laboratorio); sus rutas de navegación no deberán utilizar el espacio del backend.
- El backend se expondrá únicamente desde un AWS HTTP API Gateway (p. ej., `https://api-id.execute-api.us-east-1.amazonaws.com/api/...`) con rutas `/api/auth/*`, `/api/products/*`, `/api/cart/*`, `/api/orders/*`, `/api/payments/*`, `/api/notifications/*`, `/api/audit/*`, `/api/analytics/*` y `/api/dispatch/*` cuando corresponda.
- Los servicios backend funcionarán en EC2 dedicadas privadas y recibirán tráfico a través de VPC Link y su balanceador interno; no se expondrán puertos REST directamente a Internet.
- La URL de API consumida por React deberá configurarse por ambiente sin recompilar ni codificar IP del laboratorio anterior.

## RQ-SEC-01 — CORS diferenciado

- Separar la configuración del origen del frontend y la política CORS del backend. CORS es una política aplicada por el servidor que entrega el recurso solicitado; el navegador no puede habilitarla desde React.
- En producción/laboratorio, el AWS HTTP API Gateway gestionará el CORS de las rutas `/api/*`: orígenes explícitos del frontend, métodos necesarios, encabezados `Authorization` y `Content-Type`, y preflight `OPTIONS` sin JWT. No usar `*` en orígenes autorizados para el API.
- Los backend Spring Security mantendrán una configuración CORS separada, estricta y parametrizada para pruebas locales, sin generar encabezados duplicados en las respuestas del HTTP API Gateway. Evitar deshabilitar seguridad por omisión.
- El servidor web del frontend tendrá su propia configuración HTTP/Nginx para servir la SPA, recursos estáticos y rutas client-side; solo se configurará CORS para recursos del frontend si existe un uso entre orígenes que lo requiera. Las peticiones de React al backend no se solucionan configurando CORS del frontend.
- Verificar solicitudes preflight, llamadas autorizadas, errores 401/403 y solicitudes desde orígenes no permitidos.

## RQ-MSG-01 — Consola RabbitMQ por IP pública

- La EC2 de RabbitMQ será una instancia dedicada, con IP pública, en una subred pública con ruta al Internet Gateway. Es una excepción documentada al resto de microservicios backend privados.
- Habilitar `rabbitmq_management` y proporcionar acceso desde un navegador externo mediante un endpoint asociado a la IP pública (preferentemente por TLS, con puerto 15671 o un proxy TLS 443).
- El Security Group permitirá la consola de administración **solo** desde una lista de CIDR IPv4 autorizados, inicialmente la IP de quien administra el laboratorio con sufijo `/32` y temporalmente la IP del evaluador cuando corresponda. No publicar la consola con `0.0.0.0/0`.
- El puerto AMQP `5672` (o `5671` con TLS) se permitirá solo desde los Security Groups de microservicios que lo necesiten. No se expondrá el broker al público.
- Credenciales de administración distintas de las de aplicaciones, contraseñas fuera del repositorio, y TLS para la consola; no usar el usuario `guest` para acceso remoto.
- Reducir costo: la IP pública dinámica de EC2 puede cambiar tras stop/start, y Terraform/scripts deben mostrar el valor vigente. No requerir Elastic IP salvo necesidad demostrada, porque también genera cargos.
- La VM RabbitMQ entrará en los scripts de encendido/apagado y healthchecks del laboratorio, conservando el volumen persistente.

## Entregables Terraform y pruebas de aceptación

- Módulos/variables Terraform para orígenes CORS, rutas del gateway, frontend y RabbitMQ, incluida `rabbitmq_admin_allowed_cidrs`.
- `terraform output` para `frontend_url`, `api_base_url`, `rabbitmq_management_url` y, cuando esté en ejecución, `rabbitmq_public_ip`. Donde las IP sean dinámicas, los scripts recuperarán la IP actual de AWS en vez de confiar en un valor almacenado antiguo.
- `tests/acceptance`: comprobar que `/` sirve React y no es ruta API; `/api/products` llega al backend correcto; `OPTIONS` acepta el origen autorizado sin JWT; el API rechaza orígenes no autorizados en navegadores; las rutas protegidas rechazan JWT ausente; la consola RabbitMQ es accesible por su IP desde CIDR autorizado pero no desde CIDR no permitido; AMQP no es públicamente accesible.
- Tomar capturas/evidencias separadas de rutas frontend/backend, CORS y panel RabbitMQ.

**Observación:** esto es una decisión de diseño. No significa que los cambios Terraform, Nginx o Spring ya estén implementados ni probados.
