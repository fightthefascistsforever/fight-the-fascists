import { useNeeds, useChapterSlug } from '../hooks'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import NeedCard from '../components/NeedCard'
import HeatBanner from '../components/HeatBanner'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { flagCovered, fetchAnnouncements, fetchForecast } from '../api'
import { Link } from 'react-router-dom'

export default function BoardPage() {
  const { locale, deviceSecret } = useAppStore()
  const chapterSlug = useChapterSlug()
  const t = strings[locale]
  const { data: needs, isLoading, isError } = useNeeds()
  const { data: announcements } = useQuery({
    queryKey: ['announcements', chapterSlug],
    queryFn: () => fetchAnnouncements(chapterSlug),
    refetchInterval: 60_000,
  })
  const { data: forecast } = useQuery({
    queryKey: ['forecast', chapterSlug],
    queryFn: () => fetchForecast(chapterSlug),
    refetchInterval: 15 * 60_000,
  })
  const qc = useQueryClient()

  const handleFlag = async (id: string) => {
    if (!deviceSecret) return
    await flagCovered(chapterSlug, deviceSecret, id)
    qc.invalidateQueries({ queryKey: ['needs', chapterSlug] })
  }

  if (isLoading) return <p className="text-center py-8 text-slate-400">{t.loading}</p>
  if (isError) return <p className="text-center py-8 text-red-400">Failed to load board</p>

  return (
    <div className="space-y-3">
      <HeatBanner />
      {!navigator.onLine && (
        <p className="text-amber-400 text-sm text-center bg-amber-950/50 rounded-lg py-2">{t.offline}</p>
      )}
      {announcements?.length > 0 && (
        <Link to={`/${chapterSlug}/announce`} className="block border border-teal-700 rounded-xl p-3 bg-teal-950/30 text-sm">
          <span className="text-teal-400 font-medium">{t.announcements}: </span>
          {announcements[0].bodyEn.slice(0, 80)}{announcements[0].bodyEn.length > 80 ? '…' : ''}
        </Link>
      )}
      {forecast?.shortfalls?.length > 0 && (
        <div className="border border-amber-700 rounded-xl p-3 bg-amber-950/20">
          <p className="text-sm font-medium text-amber-300 mb-2">{t.forecastTitle}</p>
          {forecast.shortfalls.map((s: { category: string; shortfall: number; unit: string }) => (
            <p key={s.category} className="text-xs text-amber-200">
              {t.categories[s.category as keyof typeof t.categories] || s.category}: ~{Math.round(s.shortfall)} {s.unit.toLowerCase()} {t.shortfall}
            </p>
          ))}
        </div>
      )}
      {!needs?.length ? (
        <p className="text-center py-8 text-slate-300">{t.noNeeds}</p>
      ) : (
        needs.map((need: import('../api').Need) => (
          <NeedCard key={need.id} need={need} onFlag={deviceSecret ? handleFlag : undefined} />
        ))
      )}
    </div>
  )
}
