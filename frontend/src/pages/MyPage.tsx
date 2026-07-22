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
        <div className="ftf-card p-4">
          <p className="text-sm text-slate-500">Your handle</p>
          <p className="text-xl font-bold text-blue-800">{handle}</p>
        </div>
      ) : (
        <p className="text-slate-500 text-center">No device registered yet.</p>
      )}

      {!deviceSecret && (
        <button onClick={handleRegister}
          className="w-full min-h-12 ftf-btn-primary rounded-xl font-bold">
          Register device
        </button>
      )}

      {deviceSecret && (
        <button onClick={handleForget}
          className="w-full min-h-11 border border-red-300 text-red-700 bg-red-50 hover:bg-red-100 rounded-xl">
          {t.forgetDevice}
        </button>
      )}

      <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-sm text-red-800">
        {t.emergency}
      </div>
    </div>
  )
}
