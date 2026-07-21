import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useZones } from '../hooks'
import { createNeed, registerDevice } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'

const CATEGORIES = ['WATER', 'FOOD_COOKED', 'FOOD_DRY', 'ORS_ELECTROLYTE', 'MEDICAL_SUPPLY',
  'SHADE_TARPAULIN', 'BEDGING', 'SANITATION', 'POWER_CHARGING', 'CLOTHING', 'OTHER']
const UNITS = ['LITRES', 'MEALS', 'PACKETS', 'PIECES', 'PEOPLE_SERVED']
const URGENCIES = ['URGENT', 'SOON', 'ROUTINE']

export default function PostPage() {
  const { locale, deviceSecret, setDevice } = useAppStore()
  const t = strings[locale]
  const { data: zones } = useZones()
  const navigate = useNavigate()
  const [zoneId, setZoneId] = useState<number>(1)
  const [category, setCategory] = useState('WATER')
  const [quantity, setQuantity] = useState('40')
  const [unit, setUnit] = useState('LITRES')
  const [urgency, setUrgency] = useState('URGENT')
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const ensureDevice = async () => {
    if (deviceSecret) return deviceSecret
    const reg = await registerDevice()
    setDevice(reg.deviceSecret, reg.handle)
    return reg.deviceSecret
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const secret = await ensureDevice()
      const idempotencyKey = crypto.randomUUID()
      await createNeed(secret, {
        zoneId,
        category,
        quantity: parseFloat(quantity),
        unit,
        urgency,
        note: note || null,
      }, idempotencyKey)
      navigate('/')
    } catch (err: unknown) {
      const e = err as { error?: { message?: string } }
      setError(e?.error?.message || 'Failed to post need')
    } finally {
      setSubmitting(false)
    }
  }

  const selectClass = 'w-full min-h-11 px-3 bg-slate-800 border border-slate-700 rounded-lg text-base'

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm text-slate-400 mb-1">{t.zone}</label>
        <select value={zoneId} onChange={e => setZoneId(Number(e.target.value))} className={selectClass}>
          {zones?.map((z: { id: number; code: string; nameEn: string; nameHi: string }) => (
            <option key={z.id} value={z.id}>
              {locale === 'hi' ? z.nameHi : z.nameEn}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm text-slate-400 mb-1">{t.category}</label>
        <select value={category} onChange={e => setCategory(e.target.value)} className={selectClass}>
          {CATEGORIES.map(c => (
            <option key={c} value={c}>{t.categories[c as keyof typeof t.categories] || c}</option>
          ))}
        </select>
      </div>

      <div className="flex gap-2">
        <div className="flex-1">
          <label className="block text-sm text-slate-400 mb-1">{t.quantity}</label>
          <input type="number" value={quantity} onChange={e => setQuantity(e.target.value)}
            className={selectClass} min="1" required />
        </div>
        <div className="flex-1">
          <label className="block text-sm text-slate-400 mb-1">Unit</label>
          <select value={unit} onChange={e => setUnit(e.target.value)} className={selectClass}>
            {UNITS.map(u => <option key={u} value={u}>{u}</option>)}
          </select>
        </div>
      </div>

      <div>
        <label className="block text-sm text-slate-400 mb-1">{t.urgency}</label>
        <div className="flex gap-2">
          {URGENCIES.map(u => (
            <button key={u} type="button" onClick={() => setUrgency(u)}
              className={`flex-1 min-h-11 rounded-lg text-sm font-medium ${
                urgency === u ? 'bg-teal-700' : 'bg-slate-800 border border-slate-700'
              }`}>
              {t.urgencies[u as keyof typeof t.urgencies]}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label className="block text-sm text-slate-400 mb-1">{t.note}</label>
        <textarea value={note} onChange={e => setNote(e.target.value)} maxLength={200}
          className={`${selectClass} min-h-20`} />
      </div>

      {error && <p className="text-red-400 text-sm">{error}</p>}

      <button type="submit" disabled={submitting}
        className="w-full min-h-12 bg-teal-600 hover:bg-teal-500 disabled:opacity-50 rounded-xl font-bold text-lg">
        {submitting ? t.loading : t.submit}
      </button>
    </form>
  )
}
