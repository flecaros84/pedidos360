# Pedidos360 / Farmacia Simple — Proyecto Cloud Native

> **Estado documentado:** 13 de octubre de 2026  
> **Versión desplegada confirmada:** `0.3.0-rc.2`. **Versión de código preparada:** `0.4.0-rc.1` (etapas 2+3; NO desplegada ni validada integralmente)  
> **Región AWS:** `us-east-1`  
> **Repositorio:** https://github.com/flecaros84/pedidos360  
> **Ambiente AWS:** laboratorio académico AWS Academy; la aplicación no realiza ventas ni pagos reales.

## 1. Objetivo y alcance

Pedidos360 es la base técnica de **Farmacia Simple**, una aplicación de demostración para consultar un catálogo farmacéutico, autenticarse mediante Microsoft Entra ID y administrar un carrito persistente. Su propósito académico es demostrar microservicios, seguridad JWT, CI/CD, redes privadas en AWS, persistencia y, en futuras etapas, mensajería asíncrona y gestión de pedidos.

### Funcionalidades implementadas

- Autenticación y cierre de sesión mediante **Microsoft Entra ID**, React/MSAL y Access Token JWT.
- Validación JWT en **AWS API Gateway HTTP API** y nuevamente mediante Spring Security en los backend.
- `Auth Service`: información del usuario autenticado y claims del token.
- `Products Service`: catálogo **almacenado en PostgreSQL**, con búsqueda por texto, filtrado por categoría, listado de categorías y detalle por ID.
- Productos de demostración F001–F006; los precios del catálogo son **referenciales**, no precios finales de compra.
- `Cart Service`: consulta, agregado y eliminación de artículos asociados al usuario; consolidación de artículos repetidos por SKU y cantidad persistente en RDS.
- Frontend **React/Vite** con identidad Farmacia Simple, categorías, búsqueda, visualización del catálogo, carrito y total referencial.
- Despliegues independientes en EC2 con **GitHub Actions + GHCR + Docker**.
- **Products en EC2 privada** mediante **SSM, NAT Gateway, ALB interno y API Gateway VPC Link**.

### Fuera de alcance por ahora

No hay compra real, pago real con PayPal, validación de recetas, órdenes, reservas de stock, notificaciones ni auditoría por eventos. El inventario por sucursal y la cotización automática son parte del código **0.4.0-rc.1 en preparación**, no del despliegue AWS ya verificado.

---


### En desarrollo: Etapas 2 + 3 unificadas (`0.4.0-rc.1`)

**No confundir con la versión desplegada `0.3.0-rc.2`.** Esta entrega de código incorpora:

- Cinco sucursales de demostración, precios y stock de cada SKU por sucursal; persistencia en esquema `catalogo`.
- Cinco zonas de entrega simuladas y tarifas/tiempos por par sucursal→zona, almacenadas en PostgreSQL.
- Un carrito único por usuario y SKU, **sin vincularse permanentemente a una sucursal**.
- Cotizador automático que compara **menor precio total (productos + envíos)** con **menor plazo estimado de entrega**; admite combinar sucursales y distribuir unidades de un SKU cuando el stock lo exige.
- Entrega de resultados desglosados por origen; sin reservas de inventario ni pagos.
- Tests unitarios de optimización y tests de APIs; workflow Cart actualizado para consultar Products por el ALB interno.

**Orden obligatorio:** primero entrega única de código (servicios + React + workflows + README); luego pruebas locales; después **[guía AWS completa de 0.4.0](docs/ETAPAS_2_3_0.4.0_AWS.md)**, infraestructura/restricciones previamente documentadas, despliegue y validación. No abrir nuevos SG ni crear EC2 para estas etapas.

## 2. Arquitectura desplegada

```text
                         USUARIO / NAVEGADOR
                                  |
                          HTTPS + JWT/MSAL
                                  |
                         AWS API GATEWAY
                          HTTP API + JWT
                      API ID m5cup771ni
                                  |
             +--------------------+--------------------+
             |                    |                    |
             v                    v                    v
       Frontend EC2           Auth EC2              Cart EC2
        (pública)            (pública)             (pública)
       Nginx/React         Spring :8081           Spring :8082
             |                    |                    |
             |             integraciones HTTP          |
             |               públicas actuales          |
             |                                         |
             |           Ruta /api/products*           |
             |                    |                    |
             |          API Gateway VPC Link           |
             |           pedidos360-vpclink            |
             |                    |                    |
             |             ALB INTERNO                 |
             |        pedidos360-internal-alb          |
             |                    |                    |
             |         Target Group Products           |
             |                    |                    |
             |              Products EC2               |
             |           (SUBRED PRIVADA B)            |
             |              Spring :8083               |
             |                    |                    |
             +--------------------+--------------------+
                                  |
                    PostgreSQL 16 en AWS RDS
                        (acceso no público)

Products EC2 -> NAT Gateway público -> Internet (solo salida: SSM/GHCR/actualizaciones)
GitHub Actions -> túnel SSH sobre SSM -> Products EC2 (despliegue privado)
```

**Diferencia histórica importante:** Frontend, Auth y Cart pertenecen a la primera entrega y permanecen en subredes públicas con Elastic IP. **Products ya utiliza la arquitectura privada acordada para los nuevos backend.** Mantener temporalmente las EC2 antiguas no convierte ese diseño anterior en una referencia de seguridad: su migración está pendiente.

### 2.1 Componentes y puertos

| Componente | Tecnología | Puerto | Red | Acceso operativo |
|---|---|---:|---|---|
| Frontend | React/Vite + Nginx en Docker | 80 | EC2 pública | GitHub Actions por SSH |
| Auth | Java 17, Spring Boot/Security | 8081 | EC2 pública | GitHub Actions por SSH |
| Cart | Java 17, Spring Boot/JPA | 8082 | EC2 pública | GitHub Actions por SSH |
| Products | Java 17, Spring Boot/JPA/Actuator | 8083 | EC2 privada | GitHub Actions por SSM + SSH encapsulado |
| PostgreSQL | AWS RDS PostgreSQL 16 | 5432 | Privada / sin acceso público | Security Groups autorizados |
| ALB | Application Load Balancer interno | 80 | Subredes privadas A y B | Solo desde VPC Link |
| API Gateway | HTTP API | 443 | Servicio administrado | JWT en rutas backend |

---

## 3. Inventario de AWS (configuración reproducible)

