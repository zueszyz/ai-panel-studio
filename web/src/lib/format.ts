/** 时间与数字格式化：相对时间优先，绝对时间 24 小时制 */

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

function pad(value: number): string {
  return value.toString().padStart(2, '0')
}

export function relativeTime(iso: string, now: Date = new Date()): string {
  const target = new Date(iso)
  if (Number.isNaN(target.getTime())) {
    return ''
  }
  const diff = now.getTime() - target.getTime()
  if (diff < MINUTE) {
    return '刚刚'
  }
  if (diff < HOUR) {
    return `${Math.floor(diff / MINUTE)} 分钟前`
  }
  if (diff < DAY) {
    return `${Math.floor(diff / HOUR)} 小时前`
  }
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (target.toDateString() === yesterday.toDateString()) {
    return `昨天 ${pad(target.getHours())}:${pad(target.getMinutes())}`
  }
  if (diff < 7 * DAY) {
    return `${Math.floor(diff / DAY)} 天前`
  }
  return absoluteTime(iso)
}

export function absoluteTime(iso: string): string {
  const target = new Date(iso)
  if (Number.isNaN(target.getTime())) {
    return ''
  }
  return `${target.getFullYear()}-${pad(target.getMonth() + 1)}-${pad(target.getDate())} ${pad(
    target.getHours(),
  )}:${pad(target.getMinutes())}`
}

export function turnLabel(turnIndex: number): string {
  return `第 ${turnIndex} 回合`
}

export function trim(text: string, max: number): string {
  if (text.length <= max) {
    return text
  }
  return `${text.slice(0, max)}…`
}
