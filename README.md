# Pedidos360 - Proyecto Cloud Native

Proyecto académico orientado a demostrar una arquitectura Cloud Native simple con autenticación mediante **Microsoft Entra ID (Azure AD/MSAL)**, validación de JWT, microservicios Spring Boot desplegados en AWS EC2, protección mediante AWS API Gateway y persistencia del carrito en PostgreSQL mediante AWS RDS.

> Estado actual: **funcional en ambiente local y desplegado completamente en AWS con autenticación Microsoft Entra ID**.

---

## Objetivo

El alcance funcional se mantiene intencionalmente pequeño para concentrar el proyecto en autenticación, seguridad, despliegue y comunicación entre servicios.

El sistema permite:

- iniciar y cerrar sesión con Microsoft Entra ID;
- obtener un Access Token mediante MSAL;
- enviar el JWT en `Authorization: Bearer ...`;
- validar el JWT en AWS API Gateway;
- validar nuevamente el JWT en Spring Security;
- obtener los datos del usuario autenticado;
- seleccionar productos desde un catálogo fijo;
- agregar productos al carrito;
- consultar los productos almacenados;
- eliminar productos del carrito;
- persistir la información en PostgreSQL mediante AWS RDS.

No se implementan pagos, órdenes, inventario ni checkout.

---

## Arquitectura

```text
                         Usuario
                            |
                            v
                  Frontend React + Vite
                        + MSAL
                            |
                            | Access Token JWT
                            v
                  AWS API Gateway HTTP API
                      JWT Authorizer
                            |
                 +----------+----------+
                 |                     |
                 v                     v
          Auth Service            Cart Service
           Spring Boot             Spring Boot
         Spring Security         Spring Security
         JWT Validation          JWT Validation
                                       |
                                       v
                              PostgreSQL AWS RDS
```

Los tres componentes principales se encuentran desplegados en **instancias EC2 independientes**:

```text
Frontend EC2
   |
   +-- Nginx
   +-- React/Vite

Auth EC2
   |
   +-- Docker
   +-- Spring Boot
   +-- Puerto 8081

Cart EC2
   |
   +-- Docker
   +-- Spring Boot
   +-- Puerto 8082
          |
          v
     PostgreSQL RDS
```

Cada instancia EC2 utiliza una **Elastic IP**, evitando que la dirección pública cambie al detener y volver a iniciar la instancia.

---

## Componentes

```text
pedidos360-base/
├── frontend/                    React + Vite + MSAL
├── auth-service/                Spring Boot - identidad y claims JWT
├── cart-service/                Spring Boot - carrito + JPA
├── .github/
│   └── workflows/
│       ├── ci.yml
│       ├── build-images.yml
│       ├── deploy-auth-ec2.yml
│       ├── deploy-cart-ec2.yml
│       └── deploy-frontend-ec2.yml
├── scripts/
├── docker-compose.yml           ambiente local
├── docker-compose.prod.yml      ambiente AWS
├── .env.example
└── README.md
```

---

## Tecnologías utilizadas

### Frontend

- React
- Vite
- Microsoft Authentication Library (MSAL)
- Nginx
- Docker

### Backend

- Java
- Spring Boot
- Spring Security
- OAuth2 Resource Server
- Spring Data JPA
- Docker

### Cloud

- Microsoft Entra ID
- AWS EC2
- AWS API Gateway
- AWS RDS PostgreSQL
- AWS VPC
- AWS Security Groups
- AWS Elastic IP

### DevOps

- Git
- GitHub
- GitHub Actions
- GitHub Container Registry (GHCR)
- Docker

---

## Ambiente productivo

La aplicación se encuentra publicada mediante AWS API Gateway.

```text
https://m5cup771ni.execute-api.us-east-1.amazonaws.com/desarrollo/
```

API Gateway funciona como punto de entrada tanto para el frontend como para los servicios backend.

```text
/desarrollo/
    |
    +-- /                       -> Frontend EC2
    |
    +-- /api/auth/me            -> Auth EC2 :8081
    |
    +-- /api/cart               -> Cart EC2 :8082
    |
    +-- /api/cart/items         -> Cart EC2 :8082
    |
    +-- /api/cart/items/{id}    -> Cart EC2 :8082
```

Las rutas backend se encuentran protegidas mediante JWT Authorizer.

