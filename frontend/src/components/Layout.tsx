import { Link, Outlet, useLocation } from 'react-router-dom'
import { strings } from '../i18n/strings'
import { useAppStore } from '../store'

const primaryNav = [
  { to: '/', key: 'board' as const },
  { to: '/post', key: 'postNeed' as const },
  { to: '/shifts', key: 'shifts' as const },
]

const secondaryNav = [
  { to: '/announce', key: 'announcements' as const },
  { to: '/aid', key: 'aid' as const },
  { to: '/give', key: 'give' as const },
  { to: '/about', key: 'about' as const },
]

export default function Layout() {
  const { locale, setLocale, handle } = useAppStore()
  const t = strings[locale]
  const loc = useLocation()

  const navClass = (to: string) =>
    `flex-1 text-center py-2 rounded-lg text-xs font-medium min-h-10 flex items-center justify-center ${
      loc.pathname === to ? 'bg-teal-700 text-white' : 'bg-slate-800 text-slate-300'
    }`

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <header className="sticky top-0 z-10 border-b border-slate-800 bg-slate-950/95 backdrop-blur px-4 py-3">
        <div className="mx-auto max-w-lg flex items-center justify-between gap-2">
          <div>
            <h1 className="text-lg font-bold leading-tight">{t.appName}</h1>
            <p className="text-xs text-slate-400">{t.tagline}</p>
          </div>
          <button
            onClick={() => setLocale(locale === 'en' ? 'hi' : 'en')}
            className="min-h-11 min-w-11 px-2 text-sm border border-slate-700 rounded-lg"
            aria-label="Toggle language"
          >
            {locale === 'en' ? 'हि' : 'EN'}
          </button>
        </div>
        {handle && (
          <p className="mx-auto max-w-lg mt-1 text-xs text-teal-400 px-4">{handle}</p>
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
          <Link to="/my" className={navClass('/my')}>{t.myClaims}</Link>
        </nav>
      </header>
      <main className="mx-auto max-w-lg px-4 py-4 pb-8">
        <Outlet />
      </main>
      <footer className="text-center text-xs text-slate-600 pb-4">
        <a href="/api/v1/lite" className="underline">{t.lite}</a>
      </footer>
    </div>
  )
}
