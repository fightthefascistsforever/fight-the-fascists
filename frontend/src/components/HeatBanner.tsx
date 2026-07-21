import { useQuery } from '@tanstack/react-query'
import { fetchHeatBand } from '../api'
import { useAppStore } from '../store'

export default function HeatBanner() {
  const { locale } = useAppStore()
  const { data: heat } = useQuery({ queryKey: ['heat'], queryFn: fetchHeatBand, staleTime: 15 * 60_000 })

  if (!heat) return null

  const colors = {
    GREEN: 'bg-green-950 border-green-700 text-green-100',
    AMBER: 'bg-amber-950 border-amber-600 text-amber-100',
    RED: 'bg-red-950 border-red-600 text-red-100',
  }[heat.band as 'GREEN' | 'AMBER' | 'RED'] || 'bg-slate-900 border-slate-700'

  return (
    <div className={`border rounded-xl p-3 text-sm ${colors}`}>
      <div className="flex justify-between items-center">
        <span className="font-bold">{heat.band} — {heat.temperatureC.toFixed(0)}°C</span>
      </div>
      <p className="mt-1">{locale === 'hi' ? heat.messageHi : heat.messageEn}</p>
    </div>
  )
}