---

## Microsoft Entra ID

La autenticación utiliza una App Registration de Microsoft Entra ID.

El frontend utiliza MSAL para iniciar sesión y obtener un Access Token.

Flujo:

```text
Usuario
   |
   v
Microsoft Entra ID
   |
   | Access Token
   v
React + MSAL
   |
   | Authorization: Bearer <JWT>
   v
AWS API Gateway
```

El scope utilizado por Pedidos360 es:

```text
api://a561d8ef-b187-4bf0-9555-8f66b445df2e/pedidos360.access
```

El backend valida, entre otros elementos:

- firma digital del token;
- issuer (`iss`);
- audience (`aud`);
- expiración;
- autenticación del request;
- scopes presentes en el token.

El `Auth Service` permite además mostrar información obtenida desde los claims del JWT, como:

- identificador del usuario;
- nombre;
- scopes;
- roles.

---

## Seguridad JWT

La validación se realiza en dos niveles.

```text
Access Token
     |
     v
AWS API Gateway
JWT Authorizer
     |
     | token válido
     v
Spring Security
OAuth2 Resource Server
     |
     v
Endpoint
```

Esto permite demostrar que un request sin un JWT válido no puede acceder directamente a los endpoints protegidos.

Por ejemplo:

```http
GET /api/auth/me
```

sin token:

```text
401 Unauthorized
```

con token válido:

```text
200 OK
```

---

## Endpoints

### Auth Service

Obtener información del usuario autenticado:

```http
GET /api/auth/me
Authorization: Bearer <access-token>
```

Respuesta de ejemplo:

```json
{
  "authenticated": true,
  "userId": "...",
  "name": "Usuario",
  "scopes": [
    "pedidos360.access"
  ],
  "roles": []
}
```

---

### Cart Service

### Consultar carrito

```http
GET /api/cart
Authorization: Bearer <access-token>
```

---

### Agregar producto

```http
POST /api/cart/items
Authorization: Bearer <access-token>
Content-Type: application/json
```

Ejemplo:

```json
{
  "productCode": "P001",
  "productName": "Notebook",
  "quantity": 1
}
```

Respuesta esperada:

```text
201 Created
```

---

### Eliminar producto

```http
DELETE /api/cart/items/{id}
Authorization: Bearer <access-token>
```

Respuesta esperada:

```text
204 No Content
```

---

## Base de datos

En producción se utiliza:

```text
AWS RDS
PostgreSQL 16
```

Base de datos:

```text
pedidos360
```

La instancia RDS **no posee acceso público**.

Solamente el `Cart Service` puede conectarse directamente a PostgreSQL mediante las reglas configuradas en los Security Groups.

Endpoint:

```text
pedidos360-db.cvmuxunht7ru.us-east-1.rds.amazonaws.com
```

Puerto:

```text
5432
```

La conexión utilizada por Spring Boot tiene la forma:

```env
DATABASE_URL=jdbc:postgresql://pedidos360-db.cvmuxunht7ru.us-east-1.rds.amazonaws.com:5432/pedidos360
DATABASE_USERNAME=pedidos360
DATABASE_PASSWORD=<secret>
```

La contraseña se almacena como **GitHub Secret** y nunca debe almacenarse en el repositorio.

---

## Tabla del carrito

La tabla principal es:

```text
cart_item
```

Estructura:

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

Cada registro queda asociado al identificador del usuario autenticado obtenido desde el JWT.

---

## Desarrollo local

El proyecto continúa soportando ejecución completamente local.

En este ambiente se utiliza:

```text
APP_SECURITY_MODE=local
AUTH_MODE=local
```

En modo local no se requiere Microsoft Entra ID.

El frontend utiliza un usuario de demostración:

```text
local-demo-user
```

mediante:

```text
X-Demo-User
```

Esto permite probar:

1. frontend;
2. comunicación HTTP;
3. endpoints Spring Boot;
4. persistencia JPA;
5. PostgreSQL;
6. Docker;
7. GitHub Actions.

---

## Puertos locales

| Componente | Dirección |
|---|---|
| Frontend Docker | `http://localhost` |
| Frontend Vite | `http://localhost:5173` |
| Auth Service | `http://localhost:8081` |
| Cart Service | `http://localhost:8082` |
| PostgreSQL | `localhost:5432` |

