<script setup lang="ts">
import { computed } from 'vue'
import { copy } from '@/lib/copy'
import { seatColor } from '@/lib/seat'
import type { RunView, Seat } from '@/lib/types'

const props = defineProps<{
  runs: RunView[]
  seats: Seat[]
  activeMessageId?: string | null
}>()

const emit = defineEmits<{ locate: [messageId: string] }>()

const nameById = computed<Record<string, { name: string; colorToken: string }>>(() => {
  const map: Record<string, { name: string; colorToken: string }> = {}
  for (const seat of props.seats) {
    map[seat.id] = { name: seat.name, colorToken: seat.colorToken }
  }
  return map
})

const groups = computed(() => {
  const byRound = new Map<number, RunView[]>()
  for (const run of props.runs) {
    const list = byRound.get(run.roundNo) ?? []
    list.push(run)
    byRound.set(run.roundNo, list)
  }
  return [...byRound.entries()]
    .sort((left, right) => left[0] - right[0])
    .map(([round, items]) => ({ round, items }))
})

function triggerLabel(trigger: string | null): string {
  return trigger ? (copy.live.triggers[trigger] ?? trigger) : ''
}

function phaseLabel(phase: string | null): string {
  return phase ? (copy.live.phases[phase] ?? phase) : ''
}
</script>

<template>
  <aside class="trace" :aria-label="copy.live.traceTitle">
    <h2 class="trace__title">{{ copy.live.traceTitle }}</h2>

    <p v-if="groups.length === 0" class="trace__empty muted">{{ copy.live.traceEmpty }}</p>

    <ol v-else class="trace__groups">
      <li v-for="group in groups" :key="group.round" class="trace__group">
        <h3 class="trace__round">{{ copy.lobby.turnBadge(group.round) }}</h3>
        <ul class="trace__items">
          <li
            v-for="run in group.items"
            :key="run.id"
            class="trace__item"
            :class="{ 'trace__item--active': Boolean(props.activeMessageId && run.messageId === props.activeMessageId) }"
            :style="{ '--seat-color': seatColor(nameById[run.agentId ?? '']?.colorToken ?? 'slate') }"
          >
            <button
              type="button"
              class="trace__head"
              data-testid="trace-item"
              :disabled="!run.messageId"
              @click="run.messageId && emit('locate', run.messageId)"
            >
              <span class="trace__dot" :class="`trace__dot--${(run.phase ?? 'DONE').toLowerCase()}`"></span>
              <span class="trace__name">{{ nameById[run.agentId ?? '']?.name ?? '嘉宾' }}</span>
              <span class="trace__trigger">{{ triggerLabel(run.trigger) }}</span>
              <span class="trace__phase">{{ phaseLabel(run.phase) }}</span>
            </button>
            <p class="trace__meta">
              <span v-if="run.durationMs">{{ copy.live.traceDuration(run.durationMs) }}</span>
              <span v-if="run.tokens">{{ copy.live.traceTokens(run.tokens) }}</span>
            </p>
            <details class="trace__details">
              <summary>{{ copy.live.traceDetail }}</summary>
              <pre class="trace__json">{{ JSON.stringify(run.meta ?? {}, null, 2) }}</pre>
            </details>
          </li>
        </ul>
      </li>
    </ol>
  </aside>
</template>

<style scoped>
.trace {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  overflow-y: auto;
}

.trace__title {
  font-size: var(--text-xs);
  color: var(--text-muted);
  letter-spacing: 0.08em;
}

.trace__empty {
  font-size: var(--text-xs);
}

.trace__groups {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.trace__round {
  position: sticky;
  top: calc(var(--space-4) * -1);
  z-index: 1;
  padding: var(--space-1) 0;
  background: var(--surface);
  font-size: var(--text-xs);
  color: var(--text-secondary);
}

.trace__items {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.trace__item {
  padding: var(--space-2);
  border-radius: var(--radius-md);
  border-left: var(--border-accent) solid var(--seat-color);
  background: var(--card);
  transition: background-color var(--dur-fast) var(--ease);
}

.trace__item:hover {
  background: var(--card-hover);
}

/* 点击消息后高亮对应轨迹（FR-5 双向联动） */
.trace__item--active {
  background: var(--primary-soft);
  box-shadow: inset 0 0 0 1px var(--primary-line);
}

.trace__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text);
  font-size: var(--text-xs);
  text-align: left;
  cursor: pointer;
}

.trace__head:disabled {
  cursor: default;
}

.trace__head:hover:not(:disabled) .trace__name {
  color: var(--primary);
}

.trace__dot {
  width: var(--status-dot);
  height: var(--status-dot);
  border-radius: var(--radius-pill);
  background: var(--success);
  flex: none;
}

.trace__dot--failed {
  background: var(--danger);
}

.trace__dot--thinking,
.trace__dot--generating {
  background: var(--warning);
}

.trace__name {
  font-weight: 600;
}

.trace__trigger,
.trace__phase {
  color: var(--text-muted);
}

.trace__phase {
  margin-left: auto;
}

.trace__meta {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.trace__details {
  margin-top: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.trace__details summary {
  cursor: pointer;
  list-style: none;
}

.trace__json {
  margin-top: var(--space-1);
  padding: var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--bg);
  color: var(--text-secondary);
  font-size: var(--text-xs);
  overflow-x: auto;
}
</style>
