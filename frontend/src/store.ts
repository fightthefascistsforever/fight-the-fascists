import { create } from 'zustand'
import type { Locale } from './i18n/strings'

const DEVICE_KEY = 'ftf_device_secret'
const LOCALE_KEY = 'ftf_locale'
const TOKEN_KEY = 'ftf_steward_token'
const TIER_KEY = 'ftf_steward_tier'

interface AppState {
  locale: Locale
  deviceSecret: string | null
  handle: string | null
  stewardToken: string | null
  stewardTier: string | null
  setLocale: (l: Locale) => void
  setDevice: (secret: string, handle: string) => void
  setStewardToken: (token: string, tier: string) => void
  clearStewardToken: () => void
  clearDevice: () => void
  loadFromStorage: () => void
}

export const useAppStore = create<AppState>((set) => ({
  locale: (localStorage.getItem(LOCALE_KEY) as Locale) || 'en',
  deviceSecret: localStorage.getItem(DEVICE_KEY),
  handle: localStorage.getItem('ftf_handle'),
  stewardToken: localStorage.getItem(TOKEN_KEY),
  stewardTier: localStorage.getItem(TIER_KEY),
  setLocale: (l) => {
    localStorage.setItem(LOCALE_KEY, l)
    set({ locale: l })
  },
  setDevice: (secret, handle) => {
    localStorage.setItem(DEVICE_KEY, secret)
    localStorage.setItem('ftf_handle', handle)
    set({ deviceSecret: secret, handle })
  },
  setStewardToken: (token, tier) => {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(TIER_KEY, tier)
    set({ stewardToken: token, stewardTier: tier })
  },
  clearStewardToken: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(TIER_KEY)
    set({ stewardToken: null, stewardTier: null })
  },
  clearDevice: () => {
    localStorage.removeItem(DEVICE_KEY)
    localStorage.removeItem('ftf_handle')
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(TIER_KEY)
    set({ deviceSecret: null, handle: null, stewardToken: null, stewardTier: null })
  },
  loadFromStorage: () => {
    set({
      deviceSecret: localStorage.getItem(DEVICE_KEY),
      handle: localStorage.getItem('ftf_handle'),
      stewardToken: localStorage.getItem(TOKEN_KEY),
      stewardTier: localStorage.getItem(TIER_KEY),
      locale: (localStorage.getItem(LOCALE_KEY) as Locale) || 'en',
    })
  },
}))