**Esta sección es la referencia previa obligatoria a cualquier nueva EC2 o microservicio.** Los identificadores son los observados durante el laboratorio y pueden cambiar **si AWS Academy destruye/recrea recursos**. No adivinar IDs faltantes: obtenerlos en la consola y completar el inventario.

### 3.1 Región y VPC

| Recurso | Nombre / valor | ID o detalle |
|---|---|---|
| Región | Norte de Virginia | `us-east-1` |
| VPC del proyecto | `pedidos360-vpc` | `vpc-06f422a8b206c315c` |
| CIDR VPC | Red privada | `10.0.0.0/16` |
| Subred pública B | `pedidos360-public-b` | `subnet-0a7558dd7c240087b`, `us-east-1b`, `10.0.2.0/24` |
| Subred privada B | `pedidos360-private-b` | `subnet-0a205bb35d0802b09`, `us-east-1b`, `10.0.12.0/24` |
| Subred privada A | `pedidos360-private-a` | AZ `us-east-1a`; registrar su ID actual en consola |
| Subred pública A | `pedidos360-public-a` | AZ `us-east-1a`; registrar su ID actual en consola |
| Tabla privada original | `pedidos360-private-rt` | `rtb-098194dcb34614426` |
| Tabla privada B | `pedidos360-private-b-rt` | Nueva tabla exclusiva para `private-b`; registrar su ID |

**No utilizar la VPC predeterminada** `vpc-0598ed4e4a6104858` (`172.31.0.0/16`): fue ofrecida por defecto en las consolas EC2/ALB, pero no pertenece al proyecto.

### 3.2 Diseño de rutas y NAT

| Recurso | Configuración requerida |
|---|---|
| Tabla de ruta pública B | `10.0.0.0/16 -> local`; `0.0.0.0/0 -> Internet Gateway` |
| `pedidos360-private-rt` | `10.0.0.0/16 -> local`; **no agregar aquí NAT por accidente** |
| `pedidos360-private-b-rt` | `10.0.0.0/16 -> local`; `0.0.0.0/0 -> pedidos360-nat-b` |
| Asociación exclusiva | `pedidos360-private-b-rt` ↔ `subnet-0a205bb35d0802b09` |
| NAT Gateway | `pedidos360-nat-b`, **público y zonal** en `pedidos360-public-b` |
| Elastic IP NAT | `98.86.102.219` |
| Allocation ID NAT | `eipalloc-0e5fdd6ea9ff662e8` |
| Conectividad de Products | **Sin IPv4 pública ni Elastic IP propia**; salida a GHCR/SSM por NAT |

**Sentido del NAT:** permite **conexiones iniciadas desde** la EC2 privada, pero no publica Products en Internet ni sirve como punto de entrada al puerto `8083`. La ruta local de la VPC permite comunicar Products con RDS/ALB sin pasar por NAT. Un NAT zonal único reduce costos frente a múltiples NAT, pero no proporciona alta disponibilidad multi-AZ.

### 3.3 Instancias EC2

| Instancia | ID observado | Ubicación | Comentario |
|---|---|---|---|
| `pedidos360-frontend` | `i-0ef35585032d2ecbb` | Pública | Elastic IP histórica; confirmar valor vigente |
| `pedidos360-auth` | `i-0564e51fbaa92fbc2` | Pública | Elastic IP histórica; confirmar valor vigente |
| `pedidos360-cart` | `i-0cae001cb728e3d89` | Pública | Elastic IP histórica; confirmar valor vigente |
| **`pedidos360-products`** | **`i-03a235c680f1ad939`** | **`pedidos360-private-b`** | Sin IP pública; Docker 25.0.14 y SSM confirmados |

Configuración usada/recomendada para nuevas EC2 backend privadas:

- Amazon Linux 2023 x86_64, Java 17 **dentro de Docker**, una EC2 por microservicio.
- Tamaño orientativo `t3.small` (2 GiB RAM) y almacenamiento `gp3` de 20 GiB; **comprobar el tamaño real de cada EC2 en la consola**, no inferirlo del nombre.
- Perfil `LabInstanceProfile`, asociado a `LabRole`; política `AmazonSSMManagedInstanceCore` comprobada.
- IAM, SSM Agent operativo; **no asignar IP pública, ni Elastic IP, ni abrir SSH desde Internet**.
- Contenedor con `--restart unless-stopped`; Docker iniciado con `sudo systemctl enable --now docker`.

### 3.4 Security Groups — matriz de mínima exposición

| Grupo | Asociado a | Entrada permitida | Salida necesaria |
|---|---|---|---|
| `pedidos360-vpclink-sg` | VPC Link | Sin reglas de entrada | TCP `80` a `pedidos360-alb-sg` |
| `pedidos360-alb-sg` | ALB interno | TCP `80` desde `pedidos360-vpclink-sg` | TCP `8083` a `pedidos360-products-sg` (y futuros puertos internos autorizados) |
| `pedidos360-products-sg` | EC2 Products | TCP `8083` **solo** desde `pedidos360-alb-sg` | PostgreSQL `5432` hacia RDS y HTTPS saliente según necesidad |
| `pedidos360-rds-sg` | PostgreSQL RDS | TCP `5432` desde los SG autorizados de Cart y Products | Mantener según configuración RDS |
| `pedidos360-auth-sg` / `pedidos360-cart-sg` | Backend legado | Puertos de la entrega inicial; **revisar exposición pública** | Según servicios existentes |
| `pedidos360-frontend-sg` | Frontend legado | HTTP/SSH según integración existente | Según necesidades |

Security Group de RDS observado: **`pedidos360-rds-sg` (`sg-03320f7b1f942558a`)**. Antes de habilitar Products tenía una regla de entrada desde `sg-0ef90056987aa9d2c`; después se autorizó también su Security Group y el despliegue pasó a estado saludable. **No quitar las reglas antiguas sin identificar los recursos a los que pertenecen.**

Procedimiento exacto para referenciar un SG en otro:

1. Crear el Security Group en la **VPC `vpc-06f422a8b206c315c`**.
2. Registrar su **ID `sg-...`** y nombre.
3. En el campo *Origen* o *Destino* seleccionar **Security Group**, no CIDR `0.0.0.0/0`.
4. Para un nuevo servicio: agregar su puerto al SG del ALB (salida), al SG del servicio (entrada desde ALB), y `5432` en RDS (entrada desde el SG del servicio) si usa PostgreSQL.
5. Comprobar las reglas de salida del SG del servicio y del ALB; los grupos de seguridad son **stateful** y las respuestas a conexiones admitidas se permiten automáticamente.

