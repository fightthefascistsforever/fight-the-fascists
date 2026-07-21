import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { createClaim, registerDevice, fetchNeeds } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { useChapterSlug } from '../hooks'

const ETA_OPTIONS = [
  { label: '30 min', minutes: 30 },
  { label: '1h', minutes: 60 },
  { label: '2h', minutes: 120 },
  { label: '4h', minutes: 240 },
]

export default function ClaimPage() {
  const { needId } = useParams<{ needId: string }>()
  const chapterSlug = useChapterSlug()
  const { locale, deviceSecret, setDevice } = useAppStore()
  const t = strings[locale]
  const navigate = useNavigate()
  const [quantity, setQuantity] = useState('')
  const [etaMinutes, setEtaMinutes] = useState(60)
  const [confirmed, setConfirmed] = useState(false)
  const [handoffCode, setHandoffCode] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const { data: needs } = useQuery({
    queryKey: ['needs', chapterSlug],
    queryFn: () => fetchNeeds(chapterSlug),
  })
  const need = needs?.find((n: { id: string }) => n.id === needId)

  if (!need) return <p className="text-center py-8">{t.loading}</p>

  if (handoffCode) {
    return (
      <div className="text-center py-8 space-y-4">
        <p className="text-slate-400">{t.handoffCode}</p>
        <p className="text-5xl font-bold tracking-widest text-teal-400">{handoffCode}</p>
        <p className="text-sm text-slate-400">{t.showAtDrop}</p>
        <button onClick={() => navigate(`/${chapterSlug}`)} className="min-h-11 px-6 bg-slate-800 rounded-lg">
          {t.board}
        </button>
      </div>
    )
  }

  const ensureDevice = async () => {
    if (deviceSecret) return deviceSecret
    const reg = await registerDevice()
    setDevice(reg.deviceSecret, reg.handle)
    return reg.deviceSecret
  }

  const handleClaim = async () => {
    setSubmitting(true)
    setError('')
    try {
      const secret = await ensureDevice()
      const qty = parseFloat(quantity) || (need.quantity - need.pledged)
      const res = await createClaim(chapterSlug, secret, needId!, { quantity: qty, etaMinutes })
      setHandoffCode(res.data.handoffCode)
    } catch (err: unknown) {
      const e = err as { error?: { message?: string } }
      setError(e?.error?.message || 'Failed to claim')
    } finally {
      setSubmitting(false)
    }
  }

  const remaining = need.quantity - need.pledged

  return (
    <div className="space-y-4">
      <div className="bg-slate-900 rounded-xl p-4 border border-slate-700">
        <p className="text-teal-400 font-bold text-xl">{need.zoneCode}</p>
        <p className="text-slate-300 mt-1">
          {need.pledged} / {need.quantity} {need.unit.toLowerCase()} {t.pledged}
        </p>
      </div>

      {!confirmed ? (
        <>
          <p className="text-slate-300">
            You're pledging supplies for <strong>{need.zoneCode}</strong>.
            Zone {need.zoneCode} is relying on this.
          </p>
          <button onClick={() => setConfirmed(true)}
            className="w-full min-h-12 bg-teal-600 rounded-xl font-bold">
            {t.confirm}
          </button>
          <button onClick={() => navigate(`/${chapterSlug}`)} className="w-full min-h-11 text-slate-400">
            {t.cancel}
          </button>
        </>
      ) : (
        <>
          <div>
            <label className="block text-sm text-slate-400 mb-1">{t.quantity}</label>
            <input type="number" value={quantity} onChange={e => setQuantity(e.target.value)}
              placeholder={String(remaining)}
              className="w-full min-h-11 px-3 bg-slate-800 border border-slate-700 rounded-lg" />
          </div>
          <div>
            <label className="block text-sm text-slate-400 mb-1">ETA</label>
            <div className="flex gap-2 flex-wrap">
              {ETA_OPTIONS.map(o => (
                <button key={o.minutes} type="button" onClick={() => setEtaMinutes(o.minutes)}
                  className={`min-h-11 px-4 rounded-lg text-sm ${
                    etaMinutes === o.minutes ? 'bg-teal-700' : 'bg-slate-800 border border-slate-700'
                  }`}>
                  {o.label}
                </button>
              ))}
            </div>
          </div>
          {error && <p className="text-red-400 text-sm">{error}</p>}
          <button onClick={handleClaim} disabled={submitting}
            className="w-full min-h-12 bg-teal-600 rounded-xl font-bold disabled:opacity-50">
            {submitting ? t.loading : t.claim}
          </button>
        </>
      )}
    </div>
  )
}
