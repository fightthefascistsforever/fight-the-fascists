import type { Need } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { Link, useParams } from 'react-router-dom'

interface Props {
  need: Need
  onFlag?: (id: string) => void
}

export default function NeedCard({ need, onFlag }: Props) {
  const { locale } = useAppStore()
  const { chapterSlug } = useParams<{ chapterSlug: string }>()
  const t = strings[locale]
  const pct = Math.min(100, (need.pledged / need.quantity) * 100)
  const urgencyClass =
    need.urgency === 'URGENT' ? 'border-red-400' :
    need.urgency === 'SOON' ? 'border-amber-400' : 'border-slate-200'

  return (
    <article className={`border-2 rounded-xl p-4 ${urgencyClass} ftf-card`}>
      <div className="flex justify-between items-start gap-2">
        <div>
          <span className="ftf-accent-text text-lg">{need.zoneCode}</span>
          <span className="ml-2 text-sm text-slate-500">
            {t.categories[need.category as keyof typeof t.categories] || need.category}
          </span>
        </div>
        <span className={`text-xs px-2 py-1 rounded font-medium ${
          need.urgency === 'URGENT' ? 'bg-red-100 text-red-800' :
          need.urgency === 'SOON' ? 'bg-amber-100 text-amber-900' : 'bg-slate-100 text-slate-600'
        }`}>
          {t.urgencies[need.urgency as keyof typeof t.urgencies] || need.urgency}
        </span>
      </div>

      <div className="mt-3">
        <div className="flex justify-between text-sm mb-1 text-slate-700">
          <span>{need.pledged} / {need.quantity} {need.unit.toLowerCase()} {t.pledged}</span>
          <span>{Math.round(pct)}%</span>
        </div>
        <div className="h-2 bg-slate-200 rounded-full overflow-hidden">
          <div className="h-full bg-blue-700 transition-all" style={{ width: `${pct}%` }} />
        </div>
      </div>

      {need.note && <p className="mt-2 text-sm text-slate-500">{need.note}</p>}

      <div className="mt-3 flex gap-2">
        <Link
          to={`/${chapterSlug}/claim/${need.id}`}
          className="flex-1 min-h-11 flex items-center justify-center ftf-btn-primary rounded-lg text-center"
        >
          {t.claim}
        </Link>
        {onFlag && (
          <button
            onClick={() => onFlag(need.id)}
            className="min-h-11 px-3 ftf-btn-secondary text-sm"
          >
            {t.alreadyCovered}
          </button>
        )}
      </div>
    </article>
  )
}