**Precisión:** usar el SG del ALB como origen no hace que la EC2 sea pública. Los puertos `8081/8082` de Auth/Cart siguen siendo una **deuda de seguridad** si se permiten conexiones directas desde Internet. La validación JWT no sustituye las reglas de red.

### 3.5 ALB interno, Target Group y VPC Link

| Recurso | Valor |
|---|---|
| ALB | **`pedidos360-internal-alb`** |
| Tipo/esquema | Application Load Balancer / **Internal** / IPv4 |
| VPC y subredes | VPC del proyecto; `pedidos360-private-a` y `pedidos360-private-b` |
| Security Group ALB | `pedidos360-alb-sg` |
| Listener actual | **HTTP :80** |
| Acción por defecto actual | Reenviar a `pedidos360-products-tg` |
| Target Group | **`pedidos360-products-tg`** |
| Tipo de target | **Instancia** (no IP fija), `i-03a235c680f1ad939` |
| Protocolo/puerto target | HTTP `8083` |
| Health check | `GET /actuator/health`, éxito HTTP `200` |
| Estado observado | **Healthy** el 13-10-2026 |
| VPC Link | **`pedidos360-vpclink`**, ID **`ajo8dg`** |
| VPC Link tipo | Enlace compatible con HTTP API / integración privada |
| SG del enlace | `pedidos360-vpclink-sg` |
| Estado observado | **Available** el 13-10-2026 |

**Enrutamiento futuro:** reutilizar el **mismo ALB y VPC Link**. Cada nuevo backend requiere su propio Target Group y una regla del listener (ej.: `Path /api/orders* -> pedidos360-orders-tg`), además de sus reglas en API Gateway. Cuando haya varios servicios, conviene reemplazar la acción predeterminada indiscriminada hacia Products por una respuesta `404` controlada; antes de hacerlo, crear la regla explícita `/api/products*` y probarla.

**Capacidad:** un ALB interno convencional requiere subredes en al menos dos zonas de disponibilidad. Los targets pueden estar inicialmente en una sola AZ, pero ello no garantiza alta disponibilidad del backend.

### 3.6 AWS Systems Manager y credenciales

- En **EC2 → Products → Conectar → Session Manager** se comprobó acceso exitoso al sistema operativo sin dirección pública.
- `LabRole` tiene `AmazonSSMManagedInstanceCore`; el agente necesita conectividad HTTPS hacia AWS, resuelta mediante NAT en `private-b`.
- **SSM Session Manager no necesita un SG con puerto 22 público.** El workflow actual emplea **SSH encapsulado por una sesión SSM** (`AWS-StartSSHSession`), por lo que requiere SSH operativo **dentro de la instancia**, pero **no una entrada pública abierta al puerto 22**.
- El workflow de GitHub configura `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` temporales de AWS Academy y reutiliza `EC2_SSH_PRIVATE_KEY` para el túnel SSM.
- Esas credenciales temporales **caducan**. Tras reiniciar/renovar la sesión del laboratorio puede ser necesario actualizar los GitHub Secrets, sin modificar la VPC ni reconstruir la EC2.
- Para un entorno no académico se prefiere **GitHub OIDC + rol IAM de mínimo privilegio**; es una **mejora futura**, no el procedimiento operativo actual.

### 3.7 AWS API Gateway y autenticación

| Campo | Valor |
|---|---|
| API | `pedidos360-api` |
| Tipo | **HTTP API** |
| API ID | **`m5cup771ni`** |
| URL base | `https://m5cup771ni.execute-api.us-east-1.amazonaws.com` |
| Stage usado efectivamente por React | **`desarrollo`** |
| Stage adicional existente | `$default` (no suponer que el frontend lo utiliza) |
| Integración Auth/Cart/Frontend | HTTP pública original; mantener mientras se migra |
| Integración Products | **Recurso privado** → VPC Link → listener ALB `HTTP:80` |
| Autorización | JWT Authorizer de Microsoft Entra ID en rutas backend |
| Parameter mapping de Products | **Todas las solicitudes entrantes**: `overwrite:path = $request.path` |

Rutas existentes y de Products:

| Método | Ruta | Destino | Autorización |
|---|---|---|---|
| `GET` | `/api/auth/me` | Auth EC2 | JWT |
| `GET` | `/api/cart` | Cart EC2 | JWT |
| `POST` | `/api/cart/items` | Cart EC2 | JWT |
| `DELETE` | `/api/cart/items/{id}` | Cart EC2 | JWT |
| `GET` | `/api/products` | ALB interno → Products | JWT |
| `GET` | `/api/products/categories` | ALB interno → Products | JWT |
| `GET` | `/api/products/{id}` | ALB interno → Products | JWT |
| `GET` | `/` | Frontend | Según configuración actual |
| `ANY` | `/{proxy+}` | Frontend (fallback SPA) | Según configuración actual |

Consulta validada desde Chrome (Network):

```http
GET https://m5cup771ni.execute-api.us-east-1.amazonaws.com/desarrollo/api/products
HTTP/2 200
Content-Type: application/json
```

La consola mostró **200 OK**, `application/json` y solicitudes adicionales a `/api/products/categories`. Esto prueba el consumo real desde API Gateway; no sustituye todas las pruebas funcionales del carrito y filtros.

**Advertencia de stages:** API Gateway puede anteponer `/desarrollo` a la ruta enviada por una integración privada; el mapeo `overwrite:path` con `$request.path` evita que Spring Boot reciba `/desarrollo/api/products` cuando espera `/api/products`. No cambiar stages, Redirect URI o URL de frontend sin actualizar de forma coordinada Entra ID, GitHub Variables y API Gateway.

---

## 4. Código fuente y tecnologías

```text
pedidos360/
├── frontend/                         React + Vite + MSAL + Nginx
├── auth-service/                     Spring Boot / JWT / identidad
├── cart-service/                     Spring Boot / JPA / carrito
├── products-service/                 Spring Boot / JPA / catálogo
├── .github/
│   └── workflows/
│       ├── ci.yml
│       ├── build-images.yml
│       ├── deploy-auth-ec2.yml
│       ├── deploy-cart-ec2.yml
│       ├── deploy-frontend-ec2.yml
│       └── deploy-products-ec2.yml
├── scripts/                          Utilidades del repositorio
├── docker-compose.yml                Desarrollo local existente
├── docker-compose.prod.yml           Compose histórico de la primera entrega
├── .env.example
└── README.md
```

