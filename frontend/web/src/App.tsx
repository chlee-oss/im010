import { Route, Routes } from 'react-router-dom'
import Footer from './components/Footer'
import Header from './components/Header'
import { PartnerProvider } from './lib/partners'
import { ToastProvider } from './lib/toast'
import HomePage from './pages/HomePage'
import NotFoundPage from './pages/NotFoundPage'
import PlanDetailPage from './pages/PlanDetailPage'

export default function App() {
  return (
    <ToastProvider>
      <PartnerProvider>
        <Header />
        <main>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/plans/:id" element={<PlanDetailPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </main>
        <Footer />
      </PartnerProvider>
    </ToastProvider>
  )
}
