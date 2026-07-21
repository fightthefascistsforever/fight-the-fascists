import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchChapters } from '../api'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'

export default function ChapterPickerPage() {
  const { locale } = useAppStore()
  const t = strings[locale]
  const { data: chapters, isLoading, isError } = useQuery({
    queryKey: ['chapters'],
    queryFn: fetchChapters,
  })

  if (isLoading) return <p className="text-center py-12 text-slate-400">{t.loading}</p>
  if (isError) return <p className="text-center py-12 text-red-400">{t.chaptersError}</p>

  const active = chapters?.filter(c => c.status === 'ACTIVE') ?? []
  const planned = chapters?.filter(c => c.status === 'PLANNED') ?? []

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 px-4 py-8">
      <div className="mx-auto max-w-lg space-y-6">
        <header className="text-center">
          <h1 className="text-2xl font-bold">{t.appName}</h1>
          <p className="text-slate-400 mt-1">{t.chapterPickerTitle}</p>
        </header>

        {active.length > 0 && (
          <section className="space-y-3">
            <h2 className="text-sm font-medium text-teal-400 uppercase tracking-wide">{t.activeChapters}</h2>
            {active.map(ch => (
              <Link
                key={ch.slug}
                to={`/${ch.slug}`}
                className="block border border-teal-800 rounded-xl p-4 bg-teal-950/30 hover:bg-teal-950/50 transition"
              >
                <p className="font-bold text-lg">{locale === 'hi' && ch.nameHi ? ch.nameHi : ch.nameEn}</p>
                <p className="text-sm text-slate-400 mt-1">
                  {locale === 'hi' && ch.locationLabelHi ? ch.locationLabelHi : ch.locationLabelEn}
                </p>
              </Link>
            ))}
          </section>
        )}

        {planned.length > 0 && (
          <section className="space-y-3">
            <h2 className="text-sm font-medium text-slate-500 uppercase tracking-wide">{t.plannedChapters}</h2>
            {planned.map(ch => (
              <div key={ch.slug} className="border border-slate-800 rounded-xl p-4 bg-slate-900 opacity-60">
                <p className="font-bold">{locale === 'hi' && ch.nameHi ? ch.nameHi : ch.nameEn}</p>
                <p className="text-sm text-slate-500 mt-1">{t.chapterNotActive}</p>
              </div>
            ))}
          </section>
        )}

        {!active.length && !planned.length && (
          <p className="text-center text-slate-400">{t.noChapters}</p>
        )}
      </div>
    </div>
  )
}
