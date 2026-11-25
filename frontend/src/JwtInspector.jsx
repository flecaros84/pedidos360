import { useState } from 'react'

// Decoding is for demonstration: API Gateway and Spring Security validate JWT signatures and scopes.
function decodeSegment(segment) {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='))
  const bytes = Uint8Array.from(binary, character => character.charCodeAt(0))
  return JSON.parse(new TextDecoder().decode(bytes))
}

function readableTimestamp(seconds) {
  return Number.isFinite(seconds) ? new Date(seconds * 1000).toLocaleString('es-CL') : '—'
}

export default function JwtInspector({ getAccessToken }) {
  const [jwt, setJwt] = useState('')
  const [decoded, setDecoded] = useState(null)
  const [visible, setVisible] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)

  async function load() {
    setBusy(true); setError(''); setCopied(false); setVisible(false)
    try {
      const token = await getAccessToken()
      const parts = token?.split('.')
      if (parts?.length !== 3) throw new Error('El token no tiene formato JWT')
      setDecoded({ header: decodeSegment(parts[0]), claims: decodeSegment(parts[1]) })
      setJwt(token)
    } catch (exception) {
      setJwt(''); setDecoded(null); setError(exception.message)
    } finally { setBusy(false) }
  }

  function clear() {
    setJwt(''); setDecoded(null); setVisible(false); setCopied(false); setError('')
  }

  async function copyJwt() {
    try {
      await navigator.clipboard.writeText(jwt)
      setCopied(true)
    } catch { setError('No se pudo copiar el token desde este navegador.') }
  }

  return <details className="jwt-inspector" onToggle={event => { if (!event.currentTarget.open) clear() }}>
    <summary>Ver JWT de acceso (demostración académica)</summary>
    <div className="jwt-content">
      <p className="muted">Token emitido por Microsoft Entra ID para consumir nuestra API. Se obtiene desde la sesión actual, solo al solicitarlo. Los claims se decodifican localmente; aquí no se verifica criptográficamente la firma.</p>
      {!decoded && <button className="button-outline" type="button" disabled={busy} onClick={load}>{busy ? 'Obteniendo token…' : 'Cargar JWT actual'}</button>}
      {error && <p className="error" role="alert">{error}</p>}
      {decoded && <>
        <div className="profile-grid">
          <span>Algoritmo</span><code>{decoded.header.alg || '—'}</code>
          <span>Key ID (kid)</span><code>{decoded.header.kid || '—'}</code>
          <span>Emisor (iss)</span><code>{decoded.claims.iss || '—'}</code>
          <span>Audiencia (aud)</span><code>{Array.isArray(decoded.claims.aud) ? decoded.claims.aud.join(', ') : (decoded.claims.aud || '—')}</code>
          <span>Tenant ID</span><code>{decoded.claims.tid || '—'}</code>
          <span>Usuario (oid)</span><code>{decoded.claims.oid || '—'}</code>
          <span>Scopes (scp)</span><code>{decoded.claims.scp || '—'}</code>
          <span>Emitido</span><code>{readableTimestamp(decoded.claims.iat)}</code>
          <span>Expira</span><code>{readableTimestamp(decoded.claims.exp)}</code>
        </div>
        <details className="jwt-claims"><summary>Encabezado JWT (JSON)</summary><pre>{JSON.stringify(decoded.header, null, 2)}</pre></details>
        <details className="jwt-claims"><summary>Claims JWT (JSON)</summary><pre>{JSON.stringify(decoded.claims, null, 2)}</pre></details>
        <p className="jwt-warning">El JWT completo es una credencial temporal: no lo incluyas en capturas, grabaciones ni informes públicos. El profesor puede revisar la estructura y los claims sin revelarlo.</p>
        <div className="jwt-actions">
          <button type="button" className="button-outline" onClick={() => setVisible(previous => !previous)}>{visible ? 'Ocultar token' : 'Mostrar token completo'}</button>
          <button type="button" className="button-outline" onClick={copyJwt}>{copied ? 'Copiado' : 'Copiar JWT'}</button>
          <button type="button" className="button-text" onClick={clear}>Cerrar y limpiar</button>
        </div>
        {visible && <textarea className="jwt-raw" readOnly spellCheck={false} value={jwt} aria-label="JWT completo" />}
      </>}
    </div>
  </details>
}
