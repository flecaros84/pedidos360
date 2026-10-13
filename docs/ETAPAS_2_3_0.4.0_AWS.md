# Farmacia Simple — Etapas 2 y 3 (`0.4.0-rc.1`)

> **Tipo:** guía integral de despliegue AWS; leer COMPLETA antes de cambiar la consola. 
> **Objetivo:** precios/stock por sucursal, selección automática de despachos más baratos o más rápidos, con Cart ya existente.
> **Estado:** preparada; **no desplegada ni validada en AWS en esta entrega de código**.
> **Región:** `us-east-1`. **Repositorio:** `flecaros84/pedidos360`.

## 0. Decisiones que no cambian

- Código GitHub; imágenes **GHCR**; build y deploy con **GitHub Actions**; no ECR.
- Frontend EC2 pública existente; Auth y Cart EC2 públicas históricas, sin migración en esta etapa.
- Products EC2 **privada** `i-03a235c680f1ad939`, subred `pedidos360-private-b`, SSM y NAT existentes.
- RDS PostgreSQL existente, sin segunda base; `catalogo` para Products, `public.cart_item` para Cart.
- ALB **interno** `pedidos360-internal-alb` con listener HTTP:80 y target group `pedidos360-products-tg` → Products:8083, sano (`Healthy` antes de la etapa).
- API Gateway HTTP API `m5cup771ni`, VPC Link `pedidos360-vpclink` (`ajo8dg`) y JWT Microsoft Entra ID existentes.
- No se crean nuevas EC2, NAT, RDS, ALB, VPC Link, Target Groups ni repositorios GHCR.
- Tarifas y tiempos de entrega son datos **simulados** y configurables; no son promesas comerciales. No se descuenta stock al cotizar.

## 1. Inventario de recursos y dependencias

| Recurso | Identificación | ¿Crear/modificar? |
|---|---|---|
| VPC | `vpc-06f422a8b206c315c`, `10.0.0.0/16` | Existente, NO tocar |
| Subred Products | `pedidos360-private-b`, `subnet-0a205bb35d0802b09`, `us-east-1b` | Existente, NO tocar |
| Tabla de rutas Products | `pedidos360-private-b-rt`, `0.0.0.0/0` vía `pedidos360-nat-b` | Existente, NO tocar |
| NAT | `pedidos360-nat-b` en pública B; EIP propia | Existente, NO tocar |
| Cart EC2 | EC2 pública actual en misma VPC, puerto 8082 | Actualizar contenedor y env |
| Products EC2 | EC2 privada actual, puerto 8083 | Actualizar contenedor; JPA crea tablas aditivas |
| Frontend EC2 | EC2 pública actual, HTTP:80 | Actualizar contenedor |
| RDS | PostgreSQL existente, puerto 5432, SG `pedidos360-rds-sg` | Respaldar antes del despliegue; no abrir a Internet |
| ALB | `pedidos360-internal-alb`, listener HTTP:80 | **Autorizar Cart SG como origen** |
| Target Group | `pedidos360-products-tg`, HTTP:8083, health `/actuator/health` | Reutilizar |
| VPC Link | `pedidos360-vpclink`, ID `ajo8dg` | Reutilizar |
| API Gateway | `m5cup771ni` | Agregar rutas nuevas JWT |
| Build GHCR | `.github/workflows/build-images.yml` | Reutilizar |
| Deploy Products | `.github/workflows/deploy-products-ec2.yml`, SSM | Reutilizar |
| Deploy Cart | `.github/workflows/deploy-cart-ec2.yml` | **Actualizar con `PRODUCTS_INTERNAL_URL`** |
| Deploy Frontend | `.github/workflows/deploy-frontend-ec2.yml` | Reutilizar |

**No inventar IPs privadas de EC2:** utilizar DNS privado estable del ALB. En EC2, detención/inicio conserva IP privada principal; si el laboratorio **termina** recursos, habrá que recrearlos.

## 2. Matriz de Security Groups — hacer ANTES de desplegar Cart

