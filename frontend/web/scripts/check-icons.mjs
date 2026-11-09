#!/usr/bin/env node
// 아이콘 글꼴 목록 확인 (package.json "build" 맨 앞에서 실행)
//
// styles/global.css 는 Material Symbols 를 쓰는 아이콘만(icon_names=) 받는다. 코드에서 새 아이콘을 쓰고
// 목록에 넣지 않으면 화면에 아이콘 대신 이름 글자(예: "chevron_right")가 나오므로, 빠진 이름이 있으면 빌드를 멈춘다.
// 찾는 곳: <Icon name="..."> · <Icon name={...}> 안의 따옴표 문자열 · 이름이 *_ICON 인 객체의 값
// icon_names 는 Google Fonts 규칙상 알파벳 순이어야 한다.

import { readdir, readFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..', 'src')

async function files(dir) {
  const out = []
  for (const e of await readdir(dir, { withFileTypes: true })) {
    const p = join(dir, e.name)
    if (e.isDirectory()) out.push(...(await files(p)))
    else if (/\.(tsx?|jsx?)$/.test(e.name)) out.push(p)
  }
  return out
}

const used = new Map()   // 아이콘 → 처음 나온 파일
const note = (name, file) => used.has(name) || used.set(name, file)
for (const file of await files(SRC)) {
  const text = await readFile(file, 'utf8')
  for (const m of text.matchAll(/<Icon\b[^>]*?\bname=(?:"([a-z0-9_]+)"|\{([^}]*)\})/g)) {
    if (m[1]) note(m[1], file)
    else for (const s of m[2].matchAll(/['"]([a-z0-9_]+)['"]/g)) note(s[1], file)
  }
  for (const m of text.matchAll(/\b[A-Z_]*ICON\b[^=]*=\s*\{([^}]*)\}/g)) {
    for (const s of m[1].matchAll(/:\s*['"]([a-z0-9_]+)['"]/g)) note(s[1], file)
  }
}

const css = await readFile(join(SRC, 'styles', 'global.css'), 'utf8')
const listed = (css.match(/icon_names=([a-z0-9_,]+)/) ?? [])[1]?.split(',') ?? []
const problems = []
if (listed.length === 0) problems.push('styles/global.css 의 Material Symbols 주소에 icon_names= 가 없습니다')
const sorted = [...listed].sort()
if (listed.join() !== sorted.join()) problems.push(`icon_names 는 알파벳 순이어야 합니다: ${sorted.join(',')}`)
for (const [name, file] of used) {
  if (!listed.includes(name)) problems.push(`아이콘 "${name}" 이 icon_names 에 없습니다 (${file.slice(SRC.length + 1)})`)
}
const unused = listed.filter((n) => !used.has(n))

if (problems.length) {
  console.error('[check-icons] ' + problems.join('\n[check-icons] '))
  process.exit(1)
}
console.log(`[check-icons] 아이콘 ${used.size}개 모두 글꼴 목록에 있음${unused.length ? ` (목록에만 있는 것: ${unused.join(', ')})` : ''}`)
