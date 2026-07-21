const API = '/api/v1'

async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input)
  const hash = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(hash)).map(b => b.toString(16).padStart(2, '0')).join('')
}

function countLeadingZeroBits(hex: string): number {
  let bits = 0
  for (const c of hex) {
    const nibble = parseInt(c, 16)
    if (nibble === 0) bits += 4
    else { bits += Math.clz32(nibble) - 28; break }
  }
  return bits
}

export async function solvePow(challenge: string, difficulty: number): Promise<string> {
  let nonce = 0
  while (true) {
    const hash = await sha256Hex(challenge + nonce)
    if (countLeadingZeroBits(hash) >= difficulty) return String(nonce)
    nonce++
    if (nonce % 500 === 0) await new Promise(r => setTimeout(r, 0))
  }
}

export async function getPowHeader(): Promise<string> {
  const res = await fetch(`${API}/pow/challenge`)
  const json = await res.json()
  const { challenge, difficulty } = json.data
  const nonce = await solvePow(challenge, difficulty)
  return `${challenge}.${nonce}`
}

async function apiFetch(path: string, opts: RequestInit & { deviceSecret?: string; pow?: boolean } = {}) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(opts.headers as Record<string, string> || {}),
  }
  if (opts.deviceSecret) headers['X-Device'] = opts.deviceSecret
  if (opts.pow) headers['X-PoW'] = await getPowHeader()

  const res = await fetch(`${API}${path}`, { ...opts, headers })
  const json = await res.json()
  if (!res.ok) throw json
  return json
}

export async function registerDevice(): Promise<{ deviceSecret: string; handle: string }> {
  const json = await apiFetch('/devices/register', { method: 'POST' })
  return { deviceSecret: json.data.deviceSecret, handle: json.data.handle }
}

export async function fetchZones() {
  const json = await apiFetch('/zones')
  return json.data
}

export async function fetchNeeds(zone?: number, category?: string) {
  const params = new URLSearchParams()
  if (zone) params.set('zone', String(zone))
  if (category) params.set('category', category)
  const q = params.toString() ? `?${params}` : ''
  const json = await apiFetch(`/needs${q}`)
  return json.data
}

export async function createNeed(deviceSecret: string, body: object, idempotencyKey: string) {
  return apiFetch('/needs', {
    method: 'POST',
    deviceSecret,
    pow: true,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(body),
  })
}

export async function createClaim(deviceSecret: string, needId: string, body: object) {
  return apiFetch(`/needs/${needId}/claims`, {
    method: 'POST',
    deviceSecret,
    pow: true,
    body: JSON.stringify(body),
  })
}

export async function flagCovered(deviceSecret: string, needId: string) {
  return apiFetch(`/needs/${needId}/flag`, {
    method: 'POST',
    deviceSecret,
    body: JSON.stringify({ reason: 'ALREADY_COVERED' }),
  })
}

export async function forgetDevice(deviceSecret: string) {
  return apiFetch('/devices/me', { method: 'DELETE', deviceSecret })
}

export interface Need {
  id: string
  zoneId: number
  zoneCode: string
  category: string
  quantity: number
  unit: string
  pledged: number
  delivered: number
  urgency: string
  state: string
  neededBy: string
  note?: string
}

export interface Zone {
  id: number
  code: string
  nameEn: string
  nameHi: string
  landmarkEn: string
  svgX: number
  svgY: number
}