---

## Inicio rápido local con Docker

Requisito:

```text
Docker + Docker Compose
```

Levantar el ambiente:

```bash
docker compose up --build
```

Abrir:

```text
http://localhost
```

Detener:

```bash
docker compose down
```

Eliminar también la base de datos local:

```bash
docker compose down -v
```

---

## Configuración Azure

Variables utilizadas por los servicios:

```env
APP_SECURITY_MODE=azure
AUTH_MODE=azure

AZURE_TENANT_ID=<tenant-id>
AZURE_CLIENT_ID=<application-client-id>
AZURE_ISSUER_URI=<issuer>
AZURE_AUDIENCE=<audience>

AZURE_API_SCOPE=api://<application-client-id>/pedidos360.access
```

En producción, el Redirect URI corresponde al frontend publicado mediante API Gateway:

```text
https://m5cup771ni.execute-api.us-east-1.amazonaws.com/desarrollo/
```

---

## AWS API Gateway

Se utiliza una **HTTP API** denominada:

```text
pedidos360-api
```

Stage:

```text
desarrollo
```

Las rutas principales son:

```text
GET    /api/auth/me
GET    /api/cart
POST   /api/cart/items
DELETE /api/cart/items/{id}
GET    /
ANY    /{proxy+}
```

Las rutas `/api/...` se encuentran protegidas por un JWT Authorizer configurado con Microsoft Entra ID.

Las rutas `/` y `/{proxy+}` permiten servir el frontend mediante Nginx.

---

## Persistencia

El flujo completo de almacenamiento es:

```text
Usuario
   |
   v
React
   |
   | JWT
   v
API Gateway
   |
   v
Cart Service
   |
   | Spring Data JPA
   v
PostgreSQL RDS
```

Los registros permanecen almacenados en RDS aunque los contenedores o las instancias EC2 se reinicien.

---

# GitHub Actions

El proyecto utiliza cinco workflows.

---

## 1. CI

Archivo:

```text
.github/workflows/ci.yml
```

Se utiliza para validar el código.

Comprueba:

- build del frontend;
- tests de `auth-service`;
- tests de `cart-service`.

Se ejecuta automáticamente ante cambios definidos por el workflow.

---

## 2. Build Images

Archivo:

```text
.github/workflows/build-images.yml
```

Construye las tres imágenes Docker:

```text
pedidos360-frontend
pedidos360-auth
pedidos360-cart
```

y las publica en GitHub Container Registry:

```text
ghcr.io/flecaros84/pedidos360-frontend
ghcr.io/flecaros84/pedidos360-auth
ghcr.io/flecaros84/pedidos360-cart
```

Las imágenes pueden etiquetarse como:

```text
latest
```

y mediante el SHA del commit correspondiente.

---

## 3. Deploy Auth EC2

Archivo:

```text
.github/workflows/deploy-auth-ec2.yml
```

Actualiza únicamente:

```text
auth-service
```

en su EC2.

---

## 4. Deploy Cart EC2

Archivo:

```text
.github/workflows/deploy-cart-ec2.yml
```

Actualiza únicamente:

```text
cart-service
```

en su EC2.

---

## 5. Deploy Frontend EC2

Archivo:

```text
.github/workflows/deploy-frontend-ec2.yml
```

Actualiza únicamente:

```text
frontend
```

en su EC2.

---

## Flujo de despliegue

Al modificar código:

```text
Desarrollo local
      |
      v
git commit
      |
      v
git push
      |
      v
CI
      |
      v
Build Docker Images
      |
      v
GHCR
      |
      +-------------------------+
      |            |            |
      v            v            v
 Deploy Auth   Deploy Cart   Deploy Frontend
      |            |            |
      v            v            v
   Auth EC2      Cart EC2    Frontend EC2
```

No es necesario desplegar todos los componentes después de cada cambio.

Por ejemplo:

```text
Cambio en frontend
→ Deploy Frontend EC2

Cambio en auth-service
→ Deploy Auth EC2

Cambio en cart-service
→ Deploy Cart EC2
```

Si un cambio afecta a los tres componentes, se pueden ejecutar los tres workflows.

---

## GitHub Secrets

La configuración sensible se mantiene fuera del repositorio mediante GitHub Secrets.

Entre las variables utilizadas se encuentran:

