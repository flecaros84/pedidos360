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
  const response = await fetch(`${baseUrl}${path}`, { ...options, headers })
  const text = response.status === 204 ? '' : await response.text()
  let body = null
  if (text) {
    try { body = JSON.parse(text) } catch { body = text }
  }
  if (!response.ok) {
    const detail = typeof body === 'string' ? body : (body?.message || body?.detail || body?.error || '')
    throw new Error(`${response.status} ${response.statusText}${detail ? ` - ${detail}` : ''}`)
  }
  if (response.status === 204 || !text) return null
  if (typeof body === 'string') throw new Error(`Respuesta inesperada: ${body.substring(0, 120)}`)
  return body
}

export const api = {
  me: (auth) => request(config.authApiUrl, '/api/auth/me', {}, auth),
  cart: (auth) => request(config.cartApiUrl, '/api/cart', {}, auth),
  addItem: (auth, item) => request(config.cartApiUrl, '/api/cart/items', {
    method: 'POST', body: JSON.stringify(item),
  }, auth),
  quote: (auth, zoneCode) => request(config.cartApiUrl,
    `/api/cart/quote?${new URLSearchParams({ zoneCode })}`, {}, auth),
  clearCart: (auth) => request(config.cartApiUrl, '/api/cart', { method: 'DELETE' }, auth),
  branches: (auth) => request(config.productsApiUrl, '/api/branches', {}, auth),
  zones: (auth) => request(config.productsApiUrl, '/api/delivery-zones', {}, auth),
  availability: (auth, id) => request(config.productsApiUrl, `/api/products/${id}/availability`, {}, auth),
  deleteItem: (auth, id) => request(config.cartApiUrl, `/api/cart/items/${id}`, {
    method: 'DELETE',
  }, auth),
  products: (auth, search = '', category = '') => {
    const params = new URLSearchParams()
    if (search.trim()) params.set('search', search.trim())
    if (category && category !== 'Todos') params.set('category', category)
    const suffix = params.toString() ? `?${params}` : ''
    return request(config.productsApiUrl, `/api/products${suffix}`, {}, auth)
  },
  product: (auth, id) => request(config.productsApiUrl, `/api/products/${id}`, {}, auth),
  categories: (auth) => request(config.productsApiUrl, '/api/products/categories', {}, auth),
}
