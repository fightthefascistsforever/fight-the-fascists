import { useQuery } from '@tanstack/react-query'
import { fetchStats } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { useChapterSlug } from '../hooks'

export default function AboutPage() {
  const chapterSlug = useChapterSlug()
  const { locale } = useAppStore()
  const t = strings[locale]
  const { data: stats, isLoading } = useQuery({
    queryKey: ['stats', chapterSlug],
    queryFn: () => fetchStats(chapterSlug),
  })

  return (
    <div className="space-y-6">
      <section>
        <h2 className="text-lg font-bold mb-2 text-slate-900">{t.aboutTitle}</h2>
        <p className="text-sm text-slate-600 leading-relaxed">{t.aboutDesc}</p>
      </section>

      <section>
        <h2 className="font-bold mb-3 text-slate-900">{t.transparency}</h2>
        {isLoading ? (
          <p className="text-slate-500">{t.loading}</p>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {[
              { label: t.statNeedsPosted, value: stats?.needsPosted },
              { label: t.statNeedsFulfilled, value: stats?.needsFulfilled },
              { label: t.statLitres, value: stats?.litresDelivered },
              { label: t.statMeals, value: stats?.mealsDelivered },
              { label: t.statClaims, value: stats?.claimsDelivered },
              { label: t.statShifts, value: stats?.volunteerShifts },
            ].map(({ label, value }) => (
              <div key={label} className="ftf-card p-3 text-center">
                <p className="text-2xl font-bold text-blue-800">{value ?? '—'}</p>
                <p className="text-xs text-slate-500 mt-1">{label}</p>
              </div>
            ))}
          </div>
        )}
        <p className="text-xs text-slate-400 mt-3">{stats?.note}</p>
      </section>

      <section className="space-y-2">
        <h2 className="font-bold mb-2 text-slate-900">{t.resources}</h2>
        <a href={`/api/v1/chapters/${chapterSlug}/board.pdf`} target="_blank" rel="noopener"
          className="block min-h-11 flex items-center justify-center ftf-btn-secondary text-sm">
          {t.printBoard}
        </a>
        <a href={`/api/v1/chapters/${chapterSlug}/mirror/html`} target="_blank" rel="noopener"
          className="block min-h-11 flex items-center justify-center ftf-btn-secondary text-sm mt-2">
          {t.staticMirror}
        </a>
        <a href={`/api/v1/chapters/${chapterSlug}/lite`}
          className="block min-h-11 flex items-center justify-center ftf-btn-secondary text-sm mt-2">
          {t.lite}
        </a>
      </section>

      <section className="text-xs text-slate-500 space-y-2">
        <p>{t.privacyNote}</p>
        <p>{t.notTracking}</p>
      </section>
    </div>
  )
}
