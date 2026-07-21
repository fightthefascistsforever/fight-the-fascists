import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  stewardLogin, registerDevice, fetchModerationQueue,
  approveNeed, removeNeed, createAnnouncement,
} from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { useChapterSlug } from '../hooks'

export default function StewardPage() {
  const chapterSlug = useChapterSlug()
  const { locale, deviceSecret, stewardToken, stewardTier, setDevice, setStewardToken, clearStewardToken } = useAppStore()
  const t = strings[locale]
  const qc = useQueryClient()
  const [passphrase, setPassphrase] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const [error, setError] = useState('')
  const [annBody, setAnnBody] = useState('')
  const [annSource, setAnnSource] = useState('OBSERVED_ON_SITE')

  const { data: queue } = useQuery({
    queryKey: ['moderation', chapterSlug],
    queryFn: () => fetchModerationQueue(chapterSlug, stewardToken!),
    enabled: !!stewardToken,
  })

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      let secret = deviceSecret
      if (!secret) {
        const reg = await registerDevice()
        setDevice(reg.deviceSecret, reg.handle)
        secret = reg.deviceSecret
      }
      const res = await stewardLogin(secret, passphrase, totpCode)
      setStewardToken(res.token, res.tier)
    } catch (err: unknown) {
      const e = err as { error?: { message?: string } }
      setError(e?.error?.message || 'Login failed')
    }
  }

  const handleApprove = async (id: string) => {
    await approveNeed(chapterSlug, stewardToken!, id)
    qc.invalidateQueries({ queryKey: ['moderation', chapterSlug] })
  }

  const handleRemove = async (id: string) => {
    await removeNeed(chapterSlug, stewardToken!, id)
    qc.invalidateQueries({ queryKey: ['moderation', chapterSlug] })
  }

  const handlePostAnnouncement = async (e: React.FormEvent) => {
    e.preventDefault()
    await createAnnouncement(chapterSlug, stewardToken!, { bodyEn: annBody, source: annSource, urgent: false })
    setAnnBody('')
    qc.invalidateQueries({ queryKey: ['announcements', chapterSlug] })
  }

  if (!stewardToken) {
    return (
      <form onSubmit={handleLogin} className="space-y-4 max-w-sm mx-auto">
        <h2 className="text-lg font-bold text-center">{t.stewardLogin}</h2>
        <div>
          <label className="block text-sm text-slate-400 mb-1">{t.passphrase}</label>
          <input type="password" value={passphrase} onChange={e => setPassphrase(e.target.value)}
            className="w-full min-h-11 px-3 bg-slate-800 border border-slate-700 rounded-lg" required />
        </div>
        <div>
          <label className="block text-sm text-slate-400 mb-1">{t.totpCode}</label>
          <input type="text" inputMode="numeric" value={totpCode} onChange={e => setTotpCode(e.target.value)}
            className="w-full min-h-11 px-3 bg-slate-800 border border-slate-700 rounded-lg" required />
        </div>
        {error && <p className="text-red-400 text-sm">{error}</p>}
        <button type="submit" className="w-full min-h-12 bg-teal-600 rounded-xl font-bold">{t.login}</button>
      </form>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <span className="text-sm text-teal-400">Tier: {stewardTier}</span>
        <button onClick={clearStewardToken} className="text-sm text-slate-400 underline">{t.logout}</button>
      </div>

      <section>
        <h2 className="font-bold mb-3">{t.moderationQueue}</h2>
        {!queue?.length ? (
          <p className="text-slate-400 text-sm">Queue empty.</p>
        ) : (
          <div className="space-y-2">
            {queue.map((item: import('../api').QueueItem) => (
              <div key={item.id} className="border border-slate-700 rounded-lg p-3 bg-slate-900">
                <p className="text-sm"><strong>{item.zoneCode}</strong> — {item.category}</p>
                {item.notePreview && <p className="text-xs text-slate-400 mt-1">{item.notePreview}</p>}
                <div className="flex gap-2 mt-2">
                  <button onClick={() => handleApprove(item.id)}
                    className="flex-1 min-h-10 bg-teal-700 rounded-lg text-sm">{t.approve}</button>
                  <button onClick={() => handleRemove(item.id)}
                    className="flex-1 min-h-10 border border-red-800 text-red-400 rounded-lg text-sm">{t.remove}</button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <h2 className="font-bold mb-3">{t.postAnnouncement}</h2>
        <form onSubmit={handlePostAnnouncement} className="space-y-3">
          <textarea value={annBody} onChange={e => setAnnBody(e.target.value)} required maxLength={500}
            className="w-full min-h-24 px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg" />
          <select value={annSource} onChange={e => setAnnSource(e.target.value)}
            className="w-full min-h-11 px-3 bg-slate-800 border border-slate-700 rounded-lg">
            {Object.entries(t.sources).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
          <button type="submit" className="w-full min-h-11 bg-teal-600 rounded-lg font-medium">{t.submit}</button>
        </form>
      </section>
    </div>
  )
}
