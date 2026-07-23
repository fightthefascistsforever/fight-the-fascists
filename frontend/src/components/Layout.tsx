import { Link, Outlet, useLocation, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { fetchChapters } from '../api'

export default function Layout() {
  const { chapterSlug } = useParams<{ chapterSlug: string }>()
  const { locale, setLocale, handle } = useAppStore()
  const t = strings[locale]
  const loc = useLocation()
  const base = `/${chapterSlug}`

  const { data: chapters } = useQuery({ queryKey: ['chapters'], queryFn: fetchChapters })
  const chapter = chapters?.find(c => c.slug === chapterSlug)
  const chapterName = chapter
    ? (locale === 'hi' && chapter.nameHi ? chapter.nameHi : chapter.nameEn)
    : chapterSlug

  const primaryNav = [
    { to: base, key: 'board' as const },
    { to: `${base}/post`, key: 'postNeed' as const },
    { to: `${base}/shifts`, key: 'shifts' as const },
  ]

  const secondaryNav = [
    { to: `${base}/announce`, key: 'announcements' as const },
    { to: `${base}/aid`, key: 'aid' as const },
    { to: `${base}/give`, key: 'give' as const },
    { to: `${base}/about`, key: 'about' as const },
  ]

  const navClass = (to: string) =>
    `flex-1 text-center py-2 rounded-lg text-xs font-medium min-h-10 flex items-center justify-center transition-colors ${
      loc.pathname === to ? 'ftf-nav-active' : 'ftf-nav-inactive'
    }`

  return (
    <div className="ftf-page">
      <header className="ftf-header px-4 py-3">
        <div className="mx-auto max-w-lg flex items-center justify-between gap-2">
          <div>
            <Link to="/" className="text-xs text-slate-500 hover:text-blue-800 transition-colors">{t.appName}</Link>
            <h1 className="text-lg font-bold leading-tight text-slate-900">{chapterName}</h1>
            {chapter && (
              <p className="text-xs text-slate-500">
                {locale === 'hi' && chapter.locationLabelHi ? chapter.locationLabelHi : chapter.locationLabelEn}
              </p>
            )}
          </div>
          <button
            onClick={() => setLocale(locale === 'en' ? 'hi' : 'en')}
            className="min-h-11 min-w-11 px-2 text-sm border border-slate-300 rounded-lg bg-white text-slate-700 hover:bg-slate-50"
            aria-label="Toggle language"
          >
            {locale === 'en' ? 'हि' : 'EN'}
          </button>
        </div>
        {handle && (
          <p className="mx-auto max-w-lg mt-1 text-xs text-blue-700 font-medium px-4">{handle}</p>
        )}
        <nav className="mx-auto max-w-lg flex gap-1 mt-2 px-2">
          {primaryNav.map(({ to, key }) => (
            <Link key={to} to={to} className={navClass(to)}>{t[key]}</Link>
          ))}
        </nav>
        <nav className="mx-auto max-w-lg flex gap-1 mt-1 px-2">
          {secondaryNav.map(({ to, key }) => (
            <Link key={to} to={to} className={navClass(to)}>{t[key]}</Link>
          ))}
          <Link to={`${base}/my`} className={navClass(`${base}/my`)}>{t.myClaims}</Link>
        </nav>
      </header>
      <main className="mx-auto max-w-lg px-4 py-4 pb-8">
        <Outlet />
      </main>
      <footer className="text-center text-xs text-slate-400 pb-4">
        <a href={`/api/v1/chapters/${chapterSlug}/lite`} className="underline hover:text-blue-800">{t.lite}</a>
      </footer>
    </div>
  )
}
