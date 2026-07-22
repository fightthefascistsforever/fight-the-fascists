import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchBulkPledges, createBulkPledge, registerDevice } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { useChapterSlug } from '../hooks'

const CATEGORIES = ['WATER', 'FOOD_COOKED', 'FOOD_DRY', 'ORS_ELECTROLYTE']
const SLOTS = [
  { hour: 8, label: 'Breakfast 8am' },
  { hour: 13, label: 'Lunch 1pm' },
  { hour: 20, label: 'Dinner 8pm' },
]

export default function GivePage() {
  const chapterSlug = useChapterSlug()
  const { locale, deviceSecret, setDevice } = useAppStore()
  const t = strings[locale]
  const qc = useQueryClient()
  const { data: pledges } = useQuery({
    queryKey: ['bulk', chapterSlug],
    queryFn: () => fetchBulkPledges(chapterSlug),
  })

  const [orgName, setOrgName] = useState('')
  const [category, setCategory] = useState('FOOD_COOKED')
  const [quantity, setQuantity] = useState('200')
  const unit = category === 'WATER' ? 'LITRES' : category === 'FOOD_COOKED' || category === 'FOOD_DRY' ? 'MEALS' : 'PACKETS'
  const [slotHour, setSlotHour] = useState(20)
  const [slotLabel, setSlotLabel] = useState('Dinner 8pm')
  const [foodSafetyAck, setFoodSafetyAck] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      let secret = deviceSecret
      if (!secret) {
        const reg = await registerDevice()
        setDevice(reg.deviceSecret, reg.handle)
        secret = reg.deviceSecret
      }
      await createBulkPledge(chapterSlug, secret, {
        orgName, category, quantity: parseFloat(quantity), unit,
        slotHour, slotLabel, foodSafetyAck,
        prepWindowMinutes: category === 'FOOD_COOKED' ? 120 : null,
      })
      qc.invalidateQueries({ queryKey: ['bulk', chapterSlug] })
      setOrgName('')
    } catch (err: unknown) {
      const e = err as { error?: { message?: string } }
      setError(e?.error?.message || 'Failed to submit')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-6">
      <p className="text-sm text-slate-600">{t.giveDesc}</p>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm text-slate-600 mb-1 font-medium">{t.orgName}</label>
          <input value={orgName} onChange={e => setOrgName(e.target.value)} required
            className="ftf-input" placeholder="Langar name, restaurant, NGO…" />
        </div>
        <div className="flex gap-2">
          <div className="flex-1">
            <label className="block text-sm text-slate-600 mb-1 font-medium">{t.category}</label>
            <select value={category} onChange={e => setCategory(e.target.value)} className="ftf-input">
              {CATEGORIES.map(c => (
                <option key={c} value={c}>{t.categories[c as keyof typeof t.categories] || c}</option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <label className="block text-sm text-slate-600 mb-1 font-medium">{t.quantity}</label>
            <input type="number" value={quantity} onChange={e => setQuantity(e.target.value)}
              className="ftf-input" min="1" required />
          </div>
        </div>
        <div>
          <label className="block text-sm text-slate-600 mb-1 font-medium">{t.slot}</label>
          <div className="flex gap-2 flex-wrap">
            {SLOTS.map(s => (
              <button key={s.hour} type="button"
                onClick={() => { setSlotHour(s.hour); setSlotLabel(s.label) }}
                className={`min-h-10 px-3 rounded-lg text-sm transition-colors ${slotHour === s.hour ? 'bg-blue-800 text-white' : 'bg-white border border-slate-300 text-slate-700 hover:bg-slate-50'}`}>
                {s.label}
              </button>
            ))}
          </div>
        </div>
        {category === 'FOOD_COOKED' && (
          <label className="flex items-start gap-2 text-sm text-slate-700">
            <input type="checkbox" checked={foodSafetyAck} onChange={e => setFoodSafetyAck(e.target.checked)}
              className="mt-1" />
            {t.foodSafetyAck}
          </label>
        )}
        {error && <p className="text-red-600 text-sm">{error}</p>}
        <button type="submit" disabled={submitting || (category === 'FOOD_COOKED' && !foodSafetyAck)}
          className="w-full min-h-12 ftf-btn-primary rounded-xl font-bold disabled:opacity-50">
          {submitting ? t.loading : t.submitPledge}
        </button>
      </form>

      {pledges?.length > 0 && (
        <section>
          <h2 className="font-bold mb-3 text-slate-900">{t.activePledges}</h2>
          <div className="space-y-2">
            {pledges.map((p: import('../api').BulkPledge) => (
              <div key={p.id} className="ftf-card p-3 text-sm">
                <p className="font-medium text-slate-900">{p.orgName}</p>
                <p className="text-slate-500">{p.quantity} {p.unit.toLowerCase()} — {p.slotLabel}</p>
                {!p.approved && <span className="text-xs text-amber-700">{t.pendingApproval}</span>}
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
