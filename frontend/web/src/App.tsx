import { Route, Routes } from 'react-router-dom'
import Footer from './components/Footer'
import Header from './components/Header'
import { PartnerProvider } from './lib/partners'
import { ToastProvider } from './lib/toast'
import FaqPage from './pages/FaqPage'
import HomePage from './pages/HomePage'
import NotFoundPage from './pages/NotFoundPage'
import PlanDetailPage from './pages/PlanDetailPage'
import TermsPage from './pages/TermsPage'

export default function App() {
  return (
    <ToastProvider>
      <PartnerProvider>
        <Header />
        <main>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/plans/:id" element={<PlanDetailPage />} />
            <Route path="/terms/:slug" element={<TermsPage />} />
            <Route path="/faq" element={<FaqPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </main>
        <Footer />
      </PartnerProvider>
    </ToastProvider>
  )
}
