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
      <div className="bg-red-950 border-2 border-red-600 rounded-xl p-4 text-red-100 text-sm font-medium">
        {t.emergency}
      </div>
      <p className="text-xs text-slate-400 bg-slate-900 rounded-lg p-3">{t.fastNotice}</p>

      {isLoading ? (
        <p className="text-center py-8 text-slate-400">{t.loading}</p>
      ) : (
        <div className="space-y-3">
          {points?.map((p: import('../api').AidPoint) => (
            <article key={p.id} className="border border-slate-700 rounded-xl p-4 bg-slate-900">
              <div className="flex justify-between items-start">
                <div>
                  <span className="text-teal-400 font-bold">{p.zoneCode}</span>
                  <p className="font-medium mt-1">{p.name}</p>
                </div>
                <span className={`text-xs px-2 py-1 rounded font-medium ${
                  p.status === 'OPEN' ? 'bg-green-900 text-green-200' :
                  p.status === 'AT_CAPACITY' ? 'bg-amber-900 text-amber-200' :
                  p.status === 'CLOSED' ? 'bg-red-900 text-red-200' : 'bg-slate-800 text-slate-400'
                }`}>
                  {t.aidStatus[p.status as keyof typeof t.aidStatus] || p.status}
                </span>
              </div>
              {p.hoursNote && <p className="text-sm text-slate-400 mt-2">{p.hoursNote}</p>}
              {p.cannotHandle && (
                <p className="text-xs text-amber-400 mt-1">Cannot handle: {p.cannotHandle}</p>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
