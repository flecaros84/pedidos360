import { useEffect, useState } from 'react'
import { api } from './api'
import { config } from './config'
import { version } from '../package.json'

// Etapa 0: catálogo visual de demostración. La etapa 1 reemplazará estos datos
// por productos administrados mediante Products Service y PostgreSQL.
const PRODUCTS = [
  { productCode: 'F001', productName: 'Paracetamol 500 mg', quantity: 1, category: 'Medicamentos', presentation: '20 comprimidos', detail: 'Principio activo: paracetamol', symbol: '💊' },
  { productCode: 'F002', productName: 'Ibuprofeno 200 mg', quantity: 1, category: 'Medicamentos', presentation: '10 comprimidos', detail: 'Principio activo: ibuprofeno', symbol: '💊' },
  { productCode: 'F003', productName: 'Vitamina C 500 mg', quantity: 1, category: 'Bienestar', presentation: '30 comprimidos', detail: 'Suplemento alimentario', symbol: '🍊' },
  { productCode: 'F004', productName: 'Suero fisiológico 0,9%', quantity: 1, category: 'Cuidado personal', presentation: '500 mL', detail: 'Solución salina', symbol: '💧' },
  { productCode: 'F005', productName: 'Alcohol gel 70%', quantity: 1, category: 'Cuidado personal', presentation: '250 mL', detail: 'Higiene de manos', symbol: '🧴' },
  { productCode: 'F006', productName: 'Termómetro digital', quantity: 1, category: 'Insumos', presentation: '1 unidad', detail: 'Control de temperatura', symbol: '🌡️' },
]
const CATEGORIES = ['Todos', 'Medicamentos', 'Bienestar', 'Cuidado personal', 'Insumos']

function Brand() {
  return (
    <a className="brand" href="#inicio" aria-label="Farmacia Simple, ir al inicio">
      <span className="brand-mark" aria-hidden="true">+</span>
      <span className="brand-name">farmacia<span>simple</span><small>tu salud, más cerca</small></span>
    </a>
  )
}

