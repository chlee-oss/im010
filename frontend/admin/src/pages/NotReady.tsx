import { useLocation } from 'react-router-dom'
import { Empty, PageHead } from '../components/ui'
import { useMe } from '../lib/me'

/** 메뉴는 있지만 아직 만들지 않은 화면 (3-2단계: 환경설정 · 접수관리 · 인터넷관리) */
export default function NotReady() {
  const { pathname } = useLocation()
  const { me } = useMe()
  const item = me?.menus.flatMap((g) => g.items).find((i) => pathname.startsWith(i.path))
  return (
    <>
      <PageHead title={item ? item.name : '화면 없음'} />
      <Empty>{item ? `${item.id} ${item.name} 화면은 다음 단계(3-2)에서 만듭니다.` : '없는 화면입니다.'}</Empty>
    </>
  )
}
