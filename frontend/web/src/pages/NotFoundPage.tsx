import { Link } from 'react-router-dom'
import { usePageTitle } from '../lib/usePageTitle'

export default function NotFoundPage() {
  usePageTitle('페이지를 찾을 수 없음')
  return (
    <section className="not-found">
      <div className="wrap">
        <div className="nf-code num">404</div>
        <h1>찾으시는 페이지가 없어요</h1>
        <p>주소가 바뀌었거나 삭제된 페이지일 수 있어요.</p>
        <div className="nf-actions">
          <Link className="btn-primary" to="/">
            메인으로
          </Link>
          <a className="card-cta" href="/#calc">
            요금제 비교하기
          </a>
        </div>
      </div>
    </section>
  )
}
