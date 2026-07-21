const API = '/api/v1'

function chapterPath(slug: string, path: string) {
  return `/chapters/${slug}${path}`
}

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

async function apiFetch(path: string, opts: RequestInit & { deviceSecret?: string; stewardToken?: string; pow?: boolean } = {}) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(opts.headers as Record<string, string> || {}),
  }
  if (opts.deviceSecret) headers['X-Device'] = opts.deviceSecret
  if (opts.stewardToken) headers['Authorization'] = `Bearer ${opts.stewardToken}`
  if (opts.pow) headers['X-PoW'] = await getPowHeader()

  const res = await fetch(`${API}${path}`, { ...opts, headers })
  const json = await res.json()
  if (!res.ok) throw json
  return json
}

export async function fetchChapters() {
  const json = await apiFetch('/chapters')
  return json.data as Chapter[]
}

export async function registerDevice(): Promise<{ deviceSecret: string; handle: string }> {
  const json = await apiFetch('/devices/register', { method: 'POST' })
  return { deviceSecret: json.data.deviceSecret, handle: json.data.handle }
}

export async function fetchZones(chapterSlug: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/zones'))
  return json.data
}

export async function fetchNeeds(chapterSlug: string, zone?: number, category?: string) {
  const params = new URLSearchParams()
  if (zone) params.set('zone', String(zone))
  if (category) params.set('category', category)
  const q = params.toString() ? `?${params}` : ''
  const json = await apiFetch(chapterPath(chapterSlug, `/needs${q}`))
  return json.data
}

export async function createNeed(chapterSlug: string, deviceSecret: string, body: object, idempotencyKey: string) {
  return apiFetch(chapterPath(chapterSlug, '/needs'), {
    method: 'POST',
    deviceSecret,
    pow: true,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(body),
  })
}

export async function createClaim(chapterSlug: string, deviceSecret: string, needId: string, body: object) {
  return apiFetch(chapterPath(chapterSlug, `/needs/${needId}/claims`), {
    method: 'POST',
    deviceSecret,
    pow: true,
    body: JSON.stringify(body),
  })
}

export async function flagCovered(chapterSlug: string, deviceSecret: string, needId: string) {
  return apiFetch(chapterPath(chapterSlug, `/needs/${needId}/flag`), {
    method: 'POST',
    deviceSecret,
    body: JSON.stringify({ reason: 'ALREADY_COVERED' }),
  })
}

export async function forgetDevice(deviceSecret: string) {
  return apiFetch('/devices/me', { method: 'DELETE', deviceSecret })
}

export async function fetchShifts(chapterSlug: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/shifts'))
  return json.data
}

export async function signupShift(chapterSlug: string, deviceSecret: string, shiftId: string) {
  return apiFetch(chapterPath(chapterSlug, `/shifts/${shiftId}/signup`), { method: 'POST', deviceSecret })
}

export async function fetchAnnouncements(chapterSlug: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/announcements'))
  return json.data
}

export async function fetchAidPoints(chapterSlug: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/aid-points'))
  return json.data
}

export async function stewardLogin(deviceSecret: string, passphrase: string, totpCode: string) {
  const json = await apiFetch('/admin/login', {
    method: 'POST',
    deviceSecret,
    body: JSON.stringify({ passphrase, totpCode }),
  })
  return json.data as { token: string; tier: string }
}

export async function fetchModerationQueue(chapterSlug: string, stewardToken: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/moderation/queue'), { stewardToken })
  return json.data
}

export async function approveNeed(chapterSlug: string, stewardToken: string, needId: string) {
  return apiFetch(chapterPath(chapterSlug, `/moderation/${needId}/approve`), { method: 'POST', stewardToken })
}

export async function removeNeed(chapterSlug: string, stewardToken: string, needId: string) {
  return apiFetch(chapterPath(chapterSlug, `/moderation/${needId}/remove`), { method: 'POST', stewardToken })
}

export async function createAnnouncement(chapterSlug: string, stewardToken: string, body: object) {
  return apiFetch(chapterPath(chapterSlug, '/announcements'), { method: 'POST', stewardToken, body: JSON.stringify(body) })
}

export async function fetchBulkPledges(chapterSlug: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/bulk-pledges'))
  return json.data
}

export async function createBulkPledge(chapterSlug: string, deviceSecret: string, body: object) {
  return apiFetch(chapterPath(chapterSlug, '/bulk-pledges'), { method: 'POST', deviceSecret, body: JSON.stringify(body) })
}

export async function fetchForecast(chapterSlug: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/forecast'))
  return json.data
}

export async function fetchHeatBand(chapterSlug: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/heat-band'))
  return json.data
}

export async function fetchStats(chapterSlug: string) {
  const json = await apiFetch(chapterPath(chapterSlug, '/stats'))
  return json.data
}

export interface Chapter {
  slug: string
  nameEn: string
  nameHi?: string
  locationLabelEn: string
  locationLabelHi?: string
  status: string
}

export interface BulkPledge {
  id: string
  orgName: string
  category: string
  quantity: number
  unit: string
  slotHour: number
  slotLabel: string
  approved: boolean
  foodSafetyAck: boolean
}

export interface Forecast {
  heatBand: string
  headcountEstimate: number
  timeWindow: string
  shortfalls: Array<{
    category: string
    unit: string
    projected: number
    bulkSupply: number
    openNeeds: number
    shortfall: number
  }>
}

export interface HeatBand {
  band: string
  temperatureC: number
  messageEn: string
  messageHi: string
}

export interface Stats {
  period: string
  needsPosted: number
  needsFulfilled: number
  litresDelivered: number
  mealsDelivered: number
  claimsDelivered: number
  volunteerShifts: number
  activeDevices: number
  note: string
}

export interface Shift {
  id: string
  zoneCode: string
  role: string
  startsAt: string
  endsAt: string
  minVolunteers: number
  maxVolunteers: number
  signedUp: number
  notice?: string
}

export interface Announcement {
  id: string
  bodyEn: string
  bodyHi?: string
  source: string
  urgent: boolean
  published: boolean
  createdAt: string
}

export interface AidPoint {
  id: number
  zoneCode: string
  name: string
  status: string
  hoursNote?: string
  cannotHandle?: string
}

export interface QueueItem {
  id: string
  targetType: string
  zoneCode: string
  category: string
  createdAt: string
  notePreview?: string
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
