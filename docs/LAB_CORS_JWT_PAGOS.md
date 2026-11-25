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
