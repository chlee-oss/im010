import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { usePartners } from '../lib/partners'
import { useToast } from '../lib/toast'
import { useApi } from '../lib/useApi'

// 사업자 정보 · 고객센터 · 고지 문구는 백오피스 ST-06 Footer관리, 약관은 ST-04 약관관리에서 관리한다.
export default function Footer() {
  const toast = useToast()
  const partners = [...usePartners().values()].filter((p) => p.homepageUrl)
  const { data: f } = useApi((signal) => api.footer(signal), [])
  const pending = (what: string) => (e: React.MouseEvent) => {
    e.preventDefault()
    toast(`${what} 페이지는 준비 중이에요`)
  }

  return (
    <footer>
      <div className="wrap">
        <div className="footer-grid">
          <div>
            <div className="footer-logo">im010</div>
            <div style={{ fontSize: 13, maxWidth: 280, lineHeight: 1.6 }}>
              알뜰폰 요금제 비교부터 인터넷 상품 신청 연결까지, im010에서 한 번에 비교하세요.
            </div>
          </div>
          <div className="footer-col">
            <h5>서비스</h5>
            <a href="/#month">이달의 요금제</a>
            <a href="/#partners">요금제</a>
            <a href="/#prepaid">선불요금제</a>
            <a href="/#internet-compare">인터넷</a>
            <a href="#" onClick={pending('이벤트')}>
              이벤트
            </a>
            <a href="/#analysis">요금제분석</a>
          </div>
          <div className="footer-col">
            <h5>고객지원</h5>
            <Link to="/faq">자주 묻는 질문</Link>
            {f && <span>고객센터 {f.csPhone}</span>}
            {f?.email && <span>{f.email}</span>}
          </div>
          <div className="footer-col">
            <h5>제휴사</h5>
            {partners.map((p) => (
              <a key={p.code} href={p.homepageUrl!} target="_blank" rel="noopener noreferrer">
                {p.name}
              </a>
            ))}
          </div>
        </div>
        <div className="footer-bottom">
          <div className="footer-links">
            <Link to="/terms/service">이용약관</Link>
            <Link to="/terms/privacy" className="strong">
              개인정보처리방침
            </Link>
          </div>
          {f && (
            <>
              상호 : {f.companyName} &nbsp;|&nbsp; 대표 : {f.ceo} &nbsp;|&nbsp; 사업자등록번호 : {f.businessNo}
              {f.mailOrderNo && <> &nbsp;|&nbsp; 통신판매업신고 : {f.mailOrderNo}</>}
              <br />
              주소 : {f.address} &nbsp;|&nbsp; 고객센터 : {f.csPhone}
              {f.csHours && ` (${f.csHours})`}
              {f.email && <> &nbsp;|&nbsp; 이메일 : {f.email}</>}
              <br />
              {f.notice} © im010. All rights reserved.
            </>
          )}
        </div>
      </div>
    </footer>
  )
}