export default function App({ auth }) {
  const [profile, setProfile] = useState(null)
  const [cart, setCart] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('Todos')

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
    // La referencia de auth cambia con MSAL; solo recargamos ante cambios de autenticación.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.isAuthenticated])

  async function add(product) {
    setLoading(true)
    setError('')
    setNotice('')
    try {
      // Conservamos exactamente el contrato actual del microservicio Cart.
      await api.addItem(auth, {
        productCode: product.productCode,
        productName: product.productName,
        quantity: product.quantity,
      })
      await refresh()
      setNotice(`${product.productName} agregado al carrito.`)
    } catch (e) {
      setError(e.message)
      setLoading(false)
    }
  }

  async function remove(id) {
    setLoading(true)
    setError('')
    setNotice('')
    try {
      await api.deleteItem(auth, id)
      await refresh()
      setNotice('Producto eliminado del carrito.')
    } catch (e) {
      setError(e.message)
      setLoading(false)
    }
  }

  const normalizedQuery = query.trim().toLocaleLowerCase('es')
  const visibleProducts = PRODUCTS.filter((product) => {
    const matchCategory = category === 'Todos' || product.category === category
    const searchText = `${product.productName} ${product.detail} ${product.presentation}`.toLocaleLowerCase('es')
    return matchCategory && searchText.includes(normalizedQuery)
  })
  const cartCount = cart.reduce((total, item) => total + (Number(item.quantity) || 0), 0)

  if (!auth.isAuthenticated) {
    return (
      <div className="login-layout" id="inicio">
        <header className="login-topbar"><Brand /><span className="environment-tag">Portal de demostración</span></header>
        <main className="login-main">
          <div className="login-story">
            <p className="eyebrow">BIENVENIDO A FARMACIA SIMPLE</p>
            <h1>Tu bienestar,<br /><span>más cerca.</span></h1>
            <p>Un espacio para explorar nuestro futuro catálogo farmacéutico y gestionar tus productos favoritos.</p>
            <div className="feature-note"><span aria-hidden="true">✦</span> Plataforma académica en desarrollo. Sin ventas ni pagos reales.</div>
          </div>
          <section className="card login-card" aria-labelledby="login-title">
            <span className="login-symbol" aria-hidden="true">+</span>
            <p className="eyebrow">ACCESO SEGURO</p>
            <h2 id="login-title">Ingresa a tu cuenta</h2>
            <p className="muted">Utiliza tu cuenta de Microsoft Entra ID para continuar.</p>
            <button className="button-primary wide" type="button" onClick={auth.login}>Iniciar sesión con Microsoft <span aria-hidden="true">→</span></button>
            <p className="fine-print">Inicio de sesión protegido mediante Microsoft Entra ID.</p>
          </section>
        </main>
        <footer className="login-footer">Farmacia Simple · Prototipo académico · v{version}</footer>
      </div>
    )
  }

  return (
    <div className="site" id="inicio">
      <div className="announcement">Plataforma académica de demostración · No realiza ventas ni pagos reales</div>
      <header className="site-header">
        <div className="header-inner">
          <Brand />
          <nav className="main-nav" aria-label="Navegación principal">
            <a href="#catalogo">Catálogo</a>
            <a href="#carrito">Mi carrito <span className="cart-count">{cartCount}</span></a>
            <a href="#cuenta">Mi cuenta</a>
          </nav>
          <div className="header-actions">
            <span className="signed-in" title={profile?.name || auth.userName || 'Sesión iniciada'}>● <span>Sesión activa</span></span>
            {auth.mode === 'azure' && <button type="button" className="button-text" onClick={auth.logout}>Cerrar sesión</button>}
          </div>
        </div>
      </header>

      <main className="page">
        <section className="hero" aria-labelledby="hero-title">
          <div className="hero-content">
            <p className="eyebrow hero-kicker">FARMACIA SIMPLE · TU SALUD, MÁS CERCA</p>
            <h1 id="hero-title">Cuidarte debería<br />ser <span>simple.</span></h1>
            <p>Descubre nuestra selección de productos de demostración. Pronto podrás comparar precios y disponibilidad entre sucursales.</p>
            <a className="button-primary hero-cta" href="#catalogo">Ver catálogo <span aria-hidden="true">→</span></a>
          </div>
          <div className="hero-illustration" aria-hidden="true">
            <span className="hero-orbit orbit-one" /><span className="hero-orbit orbit-two" />
            <span className="hero-cross">+</span>
            <span className="hero-pill hero-pill-one">Bienestar</span>
            <span className="hero-pill hero-pill-two">Confianza</span>
          </div>
        </section>

        <div className="service-strip" aria-label="Próximas funcionalidades">
          <div><span className="service-icon" aria-hidden="true">⌕</span><strong>Búsqueda simple</strong><small>Catálogo de demostración</small></div>
          <div><span className="service-icon" aria-hidden="true">↔</span><strong>Precios por sucursal</strong><small>Próximamente</small></div>
          <div><span className="service-icon" aria-hidden="true">▤</span><strong>Seguimiento de pedidos</strong><small>Próximamente</small></div>
        </div>

        {error && <div className="error" role="alert"><strong>Error:</strong> {error}</div>}
        {notice && <div className="notice" role="status">{notice}</div>}

        <section className="catalog-section" id="catalogo" aria-labelledby="catalog-title">
          <div className="section-heading">
            <div><p className="eyebrow">EXPLORA NUESTROS PRODUCTOS</p><h2 id="catalog-title">Catálogo farmacéutico</h2><p className="muted">Productos ilustrativos. Precios y stock por sucursal se incorporarán en la siguiente etapa.</p></div>
            <span className="results-count">{visibleProducts.length} producto{visibleProducts.length === 1 ? '' : 's'}</span>
          </div>
          <label className="search-field"><span aria-hidden="true">⌕</span><span className="sr-only">Buscar por nombre o descripción</span><input type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Busca por nombre, principio activo o presentación..." /></label>
          <div className="category-tabs" role="group" aria-label="Filtrar por categoría">
            {CATEGORIES.map((name) => <button key={name} type="button" className={`filter-button ${category === name ? 'active' : ''}`} aria-pressed={category === name} onClick={() => setCategory(name)}>{name}</button>)}
          </div>
          {visibleProducts.length === 0 ? <div className="empty-results">No encontramos productos para esa búsqueda. Intenta con otro nombre o categoría.</div> : (
            <div className="product-grid">
              {visibleProducts.map((product) => (
                <article className="product-card" key={product.productCode}>
                  <div className="product-art" aria-hidden="true"><span>{product.symbol}</span><small>{product.category}</small></div>
                  <div className="product-body">
                    <span className="product-code">{product.productCode} · Catálogo piloto</span>
                    <h3>{product.productName}</h3>
                    <p className="product-detail">{product.detail}</p>
                    <p className="product-presentation">{product.presentation}</p>
                    <button type="button" className="button-primary product-add" disabled={loading} onClick={() => add(product)}>Agregar al carrito <span aria-hidden="true">+</span></button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="card cart-section" id="carrito" aria-labelledby="cart-title">
          <div className="section-title"><div><p className="eyebrow">TUS PRODUCTOS SELECCIONADOS</p><h2 id="cart-title">Mi carrito <span className="cart-heading-count">{cartCount}</span></h2></div><button className="button-outline" type="button" disabled={loading} onClick={refresh}>Actualizar</button></div>
          {cart.length === 0 ? (
            <div className="empty-cart"><span aria-hidden="true">🛒</span><h3>Tu carrito está vacío</h3><p>Explora el catálogo y añade productos para comenzar.</p><a href="#catalogo">Explorar productos →</a></div>
          ) : (
            <div className="cart-list">{cart.map((item) => (
              <div className="cart-row" key={item.id}>
                <div className="cart-item-info"><span className="cart-item-symbol" aria-hidden="true">+</span><div><strong>{item.productName}</strong><div className="muted">{item.productCode} · Cantidad: {item.quantity}</div></div></div>
                <button className="button-remove" type="button" disabled={loading} onClick={() => remove(item.id)}>Eliminar</button>
              </div>
            ))}</div>
          )}
          <p className="cart-disclaimer">Esta versión conserva el carrito actual de Pedidos360. No se efectúan compras ni cobros. Los artículos previos de demostración pueden seguir apareciendo hasta que los elimines.</p>
        </section>

        <section className="card account-section" id="cuenta" aria-labelledby="account-title">
          <div className="section-heading account-heading"><div><p className="eyebrow">IDENTIDAD Y SEGURIDAD</p><h2 id="account-title">Mi cuenta</h2></div><span className="security-tag">Sesión autenticada</span></div>
          {profile ? (
            <><p className="account-name">{profile.name || auth.userName || 'Usuario'}</p><p className="muted">Acceso protegido con Microsoft Entra ID en el entorno AWS.</p><details className="technical-details"><summary>Ver detalles técnicos de autenticación</summary><div className="profile-grid"><span>Identificador</span><code>{profile.userId}</code><span>Scopes</span><code>{profile.scopes?.join(', ') || '-'}</code><span>Roles</span><code>{profile.roles?.join(', ') || '-'}</code><span>Entorno</span><code>{config.authMode}</code></div></details></>
          ) : <p className="muted">{loading ? 'Consultando identidad...' : 'Sin datos de identidad disponibles.'}</p>}
        </section>
      </main>
      <footer className="site-footer"><div className="footer-inner"><span><strong>farmaciasimple</strong> · Portal de demostración académica</span><span>Basado en Pedidos360 · v{version}</span></div></footer>
    </div>
  )
}
