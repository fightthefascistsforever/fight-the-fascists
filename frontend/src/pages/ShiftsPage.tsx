import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchShifts, signupShift, registerDevice } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { useChapterSlug } from '../hooks'

export default function ShiftsPage() {
  const chapterSlug = useChapterSlug()
  const { locale, deviceSecret, setDevice } = useAppStore()
  const t = strings[locale]
  const qc = useQueryClient()
  const { data: shifts, isLoading } = useQuery({
    queryKey: ['shifts', chapterSlug],
    queryFn: () => fetchShifts(chapterSlug),
  })

  const ensureDevice = async () => {
    if (deviceSecret) return deviceSecret
    const reg = await registerDevice()
    setDevice(reg.deviceSecret, reg.handle)
    return reg.deviceSecret
  }

  const handleSignup = async (id: string) => {
    const secret = await ensureDevice()
    await signupShift(chapterSlug, secret, id)
    qc.invalidateQueries({ queryKey: ['shifts', chapterSlug] })
  }

  if (isLoading) return <p className="text-center py-8 text-slate-400">{t.loading}</p>

  return (
    <div className="space-y-3">
      {shifts?.map((s: import('../api').Shift) => {
        const understaffed = s.signedUp < s.minVolunteers
        const full = s.signedUp >= s.maxVolunteers
        const start = new Date(s.startsAt).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata', hour: '2-digit', minute: '2-digit', day: 'numeric', month: 'short' })
        const end = new Date(s.endsAt).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata', hour: '2-digit', minute: '2-digit' })
        return (
          <article key={s.id} className={`border rounded-xl p-4 bg-slate-900 ${understaffed ? 'border-amber-500' : 'border-slate-700'}`}>
            <div className="flex justify-between items-start">
              <div>
                <span className="text-teal-400 font-bold">{s.zoneCode}</span>
                <p className="text-sm font-medium mt-1">
                  {t.roles[s.role as keyof typeof t.roles] || s.role}
                </p>
                <p className="text-xs text-slate-400 mt-1">{start} – {end} IST</p>
              </div>
              <div className="text-right text-sm">
                <span className={understaffed ? 'text-amber-400 font-bold' : 'text-slate-300'}>
                  {s.signedUp}/{s.maxVolunteers}
                </span>
                {understaffed && <p className="text-xs text-amber-400">{t.understaffed}</p>}
              </div>
            </div>
            {s.notice && <p className="text-xs text-amber-300 mt-2 bg-amber-950/40 rounded p-2">{s.notice}</p>}
            {!full && (
              <button onClick={() => handleSignup(s.id)}
                className="mt-3 w-full min-h-11 bg-teal-600 rounded-lg font-medium">
                {t.signUp}
              </button>
            )}
          </article>
        )
      })}
    </div>
  )
}