| Capa | Tecnologías |
|---|---|
| Frontend | React 18, Vite, MSAL, JavaScript, Nginx |
| Backend | Java 17, Spring Boot 3, Spring Security, OAuth2 Resource Server, JPA/Hibernate |
| Persistencia | PostgreSQL 16 local y AWS RDS |
| Contenedores | Docker; Docker Compose local |
| CI/CD | Git, GitHub Actions, GHCR |
| Identidad | Microsoft Entra ID / OAuth2 / JWT |
| Red y cloud | AWS EC2, VPC, subredes públicas/privadas, IGW, NAT, ALB, VPC Link, API Gateway, Security Groups, Systems Manager |

**Compatibilidad local:** `docker-compose.yml` principal contiene la infraestructura histórica; el archivo `docker-compose.override.yml` de la etapa de catálogo incorpora `products-service`, su configuración local y la URL interna de Cart. Ejecutar ambos juntos (`docker compose -f docker-compose.yml -f docker-compose.override.yml up --build`). Verificar que el override forme parte del repositorio antes de iniciar pruebas.

---

## 5. Microsoft Entra ID y JWT

Se conserva la App Registration original del proyecto. El frontend obtiene un Access Token con MSAL y llama al API Gateway con:

```http
Authorization: Bearer <access-token>
```

Scope actual:

```text
api://a561d8ef-b187-4bf0-9555-8f66b445df2e/pedidos360.access
```

Los backend verifican firma/issuer/audience/vigencia y los controles configurados de scopes. Las rutas privadas de Products reutilizan el JWT Authorizer existente; **SSM y una EC2 privada no sustituyen la autorización de la aplicación**.

Variables de identidad relevantes:

```env
APP_SECURITY_MODE=azure
AUTH_MODE=azure
AZURE_TENANT_ID=<tenant-id>
AZURE_CLIENT_ID=<client-id>
AZURE_ISSUER_URI=<issuer-verificado>
AZURE_AUDIENCE=<audience-verificada>
AZURE_API_SCOPE=api://<client-id>/pedidos360.access
```

Redirect URI del entorno usado:

```text
https://m5cup771ni.execute-api.us-east-1.amazonaws.com/desarrollo/
```

No reemplazar silenciosamente `desarrollo` por `$default`: en la validación del 13-10-2026 el navegador seguía utilizando `desarrollo`.

---

## 6. API de los microservicios

### 6.1 Auth Service

```http
GET /api/auth/me
Authorization: Bearer <access-token>
```

Respuesta ilustrativa:

```json
{
  "authenticated": true,
  "userId": "...",
  "name": "Usuario",
  "scopes": ["pedidos360.access"],
  "roles": []
}
```

### 6.2 Products Service — catálogo real

```http
GET /api/products
GET /api/products?search=paracetamol
GET /api/products?category=Medicamentos
GET /api/products/categories
GET /api/products/1
Authorization: Bearer <access-token>
```

Los filtros `search` y `category` son opcionales, se pueden combinar y se procesan en Spring Boot contra PostgreSQL. `search` consulta campos como SKU, nombre, principio activo y laboratorio; no depende de un arreglo estático en React. El endpoint por ID utiliza el **ID numérico**, no el SKU.

Contrato de respuesta del producto:

```json
{
  "id": 1,
  "sku": "F001",
  "name": "Nombre de ejemplo",
  "activeIngredient": "Principio activo",
  "strength": "500 mg",
  "presentation": "Presentación",
  "laboratory": "Laboratorio",
  "category": "Medicamentos",
  "referencePrice": 1000,
  "requiresPrescription": false
}
```

> La respuesta es **ilustrativa**; el precio y los demás valores del ejemplo no deben interpretarse como datos verificados del catálogo real.

La entidad se mapea a `catalogo.products`, con campos `id`, `sku`, `name`, `active_ingredient`, `strength`, `presentation`, `laboratory`, `category`, `reference_price`, `requires_prescription` y `active`. Los productos inactivos se excluyen de las consultas públicas.

### 6.3 Cart Service

Consultar artículos:

```http
GET /api/cart
Authorization: Bearer <access-token>
```

Agregar uno:

```http
POST /api/cart/items
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "productCode": "F001",
  "productName": "Paracetamol 500 mg",
  "quantity": 1
}
```

Eliminar:

```http
DELETE /api/cart/items/{id}
Authorization: Bearer <access-token>
```

Los artículos se asocian a `user_id`. El servicio consolida por combinación usuario + `product_code` (SKU); agregar dos veces el mismo SKU **incrementa la cantidad** en lugar de crear otra línea lógica. Los cambios anteriores se diseñaron para preservar compatibilidad con registros existentes.

### 6.4 Endpoints operativos

| Servicio | Comprobación | Resultado esperado |
|---|---|---|
| Products EC2 local | `curl http://127.0.0.1:8083/actuator/health` | `200`, `{"status":"UP"}` |
| Products Target Group | `/actuator/health` | Target `Healthy` |
| API Gateway sin JWT | `GET /desarrollo/api/products` | `401` si ruta protegida |
| API Gateway con JWT | `GET /desarrollo/api/products` | `200` y JSON |

---

## 7. Base de datos y persistencia

RDS existente:

```text
Motor:         PostgreSQL 16
DB:            pedidos360
Endpoint:      pedidos360-db.cvmuxunht7ru.us-east-1.rds.amazonaws.com
Puerto:        5432
Acceso público: No
SG:            pedidos360-rds-sg (sg-03320f7b1f942558a)
```

El Cart Service conserva `public.cart_item` y Products utiliza su esquema exclusivo `catalogo` con `catalogo.products`. La base de datos **es una instancia RDS compartida**, pero las tablas del catálogo quedan separadas lógicamente. Un nuevo servicio **no debe utilizar las tablas de otro microservicio directamente como acoplamiento habitual**.

Conexión desde backend:

```env
DATABASE_URL=jdbc:postgresql://pedidos360-db.cvmuxunht7ru.us-east-1.rds.amazonaws.com:5432/pedidos360
DATABASE_USERNAME=<usuario-rds>
DATABASE_PASSWORD=<secreto>
```

Configuración vigente del deploy Products:

```env
APP_SECURITY_MODE=azure
DATABASE_DDL_AUTO=update
CATALOG_SEED_DEMO=true
SERVER_PORT=8083
# Junto a DATABASE_* y AZURE_ISSUER_URI/AZURE_AUDIENCE
```