| Origen | Destino | Puerto | Acción | Motivo |
|---|---|---|---|---|
| SG de Cart EC2 existente (identificar en EC2 → Instancias → Cart → Seguridad) | **`pedidos360-alb-sg`** | TCP 80 | **Agregar regla de entrada al ALB SG**; mantener la existente desde VPC Link | Cart podrá consultar Products por ALB interno |
| SG de Cart EC2 | `pedidos360-alb-sg` | TCP 80 | Verificar salida de Cart: si ya permite todo, no cambiar; si está restringida, permitir TCP 80 hacia ALB SG | Conectividad Cart → ALB |
| `pedidos360-vpclink-sg` | `pedidos360-alb-sg` | TCP 80 | Mantener entrada existente al ALB | API Gateway → ALB |
| `pedidos360-alb-sg` | `pedidos360-products-sg` | TCP 8083 | Mantener salida ALB y entrada Products existentes | ALB → Products |
| `pedidos360-products-sg` | `pedidos360-rds-sg` | TCP 5432 | Mantener entrada RDS desde Products SG | Persistencia Products |
| SG de Cart existente | `pedidos360-rds-sg` | TCP 5432 | Mantener entrada RDS existente desde Cart SG | Persistencia Cart |
| Internet | `pedidos360-products-sg` | Cualquiera | **NO permitir** | Products debe seguir privada |

**No crear nuevos Security Groups para esta etapa.** Editar únicamente ALB SG para agregar como origen el SG ya existente de Cart, **sin eliminar la entrada existente desde VPC Link**. Para las conexiones de Cart a Products, no dar acceso directo de Cart al puerto 8083 de Products: usar ALB interno.

## 3. Datos / RDS — hacer respaldo ANTES de la primera actualización

1. Confirmar que Products y Cart usen el endpoint **privado** de la RDS existente y que ambos puedan resolverla.
2. Tomar snapshot de RDS (si AWS Academy lo permite) o backup lógico consistente antes de desplegar. Documentar su nombre y la hora.
3. En Products, el modo actual `DATABASE_DDL_AUTO=update` agregará en `catalogo` las tablas `branches`, `branch_inventory`, `shipping_rates` y sus relaciones si aún no existen. El `schema.sql` crea `catalogo` si hace falta. No se eliminan tablas.
4. En Cart, `CartDataMigration` conserva/consolida por `(user_id, product_code)` y **ya no fuerza sucursal**. La columna heredada `branch_code` se mantiene por compatibilidad, pero no gobierna el carrito.
5. `CATALOG_SEED_DEMO=true` ya figura en el despliegue Products; las cinco sucursales, existencias y tarifas simuladas se insertan **solo si faltan**. No modifica precios o stock configurados previamente.
6. Las tarifas/tiempos reales no existen en esta versión. Tabla `catalogo.shipping_rates`: `branch_code`, `zone_code`, `cost`, `delivery_days`. Para alterar precios/plazos hay que actualizar la BD de demostración y registrar el cambio.

Sucursales demo: `PM-CENTRO`, `PM-ALERCE`, `PV-CENTRO`, `FRUTILLAR`, `CALBUCO`. Zonas: `PM`, `ALERCE`, `PV`, `FR`, `CAL`. Todos los montos son CLP.

## 4. Comunicación privada Cart → Products

**Clave:** la Cart EC2 histórica es pública, pero se encuentra en nuestra VPC y puede llamar al DNS **privado del ALB interno** por TCP 80 si el Security Group lo permite. Products conserva su aislamiento y validación JWT.

1. En AWS EC2 → Balanceadores → `pedidos360-internal-alb` → **Detalles**, copiar el valor completo **Nombre de DNS**. No copiar dirección IP ni hostname de Products. Ejemplo estructural (NO usar literalmente): `internal-pedidos360-internal-alb-<id>.us-east-1.elb.amazonaws.com`.
2. En GitHub → `Settings → Secrets and variables → Actions → Variables`, crear **`PRODUCTS_INTERNAL_URL`** con valor `http://<DNS_INTERNO_DEL_ALB>` **sin barra final ni `/api/products`**.
3. El workflow de Cart ya entregado inserta esa variable en `~/cart.env` como `PRODUCTS_INTERNAL_URL`. No duplicar configuración de Auth/Products.
4. Products comprueba tokens JWT entrantes cuando `APP_SECURITY_MODE=azure`. Cart **reenvía el Bearer JWT recibido del usuario** al hacer peticiones internas al ALB; no se crean tokens técnicos falsos.
5. El ALB hace `HTTP:80 → target group Products :8083`; mantener health check `GET /actuator/health = 200`.

