# CORS, JWT y reconciliación del carrito (1.0.0-rc.2)

## Estado
Código preparado en `feature/integracion-final`, **no aplicado automáticamente en AWS**. La instalación actual continúa funcionando hasta realizar las acciones de migración.

## Arquitectura
- La URL `api_base_url` conserva el AWS HTTP API Gateway existente, JWT, scopes y CORS.
- La nueva URL `frontend_url` corresponde a una segunda HTTP API HTTPS para Nginx. Su integración usa el VPC Link existente pero un listener exclusivo 8080 del ALB. No publica rutas de microservicios que eludan el JWT.
- Las rutas del frontend en el API antiguo quedan habilitadas temporalmente para no cortar la sesión en Entra ID; retirar después del corte con el profesor.
- El backend declara `allow_origins` como lista explícita incluyendo la nueva URL del frontend. Se incluyen `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Demo-User`, `X-Demo-Name`; `OPTIONS` no exige JWT.

## Preparación del despliegue
1. Sincronizar la rama y comprobar que el workflow de GitHub Actions compila Java, React y valida Terraform.
2. Revisar `terraform plan` completo antes de aplicar: **el cambio anterior de user_data de EC2 puede proponer reemplazos**. No ejecutar `terraform apply` sin revisar. Para la migración de CORS/frontend, aplicar únicamente los recursos de API, listener, Security Group y rutas necesarias, usando targets validados en el plan. No destruir EC2.
3. Obtener la nueva URL mediante `terraform output -raw frontend_url`, recortar slash final y registrarla como nueva URI SPA en Microsoft Entra ID. Conservar temporalmente la URI antigua.
4. Regenerar `.env.generated` con `scripts/lab/generate-env.ps1`. El valor `INTERNAL_SERVICE_TOKEN` debe ser el mismo para Cart y Orders y se almacena en SSM SecureString.
5. Actualizar SSM para `cart`, `orders`, `frontend` y los otros servicios que deban cambiar `APP_CORS_ALLOWED_ORIGINS`.
6. Desplegar `cart` ANTES que `orders` y después `frontend` con `scripts/lab/lab-deploy.ps1 -Services cart,orders,frontend`; todos los otros servicios pueden actualizarse luego.
7. Probar nueva URL frontend, OPTIONS con origen permitido, ausencia de ACAO para origen no permitido, 401 sin token y llamada autorizada con token desde navegador.
8. Crear una orden, aprobar pago simulado y observar transición PENDING_PAYMENT a PAID. El reconciliador en Orders efectúa `POST /internal/cart/paid` a Cart; el controlador exige token interno y una tabla de idempotencia previene descuentos duplicados. Pagos rechazados o pendientes no limpian carrito.
9. En `Mi cuenta > Ver detalles técnicos > Ver JWT`, cargar y revisar header/claims. La decodificación local no equivale a validación criptográfica. El token completo solo se revela de forma voluntaria y se limpia al cerrar el detalle.

## Seguridad
Los secrets siguen fuera de Git/Terraform en SSM SecureString. Nunca usar un JWT completo en capturas públicas. No exponer `/internal/cart/paid` en un listener público ni desactivar autenticación del API. Para pagos posteriores, el carrito se ajusta solo por las cantidades efectivamente adquiridas.

## Corrección de preflight (24-11-2026)

Comprobación real: la HTTP API incluía el nuevo origen permitido y agregaba `Access-Control-Allow-Origin`, pero devolvía `403 Invalid CORS request`. La ruta explícita `OPTIONS /api/{proxy+}` sin JWT reenviaba el preflight al ALB y Spring Security, cuyo `APP_CORS_ALLOWED_ORIGINS` todavía conservaba los orígenes antiguos.

Solución: conservar `OPTIONS /api/{proxy+}` sin JWT, pero agregar `aws_lb_listener_rule.cors_preflight` con prioridad 10, método `OPTIONS` y ruta `/api/*`, que responde `204` directamente. Las rutas funcionales GET/POST y demás siguen redirigiéndose a los microservicios y exigiendo JWT según corresponda. HTTP API aplica sus encabezados CORS también al 204. No se requiere reiniciar las EC2 para esta corrección.

Plan de migración seguro:

```powershell
terraform -chdir=terraform/lab plan "-target=aws_lb_listener_rule.cors_preflight" "-out=preflight.tfplan"
# Validate: 1 to add, 0 to change, 0 to destroy.
terraform -chdir=terraform/lab apply preflight.tfplan
```

Prueba:

```powershell
$front="https://rqe2am6670.execute-api.us-east-1.amazonaws.com"
$api="https://46lmamgixk.execute-api.us-east-1.amazonaws.com"
curl.exe -i -X OPTIONS "$api/api/cart/items" -H "Origin: $front" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: authorization,content-type"
```

Esperado: HTTP 204, `access-control-allow-origin` igual a `$front`, métodos y encabezados permitidos. Un origen no autorizado no debe recibir `access-control-allow-origin`; el ALB devuelve 204 a OPTIONS independientemente del origen, pero la política CORS de API Gateway controla qué navegador puede proceder. Validar además JWT ausente y JWT legítimo en peticiones reales.

## Corrección de reglas de seguridad ALB (24-11-2026)

Tras el despliegue de preflight, `aws_security_group.alb` volvió a configurar sus reglas de entrada administradas *inline* y eliminó la regla separada de acceso 8080 desde el VPC Link. Resultado: preflight 204 y autenticación 401 correctos, pero el SG del ALB solo exhibía el puerto 80 y el segundo frontend HTTPS podría devolver 503.

Corrección de código: declarar el puerto 8080 desde el SG del VPC Link dentro de `aws_security_group.alb` (regla dinámica cuando `enable_api=true` y `enable_cloudfront=false`) y retirar el `aws_security_group_rule.frontend_listener_from_vpc_link` separado. **No combinar reglas ingress inline y recursos aws_security_group_rule para el mismo Security Group.**

Procedimiento de mantenimiento del estado en el laboratorio, desde la raíz del repositorio y después de sincronizar la rama:
1. Confirmar mediante AWS CLI que la regla de 8080 ya no existe.
2. `terraform -chdir=terraform/lab state list` y, si todavía se rastrea `aws_security_group_rule.frontend_listener_from_vpc_link[0]`, ejecutar `terraform -chdir=terraform/lab state rm 'aws_security_group_rule.frontend_listener_from_vpc_link[0]'`. **state rm solo desasocia Terraform; no elimina recursos AWS**. Este caso es seguro solamente porque ya se verificó que no existe la regla en AWS.
3. `terraform -chdir=terraform/lab plan "-target=aws_security_group.alb" "-out=alb-8080.tfplan"` y verificar que el único cambio sea `update` en `aws_security_group.alb`, sin eliminaciones ni EC2.
4. `terraform -chdir=terraform/lab apply alb-8080.tfplan`.
5. Confirmar SG ingress 80 desde VPC y 8080 desde SG VPC Link; verificar `GET https://rqe2am6670.execute-api.us-east-1.amazonaws.com/` con 200, preflight CORS 204 y respuesta 401 desde backend sin JWT.

Nunca ejecutar un plan que actualice o reemplace EC2. Evitar imprimir tokens o secretos en logs.
