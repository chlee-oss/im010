// 제휴사 회전 순서(src/lib/partnerOrder.js) 확인: npm run check:partner-order
// 날짜를 넣어 가며 하루에 한 칸씩 도는지, 한국 시간 05:00 에 바뀌는지, 제휴사마다 맨 앞에 서는 날이 고르게 오는지 본다.
import assert from 'node:assert/strict'
import { rotateByDay, rotationDay } from '../src/lib/partnerOrder.js'

const base = ['mv', 'torri', 'id', 'insmobile'] // 운영 sort_order 순
/** 한국 시간 문자열 → Date */
const kst = (s) => new Date(`${s}+09:00`)

// 1) 4일치: 하루에 한 칸씩
const days = ['2026-11-24', '2026-11-25', '2026-11-26', '2026-11-27']
const orders = days.map((d) => rotateByDay(base, kst(`${d}T12:00:00`)))
days.forEach((d, i) => console.log(`${d} (회전 ${rotationDay(kst(`${d}T12:00:00`))})  ${orders[i].join(' → ')}`))
for (let i = 1; i < orders.length; i++) {
  assert.deepEqual(orders[i], [...orders[i - 1].slice(1), orders[i - 1][0]], `${days[i]} 는 전날보다 한 칸 돈 순서여야 함`)
}

// 2) 하루의 시작은 한국 시간 05:00: 04:59 는 전날, 05:00 은 그날, 자정~05:00 은 전날 순서 유지
assert.deepEqual(rotateByDay(base, kst('2026-11-25T04:59:59')), orders[0], '05:00 전은 전날 순서')
assert.deepEqual(rotateByDay(base, kst('2026-11-25T00:30:00')), orders[0], '자정 이후 05:00 전까지는 전날 순서')
assert.deepEqual(rotateByDay(base, kst('2026-11-25T05:00:00')), orders[1], '05:00 부터 그날 순서')
assert.deepEqual(rotateByDay(base, kst('2026-11-25T23:59:59')), orders[1], '하루 동안 같은 순서')
// 서버 · 브라우저 시간대와 무관 (같은 순간이면 같은 결과)
assert.deepEqual(rotateByDay(base, new Date('2026-11-24T20:00:00Z')), orders[1], 'UTC 20:00 = 한국 05:00')

// 3) 제휴사 수만큼의 날 동안 모두 한 번씩 맨 앞
const leads = Array.from({ length: base.length }, (_, i) => rotateByDay(base, new Date(kst('2026-11-24T12:00:00').getTime() + i * 86400000))[0])
assert.deepEqual([...leads].sort(), [...base].sort(), '제휴사마다 맨 앞에 한 번씩')

// 4) 원본 보존 · 0~1개
const copy = [...base]
rotateByDay(base, kst('2026-11-26T12:00:00'))
assert.deepEqual(base, copy, '원본 목록은 바뀌지 않음')
assert.deepEqual(rotateByDay([]), [])
assert.deepEqual(rotateByDay(['only']), ['only'])

console.log('[check-partner-order] 모두 통과')
