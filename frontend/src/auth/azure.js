import { PublicClientApplication } from '@azure/msal-browser'
import { config, assertAzureConfig } from '../config'

assertAzureConfig()

export const msalConfig = {
  auth: {
    clientId: config.azureClientId,
    authority: `https://login.microsoftonline.com/${config.azureTenantId}`,
    redirectUri: config.azureRedirectUri,
    postLogoutRedirectUri: config.azureRedirectUri,
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false,
  },
}

export const loginRequest = {
  scopes: [config.azureApiScope],
  prompt: 'login',
}

export const apiRequest = {
  scopes: [config.azureApiScope],
}

export const msalInstance = new PublicClientApplication(msalConfig)
