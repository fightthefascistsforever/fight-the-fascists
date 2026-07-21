import type { Need } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { Link } from 'react-router-dom'

interface Props {
  need: Need
  onFlag?: (id: string) => void
}

export default function NeedCard({ need, onFlag }: Props) {
  const { locale } = useAppStore()
  const t = strings[locale]
  const pct = Math.min(100, (need.pledged / need.quantity) * 100)
  const urgencyClass =
    need.urgency === 'URGENT' ? 'border-red-500' :
    need.urgency === 'SOON' ? 'border-amber-500' : 'border-slate-700'

  return (
    <article className={`border-2 rounded-xl p-4 ${urgencyClass} bg-slate-900`}>
      <div className="flex justify-between items-start gap-2">
        <div>
          <span className="text-teal-400 font-bold text-lg">{need.zoneCode}</span>
          <span className="ml-2 text-sm text-slate-400">
            {t.categories[need.category as keyof typeof t.categories] || need.category}
          </span>
        </div>
        <span className={`text-xs px-2 py-1 rounded font-medium ${
          need.urgency === 'URGENT' ? 'bg-red-900 text-red-200' :
          need.urgency === 'SOON' ? 'bg-amber-900 text-amber-200' : 'bg-slate-800'
        }`}>
          {t.urgencies[need.urgency as keyof typeof t.urgencies] || need.urgency}
        </span>
      </div>

      <div className="mt-3">
        <div className="flex justify-between text-sm mb-1">
          <span>{need.pledged} / {need.quantity} {need.unit.toLowerCase()} {t.pledged}</span>
          <span>{Math.round(pct)}%</span>
        </div>
        <div className="h-2 bg-slate-800 rounded-full overflow-hidden">
          <div className="h-full bg-teal-500 transition-all" style={{ width: `${pct}%` }} />
        </div>
      </div>

      {need.note && <p className="mt-2 text-sm text-slate-400">{need.note}</p>}

      <div className="mt-3 flex gap-2">
        <Link
          to={`/claim/${need.id}`}
          className="flex-1 min-h-11 flex items-center justify-center bg-teal-600 hover:bg-teal-500 rounded-lg font-medium text-center"
        >
          {t.claim}
        </Link>
        {onFlag && (
          <button
            onClick={() => onFlag(need.id)}
            className="min-h-11 px-3 border border-slate-600 rounded-lg text-sm text-slate-400"
          >
            {t.alreadyCovered}
          </button>
        )}
      </div>
    </article>
  )
}
