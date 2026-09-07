# Pedidos360 - Proyecto base Cloud Native

Proyecto base simple para demostrar autenticación con Microsoft Entra ID (Azure AD/MSAL), validación de JWT, backend Spring Boot en AWS EC2, protección adicional mediante AWS API Gateway y persistencia del carrito en una base de datos.

> Estado inicial: **funcional en modo local sin Azure ni AWS**. La integración real con Entra ID y AWS se activa posteriormente mediante variables de entorno, sin cambiar la lógica principal.

## Objetivo

El alcance funcional se mantiene intencionalmente pequeño:

- iniciar/cerrar sesión con Microsoft Entra ID;
- obtener un Access Token desde el frontend;
- enviar el JWT en `Authorization: Bearer ...`;
- validar el JWT en AWS API Gateway;
- validar nuevamente el JWT en Spring Security;
- seleccionar productos de un catálogo fijo;
- guardar y consultar los productos del carrito en BD.

No se implementan pagos, órdenes, inventario ni checkout.

## Arquitectura objetivo

```text
Usuario
  |
  v
Frontend React + Vite + MSAL
  |
  | Access Token JWT
  v
AWS API Gateway (JWT Authorizer)
  |
  +--------------------------+
  |                          |
  v                          v
Auth Service             Cart Service
Spring Boot              Spring Boot
JWT validation           JWT validation
                             |
                             v
                           AWS RDS
```

En desarrollo local se reemplazan temporalmente Azure y AWS por un modo de autenticación de demostración y PostgreSQL en Docker.

## Componentes

```text
pedidos360-base/
├── frontend/                 React + Vite + MSAL preparado
├── auth-service/             Spring Boot - identidad/claims
├── cart-service/             Spring Boot - carrito + JPA
├── .github/workflows/        CI, build de imágenes y deploy EC2
├── scripts/                  utilidades de despliegue
├── docker-compose.yml        ambiente local
├── docker-compose.prod.yml   ambiente AWS/EC2
├── .env.example
└── README.md
```

## Puertos locales

| Componente | URL |
|---|---|
| Frontend | http://localhost |
| Frontend Vite (opcional) | http://localhost:5173 |
| Auth Service | http://localhost:8081 |
| Cart Service | http://localhost:8082 |
| PostgreSQL | localhost:5432 |

## Inicio rápido con Docker

Requisito: Docker con Docker Compose.

```bash
docker compose up --build
```

Abrir:

```text
http://localhost
```

Para detener:

```bash
docker compose down
```

Para eliminar también la BD local:

```bash
docker compose down -v
```

## Modo local

El proyecto parte con:

```text
APP_SECURITY_MODE=local
AUTH_MODE=local
```

En este modo no se requiere Azure. El navegador utiliza el usuario de demostración `local-demo-user` mediante el header `X-Demo-User`.

Esto permite comprobar de inmediato:

1. frontend;
2. comunicación HTTP;
3. endpoints Spring Boot;
4. persistencia JPA;
5. PostgreSQL;
6. Docker;
7. GitHub Actions.

## Endpoints

### Auth Service

```http
GET /api/auth/me
```

Local:

```bash
curl http://localhost:8081/api/auth/me
```

### Cart Service

Consultar carrito:

```http
GET /api/cart
```

Agregar producto:

```http
POST /api/cart/items
Content-Type: application/json
```

```json
{
  "productCode": "P001",
  "productName": "Notebook",
  "quantity": 1
}
```

Eliminar producto:

```http
DELETE /api/cart/items/{id}
```

## Cambio a Microsoft Entra ID

Cuando se cree el App Registration se completarán estas variables:

```env
APP_SECURITY_MODE=azure
AZURE_TENANT_ID=<tenant-id>
AZURE_CLIENT_ID=<application-client-id>
AZURE_ISSUER_URI=<valor-exacto-del-claim-iss>
AZURE_AUDIENCE=<valor-exacto-del-claim-aud>
AZURE_API_SCOPE=api://<application-client-id>/<scope>
AZURE_REDIRECT_URI=http://localhost:5173
```

El backend Azure valida:

- firma del token usando las claves públicas de Microsoft;
- issuer exacto (`iss`);
- audience;
- expiración;
- autenticación del request.

El `Auth Service` devuelve además `oid/sub`, scopes y roles presentes en el token para demostrar la lectura de claims.

## Base de datos

En local se utiliza PostgreSQL 16 en Docker.

Tabla generada por JPA:

```text
cart_item
---------
id
user_id
product_code
product_name
quantity
created_at
```

En AWS solo se cambian:

```env
DATABASE_URL=jdbc:postgresql://<rds-endpoint>:5432/pedidos360
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
```

## GitHub Actions

El proyecto incluye tres workflows.