`schema.sql` crea el esquema `catalogo` si no existe; la entidad JPA se mapea a `catalogo.products`. La carga de demostración no debe confundirse con un procedimiento para reemplazar datos productivos. En futuras etapas, planificar scripts/versionado de migraciones y **hacer snapshot de RDS antes de cambios destructivos**.

### SQL útil de diagnóstico

```sql
-- Esquemas y tablas
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema IN ('public', 'catalogo')
ORDER BY table_schema, table_name;

-- Catálogo
SELECT id, sku, name, category, reference_price, active
FROM catalogo.products
ORDER BY sku;

-- Carrito
SELECT id, user_id, product_code, product_name, quantity, created_at
FROM public.cart_item
ORDER BY id;

-- Detectar SKU duplicado por usuario (migraciones/verificación)
SELECT user_id, product_code, COUNT(*) AS filas, SUM(quantity) AS unidades
FROM public.cart_item
GROUP BY user_id, product_code
HAVING COUNT(*) > 1;
```

**La contraseña no se almacena en el código ni en este README**. GitHub Secrets se utiliza en los despliegues.

---

## 8. Desarrollo local

Requisitos: Docker/Docker Compose, Node.js 20 para frontend y Java 17/Maven para backend.

```bash
# Entorno declarado por el Compose vigente de main
# (PostgreSQL + Auth + Cart + Frontend; todavía no agrega Products).
docker compose up --build

# Apagar sin borrar datos
docker compose down

# ATENCIÓN: destruye el volumen local de PostgreSQL
docker compose down -v
```

Puertos esperados del ambiente histórico:

| Servicio | URL |
|---|---|
| React Vite | `http://localhost:5173` |
| Frontend Docker | `http://localhost` |
| Auth | `http://localhost:8081` |
| Cart | `http://localhost:8082` |
| Products | `http://localhost:8083` (al agregarlo/levantarlo explícitamente) |
| PostgreSQL local | `localhost:5432` |

El modo local admite autenticación de demostración con `APP_SECURITY_MODE=local` / `AUTH_MODE=local` y encabezado `X-Demo-User` (por ejemplo `local-demo-user`) cuando está configurado. **No usar modo local para el entorno público de AWS.**

**Desarrollo local:** utilizar el `docker-compose.override.yml` ya entregado para Products; mantener separado el despliegue productivo por EC2/workflows independientes. No considerar `docker-compose.prod.yml` histórico como procedimiento de despliegue AWS.

---

## 9. GitHub Actions, GHCR y despliegues

### 9.1 Workflows existentes

| Workflow | Archivo | Descripción |
|---|---|---|
| **CI** | `.github/workflows/ci.yml` | Frontend `npm run build`; tests Maven de **Auth, Cart y Products** |
| **Build Docker Images** | `.github/workflows/build-images.yml` | Construye y publica las **cuatro** imágenes en GHCR |
| Deploy Auth EC2 | `.github/workflows/deploy-auth-ec2.yml` | Deploy independiente por SSH público existente |
| Deploy Cart EC2 | `.github/workflows/deploy-cart-ec2.yml` | Deploy independiente por SSH público existente |
| Deploy Frontend EC2 | `.github/workflows/deploy-frontend-ec2.yml` | Deploy independiente por SSH público existente |
| **Deploy Products EC2** | `.github/workflows/deploy-products-ec2.yml` | Deploy independiente mediante **SSM + túnel SSH** a la EC2 privada |

Imágenes:

```text
ghcr.io/flecaros84/pedidos360-frontend:latest
ghcr.io/flecaros84/pedidos360-auth:latest
ghcr.io/flecaros84/pedidos360-cart:latest
ghcr.io/flecaros84/pedidos360-products:latest
```

`Build Docker Images` se ejecuta sobre `main` por `push` y también manualmente. Publica etiquetas `latest` y **SHA completo del commit**. Los workflows individuales permiten el parámetro `image_tag` (`latest` o SHA). **Para despliegues reproducibles se prefiere SHA**, aunque el procedimiento actual de laboratorio también admite `latest`.

### 9.2 Secuencia operativa comprobada

```text
Cambio local -> commit -> push/PR -> CI
  -> merge a main (según estrategia del proyecto)
  -> Build Docker Images -> GHCR
  -> Deploy <servicio> EC2 (SOLO servicio modificado)
  -> health check + prueba desde frontend
```

**No ejecutar Deploy Auth/Cart/Frontend por defecto al cambiar Products.** Los cuatro pipelines son independientes. No migrar GHCR a ECR sin una necesidad explícita.

Products `deploy-products-ec2.yml` realiza:

1. Autenticación de GitHub Actions con las credenciales **temporales de AWS Academy**.
2. Instalación de Session Manager Plugin en el runner.
3. Túnel SSH privado mediante documento SSM `AWS-StartSSHSession`, con clave reutilizada `EC2_SSH_PRIVATE_KEY`.
4. Generación temporal de `products.env` y copia por `scp` dentro del túnel.
5. `docker login` en GHCR, `docker pull`, reemplazo de **solo** `pedidos360-products` y arranque con `--restart unless-stopped`.
6. Comprobación `/actuator/health` hasta estado `UP`; muestra logs si falla.

No se requieren ECR, IP pública en Products ni SSH abierto a Internet. Sí se necesitan NAT para descargar desde GHCR/SSM, permisos IAM correctos, Security Groups apropiados y RDS accesible.

### 9.3 Secrets y Variables del repositorio

Configurar en **GitHub → Settings → Secrets and variables → Actions**.

| Nombre | Tipo | Uso |
|---|---|---|
| `EC2_USER` | Secret | Usuario SSH de EC2 públicas |
| `EC2_SSH_PRIVATE_KEY` | Secret | Clave SSH para despliegues existentes y túnel SSM de Products |
| `FRONTEND_EC2_HOST` | Secret | Elastic IP/host frontend público |
| `AUTH_EC2_HOST` | Secret | Elastic IP/host Auth público |
| `CART_EC2_HOST` | Secret | Elastic IP/host Cart público |
| **`PRODUCTS_EC2_INSTANCE_ID`** | Secret | ID `i-...` de la EC2 Products privada |
| **`AWS_ACCESS_KEY_ID`** | Secret | Credencial temporal del laboratorio |
| **`AWS_SECRET_ACCESS_KEY`** | Secret | Credencial temporal del laboratorio |
| **`AWS_SESSION_TOKEN`** | Secret | Token temporal del laboratorio |
| `AZURE_TENANT_ID` | Secret | Configuración de identidad |
| `AZURE_CLIENT_ID` | Secret | Configuración de identidad |
| `AZURE_ISSUER_URI` | Secret | Issuer del JWT |
| `AZURE_AUDIENCE` | Secret | Audience del JWT |
| `DATABASE_URL` | Secret | JDBC de RDS |
| `DATABASE_USERNAME` | Secret | Usuario PostgreSQL |
| `DATABASE_PASSWORD` | Secret | Contraseña PostgreSQL |
| `API_GATEWAY_BASE_URL` | Variable | URL base real utilizada por frontend; incluye `/desarrollo` |
| `FRONTEND_ORIGIN` | Variable | Origen autorizado por CORS |

