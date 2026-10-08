import QRCode from 'qrcode'
import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, get, post } from '../lib/api'
import type { Stage } from '../lib/types'

/**
 * CM-01 로그인: 아이디 · 비밀번호 → (처음이면 OTP 앱 등록) OTP 번호 → (최초 로그인이면) 비밀번호 변경.
 */
export default function Login({ initialStage, onDone }: { initialStage: Stage; onDone: () => void }) {
  const [stage, setStage] = useState<Stage>(initialStage)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const step = async (task: () => Promise<{ stage: Stage }>) => {
    setBusy(true)
    setError(null)
    try {
      const r = await task()
      if (r.stage === 'DONE') onDone()
      else setStage(r.stage)
    } catch (e) {
      if (e instanceof ApiError && e.code === 'LOGIN_EXPIRED') setStage(null)
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    document.title = '로그인 · im010 admin'
  }, [])

  return (
    <div className="login">
      <div className="login-card">
        <div className="login-logo">
          im<span>010</span> <small>admin</small>
        </div>
        {(stage === null || stage === 'DONE') && <PasswordStep busy={busy} onSubmit={(id, pw) => step(() => post('/auth/login', { loginId: id, password: pw }))} />}
        {stage === 'OTP_SETUP' && <OtpSetup busy={busy} onSubmit={(code) => step(() => post('/auth/otp', { code }))} />}
        {stage === 'OTP' && <OtpStep busy={busy} onSubmit={(code) => step(() => post('/auth/otp', { code }))} />}
        {stage === 'PASSWORD_CHANGE' && (
          <NewPassword busy={busy} onSubmit={(pw) => step(() => post('/auth/password', { newPassword: pw }))} />
        )}
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <p className="login-foot">비밀번호 · OTP를 5번 연속 틀리면 30분 동안 잠깁니다. 30분 동안 동작이 없으면 로그아웃됩니다.</p>
      </div>
    </div>
  )
}

function PasswordStep({ busy, onSubmit }: { busy: boolean; onSubmit: (id: string, pw: string) => void }) {
  const [id, setId] = useState('')
  const [pw, setPw] = useState('')
  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit(id, pw)
  }
  return (
    <form onSubmit={submit} className="login-form">
      <label>
        아이디
        <input value={id} onChange={(e) => setId(e.target.value)} autoComplete="username" autoFocus required maxLength={50} />
      </label>
      <label>
        비밀번호
        <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="current-password" required maxLength={64} />
      </label>
      <button className="primary wide" disabled={busy}>
        다음
      </button>
    </form>
  )
}

function OtpInput({ busy, onSubmit, label }: { busy: boolean; onSubmit: (code: string) => void; label: string }) {
  const [code, setCode] = useState('')
  return (
    <form
      className="login-form"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit(code)
        setCode('')
      }}
    >
      <label>
        {label}
        <input
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          required
          pattern="\d{6}"
          className="otp"
        />
      </label>
      <button className="primary wide" disabled={busy || code.length !== 6}>
        확인
      </button>
    </form>
  )
}

function OtpStep({ busy, onSubmit }: { busy: boolean; onSubmit: (code: string) => void }) {
  return (
    <>
      <p className="login-guide">OTP 앱에 표시된 6자리 번호를 입력해 주세요.</p>
      <OtpInput busy={busy} onSubmit={onSubmit} label="인증 번호" />
    </>
  )
}

function OtpSetup({ busy, onSubmit }: { busy: boolean; onSubmit: (code: string) => void }) {
  const [setup, setSetup] = useState<{ secret: string; otpauthUri: string } | null>(null)
  const [qr, setQr] = useState<string | null>(null)
  useEffect(() => {
    get<{ secret: string; otpauthUri: string }>('/auth/otp-setup').then(async (s) => {
      setSetup(s)
      setQr(await QRCode.toDataURL(s.otpauthUri, { margin: 1, width: 200 }))
    })
  }, [])
  return (
    <>
      <p className="login-guide">
        처음 로그인하셨습니다. Google Authenticator 같은 OTP 앱으로 QR 코드를 찍어 등록한 뒤, 앱에 표시된 번호를 입력해 주세요.
      </p>
      {qr && <img className="qr" src={qr} alt="OTP 등록 QR 코드" width={200} height={200} />}
      {setup && (
        <p className="secret">
          직접 입력: <code>{setup.secret.replace(/(.{4})/g, '$1 ').trim()}</code>
        </p>
      )}
      <OtpInput busy={busy} onSubmit={onSubmit} label="인증 번호" />
    </>
  )
}

export function NewPassword({ busy, onSubmit, withCurrent }: { busy: boolean; onSubmit: (pw: string, current?: string) => void; withCurrent?: boolean }) {
  const [current, setCurrent] = useState('')
  const [pw, setPw] = useState('')
  const [pw2, setPw2] = useState('')
  const mismatch = pw2 !== '' && pw !== pw2
  return (
    <form
      className="login-form"
      onSubmit={(e) => {
        e.preventDefault()
        if (!mismatch) onSubmit(pw, current)
      }}
    >
      {!withCurrent && <p className="login-guide">처음 로그인하셨습니다. 새 비밀번호를 정해 주세요.</p>}
      {withCurrent && (
        <label>
          현재 비밀번호
          <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
        </label>
      )}
      <label>
        새 비밀번호
        <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" required maxLength={64} />
      </label>
      <label>
        새 비밀번호 확인
        <input type="password" value={pw2} onChange={(e) => setPw2(e.target.value)} autoComplete="new-password" required maxLength={64} />
      </label>
      <p className="hint">10자 이상, 영문 대문자 · 소문자 · 숫자 · 특수문자 중 3종류 이상, 아이디 포함 불가</p>
      {mismatch && <p className="form-error">두 비밀번호가 다릅니다</p>}
      <button className="primary wide" disabled={busy || mismatch}>
        비밀번호 변경
      </button>
    </form>
  )
}
