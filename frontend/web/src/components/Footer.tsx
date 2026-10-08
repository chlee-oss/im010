import { usePartners } from '../lib/partners'
import { useToast } from '../lib/toast'

// 사업자 정보는 백오피스 ST-06 Footer관리에서 관리할 예정 (지금은 자리표시 값)
export default function Footer() {
  const toast = useToast()
  const partners = [...usePartners().values()].filter((p) => p.homepageUrl)
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
            <a href="#" onClick={pending('자주 묻는 질문')}>
              자주 묻는 질문
            </a>
            <span>고객센터 1600-0000</span>
            <span>help@im010.co.kr</span>
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
            <a href="#" onClick={pending('이용약관')}>
              이용약관
            </a>
            <a href="#" className="strong" onClick={pending('개인정보처리방침')}>
              개인정보처리방침
            </a>
          </div>
          상호 : (주)아임공일공 &nbsp;|&nbsp; 대표 : 홍길동 &nbsp;|&nbsp; 사업자등록번호 : 000-00-00000 &nbsp;|&nbsp; 통신판매업신고 : 제
          0000-서울-00000호
          <br />
          주소 : 서울특별시 OO구 OO로 00 &nbsp;|&nbsp; 고객센터 : 1600-0000 (평일 10:00–18:00) &nbsp;|&nbsp; 이메일 : help@im010.co.kr
          <br />
          요금 · 혜택 정보는 각 제휴사 공시 내용이며, 실제 개통 조건은 제휴사 정책에 따라 달라질 수 있습니다. 본 사이트는 알뜰폰 요금제 정보를
          제공하는 통합 비교 플랫폼이며, 요금제 개통과 인터넷 신청은 각 제휴사 · 제휴업체에서 진행됩니다. © im010. All rights reserved.
        </div>
      </div>
    </footer>
  )
}
