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

  // El body se lee UNA sola vez.
  const text = response.status === 204 ? '' : await response.text()

  let body = null

  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = text
    }
  }

  if (!response.ok) {
    let detail = ''

    if (typeof body === 'string') {
      detail = body
    } else if (body) {
      detail = body.message || body.error || JSON.stringify(body)
    }

    throw new Error(
      `${response.status} ${response.statusText}${detail ? ` - ${detail}` : ''}`
    )
  }

  if (response.status === 204 || !text) {
    return null
  }

  if (typeof body === 'string') {
    throw new Error(`Respuesta inesperada del servidor: ${body.substring(0, 120)}`)
  }

  return body
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