### 1. CI - `.github/workflows/ci.yml`

Se ejecuta en cada `push` y `pull_request`.

Comprueba:

- build del frontend;
- tests de `auth-service`;
- tests de `cart-service`.

### 2. Build Images - `.github/workflows/build-images.yml`

En cada push a `main` y también manualmente:

- construye las tres imágenes Docker;
- publica imágenes en GitHub Container Registry (GHCR);
- etiqueta cada imagen como `latest` y con el SHA del commit.

Imágenes:

```text
ghcr.io/<usuario>/pedidos360-frontend
ghcr.io/<usuario>/pedidos360-auth
ghcr.io/<usuario>/pedidos360-cart
```

### 3. Deploy EC2 - `.github/workflows/deploy-ec2.yml`

El despliegue es manual mediante `workflow_dispatch`.

Esto es intencional para trabajar con laboratorios AWS temporales: al crear una nueva EC2 basta actualizar los Secrets y volver a ejecutar el workflow.

Secrets esperados:

```text
EC2_HOST
EC2_USER
EC2_SSH_PRIVATE_KEY
GHCR_TOKEN

AZURE_TENANT_ID
AZURE_CLIENT_ID
AZURE_ISSUER_URI
AZURE_AUDIENCE
AZURE_API_SCOPE
AZURE_REDIRECT_URI

AUTH_API_URL
CART_API_URL
APP_CORS_ALLOWED_ORIGINS

DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

`GHCR_TOKEN` puede ser un Personal Access Token con permiso `read:packages` si las imágenes no son públicas.

## Preparación de una EC2 nueva

El repositorio incluye:

```text
scripts/bootstrap-ec2.sh
```

Copiarlo o ejecutarlo en una EC2 limpia para instalar Docker y habilitar el servicio.

Después, el workflow `Deploy EC2` puede encargarse de copiar el `docker-compose.prod.yml`, generar `.env`, descargar las imágenes y levantar los contenedores.

## Roadmap de desarrollo

### Etapa 1 - Base local

- [x] React + Vite.
- [x] Auth Service Spring Boot.
- [x] Cart Service Spring Boot.
- [x] PostgreSQL local.
- [x] Docker Compose.
- [x] CRUD mínimo de carrito.
- [x] modo local sin Azure.

### Etapa 2 - GitHub Actions

- [x] CI.
- [x] build de imágenes Docker.
- [x] publicación en GHCR.
- [x] workflow manual de despliegue EC2.

### Etapa 3 - Microsoft Entra ID

- [ ] crear App Registration;
- [ ] configurar SPA Redirect URI;
- [ ] exponer scope de la API;
- [ ] completar Tenant ID / Client ID / Scope;
- [ ] probar login/logout MSAL;
- [ ] obtener Access Token.

### Etapa 4 - JWT local

- [ ] activar `APP_SECURITY_MODE=azure`;
- [ ] comprobar 401 sin token;
- [ ] comprobar 401 con token inválido;
- [ ] comprobar 200 con token válido;
- [ ] verificar `iss`, `aud`, `exp`, scopes y roles.

### Etapa 5 - AWS RDS

- [ ] crear PostgreSQL RDS;
- [ ] configurar Security Group;
- [ ] probar conexión desde Cart Service.

### Etapa 6 - AWS EC2

- [ ] crear EC2;
- [ ] instalar Docker;
- [ ] configurar GitHub Secrets;
- [ ] ejecutar `Deploy EC2`;
- [ ] comprobar servicios.

### Etapa 7 - AWS API Gateway

- [ ] crear HTTP API;
- [ ] integrar `/api/auth/*` con Auth Service;
- [ ] integrar `/api/cart/*` con Cart Service;
- [ ] configurar CORS.

### Etapa 8 - JWT Authorizer

- [ ] configurar issuer de Entra ID;
- [ ] configurar audience;
- [ ] proteger rutas;
- [ ] probar 401/200 desde API Gateway.

### Etapa 9 - Frontend público

- [ ] desplegar frontend en EC2/Nginx;
- [ ] actualizar Redirect URI de Azure;
- [ ] apuntar frontend a API Gateway;
- [ ] prueba end-to-end.

## Flujo final esperado

```text
Login Azure
   ↓
React + MSAL
   ↓
JWT
   ↓
AWS API Gateway
   ↓
JWT Authorizer
   ↓
Spring Security
   ↓
Cart Service
   ↓
AWS RDS
```

## Nota sobre seguridad

El modo `local` existe exclusivamente para desarrollo. En el despliegue evaluado debe utilizarse:

```env
APP_SECURITY_MODE=azure
AUTH_MODE=azure
```

Nunca subir `.env`, contraseñas, private keys o tokens al repositorio.