`GITHUB_TOKEN` lo provee GitHub Actions en los workflows; no debe duplicarse manualmente. No almacenar `.pem`, tokens, contraseñas o archivos `*.env` reales en Git.

**Rotación:** tras una nueva sesión de AWS Academy, si GitHub Actions falla en `configure-aws-credentials`, actualizar los **tres secretos temporales** con la nueva sección AWS CLI del laboratorio, sin modificar el workflow ni la EC2.

---

## 10. Operación, conexión y persistencia al reiniciar AWS Academy

### 10.1 EC2 pública: SSH desde Windows

Si la consola indica que la Elastic IP histórica de Cart sigue siendo `44.209.83.71`, desde PowerShell, en la carpeta que contiene la clave:

```powershell
ssh -i .\pedidos360-key.pem ec2-user@44.209.83.71
```

Para Frontend/Auth sustituir por la **Elastic IP vigente**. No confiar en IPs almacenadas en mensajes antiguos si la cuenta o el laboratorio recreó recursos.

### 10.2 Products privada: SSM (método recomendado)

En consola: **EC2 → Instancias → pedidos360-products → Conectar → Administrador de sesiones de SSM**.

```bash
sudo docker --version
sudo systemctl is-active docker
sudo docker ps
sudo docker logs --tail 80 pedidos360-products
curl -i http://127.0.0.1:8083/actuator/health
```

No ejecutar `ssh ec2-user@IP_PUBLICA_PRODUCTS` porque Products **no tiene IP pública**. En el workflow, GitHub usa el **instance ID** mediante AWS SSM. La clave SSH existe para el túnel encapsulado, no para publicar la EC2.

### 10.3 Acceder a RDS mediante túnel histórico de Cart

El README original permite conservar DBeaver/pgAdmin mediante la EC2 pública Cart. Si la Elastic IP es la histórica:

```powershell
ssh -i .\pedidos360-key.pem `
  -L 15432:pedidos360-db.cvmuxunht7ru.us-east-1.rds.amazonaws.com:5432 `
  ec2-user@44.209.83.71
```

En DBeaver/pgAdmin:

```text
Host: localhost
Puerto: 15432
Base de datos: pedidos360
Usuario: <DATABASE_USERNAME>
Contraseña: <DATABASE_PASSWORD>
```

El túnel requiere que Cart pueda alcanzar RDS, que el acceso SSH autorizado funcione y que la ventana PowerShell permanezca abierta. **No habilitar acceso público a RDS para conectarse con DBeaver.** Esta técnica es una solución administrativa histórica; cuando Cart migre a privado, redefinir el acceso mediante SSM.

### 10.4 Comportamiento esperado del laboratorio

| Situación | Qué suele permanecer | Qué comprobar |
|---|---|---|
| Reiniciar Docker/instancia | Contenedores `--restart unless-stopped`, EBS | Esperar salud de Spring, comprobar `docker ps` |
| `Stop/Start` de EC2 existente | IP privada primaria y EBS; Elastic IP asociada | SSM, targets `Healthy`, credenciales temporales |
| Reanudar sesión académica | Depende de reglas del laboratorio | Estado de EC2, NAT, ALB, RDS, API Gateway y VPC Link |
| Renovación de credenciales AWS Academy | Infraestructura si no fue borrada | Secrets de GitHub para despliegue SSM |
| Eliminación/recreación de instancia o cuenta | **No hay garantía de persistencia** | Recrear recurso, actualizar `i-...`, SG/targets/secrets |

**No confundir `Stop/Start` con la eliminación de recursos por el laboratorio.** La Elastic IP y la IP privada ayudan con la primera situación; ninguna protege frente a la eliminación. El NAT Gateway y el ALB pueden generar **costo por hora aunque no haya solicitudes**: revisar presupuesto AWS Academy.

### 10.5 Lista de recuperación después de reiniciar

- [ ] AWS Academy Lab iniciado; confirmar recursos en `us-east-1` y VPC `vpc-06f422a8b206c315c`.
- [ ] EC2 Frontend/Auth/Cart/Products `Running` y RDS `Available`.
- [ ] NAT `pedidos360-nat-b` `Available`; subred B asociada a `pedidos360-private-b-rt` con `0.0.0.0/0 -> NAT`.
- [ ] Session Manager permite entrar a Products; Docker `active`; contenedor `pedidos360-products` `Up`.
- [ ] ALB interno `Active`; Target Group de Products `Healthy`.
- [ ] VPC Link `pedidos360-vpclink` `Available`.
- [ ] API Gateway y stage `desarrollo` accesibles; JWT y rutas del catálogo sin cambios.
- [ ] GitHub Secrets temporales AWS actualizados **solo si** la sesión del laboratorio rotó credenciales.
- [ ] Probar el frontend y observar `GET /desarrollo/api/products` `200` con sesión autenticada.

### 10.6 Diagnóstico por síntoma (sin modificar todo a la vez)

| Síntoma | Revisar primero | No hacer |
|---|---|---|
| SSM no conecta / timeout 443 | IAM `LabRole`, agente SSM, ruta privada B → NAT y salida HTTPS | Asignar Elastic IP a Products |
| `docker pull` GHCR falla | NAT, DNS, login GHCR y permisos al paquete | Migrar a ECR por reflejo |
| Productos falla con `Connect timed out` a PostgreSQL | **RDS SG entrada 5432 desde Products SG**, VPC/DNS/JDBC | Abrir RDS a `0.0.0.0/0` |
| Target Group `Unhealthy` | Contenedor 8083, `/actuator/health`, SG ALB ↔ Products | Exponer EC2 públicamente |
| API Gateway responde `401` | JWT Authorizer, token/scopes | Cambiar ALB/Docker |
| API Gateway responde `404` | Ruta real, stage `desarrollo`, integración `overwrite:path` | Renombrar rutas Java sin diagnosticar |
| API Gateway responde `502/503/504` | VPC Link, listener, ALB, target, SG, logs | Recrear infraestructura inmediatamente |
| GitHub Actions falla en credenciales AWS | Secrets temporales renovados, permisos SSM | Crear otra instancia pública |
| Catalogo muestra datos viejos | Verificar DevTools Network/Response, stage y tag de imagen | Suponer que el frontend usa un JSON estático |

