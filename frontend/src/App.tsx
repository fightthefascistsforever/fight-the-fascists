import { useEffect } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Layout from './components/Layout'
import ChapterPickerPage from './pages/ChapterPickerPage'
import BoardPage from './pages/BoardPage'
import PostPage from './pages/PostPage'
import ClaimPage from './pages/ClaimPage'
import MyPage from './pages/MyPage'
import ShiftsPage from './pages/ShiftsPage'
import AnnouncementsPage from './pages/AnnouncementsPage'
import AidPage from './pages/AidPage'
import StewardPage from './pages/StewardPage'
import GivePage from './pages/GivePage'
import AboutPage from './pages/AboutPage'
import { useAppStore } from './store'

const qc = new QueryClient()

function AppInner() {
  const loadFromStorage = useAppStore(s => s.loadFromStorage)
  useEffect(() => { loadFromStorage() }, [loadFromStorage])

  return (
    <BrowserRouter>
      <Routes>
        <Route index element={<ChapterPickerPage />} />
        <Route path=":chapterSlug" element={<Layout />}>
          <Route index element={<BoardPage />} />
          <Route path="post" element={<PostPage />} />
          <Route path="claim/:needId" element={<ClaimPage />} />
          <Route path="shifts" element={<ShiftsPage />} />
          <Route path="announce" element={<AnnouncementsPage />} />
          <Route path="aid" element={<AidPage />} />
          <Route path="steward" element={<StewardPage />} />
          <Route path="give" element={<GivePage />} />
          <Route path="about" element={<AboutPage />} />
          <Route path="my" element={<MyPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={qc}>
      <AppInner />
    </QueryClientProvider>
  )
}
