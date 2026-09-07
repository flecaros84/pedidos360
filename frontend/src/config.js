const env = window.__ENV__ ?? {}

export const config = {
  authMode: env.AUTH_MODE || 'local',
  azureTenantId: env.AZURE_TENANT_ID || '',
  azureClientId: env.AZURE_CLIENT_ID || '',
  azureApiScope: env.AZURE_API_SCOPE || '',
  azureRedirectUri: env.AZURE_REDIRECT_URI || window.location.origin,
  authApiUrl: (env.AUTH_API_URL || 'http://localhost:8081').replace(/\/$/, ''),
  cartApiUrl: (env.CART_API_URL || 'http://localhost:8082').replace(/\/$/, ''),
}

export function assertAzureConfig() {
  const missing = []
  if (!config.azureTenantId) missing.push('AZURE_TENANT_ID')
  if (!config.azureClientId) missing.push('AZURE_CLIENT_ID')
  if (!config.azureApiScope) missing.push('AZURE_API_SCOPE')

  if (missing.length) {
    throw new Error(`Faltan variables para Azure: ${missing.join(', ')}`)
  }
}
