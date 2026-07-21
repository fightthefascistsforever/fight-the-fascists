import { useEffect } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Layout from './components/Layout'
import BoardPage from './pages/BoardPage'
import PostPage from './pages/PostPage'
import ClaimPage from './pages/ClaimPage'
import MyPage from './pages/MyPage'
import { useAppStore } from './store'

const qc = new QueryClient()

function AppInner() {
  const loadFromStorage = useAppStore(s => s.loadFromStorage)
  useEffect(() => { loadFromStorage() }, [loadFromStorage])

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<BoardPage />} />
          <Route path="post" element={<PostPage />} />
          <Route path="claim/:needId" element={<ClaimPage />} />
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
