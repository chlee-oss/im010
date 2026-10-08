/** 목록 로딩 · 오류 · 빈 상태 공통 표시. */
export default function StateMessage({ loading, error, empty }: { loading?: boolean; error?: Error | null; empty?: boolean }) {
  if (loading) return <p className="state-msg">불러오는 중…</p>
  if (error) return <p className="state-msg error">정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
  if (empty) return <p className="state-msg">표시할 요금제가 없어요.</p>
  return null
}
