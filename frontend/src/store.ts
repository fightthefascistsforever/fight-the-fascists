import { create } from 'zustand'
import type { Locale } from './i18n/strings'

const DEVICE_KEY = 'ftf_device_secret'
const LOCALE_KEY = 'ftf_locale'

interface AppState {
  locale: Locale
  deviceSecret: string | null
  handle: string | null
  setLocale: (l: Locale) => void
  setDevice: (secret: string, handle: string) => void
  clearDevice: () => void
  loadFromStorage: () => void
}

export const useAppStore = create<AppState>((set) => ({
  locale: (localStorage.getItem(LOCALE_KEY) as Locale) || 'en',
  deviceSecret: localStorage.getItem(DEVICE_KEY),
  handle: localStorage.getItem('ftf_handle'),
  setLocale: (l) => {
    localStorage.setItem(LOCALE_KEY, l)
    set({ locale: l })
  },
  setDevice: (secret, handle) => {
    localStorage.setItem(DEVICE_KEY, secret)
    localStorage.setItem('ftf_handle', handle)
    set({ deviceSecret: secret, handle })
  },
  clearDevice: () => {
    localStorage.removeItem(DEVICE_KEY)
    localStorage.removeItem('ftf_handle')
    set({ deviceSecret: null, handle: null })
  },
  loadFromStorage: () => {
    set({
      deviceSecret: localStorage.getItem(DEVICE_KEY),
      handle: localStorage.getItem('ftf_handle'),
      locale: (localStorage.getItem(LOCALE_KEY) as Locale) || 'en',
    })
  },
}))