Esto no precisa VPC Link nuevo, IP elástica ni acceso Internet a Products. El Security Group de Cart podría tener salida amplia histórica; si es así, no hace falta otra regla de salida.

## 5. API Gateway — rutas nuevas y existentes necesarias

En API Gateway → `m5cup771ni` → **Rutas**, asociar a la **misma integración privada Products** (`pedidos360-vpclink` → `pedidos360-internal-alb` HTTP:80), asignando el **JWT Authorizer existente de Microsoft Entra ID**:

| Método y ruta | Destino | Motivo |
|---|---|---|
| `GET /api/products` | Integración privada Products | Ya existe: listado y stock agregado |
| `GET /api/products/categories` | Integración privada Products | Ya existe |
| `GET /api/products/{id}` | Integración privada Products | Ya existe |
| `GET /api/products/{id}/availability` | Integración privada Products | **Verificar o crear**; comparación manual en UI |
| `GET /api/branches` | Integración privada Products | Opcional si se desea exponer sucursales por API |
| **`GET /api/delivery-zones`** | Integración privada Products | **Nueva: crear**, selección zona despacho |

Solo dentro de Cart (a través del ALB y JWT reenviado) funcionan las rutas `/api/products/offers` y `/api/delivery-zones/{zoneCode}/rates`; **no es obligatorio publicarlas a clientes mediante API Gateway**.

Para Cart, utilizar la **integración pública existente de Cart**, no VPC Link de Products, y JWT Authorizer existente:

| Método y ruta | Destino | Motivo |
|---|---|---|
| `GET /api/cart` | Cart | Ya existe |
| `POST /api/cart/items` | Cart | Ya existe |
| `DELETE /api/cart/items/{id}` | Cart | Ya existe |
| `DELETE /api/cart` | Cart | Ya existe si se activó para vaciar |
| **`GET /api/cart/quote`** | Cart | **Nueva: crear**, con `?zoneCode=PM` |

**Mapeo de la integración privada Products:** conservar `overwrite:path = $request.path` (asignación de solicitud para evitar prefijo de stage). No modificar integraciones existentes de Auth o Cart salvo asociación de la ruta nueva de quote.

**Stages:** `$default` y/o `desarrollo` según la URL usada actualmente en Frontend. Confirmar si auto-deploy está habilitado en el stage que **efectivamente** consume el navegador; de lo contrario desplegar cambios de rutas manualmente en ese stage. No eliminar `/desarrollo` si está en uso.

## 6. Código y despliegues — orden único (tras pruebas locales)

Antes de la consola: incorporar el ZIP entero en una rama `feature/farmacia-simple-sucursales-cotizador`, ejecutar pruebas locales; luego PR y merge a `main`. No entregar código adicional después de comenzar AWS salvo correcciones reales.

1. **Respaldo RDS** y **Security Groups** (secciones 2 y 3); crear variable `PRODUCTS_INTERNAL_URL` y configurar rutas API Gateway nuevas.
2. Ejecutar **Build Docker Images** desde `main`; deben compilar `products-service`, `cart-service`, `frontend`, `auth-service`. No desplegar Auth por rutina.
3. Ejecutar **Deploy Products EC2** (SSM + GHCR) usando la etiqueta SHA del commit (o `latest` si se comprobó su correspondencia). Verificar Target Group `Healthy` y catálogo/zonas.
4. Ejecutar **Deploy Cart EC2** (SSH existente + GHCR), con `PRODUCTS_INTERNAL_URL` en `cart.env`. Confirmar `GET /actuator/health` y conectividad Cart → Products.
5. Ejecutar **Deploy Frontend EC2** (workflow actual) y recargar navegador sin caché.
6. Validar búsqueda, stock sumado, comparación, alta de carrito, precio frente a rapidez, envío por sucursal y persistencia al refrescar.

No es necesario volver a instalar Docker, reconfigurar NAT, crear balanceador nuevo ni nuevos roles IAM.

## 7. Pruebas de aceptación

