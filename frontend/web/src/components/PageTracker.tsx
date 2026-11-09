import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { startTracking, trackPageView } from '../lib/track'

let started = false

/** 이용 통계: 화면 이동마다 page_view (수집이 꺼져 있으면 lib/track 이 보내지 않는다) */
export default function PageTracker() {
  const { pathname, search } = useLocation()

  useEffect(() => {
    if (!started) {
      started = true
      startTracking()
    }
  }, [])

  useEffect(() => {
    trackPageView(pathname, search)
  }, [pathname, search])

  return null
}
