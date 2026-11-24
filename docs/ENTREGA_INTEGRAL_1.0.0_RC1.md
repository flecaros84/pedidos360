# Pedidos360 — Entrega integral candidata 1.0.0-rc.1

## Importante: estado real

**Código candidato generado, NO validado end-to-end.** Incluye nuevos componentes y su configuración, pero no se ha compilado con todas las dependencias Maven, no se ha levantado con Docker ni se ha aplicado Terraform sobre un AWS Academy Lab. No se puede afirmar que el proyecto sea desplegable sin errores todavía. Los workflows CI/CD y las pruebas completas son el siguiente filtro obligatorio **antes de consumir créditos**.

No se modificó `main`. Si se integra desde un ZIP, comparar previamente con `feature/integracion-final` (el documento ADR de CORS/rutas/RabbitMQ ya fue agregado a esa rama).

## Alcance entregado en esta candidata

| Servicio | Ruta | Función | EC2 prevista |
|---|---|---|---|
| Auth | `auth-service/` | Entra ID/JWT | t3.small |
| Cart | `cart-service/` | Carrito/cotización + Kafka | t3.small |
| Products | `products-service/` | Catálogo, inventario, reservas + Kafka | t3.small |
| Orders | `orders-service/` | Órdenes + outbox y estados por RabbitMQ | t3.small |
| Notifications | `notifications-service/` | SMTP y POST de prueba | t3.small |
| Payments | `payment-service/` | Pago simulado, antifraude y Webpay Plus | t3.small |
| Audit | `audit-service/` | Consumo/auditoría de eventos RabbitMQ | t3.small |
| Analytics | `analytics-service/` | Consumo Kafka, DynamoDB, métricas y WS | t3.medium |
| Dispatch | `dispatch-service/` | Seguimiento de despachos | t3.small |

También: frontend React + checkout/pagos/recomendaciones/dashboard, RabbitMQ en EC2 propia con consola por IP pública filtrada, Kafka KRaft en EC2, RDS PostgreSQL, DynamoDB, API Gateway con JWT, ALB privado, WebSocket API, CloudFront HTTPS opcional, NAT instance, workflows GitHub Actions y scripts PowerShell de control del Lab.

**No está implementada una verdadera pasarela de pago productiva**: Webpay está conectado por sus endpoints de integración, pero requiere claves de prueba e integración efectiva para confirmar el bonus académico. No ingreses datos reales de tarjeta en los modos simulados. El antifraude evalúa referencias HMAC del simulador; el portal de Webpay no revela los números de tarjeta al backend.

## Seguridad y separación de rutas

- Frontend: origen independiente en CloudFront HTTPS (si habilitado). Backend: AWS HTTP API `ANY /api/{proxy+}` protegido por JWT de Entra ID con alcance `pedidos360.access`.
- Preflight `OPTIONS` sin autenticación, CORS de API Gateway con origen permitido explícito, backend Spring CORS local parametrizado y Nginx dedicado a la SPA.
- Microservicios privados, excepto frontend público y RabbitMQ público solo para demostrar el panel de administración. El AMQP 5672 **no** debe exponerse a Internet. **La candidata aún expone el panel RabbitMQ mediante HTTP 15672; requiere TLS o una conexión segura alternativa antes de usar credenciales reales o una red no confiable.** Limitar la lista `rabbitmq_admin_allowed_cidrs` a `/32`, usar cuenta exclusiva y credenciales de laboratorio.
- La autenticación de los servicios Spring es fail-closed en AWS si se emplea el modo `azure`; `local` existe exclusivamente para la demostración local.

## 1. Validación estructural sin AWS

Desde la raíz:

```powershell
python scripts/validate/validate_project.py
```

Requisitos locales: Python 3, `PyYAML` para validar YAML (opcional para el script), Docker Desktop, Maven y Java 17, Node 22, Terraform CLI 1.10.x y AWS CLI v2 para las etapas posteriores.

## 2. Construcción y pruebas por pipeline integral

Los workflows nuevos son:

- `.github/workflows/ci-cd-integral.yml`: 9 jobs de `mvn clean verify`, build frontend, `terraform init -backend=false && terraform validate`, `docker compose config`, build/push GHCR de 10 imágenes.
- `.github/workflows/deploy-integral.yml`: despliegue manual con tag SHA mediante SSM a EC2 dedicadas; requiere temporales de AWS Academy en secrets del ambiente `aws-lab`.

La ejecución verde de GitHub Actions es **obligatoria** antes de aplicar Terraform y es el mecanismo recomendado si el equipo local no tiene Maven/Terraform.

## 3. Integración local

```powershell
docker compose -f docker-compose.full.yml config
docker compose -f docker-compose.full.yml up --build -d
docker compose -f docker-compose.full.yml ps
docker compose -f docker-compose.full.yml logs --tail=100
```

El archivo integrado configura PostgreSQL, RabbitMQ, Kafka, Mailpit, DynamoDB Local, los 9 microservicios y React. El modo local desactiva la validación Entra deliberadamente: **nunca utilizar el Compose local tal cual en EC2 pública**. En local, Mailpit está en `http://localhost:8025` y RabbitMQ Management en `http://localhost:15672`, ambos ligados a loopback.

Ejecutar compras de prueba (pagos simulados), repetir cuatro intentos sobre una misma referencia dentro de diez segundos y comprobar rechazo/alerta, verificar descuentos de stock y recuperación tras rechazo, revisar colas y panel de recomendaciones, completar pruebas de WebSocket y verificar correo en Mailpit. Se requieren pruebas de idempotencia y concurrencia.

