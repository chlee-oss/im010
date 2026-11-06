/**
 * Google Material Symbols (Outlined, weight 100) 아이콘 — https://fonts.google.com/icons
 * name 은 아이콘 이름(ligature) 그대로: arrow_forward, chevron_right, call ...
 * 글꼴은 styles/global.css 에서 weight 100 만 불러온다. 글자 크기를 따라가고(1.2em), 장식이라 화면 읽기에서는 빠진다.
 */
export default function Icon({ name, className }: { name: string; className?: string }) {
  return (
    <span className={className ? `msi ${className}` : 'msi'} aria-hidden="true">
      {name}
    </span>
  )
}