```text
EC2_USER
EC2_SSH_PRIVATE_KEY

FRONTEND_EC2_HOST
AUTH_EC2_HOST
CART_EC2_HOST

AZURE_TENANT_ID
AZURE_CLIENT_ID
AZURE_ISSUER_URI
AZURE_AUDIENCE
AZURE_API_SCOPE

DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

También se utiliza:

```text
API_GATEWAY_BASE_URL
FRONTEND_ORIGIN
```

como variables de configuración del repositorio.

Nunca deben almacenarse en Git:

- contraseñas;
- Access Tokens;
- Private Keys;
- archivos `.pem`;
- secretos de Azure;
- credenciales AWS.

---

## Elastic IP

Las instancias EC2 utilizan Elastic IP para evitar que la IP pública cambie al detener y volver a iniciar una máquina.

La infraestructura utiliza una IP independiente para:

```text
Frontend EC2
Auth EC2
Cart EC2
```

El uso de Elastic IP permite que:

- API Gateway mantenga sus integraciones;
- GitHub Actions pueda seguir conectándose mediante SSH;
- no sea necesario modificar los hosts después de cada reinicio.

Después de iniciar una instancia EC2 puede existir un breve período en que Docker y Spring Boot todavía estén iniciando.

Durante ese intervalo API Gateway puede responder temporalmente:

```text
503 Service Unavailable
```

Una vez iniciado el contenedor correspondiente, el servicio vuelve a responder normalmente.

---

# Estado de implementación

## Etapa 1 - Base local

- [x] React + Vite.
- [x] Auth Service Spring Boot.
- [x] Cart Service Spring Boot.
- [x] PostgreSQL local.
- [x] Docker Compose.
- [x] CRUD mínimo de carrito.
- [x] modo local sin Azure.

## Etapa 2 - GitHub Actions

- [x] CI.
- [x] build de imágenes Docker.
- [x] publicación en GHCR.
- [x] deploy independiente por componente.

## Etapa 3 - Microsoft Entra ID

- [x] crear App Registration.
- [x] configurar SPA Redirect URI.
- [x] exponer scope de la API.
- [x] configurar Tenant ID y Client ID.
- [x] probar login/logout MSAL.
- [x] obtener Access Token.

## Etapa 4 - JWT

- [x] activar `APP_SECURITY_MODE=azure`.
- [x] comprobar rechazo sin token.
- [x] comprobar acceso con token válido.
- [x] validar issuer.
- [x] validar audience.
- [x] validar expiración.
- [x] obtener scopes y claims del JWT.

## Etapa 5 - AWS RDS

- [x] crear PostgreSQL RDS.
- [x] configurar Security Group.
- [x] conectar Cart Service.
- [x] persistir información.

## Etapa 6 - AWS EC2

- [x] crear EC2 para Frontend.
- [x] crear EC2 para Auth Service.
- [x] crear EC2 para Cart Service.
- [x] instalar Docker.
- [x] configurar Elastic IP.
- [x] configurar GitHub Secrets.
- [x] desplegar servicios mediante GitHub Actions.

## Etapa 7 - AWS API Gateway

- [x] crear HTTP API.
- [x] configurar stage `desarrollo`.
- [x] integrar Auth Service.
- [x] integrar Cart Service.
- [x] integrar Frontend.
- [x] configurar CORS.

## Etapa 8 - JWT Authorizer

- [x] configurar issuer de Entra ID.
- [x] configurar audience.
- [x] proteger rutas backend.
- [x] comprobar respuestas `401`.
- [x] comprobar respuestas `200` con JWT válido.

## Etapa 9 - Frontend público

- [x] desplegar frontend en EC2/Nginx.
- [x] actualizar Redirect URI.
- [x] conectar frontend con API Gateway.
- [x] probar autenticación.
- [x] probar consulta del carrito.
- [x] probar inserción de productos.
- [x] probar eliminación de productos.
- [x] comprobar persistencia en RDS.

---

# Flujo final

```text
Microsoft Entra ID
       |
       | Login
       v
React + MSAL
       |
       | Access Token JWT
       v
AWS API Gateway
       |
       | JWT Authorizer
       v
Spring Security
       |
       +----------------------+
       |                      |
       v                      v
 Auth Service             Cart Service
                              |
                              | JPA
                              v
                        PostgreSQL RDS
