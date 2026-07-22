import { useQuery } from '@tanstack/react-query'
import { fetchAnnouncements } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { useChapterSlug } from '../hooks'

export default function AnnouncementsPage() {
  const chapterSlug = useChapterSlug()
  const { locale } = useAppStore()
  const t = strings[locale]
  const { data: items, isLoading } = useQuery({
    queryKey: ['announcements', chapterSlug],
    queryFn: () => fetchAnnouncements(chapterSlug),
    refetchInterval: 60_000,
  })

  if (isLoading) return <p className="text-center py-8 text-slate-500">{t.loading}</p>

  if (!items?.length) {
    return <p className="text-center py-12 text-slate-500">No announcements yet.</p>
  }

  return (
    <div className="space-y-3">
      {items.map((a: import('../api').Announcement) => (
        <article key={a.id} className={`ftf-card p-4 ${a.urgent ? 'border-red-300' : ''}`}>
          <div className="flex justify-between items-start gap-2 mb-2">
            <span className="text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-600">
              {t.sources[a.source as keyof typeof t.sources] || a.source}
            </span>
            {a.urgent && <span className="text-xs px-2 py-0.5 rounded bg-red-100 text-red-800">{t.urgent}</span>}
          </div>
          <p className="text-slate-800">{locale === 'hi' && a.bodyHi ? a.bodyHi : a.bodyEn}</p>
          <p className="text-xs text-slate-400 mt-2">
            {new Date(a.createdAt).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })} IST
          </p>
        </article>
      ))}
    </div>
  )
}
