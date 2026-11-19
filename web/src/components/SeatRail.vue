<script setup lang="ts">
import { computed } from 'vue'
import SeatAvatar from './SeatAvatar.vue'
import { copy } from '@/lib/copy'
import { seatColor } from '@/lib/seat'
import type { Seat, SeatLiveStatus } from '@/lib/types'

const props = defineProps<{
  seats: Seat[]
  states: Record<string, SeatLiveStatus>
  speakers: string[]
  mySeatId: string | null
  /** 窄屏时横排在顶部，避免占掉讨论流的高度（rules/20 §6）。 */
  horizontal?: boolean
}>()

const statusLabel: Record<SeatLiveStatus, string> = {
  idle: copy.live.seatIdle,
  thinking: copy.live.seatThinking,
  generating: copy.live.seatGenerating,
  done: copy.live.seatDone,
  failed: copy.live.seatFailed,
}

function stateOf(seatId: string): SeatLiveStatus {
  return props.states[seatId] ?? 'idle'
}

const sorted = computed(() => [...props.seats].sort((left, right) => left.orderIndex - right.orderIndex))
</script>

<template>
  <aside class="rail" :class="{ 'rail--horizontal': horizontal }" :aria-label="copy.live.seats">
    <h2 class="rail__title">
      {{ copy.live.seats }}
      <span class="badge badge--count">{{ seats.length }}</span>
    </h2>
    <ul class="rail__list">
      <li
        v-for="seat in sorted"
        :key="seat.id"
        class="rail__item"
        :class="{ 'rail__item--speaking': speakers.includes(seat.id) }"
        :style="{ '--seat-color': seatColor(seat.colorToken) }"
      >
        <SeatAvatar :name="seat.name" :color-token="seat.colorToken" :you="seat.id === mySeatId" size="sm" />
        <div class="rail__ident">
          <span class="rail__name">{{ seat.id === mySeatId ? copy.panel.you : seat.name }}</span>
          <span class="rail__title-text">{{ seat.title }}</span>
        </div>
        <span
          class="rail__state"
          :class="`rail__state--${stateOf(seat.id)}`"
          :title="statusLabel[stateOf(seat.id)]"
        >
          <span class="rail__dot" aria-hidden="true"></span>
          {{ statusLabel[stateOf(seat.id)] }}
        </span>
      </li>
    </ul>
    <p v-if="speakers.length > 0" class="rail__hint">
      {{ copy.live.speakersHint(speakers.length) }}
    </p>
  </aside>
</template>

<style scoped>
.rail {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  overflow-y: auto;
}

.rail__title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-xs);
  color: var(--text-muted);
  letter-spacing: 0.08em;
}

.rail__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.rail__item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2);
  border-radius: var(--radius-md);
  border-left: var(--border-accent) solid var(--seat-color);
  background: var(--card);
  transition:
    background-color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease);
}

.rail__item:hover {
  background: var(--card-hover);
}

/* 正在发言的席位：整行点亮，避免"谁在说话"要靠猜 */
.rail__item--speaking {
  background: color-mix(in srgb, var(--seat-color) 12%, var(--card));
  border-left-color: var(--seat-color);
}

.rail__item--speaking .rail__name {
  color: var(--seat-color);
}

.rail__ident {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.rail__name {
  font-size: var(--text-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rail__title-text {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.rail__state {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-muted);
  white-space: nowrap;
}

.rail__dot {
  width: var(--status-dot);
  height: var(--status-dot);
  border-radius: var(--radius-pill);
  background: currentColor;
}

.rail__state--thinking,
.rail__state--generating {
  color: var(--warning);
}

.rail__state--generating .rail__dot,
.rail__state--thinking .rail__dot {
  animation: breathe 1s var(--ease) infinite;
}

.rail__state--done {
  color: var(--success);
}

.rail__state--failed {
  color: var(--danger);
}

@keyframes breathe {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}

.rail__hint {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

/* 窄屏：横排在顶部，只保留头像与姓名，状态用色点表达 */
.rail--horizontal {
  flex-direction: column;
  overflow-x: auto;
  overflow-y: hidden;
}

.rail--horizontal .rail__title,
.rail--horizontal .rail__hint {
  display: none;
}

.rail--horizontal .rail__list {
  flex-direction: row;
  gap: var(--space-2);
}

.rail--horizontal .rail__item {
  flex: none;
  min-width: calc(var(--space-7) * 3);
}

.rail--horizontal .rail__title-text {
  display: none;
}

/* 状态只留色点：文字用 font-size:0 隐藏，色点尺寸独立不受影响 */
.rail--horizontal .rail__state {
  font-size: 0;
  margin-left: auto;
}

.rail--horizontal .rail__state .rail__dot {
  display: inline-block;
}
</style>
