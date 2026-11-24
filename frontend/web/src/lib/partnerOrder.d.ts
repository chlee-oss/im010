/** 회전 날짜 번호: 한국 시간 05:00 을 하루의 시작으로 센 1970-01-01 부터의 일 수 */
export function rotationDay(now?: Date): number
/** 기준 순서 목록을 그날 순서로 돌린다 (날짜 번호 mod 개수만큼 앞에서). 원본은 바꾸지 않는다 */
export function rotateByDay<T>(list: readonly T[], now?: Date): T[]
