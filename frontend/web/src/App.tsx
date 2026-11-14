import { Route, Routes } from 'react-router-dom'
import Footer from './components/Footer'
import Header from './components/Header'
import PageTracker from './components/PageTracker'
import ScrollToTop from './components/ScrollToTop'
import { PartnerProvider } from './lib/partners'
import { ToastProvider } from './lib/toast'
import AiListPage from './pages/AiListPage'
import AiPostPage from './pages/AiPostPage'
import FaqPage from './pages/FaqPage'
import HomePage from './pages/HomePage'
import NotFoundPage from './pages/NotFoundPage'
import OutboundPage from './pages/OutboundPage'
import PlanDetailPage from './pages/PlanDetailPage'
import ServiceLandingPage from './pages/ServiceLandingPage'
import TermsPage from './pages/TermsPage'

export default function App() {
  return (
    <ToastProvider>
      <PartnerProvider>
        <ScrollToTop />
        <PageTracker />
        <Header />
        <main>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/weekly" element={<ServiceLandingPage key="weekly" service="weekly" />} />
            <Route path="/find" element={<ServiceLandingPage key="find" service="find" />} />
            <Route path="/postpaid" element={<ServiceLandingPage key="postpaid" service="postpaid" />} />
            <Route path="/prepaid" element={<ServiceLandingPage key="prepaid" service="prepaid" />} />
            <Route path="/internet" element={<ServiceLandingPage key="internet" service="internet" />} />
            <Route path="/plans/:id" element={<PlanDetailPage />} />
            <Route path="/terms/:slug" element={<TermsPage />} />
            <Route path="/faq" element={<FaqPage />} />
            <Route path="/ai" element={<AiListPage />} />
            <Route path="/ai/posts/:id/:slug?" element={<AiPostPage />} />
            <Route path="/ai/:category" element={<AiListPage />} />
            <Route path="/out" element={<OutboundPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </main>
        <Footer />
      </PartnerProvider>
    </ToastProvider>
  )
}
