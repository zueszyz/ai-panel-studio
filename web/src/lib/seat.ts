import type { DiscussionStatus, SeatColorToken } from './types'

/** 席位色只在这里映射到设计令牌，组件不写具体色值。 */
const seatColorVars: Record<SeatColorToken, string> = {
  slate: 'var(--seat-slate)',
  amber: 'var(--seat-amber)',
  teal: 'var(--seat-teal)',
  indigo: 'var(--seat-indigo)',
  violet: 'var(--seat-violet)',
  rose: 'var(--seat-rose)',
  emerald: 'var(--seat-emerald)',
}

export function seatColor(token: string | undefined): string {
  return seatColorVars[(token ?? 'slate') as SeatColorToken] ?? seatColorVars.slate
}

/** 状态点颜色：进行中呼吸、待开场偏亮、已结束变弱。 */
export function statusDotColor(status: DiscussionStatus): string {
  switch (status) {
    case 'LIVE':
      return 'var(--seat-emerald)'
    case 'PANEL_READY':
      return 'var(--seat-indigo)'
    case 'SUMMARIZING':
      return 'var(--seat-amber)'
    case 'INTERRUPTED':
      return 'var(--danger)'
    case 'FAILED':
      return 'var(--danger)'
    default:
      return 'var(--text-muted)'
  }
}

export function initialOf(name: string): string {
  return name ? name.trim().charAt(0) : '·'
}
