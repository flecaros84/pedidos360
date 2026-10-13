import { useEffect, useMemo, useState } from 'react'
import { api } from './api'
import { config } from './config'
import { version } from '../package.json'

const currency = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 })
const symbols = { Medicamentos: '💊', Bienestar: '🍊', 'Cuidado personal': '🧴', Insumos: '🌡️' }

function Brand() {
  return (
    <a className="brand" href="#inicio" aria-label="Farmacia Simple, ir al inicio">
      <span className="brand-mark" aria-hidden="true">+</span>
      <span className="brand-name">farmacia<span>simple</span><small>tu salud, más cerca</small></span>
    </a>
  )
}

function groupCart(items) {
  // Compatibilidad defensiva con respuestas de un Cart antiguo durante el despliegue.
  // Cart v0.3.0-rc.2 ya consolida los SKU directamente en PostgreSQL.
  const groups = new Map()
  for (const item of items) {
    const code = item.productCode || `legacy-${item.id}`
    const group = groups.get(code) || { productCode: code, productName: item.productName, quantity: 0, ids: [] }
    group.quantity += Number(item.quantity) || 0
    group.ids.push(item.id)
    groups.set(code, group)
  }
  return [...groups.values()]
}

export default function App({ auth }) {
  const [profile, setProfile] = useState(null)
  const [cart, setCart] = useState([])
  const [products, setProducts] = useState([])
  const [categories, setCategories] = useState([])
  const [zones, setZones] = useState([])
  const [zoneCode, setZoneCode] = useState(() => window.localStorage.getItem('farmaciasimple.zone') || 'PM')
  const [quoteMode, setQuoteMode] = useState('CHEAPEST')
  const [quotes, setQuotes] = useState(null)
  const [quoteLoading, setQuoteLoading] = useState(false)
  const [quoteError, setQuoteError] = useState('')
  const [comparison, setComparison] = useState({})
  const [busy, setBusy] = useState(false)
  const [catalogLoading, setCatalogLoading] = useState(false)
  const [cartError, setCartError] = useState('')
  const [catalogError, setCatalogError] = useState('')
  const [notice, setNotice] = useState('')
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('Todos')
  const groupedCart = useMemo(() => groupCart(cart), [cart])
  const cartCount = groupedCart.reduce((sum, item) => sum + item.quantity, 0)
  const selectedQuote = quoteMode === 'FASTEST' ? quotes?.fastest : quotes?.cheapest

  async function refreshCart() {
    if (!auth.isAuthenticated) return
    try {
      const [me, items] = await Promise.all([api.me(auth), api.cart(auth)])
      setProfile(me)
      setCart(Array.isArray(items) ? items : [])
      setCartError('')
    } catch (e) { setCartError(e.message) }
  }

  useEffect(() => {
    if (auth.isAuthenticated) refreshCart()
    // Sólo recargar al iniciar/cerrar sesión. auth cambia de referencia en MSAL.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.isAuthenticated])

  useEffect(() => {
    if (!auth.isAuthenticated) return
    let cancelled = false
    Promise.all([api.categories(auth), api.zones(auth)])
      .then(([cats, zonesList]) => {
        if (!cancelled) {
          setCategories(Array.isArray(cats) ? cats : [])
          setZones(Array.isArray(zonesList) ? zonesList : [])
        }
      })
      .catch(e => { if (!cancelled) setCatalogError(e.message) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.isAuthenticated])

  useEffect(() => {
    if (!auth.isAuthenticated) return
    let cancelled = false
    const timer = setTimeout(async () => {
      setCatalogLoading(true)
      try {
        const list = await api.products(auth, query, category)
        if (!cancelled) {
          setProducts(Array.isArray(list) ? list : [])
          setCatalogError('')
        }
      } catch (e) {
        if (!cancelled) { setCatalogError(e.message); setProducts([]) }
      } finally {
        if (!cancelled) setCatalogLoading(false)
      }
    }, query ? 300 : 0)
    return () => { cancelled = true; clearTimeout(timer) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.isAuthenticated, query, category])

  async function add(product) {
    setBusy(true); setCartError(''); setNotice('')
    try {
      await api.addItem(auth, { productCode: product.sku, quantity: 1 })
      await refreshCart()
      setNotice(`${product.name} agregado al carrito.`)
    } catch (e) { setCartError(e.message) }
    finally { setBusy(false) }
  }

  useEffect(() => {
    if (!auth.isAuthenticated || groupedCart.length === 0) { setQuotes(null); setQuoteError(''); return }
    let cancelled = false
    setQuoteLoading(true)
    setQuotes(null)
    api.quote(auth, zoneCode).then(result => {
      if (!cancelled) { setQuotes(result); setQuoteError('') }
    }).catch(error => {
      if (!cancelled) { setQuotes(null); setQuoteError(error.message) }
    }).finally(() => { if (!cancelled) setQuoteLoading(false) })
    return () => { cancelled = true }
    // Recalcular cuando cambia el contenido del carrito o la zona; auth no es estable.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.isAuthenticated, cart, zoneCode])

  function changeZone(nextZone) {
    setZoneCode(nextZone)
    window.localStorage.setItem('farmaciasimple.zone', nextZone)
  }

  async function compare(product) {
    if (comparison[product.sku]) {
      setComparison(prev => ({ ...prev, [product.sku]: null }))
      return
    }
    try {
      const offers = await api.availability(auth, product.id)
      setComparison(prev => ({ ...prev, [product.sku]: offers }))
    } catch (e) { setCatalogError(e.message) }
  }

  async function removeGroup(group) {
    setBusy(true); setCartError(''); setNotice('')
    try {
      for (const id of group.ids) await api.deleteItem(auth, id)
      await refreshCart()
      setNotice('Producto eliminado del carrito.')
    } catch (e) {
      setCartError(e.message)
      await refreshCart()
    } finally { setBusy(false) }
  }

  if (!auth.isAuthenticated) {
    return (
      <div className="login-layout" id="inicio">
        <header className="login-topbar"><Brand /><span className="environment-tag">Portal de demostración</span></header>
        <main className="login-main">
          <div className="login-story">
            <p className="eyebrow">BIENVENIDO A FARMACIA SIMPLE</p>
            <h1>Tu bienestar,<br /><span>más cerca.</span></h1>
            <p>Explora nuestro catálogo farmacéutico y administra tus productos seleccionados.</p>
            <div className="feature-note">✦ Plataforma académica. Sin ventas ni pagos reales.</div>
          </div>
          <section className="card login-card" aria-labelledby="login-title">
            <span className="login-symbol" aria-hidden="true">+</span>
            <p className="eyebrow">ACCESO SEGURO</p><h2 id="login-title">Ingresa a tu cuenta</h2>
            <p className="muted">Utiliza tu cuenta de Microsoft Entra ID para continuar.</p>
            <button className="button-primary wide" type="button" onClick={auth.login}>Iniciar sesión con Microsoft →</button>
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
      <header className="site-header"><div className="header-inner">
        <Brand />
        <nav className="main-nav" aria-label="Navegación principal">
          <a href="#catalogo">Catálogo</a><a href="#carrito">Mi carrito <span className="cart-count">{cartCount}</span></a><a href="#cuenta">Mi cuenta</a>
        </nav>
        <div className="header-actions">
          <span className="signed-in" title={profile?.name || auth.userName || 'Sesión iniciada'}>● <span>Sesión activa</span></span>
          {auth.mode === 'azure' && <button type="button" className="button-text" onClick={auth.logout}>Cerrar sesión</button>}
        </div>
      </div></header>
      <main className="page">
        <section className="hero"><div className="hero-content">
          <p className="eyebrow hero-kicker">FARMACIA SIMPLE · TU SALUD, MÁS CERCA</p>
          <h1>Cuidarte debería<br />ser <span>simple.</span></h1>
          <p>Medicamentos y productos de bienestar en nuestro catálogo digital. Consulta precios y disponibilidad en cinco sucursales de demostración.</p>
          <a className="button-primary hero-cta" href="#catalogo">Ver catálogo →</a>
        </div><div className="hero-illustration" aria-hidden="true">
          <span className="hero-orbit orbit-one"/><span className="hero-orbit orbit-two"/>
          <span className="hero-cross">+</span><span className="hero-pill hero-pill-one">Bienestar</span><span className="hero-pill hero-pill-two">Confianza</span>
        </div></section>
        <div className="service-strip" aria-label="Funcionalidades">
          <div><span className="service-icon">⌕</span><strong>Búsqueda en línea</strong><small>Catálogo en PostgreSQL</small></div>
          <div><span className="service-icon">↔</span><strong>Precios por sucursal</strong><small>5 locales de demostración</small></div>
          <div><span className="service-icon">▤</span><strong>Seguimiento de pedidos</strong><small>Próximamente</small></div>
        </div>
        {cartError && <div className="error" role="alert">Error del carrito: {cartError}</div>}
        {notice && <div className="notice" role="status">{notice}</div>}
        <section className="catalog-section" id="catalogo" aria-labelledby="catalog-title">
          <div className="section-heading"><div>
            <p className="eyebrow">EXPLORA NUESTROS PRODUCTOS</p><h2 id="catalog-title">Catálogo farmacéutico</h2>
            <p className="muted">Productos almacenados en PostgreSQL. Precios y existencias demostrativos consultados desde PostgreSQL para cinco sucursales.</p>
          </div><span className="results-count">{catalogLoading ? 'Cargando…' : `${products.length} producto${products.length === 1 ? '' : 's'}`}</span></div>
          <div className="branch-choice">
            <strong>Precios y stock en cinco sucursales</strong>
            <small>Agrega lo que necesites. El sistema seleccionará automáticamente el origen de cada producto al cotizar el envío.</small>
          </div>
          <label className="search-field"><span aria-hidden="true">⌕</span><span className="sr-only">Buscar productos</span>
            <input type="search" value={query} onChange={e => setQuery(e.target.value)} placeholder="Nombre, principio activo, laboratorio o SKU…" />
          </label>
          <div className="category-tabs" role="group" aria-label="Filtrar por categoría">
            {['Todos', ...categories].map(name => <button key={name} type="button" className={`filter-button ${category === name ? 'active' : ''}`} aria-pressed={category === name} onClick={() => setCategory(name)}>{name}</button>)}
          </div>
          {catalogError && <div className="error" role="alert">No fue posible consultar Products Service: {catalogError} <button type="button" onClick={() => {setQuery('');setCategory('Todos')}}>Restablecer filtros</button></div>}
          {!catalogError && !catalogLoading && products.length === 0 && <div className="empty-results">No se encontraron productos para esta búsqueda.</div>}
          <div className="product-grid">{products.map(product => <article className="product-card" key={product.sku}>
            <div className="product-art" aria-hidden="true"><span>{symbols[product.category] || '💚'}</span><small>{product.category}</small></div>
            <div className="product-body">
              <span className="product-code">{product.sku} · Catálogo</span>
              <h3>{product.name}</h3>
              <p className="product-detail">{product.activeIngredient ? `Principio activo: ${product.activeIngredient}` : (product.laboratory || 'Producto de farmacia')}</p>
              <p className="product-presentation">{product.presentation}</p>
              {product.minPrice != null && <p className="product-price">Desde {currency.format(product.minPrice)} <small>según sucursal, sin envío</small></p>}
              <p className={`stock-status ${product.totalStock > 0 ? 'in-stock' : 'out-of-stock'}`}>
                {product.totalStock > 0 ? `${product.totalStock} unidades entre las cinco sucursales` : 'Sin stock en sucursales'}
              </p>
              <button type="button" className="button-text" onClick={() => compare(product)}>Comparar sucursales {comparison[product.sku] ? '−' : '+'}</button>
              {comparison[product.sku] && <div className="branch-comparison" aria-label={`Disponibilidad de ${product.name}`}>
                {comparison[product.sku].map(offer => <div key={offer.branchCode}>
                  <span>{offer.branchName}</span><strong>{currency.format(offer.price)}</strong><small>{offer.stock} un.</small>
                </div>)}
              </div>}
              <button type="button" className="button-primary product-add" disabled={busy || catalogLoading || !(product.totalStock > 0)} onClick={() => add(product)}>Agregar al carrito +</button>
            </div>
          </article>)}</div>
        </section>
        <section className="card cart-section" id="carrito" aria-labelledby="cart-title">
          <div className="section-title"><div><p className="eyebrow">COTIZACIÓN MULTISUCURSAL</p><h2 id="cart-title">Mi carrito <span className="cart-heading-count">{cartCount}</span></h2></div>
            <button type="button" className="button-outline" disabled={busy} onClick={refreshCart}>Actualizar</button></div>
          {groupedCart.length === 0 ? <div className="empty-cart"><span aria-hidden="true">🛒</span><h3>Tu carrito está vacío</h3><p>Explora el catálogo y agrega productos.</p><a href="#catalogo">Explorar productos →</a></div> :
            <div className="cart-list">{groupedCart.map(group => <div className="cart-row" key={group.productCode}>
              <div className="cart-item-info"><span className="cart-item-symbol" aria-hidden="true">+</span><div>
                <strong>{group.productName}</strong><div className="muted">{group.productCode} · Cantidad: <strong>{group.quantity}</strong></div>
              </div></div>
              <button type="button" className="button-remove" disabled={busy} onClick={() => removeGroup(group)}>Eliminar</button>
            </div>)}</div>}
          {groupedCart.length > 0 && <div className="delivery-options">
            <label htmlFor="zone-selector"><strong>Zona de entrega</strong></label>
            <select id="zone-selector" value={zoneCode} onChange={e => changeZone(e.target.value)}>
              {zones.length === 0 && <option value={zoneCode}>Cargando zonas…</option>}
              {zones.map(zone => <option key={zone.code} value={zone.code}>{zone.name}</option>)}
            </select>
            <div className="quote-actions" role="group" aria-label="Prioridad de despacho">
              <button type="button" className={`filter-button ${quoteMode === 'CHEAPEST' ? 'active' : ''}`}
                onClick={() => setQuoteMode('CHEAPEST')}>Mejor precio</button>
              <button type="button" className={`filter-button ${quoteMode === 'FASTEST' ? 'active' : ''}`}
                onClick={() => setQuoteMode('FASTEST')}>Entrega más rápida</button>
            </div>
            {quoteLoading && <p className="muted">Calculando alternativas…</p>}
            {quoteError && <p className="error" role="alert">No fue posible cotizar: {quoteError}</p>}
            {selectedQuote && <div className="quote-result">
              <div className="cart-total">Total estimado con envío: <strong>{currency.format(selectedQuote.total)}</strong></div>
              <p>Productos: {currency.format(selectedQuote.medicinesTotal)} · Envíos: {currency.format(selectedQuote.shippingTotal)}</p>
              <p><strong>Entrega completa estimada: {selectedQuote.estimatedDays} día(s)</strong> · {selectedQuote.shipments.length} despacho(s)</p>
              {selectedQuote.shipments.map(shipment => <div className="shipment" key={shipment.branchCode}>
                <div><strong>{shipment.branchName}</strong> · envío {currency.format(shipment.shippingCost)} · {shipment.deliveryDays} día(s)</div>
                {shipment.items.map(line => <p key={line.sku}>{line.name} ({line.sku}) × {line.quantity} — {currency.format(line.subtotal)}</p>)}
              </div>)}
            </div>}
          </div>}
          <p className="cart-disclaimer">Cotización académica con precios, stock, tarifas y plazos simulados. Los despachos se cobran por sucursal. No hay reservas de inventario ni pagos reales; la disponibilidad puede cambiar antes del checkout.</p>
        </section>
        <section className="card account-section" id="cuenta"><div className="section-heading account-heading">
          <div><p className="eyebrow">IDENTIDAD Y SEGURIDAD</p><h2>Mi cuenta</h2></div><span className="security-tag">Sesión autenticada</span></div>
          {profile ? <><p className="account-name">{profile.name || auth.userName || 'Usuario'}</p>
            <p className="muted">Acceso protegido con Microsoft Entra ID en AWS.</p><details className="technical-details"><summary>Ver detalles técnicos</summary>
              <div className="profile-grid"><span>Identificador</span><code>{profile.userId}</code><span>Scopes</span><code>{profile.scopes?.join(', ') || '-'}</code><span>Roles</span><code>{profile.roles?.join(', ') || '-'}</code><span>Entorno</span><code>{config.authMode}</code></div>
            </details></> : <p className="muted">Sin datos de identidad disponibles.</p>}
        </section>
      </main>
      <footer className="site-footer"><div className="footer-inner"><span><strong>farmaciasimple</strong> · Portal de demostración académica</span><span>Basado en Pedidos360 · v{version}</span></div></footer>
    </div>
  )
}
