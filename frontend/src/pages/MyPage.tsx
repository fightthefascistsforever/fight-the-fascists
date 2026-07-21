import { strings } from '../i18n/strings'
import { useAppStore } from '../store'
import { forgetDevice, registerDevice } from '../api'

export default function MyPage() {
  const { locale, deviceSecret, handle, setDevice, clearDevice } = useAppStore()
  const t = strings[locale]

  const handleRegister = async () => {
    const reg = await registerDevice()
    setDevice(reg.deviceSecret, reg.handle)
  }

  const handleForget = async () => {
    if (deviceSecret) {
      try { await forgetDevice(deviceSecret) } catch { /* ok */ }
    }
    clearDevice()
  }

  return (
    <div className="space-y-4">
      {handle ? (
        <div className="bg-slate-900 rounded-xl p-4 border border-slate-700">
          <p className="text-sm text-slate-400">Your handle</p>
          <p className="text-xl font-bold text-teal-400">{handle}</p>
        </div>
      ) : (
        <p className="text-slate-400 text-center">No device registered yet.</p>
      )}

      {!deviceSecret && (
        <button onClick={handleRegister}
          className="w-full min-h-12 bg-teal-600 rounded-xl font-bold">
          Register device
        </button>
      )}

      {deviceSecret && (
        <button onClick={handleForget}
          className="w-full min-h-11 border border-red-800 text-red-400 rounded-xl">
          {t.forgetDevice}
        </button>
      )}

      <div className="bg-red-950/50 border border-red-800 rounded-xl p-4 text-sm text-red-200">
        {t.emergency}
      </div>
    </div>
  )
}
