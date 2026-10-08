import { useEffect, useState } from 'react'

export interface ApiState<T> {
  data: T | null
  loading: boolean
  error: Error | null
}

/**
 * deps 가 바뀔 때마다 load 를 다시 호출한다. 이전 요청은 취소(AbortController).
 * load 는 deps 로만 결정되는 함수여야 한다.
 */
export function useApi<T>(load: (signal: AbortSignal) => Promise<T | null>, deps: unknown[]): ApiState<T> {
  const [state, setState] = useState<ApiState<T>>({ data: null, loading: true, error: null })

  useEffect(() => {
    const ctrl = new AbortController()
    setState((s) => ({ ...s, loading: true, error: null }))
    load(ctrl.signal)
      .then((data) => setState({ data, loading: false, error: null }))
      .catch((error: unknown) => {
        if (ctrl.signal.aborted) return
        setState({ data: null, loading: false, error: error instanceof Error ? error : new Error(String(error)) })
      })
    return () => ctrl.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return state
}