---

## 11. Cómo crear correctamente el próximo microservicio privado

**Orden obligatorio para Orders, Notifications o Audit.** Antes de crear la EC2, reservar la arquitectura de red y las dependencias para no repetir el problema de IPs públicas, Security Groups y accesos descubiertos tardíamente.

### 11.1 Inventario de requisitos previo

Para cada servicio definir: nombre, puerto, necesidad de RDS, interfaz HTTP o eventos, dependencias RabbitMQ, ruta API, variables de entorno, Dockerfile, workflow de CI/build/deploy, Health Check y SG. Confirmar la VPC, AZ y subred privada seleccionadas; no elegir la VPC default.

### 11.2 Secuencia de implementación

1. **Red:** usar `vpc-06f422a8b206c315c`; determinar subred privada y asegurar ruta a NAT/endpoints; comprobar que la subred del servicio podrá usar SSM y descargar de GHCR.
2. **Identidad:** asegurar un perfil IAM permitido por AWS Academy (`LabInstanceProfile`/SSM) y credenciales GitHub capaces de usar SSM; no abrir SSH público.
3. **Security Groups:** crear `pedidos360-<servicio>-sg` con entrada desde `pedidos360-alb-sg` al puerto del servicio; agregar salida ALB → servicio; habilitar RDS 5432 **solo cuando corresponda**.
4. **EC2 privada:** Amazon Linux 2023 en la subred correcta, **sin IP pública ni EIP**, EBS y Docker con reinicio automático.
5. **Aplicación y BD:** agregar servicio, configuración `APP_SECURITY_MODE=azure`, entidad/esquema propios, migraciones y pruebas; crear snapshot RDS antes de migraciones riesgosas.
6. **CI/CD coherente:** añadir servicio a **`ci.yml`** y **`build-images.yml`**; publicar en GHCR; crear **`deploy-<servicio>-ec2.yml`** independiente con SSM; no reemplazar workflows de los servicios existentes.
7. **Target Group:** target por **instancia**, puerto de Spring, ruta `/actuator/health`, código `200`, comprobar `Healthy`.
8. **ALB interno compartido:** configurar regla explícita basada en ruta al Target Group nuevo; revisar que la acción por defecto no envíe rutas ajenas a Products.
9. **API Gateway existente:** reutilizar `pedidos360-vpclink` e integración privada al ALB; añadir rutas por método, asociar JWT Authorizer y mapeo `overwrite:path = $request.path` si corresponde.
10. **Frontend y validación:** consumir la API vía `https://m5cup771ni.execute-api.us-east-1.amazonaws.com/desarrollo`, comprobar Network `200`, funcionalidad y persistencia; documentar IDs/SG/puertos/resultados.

### 11.3 Matriz de puertos que se irá ampliando

| Microservicio | Puerto previsto | Estado |
|---|---:|---|
| Auth | 8081 | Público heredado; migración futura |
| Cart | 8082 | Público heredado; migración futura |
| Products | 8083 | Privado, desplegado |
| Orders | 8084 (propuesto, no reservado aún) | Pendiente |
| Notifications | A definir | Pendiente |
| Audit | A definir | Pendiente |
| RabbitMQ broker | 5672/15672 (si se utiliza AMQP + consola) | Pendiente; restringir SG y acceso administrativo |

Los puertos propuestos no deben tratarse como una configuración AWS ya creada.

---

## 12. Checklist del proyecto y próximas entregas

Los marcadores `[x]` indican implementación o verificación ya observada. Los `[ ]` son pendientes; no se declaran completados por inferencia.

### Etapa 0 — Base Pedidos360 y adaptación visual

- [x] Frontend React/Vite, Auth Spring Boot, Cart Spring Boot.
- [x] PostgreSQL local y RDS PostgreSQL 16.
- [x] Microsoft Entra ID/MSAL, JWT Authorizer y Spring Security.
- [x] GitHub Actions, GHCR y deploys independientes en EC2.
- [x] API Gateway con stage `desarrollo`; frontend Farmacia Simple.

### Etapa 1 — Catálogo persistente (`0.3.0-rc.2`)

- [x] Products Service con persistencia en `catalogo.products`.
- [x] Endpoints GET catálogo, categorías y detalle; búsqueda/filtro.
- [x] Integración React → API Gateway → Products; DevTools observó `200 OK`.
- [x] Cart: consolidación de SKU por usuario en backend.
- [x] EC2 privada Products, NAT, SSM, SG, RDS, Target Group, ALB interno y VPC Link.
- [x] Pipeline independiente de Products con GHCR y SSM; deploy y health check exitosos.
- [x] CI en `main` con Auth, Cart y Products; Build Images con cuatro imágenes.
- [ ] Comprobar desde el frontend **las seis fichas F001–F006**, búsqueda, categorías, detalle, carrito repetido y persistencia después de recargar.
- [x] Incorporar Products mediante `docker-compose.override.yml` sobre el Compose histórico (validación integral local de 0.4 aún pendiente).
- [ ] Revisar el `docker-compose.prod.yml` histórico o marcarlo como legado para no inducir un despliegue AWS equivocado.
- [ ] Hacer commit/tag y cerrar **`0.3.0` estable** solo cuando se terminen las pruebas funcionales.

### Etapas 2 + 3 — Sucursales, envío y cotización automática (`0.4.0-rc.1`)

