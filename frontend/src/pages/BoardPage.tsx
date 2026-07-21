import { useNeeds } from '../hooks'
import { useQuery } from '@tanstack/react-query'
import NeedCard from '../components/NeedCard'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { flagCovered, fetchAnnouncements } from '../api'
import { useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

export default function BoardPage() {
  const { locale, deviceSecret } = useAppStore()
  const t = strings[locale]
  const { data: needs, isLoading, isError } = useNeeds()
  const { data: announcements } = useQuery({ queryKey: ['announcements'], queryFn: fetchAnnouncements, refetchInterval: 60_000 })
  const qc = useQueryClient()

  const handleFlag = async (id: string) => {
    if (!deviceSecret) return
    await flagCovered(deviceSecret, id)
    qc.invalidateQueries({ queryKey: ['needs'] })
  }

  if (isLoading) return <p className="text-center py-8 text-slate-400">{t.loading}</p>
  if (isError) return <p className="text-center py-8 text-red-400">Failed to load board</p>

  if (!needs?.length) {
    return (
      <div className="text-center py-12">
        <p className="text-lg text-slate-300">{t.noNeeds}</p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      {!navigator.onLine && (
        <p className="text-amber-400 text-sm text-center bg-amber-950/50 rounded-lg py-2">{t.offline}</p>
      )}
      {announcements?.length > 0 && (
        <Link to="/announce" className="block border border-teal-700 rounded-xl p-3 bg-teal-950/30 text-sm">
          <span className="text-teal-400 font-medium">{t.announcements}: </span>
          {announcements[0].bodyEn.slice(0, 80)}{announcements[0].bodyEn.length > 80 ? '…' : ''}
        </Link>
      )}
      {needs.map((need: import('../api').Need) => (
        <NeedCard key={need.id} need={need} onFlag={deviceSecret ? handleFlag : undefined} />
      ))}
    </div>
  )
}