**Locales:** desde la raíz existente ejecutar (en equipo con Docker):

```powershell
docker compose -f docker-compose.yml -f docker-compose.override.yml up --build -d
# Compilación y unitarios, si dispone de Maven/Java 17:
mvn -f products-service/pom.xml test
mvn -f cart-service/pom.xml test
```

La configuración local usa un PostgreSQL local y Products accesible para Cart por `http://products-service:8083` en la red Compose; es independiente de AWS. El Frontend puede requerir los valores locales de Azure establecidos en el proyecto.

**AWS:** iniciar sesión en la aplicación, en Chrome F12 → Network confirmar `200` con JWT para `GET /api/products`, `GET /api/delivery-zones`, `GET /api/cart/quote?zoneCode=...`; no pegar JWT en chats ni README. Confirmar además:

- Stock positivo en alguna sucursal: añadir SKU una vez y varias veces; se consolida sin duplicar filas.
- Stock insuficiente: Cart devuelve `409`, sin modificar cantidades ni inventario.
- Elegir una zona; cotizar `CHEAPEST` y `FASTEST`; el precio mostrado siempre **incluye envíos**.
- Si una sucursal no cubre unidades totales, el sistema puede dividir una línea en más de una sucursal.
- Repetir cotización tras cambiar zona; los costos y plazos cambian según la tabla de tarifas.
- Refrescar página: carrito sigue asociado al usuario; zona preferida queda en almacenamiento local del navegador.
- JWT ausente o inválido: API Gateway debe rechazar con `401` las rutas protegidas.
- Reiniciar EC2 Products: Docker con `--restart unless-stopped` reinicia contenedor; ALB recupera `Healthy`. El NAT/ALB/SSM tienen costos mientras estén creados.

**Caso conocido:** diferencias entre costo y rapidez pueden no aparecer en TODOS los carritos. Se verifican con pruebas unitarias diseñadas para producir opciones distintas. No confundir tarifa simulada con fecha garantizada.

## 8. Plan de retroceso y compatibilidad

- Conservar el SHA anterior de `pedidos360-products`, `pedidos360-cart` y `pedidos360-frontend` antes de desplegar; permiten redeploy por separado desde sus workflows.
- Las nuevas tablas en `catalogo` son aditivas y la columna histórica `cart_item.branch_code` no se elimina. El rollback de imagen no implica borrar tablas.
- Antes de hacer cambios destructivos al esquema o migraciones manuales, restaurar desde respaldo validado y revisar efectos sobre otras tablas.
- La cotización no cierra pedidos ni reserva stock; Orders y Checkout se implementarán posteriormente.

## 9. Checklist de implementación AWS (ejecutar en este orden)

- [ ] Código 0.4.0 completo en una rama; pruebas de Products/Cart y Frontend locales finalizadas.
- [ ] Respaldo RDS realizado; inventario de SG existentes confirmado.
- [ ] ALB SG acepta TCP 80 **desde VPC Link SG existente y desde Cart SG existente**.
- [ ] Salida Cart → ALB HTTP:80 permitida.
- [ ] Variable GitHub `PRODUCTS_INTERNAL_URL` apunta al DNS real del ALB interno.
- [ ] API Gateway: `GET /api/delivery-zones`, `GET /api/products/{id}/availability`, `GET /api/cart/quote` asignadas a integración correcta y JWT.
- [ ] `overwrite:path=$request.path` sigue configurado en Products; stage efectivo desplegado.
- [ ] PR aprobado y merge a `main`; imagen GHCR del SHA publicada correctamente.
- [ ] Deploy Products → Healthy; stock/tarifas reales de demostración visibles.
- [ ] Deploy Cart → Healthy, quote puede consultar Products.
- [ ] Deploy Frontend → Network 200 y pruebas integrales de cotización.
- [ ] Registrar resultado, SHA/versión y evidencias; cerrar `0.4.0` estable solo después de aprobar.

---

**Próximo bloque:** `0.5.0` Orders Service. Reutiliza VPC/NAT/ALB/VPC Link, pero **sí** necesitará una EC2 privada propia, SG, Target Group, listener por rutas, workflow completo y migraciones planificados **antes** de tocar AWS.