```

---

# Nota de seguridad

El modo:

```env
APP_SECURITY_MODE=local
AUTH_MODE=local
```

existe exclusivamente para desarrollo.

En AWS se utiliza:

```env
APP_SECURITY_MODE=azure
AUTH_MODE=azure
```

La instancia RDS no es pública y solo acepta conexiones provenientes de la infraestructura autorizada.

El acceso SSH abierto utilizado durante el laboratorio corresponde a una simplificación académica y no representa una configuración recomendada para producción.

Nunca deben subirse al repositorio:

```text
.env
*.pem
contraseñas
Access Tokens
credenciales AWS
secretos de Azure
```

---

# Acceso directo a PostgreSQL RDS

La base de datos RDS se encuentra en una subnet privada y **no acepta conexiones directamente desde Internet**.

Para revisar los datos existen dos métodos.

---

## Método 1 - Túnel SSH desde Windows

Este es el método recomendado para revisar la base utilizando herramientas como:

- DBeaver;
- pgAdmin;
- `psql`.

Desde PowerShell, en la carpeta donde se encuentra:

```text
pedidos360-key.pem
```

crear el túnel:

```powershell
ssh -i .\pedidos360-key.pem `
  -L 15432:pedidos360-db.cvmuxunht7ru.us-east-1.rds.amazonaws.com:5432 `
  ec2-user@44.209.83.71
```

Este comando utiliza la EC2 de `cart-service` como puente hacia RDS.

La ventana SSH debe permanecer abierta mientras se utiliza la conexión.

### Configuración en DBeaver o pgAdmin

Utilizar:

```text
Host: localhost
Port: 15432
Database: pedidos360
Username: pedidos360
Password: <DATABASE_PASSWORD>
```

No utilizar directamente el endpoint RDS como host en DBeaver, ya que la instancia RDS no tiene acceso público.

La conexión queda:

```text
PC
 |
 | localhost:15432
 v
Túnel SSH
 |
 v
Cart EC2
 |
 v
RDS :5432
```

### Con psql desde Windows

Con el túnel SSH abierto:

```powershell
psql -h localhost -p 15432 -U pedidos360 -d pedidos360
```

PostgreSQL solicitará la contraseña.

---

## Método 2 - Consultar RDS desde Cart EC2

Conectarse a la instancia EC2 de Cart:

```powershell
ssh -i .\pedidos360-key.pem ec2-user@44.209.83.71
```

Una vez dentro, se puede utilizar temporalmente el cliente PostgreSQL mediante Docker:

```bash
docker run --rm -it postgres:16-alpine \
  psql \
  -h pedidos360-db.cvmuxunht7ru.us-east-1.rds.amazonaws.com \
  -p 5432 \
  -U pedidos360 \
  -d pedidos360
```

PostgreSQL solicitará:

```text
Password for user pedidos360:
```

Ingresar el valor correspondiente a `DATABASE_PASSWORD`.

La contraseña no debe escribirse directamente en el comando ni almacenarse en el README.

---

## Consultas útiles

Una vez dentro de PostgreSQL:

### Listar tablas

```sql
\dt
```

### Revisar estructura de la tabla

```sql
\d cart_item
```

### Consultar todo el carrito

```sql
SELECT *
FROM cart_item
ORDER BY id;
```

### Mostrar campos principales

```sql
SELECT
    id,
    user_id,
    product_code,
    product_name,
    quantity,
    created_at
FROM cart_item
ORDER BY id;
```

### Contar registros

```sql
SELECT COUNT(*)
FROM cart_item;
```

### Revisar productos agrupados por usuario

```sql
SELECT
    user_id,
    product_name,
    quantity,
    created_at
FROM cart_item
ORDER BY user_id, created_at;
```

Para salir de PostgreSQL:

```text
\q
```

---

## Importante

La revisión directa de RDS debe utilizarse principalmente para:

- comprobar que JPA está persistiendo datos;
- verificar registros durante pruebas;
- validar el funcionamiento de `cart-service`;
- demostrar persistencia independiente de las EC2.

Las operaciones normales de la aplicación deben realizarse mediante:

```text
Frontend
   ↓
API Gateway
   ↓
Cart Service
   ↓
RDS
```

y no modificando manualmente la base de datos.