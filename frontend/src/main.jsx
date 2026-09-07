import React from 'react'
import ReactDOM from 'react-dom/client'
import { MsalProvider, useIsAuthenticated, useMsal } from '@azure/msal-react'
import App from './App'
import { config } from './config'
import './styles.css'

const localAuth = {
  mode: 'local',
  isAuthenticated: true,
  userId: 'local-demo-user',
  userName: 'Usuario Local',
  login: async () => {},
  logout: async () => {},
  getAccessToken: async () => null,
}

function LocalApp() {
  return <App auth={localAuth} />
}

function AzureApp() {
  const { instance, accounts } = useMsal()
  const isAuthenticated = useIsAuthenticated()
  const account = instance.getActiveAccount() || accounts[0]

  const auth = {
    mode: 'azure',
    isAuthenticated,
    userId: account?.homeAccountId || '',
    userName: account?.name || account?.username || 'Usuario',
    login: () => import('./auth/azure').then(({ loginRequest }) => instance.loginRedirect(loginRequest)),
    logout: () => instance.logoutRedirect({ account }),
    getAccessToken: async () => {
      if (!account) throw new Error('No existe una cuenta autenticada')
      const { apiRequest } = await import('./auth/azure')
      try {
        const response = await instance.acquireTokenSilent({ ...apiRequest, account })
        return response.accessToken
      } catch {
        const response = await instance.acquireTokenPopup({ ...apiRequest, account })
        return response.accessToken
      }
    },
  }

  return <App auth={auth} />
}

async function bootstrap() {
  const root = ReactDOM.createRoot(document.getElementById('root'))

  if (config.authMode === 'azure') {
    const { msalInstance } = await import('./auth/azure')
    await msalInstance.initialize()
    const redirectResult = await msalInstance.handleRedirectPromise()
    if (redirectResult?.account) {
      msalInstance.setActiveAccount(redirectResult.account)
    } else if (!msalInstance.getActiveAccount() && msalInstance.getAllAccounts().length > 0) {
      msalInstance.setActiveAccount(msalInstance.getAllAccounts()[0])
    }

    root.render(
      <React.StrictMode>
        <MsalProvider instance={msalInstance}>
          <AzureApp />
        </MsalProvider>
      </React.StrictMode>,
    )
  } else {
    root.render(
      <React.StrictMode>
        <LocalApp />
      </React.StrictMode>,
    )
  }
}

bootstrap().catch((error) => {
  console.error(error)
  document.getElementById('root').innerHTML = `
    <main style="font-family:sans-serif;padding:2rem">
      <h1>Error de configuración</h1>
      <pre>${error.message}</pre>
    </main>
  `
})
