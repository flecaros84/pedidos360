import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from './api'
import { config } from './config'
import { version } from '../package.json'
import JwtInspector from './JwtInspector'

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
  const [orders, setOrders] = useState([])
  const [pendingPaymentOrderId, setPendingPaymentOrderId] = useState(null)
  const [ordersError, setOrdersError] = useState('')
  const [recipientEmail, setRecipientEmail] = useState('')
  const [paymentMode, setPaymentMode] = useState('SIMULATED')
  const [cardReference, setCardReference] = useState('demo-card-0001')
  const [recommendations, setRecommendations] = useState([])
  const [analyticsMetrics, setAnalyticsMetrics] = useState(null)
  const [analyticsError, setAnalyticsError] = useState('')
  const requestKeyRef = useRef(null)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('Todos')
  const groupedCart = useMemo(() => groupCart(cart), [cart])
  const cartCount = groupedCart.reduce((sum, item) => sum + item.quantity, 0)
  const selectedQuote = quoteMode === 'FASTEST' ? quotes?.fastest : quotes?.cheapest
  const recipientEmailValid = /^[^\s@]{1,64}@[^\s@]{1,190}\.[^\s@]{2,}$/.test(recipientEmail.trim())

  async function loadRecommendations() {
    if (!auth.isAuthenticated) return
    try { setRecommendations(await api.recommendations(auth)) }
    catch { setRecommendations([]) }
  }

  async function payOrder(order) {
    if (busy) return
    setBusy(true); setOrdersError(''); setNotice('')
    try {
      const key = globalThis.crypto?.randomUUID?.() || `pay_${Date.now()}`
      const result = await api.pay(auth, {
        orderId: order.id, method: paymentMode,
        ...(paymentMode === 'SIMULATED' ? { cardReference, approved: true } : {}),
      }, key)
      if (paymentMode === 'WEBPAY' && result.redirectUrl && result.webpayToken) {
        const form = document.createElement('form')
        form.method = 'POST'; form.action = result.redirectUrl
        const token = document.createElement('input')
        token.type = 'hidden'; token.name = 'token_ws'; token.value = result.webpayToken
        form.appendChild(token); document.body.appendChild(form); form.submit()
        return
      }
      setNotice(result.status === 'APPROVED'
        ? 'Pago aprobado. Esperando confirmación final del inventario y cierre del carrito…'
        : `Pago ${result.status} — ${result.id}. Revisa el estado del pedido.`)
      if (result.status === 'APPROVED') setPendingPaymentOrderId(order.id)
      await refreshOrders()
      await loadRecommendations()
    } catch (e) { setOrdersError(e.message) }
    finally { setBusy(false) }
  }

  async function refreshOrders() {
    if (!auth.isAuthenticated) return
    try {
      const result = await api.orders(auth)
      setOrders(Array.isArray(result) ? result : [])
      setOrdersError('')
    } catch (e) { setOrdersError(e.message) }
  }

  // Poll only after approved checkout; RabbitMQ completes the order asynchronously.
  useEffect(() => {
    if (!auth.isAuthenticated || !pendingPaymentOrderId) return
    let cancelled = false
    let attempts = 0
    const timer = setInterval(async () => {
      if (cancelled) return
      try {
        const latest = await api.orders(auth)
        if (cancelled || !Array.isArray(latest)) return
        setOrders(latest)
        const order = latest.find(item => item.id === pendingPaymentOrderId)
        if (order?.status === 'PAID') {
          setPendingPaymentOrderId(null)
          await refreshCart()
          setNotice('Pago confirmado. El pedido está pagado; el carrito se está actualizando.')
          // Backend reconciliation may run shortly after the PAID event.
          setTimeout(() => { if (auth.isAuthenticated) refreshCart().catch(() => {}) }, 3500)
        } else if (order?.status === 'PAYMENT_FAILED') {
          setPendingPaymentOrderId(null)
          setOrdersError('El pago no se pudo completar; el carrito conserva sus productos.')
        } else if (++attempts >= 20) {
          setPendingPaymentOrderId(null)
          setNotice('Pago aprobado; la confirmación final sigue en proceso. Puedes actualizar Mis pedidos.')
        }
      } catch {
        if (++attempts >= 20) setPendingPaymentOrderId(null)
      }
    }, 3000)
    return () => { cancelled = true; clearInterval(timer) }
    // auth changes identity with MSAL; do not restart the active payment poll.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.isAuthenticated, pendingPaymentOrderId])

  async function submitOrder() {
    if (!selectedQuote || busy) return
    if (!recipientEmailValid) {
      setOrdersError('Ingresa un correo de notificación válido antes de generar el pedido.')
      return
    }
    const idempotencyKey = requestKeyRef.current || (globalThis.crypto?.randomUUID
      ? crypto.randomUUID() : `order_${Date.now()}_${Math.random().toString(36).slice(2)}`)
    requestKeyRef.current = idempotencyKey
    setBusy(true); setOrdersError(''); setNotice('')
    try {
      const body = { zoneCode, mode: quoteMode, recipientEmail: recipientEmail.trim() }
      const created = await api.createOrder(auth, body, idempotencyKey)
      requestKeyRef.current = null
      await refreshOrders()
      setNotice(`Pedido ${created.id} registrado. Selecciona un método de pago en Mis pedidos para continuar.`)
      // Inventory is reserved only when checkout starts.
      document.getElementById('mis-pedidos')?.scrollIntoView({ behavior: 'smooth' })
    } catch (e) { setOrdersError(e.message) }
    finally { setBusy(false) }
  }

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
    if (auth.isAuthenticated) { refreshCart(); refreshOrders(); loadRecommendations() }
    // Sólo recargar al iniciar/cerrar sesión. auth cambia de referencia en MSAL.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.isAuthenticated])

  useEffect(() => {
    if (!auth.isAuthenticated) return
    let alive = true
    let socket = null
    let poll = null
    api.metrics(auth).then(data => { if (alive) setAnalyticsMetrics(data) })
      .catch(e => { if (alive) setAnalyticsError(e.message) })
    if (config.websocketUrl) {
      api.websocketTicket(auth).then(({ ticket }) => {
        if (!alive) return
        socket = new WebSocket(`${config.websocketUrl}?ticket=${encodeURIComponent(ticket)}`)
        socket.onmessage = event => {
          try {
            const next = JSON.parse(event.data)
            if (alive && next.type === 'analytics.metrics') setAnalyticsMetrics(next.data)
          } catch { /* Ignore non-metric events. */ }
        }
      }).catch(e => { if (alive) setAnalyticsError(e.message) })
    } else {
      // Local fallback; AWS dashboard uses genuine WebSocket pushes.
      poll = setInterval(() => api.metrics(auth).then(data => {
        if (alive) setAnalyticsMetrics(data)
      }).catch(() => {}), 15000)
    }
    return () => { alive = false; socket?.close(); if (poll) clearInterval(poll) }
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
    requestKeyRef.current = null
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
    requestKeyRef.current = null
    setZoneCode(nextZone)
    window.localStorage.setItem('farmaciasimple.zone', nextZone)
  }

  async function compare(product) {
    if (comparison[product.sku]) {
      setComparison(prev => ({ ...prev, [product.sku]: null }))
      return
    }
    try {
      await api.product(auth, product.id) // Publishes a product.view Kafka event.
      const offers = await api.availability(auth, product.id)
      await loadRecommendations()
      setComparison(prev => ({ ...prev, [product.sku]: offers }))
    } catch (e) { setCatalogError(e.message) }
  }

  async function removeGroup(group) {
    requestKeyRef.current = null
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
          <div><span className="service-icon">▤</span><strong>Pedidos por correo</strong><small>Confirmación mediante RabbitMQ</small></div>
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
                onClick={() => { requestKeyRef.current = null; setQuoteMode('CHEAPEST') }}>Mejor precio</button>
              <button type="button" className={`filter-button ${quoteMode === 'FASTEST' ? 'active' : ''}`}
                onClick={() => { requestKeyRef.current = null; setQuoteMode('FASTEST') }}>Entrega más rápida</button>
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
            {selectedQuote && <div className="order-action">
              <label htmlFor="notification-email"><strong>Correo de notificación del pedido</strong>
                <input id="notification-email" type="email" autoComplete="email" required maxLength={254}
                  value={recipientEmail} onChange={e => setRecipientEmail(e.target.value)}
                  placeholder="destinatario@ejemplo.cl" />
              </label>
              <p className="muted">Puedes indicar un correo distinto al de tu cuenta. Es obligatorio para confirmar el pedido.</p>
              <button type="button" className="button-primary" disabled={busy || quoteLoading || !selectedQuote || !recipientEmailValid}
                onClick={submitOrder}>{busy ? 'Registrando pedido…' : 'Generar pedido'}</button>
              {ordersError && <p className="error" role="alert">{ordersError}</p>}
            </div>}
          </div>}
          <p className="cart-disclaimer">Cotización académica con precios, stock, tarifas y plazos simulados. Los despachos se cobran por sucursal. El stock se reserva durante el checkout y se confirma tras la aprobación del pago.</p>
        </section>
        <section className="card orders-section" id="mis-pedidos">
          <div className="section-title"><div><p className="eyebrow">CONFIRMACIONES</p><h2>Mis pedidos</h2></div>
            <button type="button" className="button-outline" onClick={refreshOrders} disabled={busy}>Actualizar</button></div>
          <div className="checkout-options">
            <label>Medio de pago
              <select value={paymentMode} onChange={e => setPaymentMode(e.target.value)}>
                <option value="SIMULATED">Simulación académica</option>
                <option value="WEBPAY">Webpay Plus (integración)</option>
              </select>
            </label>
            {paymentMode === 'SIMULATED' && <label>Referencia de tarjeta de prueba (sin PAN)
              <input value={cardReference} onChange={e => setCardReference(e.target.value)} minLength={4} maxLength={64}/>
            </label>}
          </div>
          {ordersError && <p className="error" role="alert">{ordersError}</p>}
          {orders.length === 0 ? <p className="muted">Todavía no hay pedidos registrados.</p> :
            <div className="orders-list">{orders.map(order => <article className="order-item" key={order.id}>
              <div><strong>Pedido {order.id.slice(0, 8).toUpperCase()}</strong>
                <p className="muted">{new Date(order.createdAt).toLocaleString('es-CL')} · {order.mode === 'CHEAPEST' ? 'Mejor precio' : 'Entrega más rápida'}</p>
                <p className="muted">Notificación: {order.recipientEmail}</p></div>
              <div><strong>{currency.format(order.totalAmount)}</strong><p className="muted">{order.status === 'PAID' ? 'Pago aprobado' : order.status === 'PAYMENT_FAILED' ? 'Pago rechazado' : pendingPaymentOrderId === order.id ? 'Confirmando pago…' : 'Pendiente de pago'}</p>
                {order.status === 'PENDING_PAYMENT' && <button type="button" className="button-primary" disabled={busy || pendingPaymentOrderId === order.id} onClick={() => payOrder(order)}>{pendingPaymentOrderId === order.id ? 'Procesando…' : 'Pagar pedido'}</button>}
              </div>
            </article>)}</div>}
          <p className="muted">Las pruebas usan el simulador o el ambiente de integración de Webpay, nunca tarjetas reales en nuestro backend.</p>
        </section>
        <section className="card analytics-section" id="analitica">
          <div className="section-title"><div><p className="eyebrow">EVENTOS KAFKA</p><h2>Analítica en tiempo real</h2></div></div>
          {analyticsError && <p className="muted">Métricas aún no disponibles: {analyticsError}</p>}
          <div className="metrics-grid">
            <article><small>Vistas de productos</small><strong>{analyticsMetrics?.['product.view'] ?? 0}</strong></article>
            <article><small>Interacciones de carrito</small><strong>{analyticsMetrics?.['cart.add'] ?? 0}</strong></article>
            <article><small>Productos visualizados</small><strong>{analyticsMetrics?.activeProducts ?? 0}</strong></article>
          </div>
          <h3>Productos más vistos</h3>
          <div className="top-products">{(analyticsMetrics?.topProducts ?? []).map(p =>
            <p key={p.sku}>{p.sku} · {p.views} visualizaciones</p>)}</div>
          <h3>Recomendaciones para ti</h3>
          {recommendations.length === 0 ? <p className="muted">Explora productos para generar recomendaciones personalizadas.</p> :
            recommendations.map(item => <p key={item.sku}>{item.sku} · afinidad {item.score}</p>)}
          <button type="button" className="button-outline" onClick={loadRecommendations}>Actualizar recomendaciones</button>
        </section>
        <section className="card account-section" id="cuenta"><div className="section-heading account-heading">
          <div><p className="eyebrow">IDENTIDAD Y SEGURIDAD</p><h2>Mi cuenta</h2></div><span className="security-tag">Sesión autenticada</span></div>
          {profile ? <><p className="account-name">{profile.name || auth.userName || 'Usuario'}</p>
            <p className="muted">Acceso protegido con Microsoft Entra ID en AWS.</p><details className="technical-details"><summary>Ver detalles técnicos</summary>
              <div className="profile-grid"><span>Identificador</span><code>{profile.userId}</code><span>Scopes</span><code>{profile.scopes?.join(', ') || '-'}</code><span>Roles</span><code>{profile.roles?.join(', ') || '-'}</code><span>Entorno</span><code>{config.authMode}</code></div>
              {auth.mode === 'azure' && <JwtInspector getAccessToken={auth.getAccessToken} />}
            </details></> : <p className="muted">Sin datos de identidad disponibles.</p>}
        </section>
      </main>
      <footer className="site-footer"><div className="footer-inner"><span><strong>farmaciasimple</strong> · Portal de demostración académica</span><span>Basado en Pedidos360 · v{version}</span></div></footer>
    </div>
  )
}
