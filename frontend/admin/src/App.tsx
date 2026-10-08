import { useCallback, useEffect, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import { Loading } from './components/ui'
import { get } from './lib/api'
import { MeProvider } from './lib/me'
import type { Stage } from './lib/types'
import Approvals from './pages/Approvals'
import BatchItems from './pages/BatchItems'
import InternetProducts from './pages/InternetProducts'
import Login from './pages/Login'
import NotReady from './pages/NotReady'
import PartnerDetail from './pages/PartnerDetail'
import Partners from './pages/Partners'
import PlanDetail from './pages/PlanDetail'
import Plans from './pages/Plans'
import Receipts from './pages/Receipts'
import Schedules from './pages/Schedules'
import Admins from './pages/settings/Admins'
import Audit from './pages/settings/Audit'
import Faq from './pages/settings/Faq'
import Footer from './pages/settings/Footer'
import Groups from './pages/settings/Groups'
import Programs from './pages/settings/Programs'
import Terms from './pages/settings/Terms'

export default function App() {
  const [stage, setStage] = useState<Stage | 'LOADING'>('LOADING')

  const check = useCallback(async () => {
    try {
      setStage((await get<{ stage: Stage }>('/auth/state')).stage)
    } catch {
      setStage(null)
    }
  }, [])

  useEffect(() => {
    void check()
    const onUnauthorized = () => setStage(null)
    window.addEventListener('im010:unauthorized', onUnauthorized)
    return () => window.removeEventListener('im010:unauthorized', onUnauthorized)
  }, [check])

  if (stage === 'LOADING') return <Loading />
  if (stage !== 'DONE') return <Login initialStage={stage} onDone={() => setStage('DONE')} />

  return (
    <MeProvider>
      <Layout onLogout={() => setStage(null)}>
        <Routes>
          {/* 로그인 후 첫 화면 = BA-02 요금제배치관리 (결정 #23) */}
          <Route path="/" element={<Navigate to="/batch-items" replace />} />
          <Route path="/batch-items" element={<BatchItems />} />
          <Route path="/approvals" element={<Approvals />} />
          <Route path="/plans" element={<Plans />} />
          <Route path="/plans/:id" element={<PlanDetail />} />
          <Route path="/partners" element={<Partners />} />
          <Route path="/partners/:code" element={<PartnerDetail />} />
          <Route path="/schedules" element={<Schedules />} />
          <Route path="/internet" element={<InternetProducts />} />
          <Route path="/receipts/plans" element={<Receipts key="plans" kind="plans" />} />
          <Route path="/receipts/internet" element={<Receipts key="internet" kind="internet" />} />
          <Route path="/settings/programs" element={<Programs />} />
          <Route path="/settings/admins" element={<Admins />} />
          <Route path="/settings/groups" element={<Groups />} />
          <Route path="/settings/terms" element={<Terms />} />
          <Route path="/settings/audit" element={<Audit />} />
          <Route path="/settings/footer" element={<Footer />} />
          <Route path="/settings/inquiries" element={<Faq />} />
          <Route path="*" element={<NotReady />} />
        </Routes>
      </Layout>
    </MeProvider>
  )
}
