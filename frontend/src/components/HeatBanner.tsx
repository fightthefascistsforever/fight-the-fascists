import { useQuery } from '@tanstack/react-query'
import { fetchHeatBand } from '../api'
import { useAppStore } from '../store'
import { useChapterSlug } from '../hooks'

export default function HeatBanner() {
  const { locale } = useAppStore()
  const chapterSlug = useChapterSlug()
  const { data: heat } = useQuery({
    queryKey: ['heat', chapterSlug],
    queryFn: () => fetchHeatBand(chapterSlug),
    staleTime: 15 * 60_000,
  })

  if (!heat) return null

  const colors = {
    GREEN: 'bg-emerald-50 border-emerald-200 text-emerald-900',
    AMBER: 'bg-amber-50 border-amber-200 text-amber-900',
    RED: 'bg-red-50 border-red-200 text-red-900',
  }[heat.band as 'GREEN' | 'AMBER' | 'RED'] || 'bg-slate-50 border-slate-200 text-slate-800'

  return (
    <div className={`border rounded-xl p-3 text-sm ${colors}`}>
      <div className="flex justify-between items-center">
        <span className="font-bold">{heat.band} — {heat.temperatureC.toFixed(0)}°C</span>
      </div>
      <p className="mt-1 opacity-90">{locale === 'hi' ? heat.messageHi : heat.messageEn}</p>
    </div>
  )
}
