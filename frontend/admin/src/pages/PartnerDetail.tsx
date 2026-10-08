import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Badge, Loading, Modal, PageHead } from '../components/ui'
import { del, get, post, put } from '../lib/api'
import { NETWORK, URL_TYPE, won } from '../lib/format'
import { useMe } from '../lib/me'
import { useRun } from '../lib/notice'
import type { CollectUrl, Partner, PartnerTab, UrlType } from '../lib/types'
import { useLoad } from '../lib/useLoad'

interface Detail {
  partner: Partner
  urls: CollectUrl[]
  tabs: PartnerTab[]
}

interface TestResult {
  count: number
  missingRequired: number
  preview: { partnerPlanCode: string | null; name: string | null; network: string | null; dataText: string | null; qosText: string | null; price: number | null; discountMonths: number | null; priceAfterDiscount: number | null }[]
  tabs: { url: string; label: string }[]
}

type UrlForm = { id?: number; urlType: UrlType; url: string; label: string; sortOrder: number }

/** PA-01 제휴사 상세: 기본 정보 · 수집 URL(유형별 탭마다) · 사이트 탭 확인 · [테스트] */
export default function PartnerDetail() {
  const { code } = useParams()
  const { can } = useMe()
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<Detail>(`/partners/${code}`), [code])
  const [info, setInfo] = useState<Partner | null>(null)
  const [urlForm, setUrlForm] = useState<UrlForm | null>(null)
  const [test, setTest] = useState<{ url: string; result: TestResult | null } | null>(null)
  const canEdit = can('PA-01', 'EDIT')

  if (!data) return <Loading error={error} />
  const { partner, urls, tabs } = data
  const registered = new Set(urls.map((u) => u.url))

  const runTest = (url: string, label?: string | null) => {
    setTest({ url, result: null })
    void run(() => post<TestResult>(`/partners/${code}/urls/test`, { url, label })).then((r) => (r ? setTest({ url, result: r }) : setTest(null)))
  }

  const saveUrl = () => {
    if (!urlForm) return
    const body = { urlType: urlForm.urlType, url: urlForm.url, label: urlForm.label, sortOrder: urlForm.sortOrder }
    void run(
      () => (urlForm.id ? put<Detail>(`/partners/${code}/urls/${urlForm.id}`, body) : post<Detail>(`/partners/${code}/urls`, body)),
      () => '수집 URL을 저장했습니다. 다음 수집부터 적용됩니다',
    ).then((d) => {
      if (d) {
        setData(d)
        setUrlForm(null)
      }
    })
  }

  const removeUrl = (u: CollectUrl) => {
    const last = urls.filter((x) => x.urlType === u.urlType).length === 1
    let hidePlans = true
    if (last && u.urlType !== 'MONTHLY') {
      if (!window.confirm(`${URL_TYPE[u.urlType]} URL이 모두 없어져 이 유형은 이제 수집하지 않습니다. 계속할까요?`)) return
      hidePlans = window.confirm('게시 중인 ' + URL_TYPE[u.urlType] + ' 요금제를 비노출로 바꿀까요?\n[확인] 비노출 (기본) · [취소] 유지\n판매 종료로 처리하지는 않습니다.')
    } else if (!window.confirm('이 수집 URL을 삭제합니다. 이 탭에만 있던 요금제는 다음 수집 때 판매 종료로 처리됩니다.')) {
      return
    }
    void run(() => del<Detail>(`/partners/${code}/urls/${u.id}?hidePlans=${hidePlans}`), () => '삭제했습니다').then((d) => d && setData(d))
  }

  return (
    <>
      <PageHead title={`제휴사 상세 — ${partner.name}`}>
        <Link to="/partners" className="button">
          ← 목록
        </Link>
      </PageHead>

      <section className="panel">
        <div className="panel-head">
          <h2>기본 정보</h2>
          {canEdit && !info && <button onClick={() => setInfo(partner)}>수정</button>}
        </div>
        {info ? (
          <>
            <div className="form-grid">
              <label>
                브랜드명 <input value={info.name} onChange={(e) => setInfo({ ...info, name: e.target.value })} maxLength={50} />
              </label>
              <label>
                홈페이지 <input value={info.homepageUrl ?? ''} onChange={(e) => setInfo({ ...info, homepageUrl: e.target.value })} maxLength={500} />
              </label>
              <label>
                칩 배경 <input type="color" value={info.chipBg} onChange={(e) => setInfo({ ...info, chipBg: e.target.value })} />
              </label>
              <label>
                칩 글자 <input type="color" value={info.chipFg} onChange={(e) => setInfo({ ...info, chipFg: e.target.value })} />
              </label>
              <label>
                순서 <input type="number" value={info.sortOrder} onChange={(e) => setInfo({ ...info, sortOrder: Number(e.target.value) })} />
              </label>
              <label className="inline">
                <input type="checkbox" checked={info.exposed} onChange={(e) => setInfo({ ...info, exposed: e.target.checked })} /> 프런트 노출
              </label>
            </div>
            <div className="actions">
              <button onClick={() => setInfo(null)}>취소</button>
              <button
                className="primary"
                disabled={busy}
                onClick={() =>
                  run(() => put<Detail>(`/partners/${code}`, info), () => '저장했습니다').then((d) => {
                    if (d) {
                      setData(d)
                      setInfo(null)
                    }
                  })
                }
              >
                저장
              </button>
            </div>
          </>
        ) : (
          <div className="meta-row">
            <span className="chip" style={{ background: partner.chipBg, color: partner.chipFg }}>
              {partner.name}
            </span>
            <span>코드 {partner.code}</span>
            {partner.homepageUrl && (
              <a className="link" href={partner.homepageUrl} target="_blank" rel="noopener noreferrer">
                {partner.homepageUrl} ↗
              </a>
            )}
            {partner.exposed ? <Badge tone="green">노출</Badge> : <Badge>비노출 — 프런트에서 이 제휴사 요금제 · 칩을 숨김</Badge>}
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>요금제 수집 URL</h2>
          {canEdit && (
            <div className="actions left">
              {(['POSTPAID', 'PREPAID', 'MONTHLY'] as UrlType[]).map((t) => (
                <button key={t} onClick={() => setUrlForm({ urlType: t, url: 'https://', label: '', sortOrder: urls.filter((u) => u.urlType === t).length + 1 })}>
                  + {URL_TYPE[t]} URL
                </button>
              ))}
            </div>
          )}
        </div>
        <table className="grid">
          <thead>
            <tr>
              <th>유형</th>
              <th>순서</th>
              <th>탭 이름</th>
              <th>URL</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {urls.length === 0 && (
              <tr>
                <td colSpan={5} className="muted">
                  등록된 URL이 없습니다. URL이 없는 유형은 수집하지 않습니다.
                </td>
              </tr>
            )}
            {urls.map((u) => (
              <tr key={u.id}>
                <td>{URL_TYPE[u.urlType]}</td>
                <td>{u.sortOrder}</td>
                <td>{u.label ?? '–'}</td>
                <td className="url">{u.url}</td>
                <td className="row-actions">
                  <button onClick={() => runTest(u.url, u.label)} disabled={busy || !canEdit}>
                    테스트
                  </button>
                  {canEdit && (
                    <>
                      <button onClick={() => setUrlForm({ id: u.id, urlType: u.urlType, url: u.url, label: u.label ?? '', sortOrder: u.sortOrder })}>수정</button>
                      <button onClick={() => removeUrl(u)}>삭제</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="hint">
          제휴사 요금제 페이지는 탭마다 URL이 다릅니다 (기본 페이지는 첫 탭만 보임). 같은 요금제가 여러 탭에 있으면 순서가 앞인 탭 값을 씁니다. 후불과 선불은 다른 URL이어야 합니다. 수집 일정은 스케줄관리에서 정합니다.
        </p>
      </section>

      <section className="panel">
        <h2>사이트 탭 확인</h2>
        <p className="hint">수집할 때 제휴사 사이트 메뉴에서 등록되지 않은 탭을 찾아 알립니다. 수집할 탭은 후불 · 선불로 등록하고, 대상이 제한된 탭(복지 · 임직원 · 태블릿 등)은 [수집 안 함]으로 둡니다.</p>
        <table className="grid">
          <thead>
            <tr>
              <th>탭 이름</th>
              <th>URL</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {tabs.length === 0 && (
              <tr>
                <td colSpan={4} className="muted">
                  확인할 탭이 없습니다.
                </td>
              </tr>
            )}
            {tabs
              .filter((t) => !registered.has(t.url))
              .map((t) => (
                <tr key={t.id}>
                  <td>{t.label ?? '–'}</td>
                  <td className="url">{t.url}</td>
                  <td>{t.status === 'NEW' ? <Badge tone="amber">⚠ 미확인</Badge> : <Badge>수집 안 함</Badge>}</td>
                  <td className="row-actions">
                    <button onClick={() => runTest(t.url, t.label)} disabled={busy || !canEdit}>
                      테스트
                    </button>
                    {canEdit && t.status === 'NEW' && (
                      <>
                        <button onClick={() => setUrlForm({ urlType: 'POSTPAID', url: t.url, label: t.label ?? '', sortOrder: urls.filter((u) => u.urlType === 'POSTPAID').length + 1 })}>후불 등록</button>
                        <button onClick={() => setUrlForm({ urlType: 'PREPAID', url: t.url, label: t.label ?? '', sortOrder: urls.filter((u) => u.urlType === 'PREPAID').length + 1 })}>선불 등록</button>
                        <button onClick={() => run(() => put<Detail>(`/partners/${code}/tabs/${t.id}`, { status: 'IGNORED' }), () => '수집 안 함으로 표시했습니다').then((d) => d && setData(d))}>수집 안 함</button>
                      </>
                    )}
                    {canEdit && t.status === 'IGNORED' && (
                      <button onClick={() => run(() => put<Detail>(`/partners/${code}/tabs/${t.id}`, { status: 'NEW' }), () => '미확인으로 되돌렸습니다').then((d) => d && setData(d))}>되돌리기</button>
                    )}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </section>

      {urlForm && (
        <Modal title={urlForm.id ? '수집 URL 수정' : '수집 URL 등록'} onClose={() => setUrlForm(null)}>
          <div className="form-grid">
            <label>
              유형
              <select value={urlForm.urlType} onChange={(e) => setUrlForm({ ...urlForm, urlType: e.target.value as UrlType })}>
                <option value="POSTPAID">후불 요금제</option>
                <option value="PREPAID">선불 요금제</option>
                <option value="MONTHLY">이달의 요금제</option>
              </select>
            </label>
            <label>
              탭 이름 <input value={urlForm.label} onChange={(e) => setUrlForm({ ...urlForm, label: e.target.value })} maxLength={50} />
            </label>
            <label>
              순서 <input type="number" min={0} value={urlForm.sortOrder} onChange={(e) => setUrlForm({ ...urlForm, sortOrder: Number(e.target.value) })} />
            </label>
            <label className="span2">
              URL <input value={urlForm.url} onChange={(e) => setUrlForm({ ...urlForm, url: e.target.value })} maxLength={500} />
            </label>
          </div>
          <p className="hint">저장 전에 [테스트]로 요금제가 제대로 읽히는지 확인해 주세요. 후불 · 선불 구분은 저장 후 바꾸면 요금제 유형도 다음 수집부터 바뀝니다.</p>
          <div className="actions">
            <button onClick={() => runTest(urlForm.url, urlForm.label)} disabled={busy}>
              테스트
            </button>
            <button onClick={() => setUrlForm(null)}>취소</button>
            <button className="primary" disabled={busy} onClick={saveUrl}>
              저장
            </button>
          </div>
        </Modal>
      )}

      {test && (
        <Modal title="수집 테스트" onClose={() => setTest(null)}>
          <p className="url">{test.url}</p>
          {!test.result ? (
            <p>페이지를 받아 읽는 중…</p>
          ) : (
            <>
              <p>
                요금제 <b>{test.result.count}건</b> 추출
                {test.result.missingRequired > 0 && <Badge tone="amber">⚠ 요금제명 · 요금 없는 건 {test.result.missingRequired}</Badge>}
                {test.result.count === 0 && <Badge tone="red">⚠ 0건 — 탭 URL이 맞는지 확인해 주세요</Badge>}
              </p>
              <table className="grid">
                <thead>
                  <tr>
                    <th>코드</th>
                    <th>요금제</th>
                    <th>망</th>
                    <th>데이터</th>
                    <th className="num">요금</th>
                    <th>할인</th>
                  </tr>
                </thead>
                <tbody>
                  {test.result.preview.map((p, i) => (
                    <tr key={i}>
                      <td>{p.partnerPlanCode ?? '–'}</td>
                      <td>{p.name ?? '–'}</td>
                      <td>{p.network ? NETWORK[p.network] : '–'}</td>
                      <td>
                        {p.dataText ?? '–'} {p.qosText && `+ ${p.qosText}`}
                      </td>
                      <td className="num">{won(p.price)}</td>
                      <td>{p.discountMonths ? `${p.discountMonths}개월 후 ${won(p.priceAfterDiscount)}` : '–'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="hint">앞 5건만 보여 줍니다. 결과는 저장하지 않습니다.</p>
            </>
          )}
          <div className="actions">
            <button onClick={() => setTest(null)}>닫기</button>
          </div>
        </Modal>
      )}
    </>
  )
}