## 4. Preparación del nuevo AWS Academy Lab

1. Activar un Lab **nuevo** y obtener credenciales temporales. Revisar cuotas EC2/vCPU, créditos, capacidad de crear RDS/DynamoDB/Lambda/API Gateway/VPC Link/CloudFront, `LabRole`, `LabInstanceProfile`, SSM y EIP. Si faltan permisos, no aplicar a ciegas: ajustar el perfil de infraestructura y documentar excepciones.
2. Revisar la capacidad disponible: se crean **9 EC2 backend exclusivas** y EC2 adicionales para React, RabbitMQ, Kafka y NAT. La configuración inicial no asegura rendimiento; `t3.small`/`t3.medium` son solo hipótesis que deben verificarse bajo carga.
3. Crear `terraform/lab/terraform.tfvars` a partir de `terraform.tfvars.example` sin subirlo a Git. Definir los orígenes CORS, CIDR de RabbitMQ `/32`, tenant, audiencia, contraseña RDS y perfil IAM existente.
4. Abrir PowerShell desde raíz, con AWS CLI configurado:

```powershell
./scripts/lab/lab-preflight.ps1
cd terraform/lab
terraform init
terraform fmt -recursive
terraform validate
terraform plan -out=tfplan
# Revisar cuidadosamente todos los recursos y costos antes de confirmar.
terraform apply tfplan
cd ../..
./scripts/lab/lab-status.ps1
```

5. Registrar URL real de CloudFront en Microsoft Entra ID (SPA redirection URI), orígenes CORS y scopes. Proveer credenciales **de integración** Webpay, SMTP, JWT/Entra y secretos del runtime en un `master.env` privado partiendo de `scripts/lab/master.env.example` (el modo de pagos simulados está habilitado por defecto exclusivamente para el laboratorio; deshabilitarlo fuera de demostraciones académicas). Ver `scripts/lab/generate-env.ps1`. Nunca introducir claves en el repositorio. Tener presente que `rds_password` **sí queda en el state de Terraform**, por lo que debe protegerse.
6. Publicar primero las imágenes GHCR con tag SHA. Luego ejecutar:

```powershell
./scripts/lab/generate-env.ps1 -MasterEnv ./master.env -ImageTag <SHA_COMPLETO>
./scripts/lab/lab-configure-all.ps1 -Directory .env.generated
./scripts/lab/lab-deploy.ps1
./scripts/lab/lab-status.ps1
```

El bootstrap `user_data` de las EC2 instala Docker, pero **Terraform apply por sí solo NO descarga credenciales ni activa las aplicaciones**. La etapa de configuración y despliegue anterior es parte inseparable del procedimiento reproducible. GHCR privado requiere autenticación del Docker del host (o cambiar la visibilidad de los paquetes) antes de `docker pull`.

## 5. Ahorro de créditos y suspensión

```powershell
./scripts/lab/lab-stop.ps1
./scripts/lab/lab-status.ps1
./scripts/lab/lab-start.ps1
```

El stop detiene EC2 y RDS, pero EBS, CloudFront, IP públicas, ALB y otros servicios siguen generando cargos. RDS puede reactivarse automáticamente después de 7 días; no olvidar revisar el estado del Lab. La automatización `enable_auto_stop` viene deshabilitada por defecto y requiere permisos IAM específicos; activarla **solo después de verificar** que Lambda puede detener EC2 y RDS. La IP dinámica de RabbitMQ puede cambiar al reiniciar; consultar `lab-status.ps1`.

## 6. Verificaciones externas mínimas

```powershell
python scripts/validate/check_routes.py --frontend https://FRONTEND-REAL --api https://API-ID.execute-api.us-east-1.amazonaws.com --origin https://FRONTEND-REAL
```

Además: ejecutar el flujo completo orden → inventario → pago Webpay de pruebas → correo → audit → dispatch; publicar/consumir Kafka; verificar DynamoDB; dashboard WebSocket con tres métricas; observar picos de RAM, reinicios y estado de las colas; repetir con API sin JWT y desde origen CORS no autorizado.

## 7. Bloqueantes conocidos antes de considerar el paquete validado

- No se ejecutó la compilación Maven ni `docker compose up` en el entorno donde se generó el ZIP; falta evidencia de compilación y pruebas integrales.
- Terraform no ha sido inicializado ni validado contra el proveedor AWS ni cuotas del Lab. CloudFront, Lambda y SSM dependen de permisos particulares del AWS Academy Learner Lab.
- Varios servicios anteriores todavía utilizan `ddl-auto=update` y sus scripts iniciales: la migración completa a Flyway de todos los esquemas no está cerrada.
- Las rutas y controles de CORS, el panel RabbitMQ, la integración Webpay Plus, AWS WebSocket, DynamoDB y los scripts PowerShell requieren comprobación real; no deben considerarse aprobados sin las pruebas.
- Las imágenes GHCR deben estar disponibles para las EC2; si son privadas habrá que proveer acceso seguro de pull.
- La consola RabbitMQ HTTP debe protegerse con TLS o acceso por túnel/VPN en un entorno de evaluación real más allá de la lista CIDR.
- El dimensionamiento mínimo de CPU/RAM está pendiente de pruebas de carga con datos representativos; no reducirlo de forma arbitraria.
- El procedimiento de stop/start debe probarse con estados de RDS y EC2 reales; no debe destruir datos ni ser presentado como ahorro total.

Esta candidata está orientada a integración y corrección, **no es un producto certificado para producción**.
