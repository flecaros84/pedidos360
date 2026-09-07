import { config } from './config'

async function request(baseUrl, path, options = {}, auth) {
  const headers = new Headers(options.headers || {})
  headers.set('Content-Type', 'application/json')

  if (auth.mode === 'azure') {
    const token = await auth.getAccessToken()
    headers.set('Authorization', `Bearer ${token}`)
  } else {
    headers.set('X-Demo-User', auth.userId || 'local-demo-user')
    headers.set('X-Demo-Name', auth.userName || 'Usuario Local')
  }

  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers,
  })

  if (!response.ok) {
    let detail = ''
    try {
      const body = await response.json()
      detail = body.message || body.error || JSON.stringify(body)
    } catch {
      detail = await response.text()
    }
    throw new Error(`${response.status} ${response.statusText}${detail ? ` - ${detail}` : ''}`)
  }

  if (response.status === 204) return null
  return response.json()
}

export const api = {
  me: (auth) => request(config.authApiUrl, '/api/auth/me', {}, auth),
  cart: (auth) => request(config.cartApiUrl, '/api/cart', {}, auth),
  addItem: (auth, item) => request(config.cartApiUrl, '/api/cart/items', {
    method: 'POST',
    body: JSON.stringify(item),
  }, auth),
  deleteItem: (auth, id) => request(config.cartApiUrl, `/api/cart/items/${id}`, {
    method: 'DELETE',
  }, auth),
}
