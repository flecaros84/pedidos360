import { useEffect, useState } from 'react'
import { api } from './api'
import { config } from './config'

const PRODUCTS = [
  { productCode: 'P001', productName: 'Notebook', quantity: 1 },
  { productCode: 'P002', productName: 'Mouse', quantity: 1 },
  { productCode: 'P003', productName: 'Teclado', quantity: 1 },
  { productCode: 'P004', productName: 'Monitor', quantity: 1 },
]

export default function App({ auth }) {
  const [profile, setProfile] = useState(null)
  const [cart, setCart] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function refresh() {
    if (!auth.isAuthenticated) return
    setLoading(true)
    setError('')
    try {
      const [me, items] = await Promise.all([api.me(auth), api.cart(auth)])
      setProfile(me)
      setCart(items)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
    // El objeto auth cambia en render cuando se usa MSAL; dependemos solo del estado autenticado.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.isAuthenticated])

  async function add(product) {
    setLoading(true)
    setError('')
    try {
      await api.addItem(auth, product)
      await refresh()
    } catch (e) {
      setError(e.message)
      setLoading(false)
    }
  }

  async function remove(id) {
    setLoading(true)
    setError('')
    try {
      await api.deleteItem(auth, id)
      await refresh()
    } catch (e) {
      setError(e.message)
      setLoading(false)
    }
  }

  if (!auth.isAuthenticated) {
    return (
      <main className="page centered">
        <section className="card login-card">
          <p className="eyebrow">Pedidos360</p>
          <h1>Acceso al sistema</h1>
          <p>Autentícate con Microsoft Entra ID para continuar.</p>
          <button onClick={auth.login}>Iniciar sesión con Microsoft</button>
        </section>
      </main>
    )
  }

  return (
    <main className="page">
      <header className="header">
        <div>
          <p className="eyebrow">Pedidos360</p>
          <h1>Carrito de demostración</h1>
          <p className="muted">Modo: <strong>{config.authMode}</strong></p>
        </div>
        {auth.mode === 'azure' && <button className="secondary" onClick={auth.logout}>Cerrar sesión</button>}
      </header>

      {error && <div className="error"><strong>Error:</strong> {error}</div>}

      <section className="card">
        <h2>Usuario autenticado</h2>
        {profile ? (
          <div className="profile-grid">
            <span>Nombre</span><strong>{profile.name}</strong>
            <span>ID</span><code>{profile.userId}</code>
            <span>Scopes</span><code>{profile.scopes?.join(', ') || '-'}</code>
            <span>Roles</span><code>{profile.roles?.join(', ') || '-'}</code>
          </div>
        ) : <p>{loading ? 'Consultando identidad...' : 'Sin datos.'}</p>}
      </section>

      <section>
        <h2>Productos</h2>
        <div className="product-grid">
          {PRODUCTS.map((product) => (
            <article className="card product" key={product.productCode}>
              <div>
                <span className="product-code">{product.productCode}</span>
                <h3>{product.productName}</h3>
              </div>
              <button disabled={loading} onClick={() => add(product)}>Agregar</button>
            </article>
          ))}
        </div>
      </section>

      <section className="card">
        <div className="section-title">
          <h2>Mi carrito</h2>
          <button className="secondary" disabled={loading} onClick={refresh}>Actualizar</button>
        </div>
        {cart.length === 0 ? (
          <p className="muted">El carrito está vacío.</p>
        ) : (
          <div className="cart-list">
            {cart.map((item) => (
              <div className="cart-row" key={item.id}>
                <div>
                  <strong>{item.productName}</strong>
                  <div className="muted">{item.productCode} · Cantidad {item.quantity}</div>
                </div>
                <button className="danger" disabled={loading} onClick={() => remove(item.id)}>Eliminar</button>
              </div>
            ))}
          </div>
        )}
      </section>
    </main>
  )
}