- [x] Código preparado: sucursales y stock/precio por sucursal, tarifas y zonas simuladas persistidas en `catalogo`.
- [x] Código preparado: carrito independiente de sucursal, consolidación por usuario+SKU y consultas internas a Products.
- [x] Código preparado: optimizador determinista de precio total y de rapidez, con varios envíos cuando corresponda.
- [x] Código preparado: Frontend con selección de zona y elección entre mejor precio y entrega más rápida.
- [x] Código preparado: pipeline `deploy-cart-ec2.yml` con URL privada al ALB; guía AWS integral de SG, RDS, rutas, JWT y pruebas.
- [ ] Ejecutar **Maven tests Products y Cart** y prueba **Docker Compose integral** en entorno con dependencias completas.
- [ ] Revisar los cambios, hacer commit, push, PR y merge a `main` tras superar pruebas locales.
- [ ] En AWS: backup RDS, autorizar Cart SG en ALB SG:80, configurar `vars.PRODUCTS_INTERNAL_URL`.
- [ ] En API Gateway: agregar rutas JWT `GET /api/delivery-zones`, `GET /api/cart/quote` y confirmar comparación por sucursal.
- [ ] Desplegar **Products → Cart → Frontend**, desde workflows existentes, sin despliegue de Auth.
- [ ] Realizar pruebas funcionales en navegador con stock, envío, precio, rapidez, persistencia y JWT.
- [ ] Cerrar `0.4.0` estable solo después de validación AWS.

### Etapa 4 — Orders Service

- [ ] Crear Orders Service en EC2 **privada** con su SG, Target Group y deploy SSM.
- [ ] Persistir órdenes, líneas, usuario, sucursal, total y estados.
- [ ] API para creación, consulta, historial y seguimiento.
- [ ] Agregar rutas al **mismo** API Gateway, VPC Link y ALB interno.

### Etapa 5 — RabbitMQ y mensajería

- [ ] Incorporar RabbitMQ y acceso privado por Security Groups.
- [ ] Definir exchanges `direct`/`topic`, colas, routing keys y bindings.
- [ ] Implementar consumidores/productores, ACK/NACK, reintentos y DLQ.
- [ ] Crear microservicio o API administrativa para exchanges/colas/bindings, según rúbrica.
- [ ] Documentar topología, eventos y fallos recuperables.

### Etapa 6 — Checkout y pago simulado

- [ ] Reservar/descontar stock de manera consistente con eventos.
- [ ] Simular aprobación/rechazo de PayPal **sin transacciones reales**.
- [ ] Aplicar estados de orden y compensaciones ante rechazo.

### Etapa 7 — Notifications

- [ ] Servicio privado para consumir eventos de órdenes.
- [ ] Notificaciones por email (SMTP/Gmail solo si está autorizado y configurado).
- [ ] Endpoint de prueba mediante Postman, según exigencia académica.
- [ ] Deploy independiente y pruebas de reintento/DLQ.

### Etapa 8 — Audit

- [ ] Servicio privado de auditoría consumidor de eventos RabbitMQ.
- [ ] Persistir trazabilidad de operaciones y estados.
- [ ] API de consulta para evidencias de auditoría.

### Etapa 9 — Clúster y entrega final

- [ ] Desplegar/validar clúster RabbitMQ de dos nodos si lo exige la evaluación.
- [ ] Evidencias de conexiones, exchanges, bindings, colas, fallos y recuperación.
- [ ] Seguridad: migrar Auth/Cart a red privada si hay tiempo y presupuesto; cerrar exposición pública de sus backend.
- [ ] Revisar costo de NAT/ALB, rotación de credenciales, logs, snapshots y recuperación del laboratorio.
- [ ] Preparar demo/presentación final y actualizar arquitectura/README.

**Prioridad:** requisitos evaluados de la entrega → integración funcional → mejoras opcionales. No incorporar OIDC, Cognito, Lambda Authorizer ni sustituir React/GHCR solo por preferencia tecnológica mientras la implementación actual cumple su función.

---

## 13. Historial resumido (Changelog)

| Versión | Cambio principal |
|---|---|
| Primera entrega (Pedidos360) | React, Auth, Cart, MSAL, JWT, PostgreSQL RDS, API Gateway y EC2 públicas con Elastic IP; GitHub Actions + GHCR. |
| `0.2.0` | Identidad visual Farmacia Simple y catálogo de demostración inicial. |
| **`0.3.0-rc.2`** | Products Service persistente, filtros y categorías, actualización de Cart y Frontend; primer backend en EC2 privada con NAT/SSM/ALB/VPC Link; integración AWS funcionando. |
| `0.3.0` | **Pendiente** de validación funcional final y cierre estable. |
| **`0.4.0-rc.1`** | **Código preparado, NO desplegado:** etapas 2+3, precios/stock en cinco sucursales, zonas/tarifas de envío, cotizador automático por precio o rapidez, Cart actualizado y guía AWS preventiva. |

## 14. Convenciones y decisiones que no deben cambiarse sin justificación

1. **Un repositorio GitHub y una imagen GHCR por componente.** No introducir ECR ni otro registro por defecto.
2. **Un workflow de despliegue por microservicio**; el build general publica imágenes y el deploy no modifica otros servicios.
3. **Nuevos backend solo en subred privada**. La IP privada de EC2 sirve para la red de la VPC; no hace falta una Elastic IP para Services.
4. **Administración privada por SSM**, preferentemente sin puerto SSH público. En AWS Academy el deploy actual usa SSM con túnel SSH; no inventar otro procedimiento.
5. **Un ALB interno y un VPC Link reutilizables**, con Target Group y reglas de rutas por nuevo microservicio.
6. **RDS sin acceso público**, Security Groups por referencia `sg-...` y permisos mínimos. Compartir la instancia PostgreSQL no significa compartir tablas entre microservicios.
7. **Mantener el stage real del frontend:** `desarrollo`, hasta que una migración coordinada pruebe `$default`.
8. **Cambios incrementales y verificables**: una modificación, una prueba; commits en español y versión explícita; no tocar servicios que ya funcionan sin requisito.
9. **Nunca almacenar secretos en Git** ni documentar passwords, JWT o claves privadas.
10. **Registrar toda nueva dependencia AWS antes de empezar a configurarla**, con inventario de rutas, subredes, SG, IAM, target, deploy, disponibilidad y costo. Este punto evita repetir los problemas de arquitectura encontrados al introducir Products.

---

## 15. Referencias del proyecto

- Repositorio: https://github.com/flecaros84/pedidos360
- Workflows: https://github.com/flecaros84/pedidos360/tree/main/.github/workflows
- Docker GHCR: https://github.com/flecaros84?tab=packages
- AWS: [API Gateway HTTP API — integraciones privadas](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html)
- AWS: [VPC Link para HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-vpc-links.html)
- AWS: [Session Manager](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html)
- AWS: [Target Groups de ALB](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-target-groups.html)

**Última revisión documental:** 13-10-2026. Los recursos identificados son un registro del laboratorio, no una plantilla de creación automática ni una garantía de persistencia si AWS Academy elimina la infraestructura.
