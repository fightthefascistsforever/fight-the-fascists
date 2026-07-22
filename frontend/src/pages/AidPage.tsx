import { useQuery } from '@tanstack/react-query'
import { fetchAidPoints } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { useChapterSlug } from '../hooks'

export default function AidPage() {
  const chapterSlug = useChapterSlug()
  const { locale } = useAppStore()
  const t = strings[locale]
  const { data: points, isLoading } = useQuery({
    queryKey: ['aid', chapterSlug],
    queryFn: () => fetchAidPoints(chapterSlug),
  })

  return (
    <div className="space-y-4">
      <div className="bg-red-50 border-2 border-red-300 rounded-xl p-4 text-red-800 text-sm font-medium">
        {t.emergency}
      </div>
      <p className="text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded-lg p-3">{t.fastNotice}</p>

      {isLoading ? (
        <p className="text-center py-8 text-slate-500">{t.loading}</p>
      ) : (
        <div className="space-y-3">
          {points?.map((p: import('../api').AidPoint) => (
            <article key={p.id} className="ftf-card p-4">
              <div className="flex justify-between items-start">
                <div>
                  <span className="ftf-accent-text">{p.zoneCode}</span>
                  <p className="font-medium mt-1 text-slate-800">{p.name}</p>
                </div>
                <span className={`text-xs px-2 py-1 rounded font-medium ${
                  p.status === 'OPEN' ? 'bg-emerald-100 text-emerald-800' :
                  p.status === 'AT_CAPACITY' ? 'bg-amber-100 text-amber-900' :
                  p.status === 'CLOSED' ? 'bg-red-100 text-red-800' : 'bg-slate-100 text-slate-600'
                }`}>
                  {t.aidStatus[p.status as keyof typeof t.aidStatus] || p.status}
                </span>
              </div>
              {p.hoursNote && <p className="text-sm text-slate-500 mt-2">{p.hoursNote}</p>}
              {p.cannotHandle && (
                <p className="text-xs text-amber-700 mt-1">Cannot handle: {p.cannotHandle}</p>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
