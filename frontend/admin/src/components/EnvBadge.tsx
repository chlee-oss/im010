/** 로컬 개발 서버(vite dev)에서만 화면 왼쪽 위에 LOCAL 표시 — 운영 빌드에는 들어가지 않는다. */
export default function EnvBadge() {
  if (!import.meta.env.DEV) return null
  return (
    <div
      aria-hidden="true"
      style={{
        position: 'fixed',
        top: 4,
        left: 4,
        zIndex: 10000,
        pointerEvents: 'none',
        background: '#16A34A',
        color: '#fff',
        font: '800 10px/1 Pretendard, -apple-system, sans-serif',
        letterSpacing: 1,
        padding: '4px 7px',
        borderRadius: 6,
        boxShadow: '0 2px 6px rgba(0,0,0,0.18)',
      }}
    >
      LOCAL
    </div>
  )
}
