import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useZones, useChapterSlug } from '../hooks'
import { createNeed, registerDevice } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'

const CATEGORIES = ['WATER', 'FOOD_COOKED', 'FOOD_DRY', 'ORS_ELECTROLYTE', 'MEDICAL_SUPPLY',
  'SHADE_TARPAULIN', 'BEDGING', 'SANITATION', 'POWER_CHARGING', 'CLOTHING', 'OTHER']
const UNITS = ['LITRES', 'MEALS', 'PACKETS', 'PIECES', 'PEOPLE_SERVED']
const URGENCIES = ['URGENT', 'SOON', 'ROUTINE']

export default function PostPage() {
  const chapterSlug = useChapterSlug()
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
      await createNeed(chapterSlug, secret, {
        zoneId,
        category,
        quantity: parseFloat(quantity),
        unit,
        urgency,
        note: note || null,
      }, idempotencyKey)
      navigate(`/${chapterSlug}`)
    } catch (err: unknown) {
      const e = err as { error?: { message?: string } }
      setError(e?.error?.message || 'Failed to post need')
    } finally {
      setSubmitting(false)
    }
  }

  const selectClass = 'ftf-input'

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm text-slate-600 mb-1 font-medium">{t.zone}</label>
        <select value={zoneId} onChange={e => setZoneId(Number(e.target.value))} className={selectClass}>
          {zones?.map((z: { id: number; code: string; nameEn: string; nameHi: string }) => (
            <option key={z.id} value={z.id}>
              {locale === 'hi' ? z.nameHi : z.nameEn}
            </option>
          ))}
        </select>
      </div>

      <div>
        <label className="block text-sm text-slate-600 mb-1 font-medium">{t.category}</label>
        <select value={category} onChange={e => setCategory(e.target.value)} className={selectClass}>
          {CATEGORIES.map(c => (
            <option key={c} value={c}>{t.categories[c as keyof typeof t.categories] || c}</option>
          ))}
        </select>
      </div>

      <div className="flex gap-2">
        <div className="flex-1">
          <label className="block text-sm text-slate-600 mb-1 font-medium">{t.quantity}</label>
          <input type="number" value={quantity} onChange={e => setQuantity(e.target.value)}
            className={selectClass} min="1" required />
        </div>
        <div className="flex-1">
          <label className="block text-sm text-slate-600 mb-1 font-medium">Unit</label>
          <select value={unit} onChange={e => setUnit(e.target.value)} className={selectClass}>
            {UNITS.map(u => <option key={u} value={u}>{u}</option>)}
          </select>
        </div>
      </div>

      <div>
        <label className="block text-sm text-slate-600 mb-1 font-medium">{t.urgency}</label>
        <div className="flex gap-2">
          {URGENCIES.map(u => (
            <button key={u} type="button" onClick={() => setUrgency(u)}
              className={`flex-1 min-h-11 rounded-lg text-sm font-medium transition-colors ${
                urgency === u ? 'bg-blue-800 text-white' : 'bg-white border border-slate-300 text-slate-700 hover:bg-slate-50'
              }`}>
              {t.urgencies[u as keyof typeof t.urgencies]}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label className="block text-sm text-slate-600 mb-1 font-medium">{t.note}</label>
        <textarea value={note} onChange={e => setNote(e.target.value)} maxLength={200}
          className={`${selectClass} min-h-20`} />
      </div>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      <button type="submit" disabled={submitting}
        className="w-full min-h-12 ftf-btn-primary disabled:opacity-50 rounded-xl font-bold text-lg">
        {submitting ? t.loading : t.submit}
      </button>
    </form>
  )
}
