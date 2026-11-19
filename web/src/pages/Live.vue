<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppDialog from '@/components/AppDialog.vue'
import EmptyState from '@/components/EmptyState.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import SeatRail from '@/components/SeatRail.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import TracePanel from '@/components/TracePanel.vue'
import { ApiError } from '@/lib/api'
import { copy } from '@/lib/copy'
import { restartDiscussion } from '@/lib/discussions'
import { useLiveStore } from '@/stores/live'

const props = defineProps<{ id: string }>()
const store = useLiveStore()
const route = useRoute()
const router = useRouter()

const draft = ref('')
const listRef = ref<HTMLElement | null>(null)
const atBottom = ref(true)
const newCount = ref(0)
const highlightId = ref<string | null>(null)
const activeMessageId = ref<string | null>(null)
const endDialogOpen = ref(false)
const traceOpen = ref(false)
const restarting = ref(false)
const restartError = ref<string | null>(null)
const submitting = ref(false)
const narrow = ref(false)
const compact = ref(false)

const canSend = computed(() => store.live && draft.value.trim().length > 0 && !store.sending)
const charCount = computed(() => draft.value.length)
const showTraceToggle = computed(() => compact.value)

let narrowQuery: MediaQueryList | null = null
let compactQuery: MediaQueryList | null = null

function syncNarrow(event: MediaQueryListEvent | MediaQueryList): void {
  narrow.value = event.matches
}

function syncCompact(event: MediaQueryListEvent | MediaQueryList): void {
  compact.value = event.matches
  if (!compact.value) {
    traceOpen.value = false
  }
}

onMounted(async () => {
  narrowQuery = window.matchMedia('(max-width: 767px)')
  compactQuery = window.matchMedia('(max-width: 1279px)')
  syncNarrow(narrowQuery)
  syncCompact(compactQuery)
  narrowQuery.addEventListener('change', syncNarrow)
  compactQuery.addEventListener('change', syncCompact)
  await store.open(props.id)
})

onBeforeUnmount(() => {
  narrowQuery?.removeEventListener('change', syncNarrow)
  compactQuery?.removeEventListener('change', syncCompact)
  store.dispose()
})

watch(
  () => store.messages.length,
  async (next, previous) => {
    if (next === previous) {
      return
    }
    await nextTick()
    if (atBottom.value) {
      scrollToBottom()
    } else if (next > previous) {
      newCount.value += next - previous
    }
  },
)

watch(
  () => store.loading,
  async (loading) => {
    if (!loading) {
      await nextTick()
      const target = typeof route.query.messageId === 'string' ? route.query.messageId : null
      if (target) {
        locate(target)
      } else {
        scrollToBottom()
      }
    }
  },
)

function onScroll(): void {
  const element = listRef.value
  if (!element) {
    return
  }
  const gap = element.scrollHeight - element.scrollTop - element.clientHeight
  atBottom.value = gap < 80
  if (atBottom.value) {
    newCount.value = 0
  }
}

function scrollToBottom(): void {
  const element = listRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

/** 「有 N 条新发言」：回到底部并清掉未读计数。 */
function jumpToLatest(): void {
  scrollToBottom()
  newCount.value = 0
}

function locate(messageId: string): void {
  atBottom.value = false
  traceOpen.value = false
  activeMessageId.value = messageId
  highlightId.value = messageId
  void nextTick(() => {
    const element = listRef.value?.querySelector<HTMLElement>(`[data-message-id="${messageId}"]`)
    element?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
  window.setTimeout(() => {
    if (highlightId.value === messageId) {
      highlightId.value = null
    }
  }, 1500)
}

async function submit(): Promise<void> {
  if (!canSend.value || submitting.value) {
    return
  }
  submitting.value = true
  const text = draft.value
  const ok = await store.send(text)
  submitting.value = false
  if (ok) {
    draft.value = ''
    atBottom.value = true
    await nextTick()
    scrollToBottom()
  }
}

async function confirmEnd(): Promise<void> {
  endDialogOpen.value = false
  await store.end()
  // 结束后直接去纪要页并自动生成，省掉「结束 → 再点生成纪要」这一步；失败时留在现场看提示。
  if (store.status === 'SUMMARIZING') {
    await router.push(`/discussions/${props.id}/minutes?auto=1`)
  }
}

function goMinutes(): void {
  void router.push(`/discussions/${props.id}/minutes`)
}

async function restart(): Promise<void> {
  if (restarting.value) {
    return
  }
  restarting.value = true
  restartError.value = null
  try {
    const created = await restartDiscussion(props.id)
    await router.push(`/discussions/${created.id}/panel`)
  } catch (err) {
    restartError.value = err instanceof ApiError ? err.message : copy.errors.network
  } finally {
    restarting.value = false
  }
}
</script>

<template>
  <section class="live">
    <header class="live__head">
      <RouterLink class="back" to="/" :aria-label="copy.common.back">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M9.5 3.5L5 8l4.5 4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
      </RouterLink>
      <div class="headline">
        <div class="headline__meta">
          <StatusBadge :status="store.status" />
          <span class="badge">{{ copy.lobby.turnBadge(store.turnIndex) }}</span>
        </div>
        <h1 class="topic" :title="store.topic">{{ store.topic || copy.common.loading }}</h1>
      </div>
      <div class="headline__actions">
        <button
          v-if="showTraceToggle"
          type="button"
          class="btn btn--sm"
          data-testid="toggle-trace"
          @click="traceOpen = true"
        >
          {{ copy.live.trace }}
        </button>
        <button
          v-if="store.status === 'PANEL_READY' || store.status === 'DRAFT'"
          type="button"
          class="btn btn--primary"
          :disabled="store.starting"
          data-testid="start-discussion"
          @click="store.start()"
        >
          {{ store.starting ? copy.live.starting : copy.live.start }}
        </button>
        <template v-if="store.live">
          <button type="button" class="btn btn--sm" data-testid="next-round" @click="store.requestNextRound()">
            {{ copy.live.nextRound }}
          </button>
          <button type="button" class="btn btn--sm" data-testid="end-discussion" @click="endDialogOpen = true">
            {{ copy.live.end }}
          </button>
        </template>
        <template v-if="store.status === 'SUMMARIZING' || store.status === 'CLOSED'">
          <button
            type="button"
            class="btn btn--primary"
            data-testid="go-minutes"
            @click="goMinutes"
          >
            {{ copy.live.viewMinutes }}
          </button>
          <button
            type="button"
            class="btn btn--sm"
            data-testid="restart-discussion"
            :disabled="restarting"
            @click="restart"
          >
            {{ restarting ? copy.live.restarting : copy.live.restart }}
          </button>
        </template>
      </div>
    </header>

    <p v-if="store.connection === 'retrying'" class="banner banner--warning" data-testid="connection-banner">
      {{ copy.live.connectionRetrying(store.retryAttempt) }}
    </p>
    <p v-if="restartError" class="banner banner--warning" data-testid="restart-error">{{ restartError }}</p>
    <p v-if="store.error" class="banner banner--error" data-testid="live-error">
      {{ store.error }}
      <button type="button" class="btn btn--sm" @click="store.clearError()">{{ copy.common.close }}</button>
    </p>

    <div class="live__grid">
      <SeatRail
        :seats="store.seats"
        :states="store.seatStates"
        :speakers="store.currentSpeakers"
        :my-seat-id="store.mySeatId"
        :horizontal="narrow"
        class="live__rail"
      />

      <main class="flow">
        <div ref="listRef" class="flow__list" data-testid="message-flow" @scroll="onScroll">
          <div v-if="store.loading" class="flow__skeleton" aria-hidden="true">
            <div v-for="index in 3" :key="index" class="card skeleton-bubble">
              <div class="skeleton skeleton-bubble__head"></div>
              <div class="skeleton skeleton-bubble__line"></div>
              <div class="skeleton skeleton-bubble__line skeleton-bubble__line--short"></div>
            </div>
          </div>
          <EmptyState
            v-else-if="store.messages.length === 0"
            variant="empty"
            :title="store.status === 'PANEL_READY' ? copy.live.notStarted : copy.live.emptyFlow"
            :body="store.status === 'PANEL_READY' ? copy.live.notStartedBody : copy.live.waiting"
          />
          <MessageBubble
            v-for="message in store.messages"
            :key="message.id"
            :message="message"
            :highlighted="highlightId === message.id"
            @locate="locate"
            @retry="store.retry"
            @select="activeMessageId = $event"
          />
        </div>

        <button v-if="newCount > 0" type="button" class="pill" @click="jumpToLatest">
          {{ copy.live.newMessages(newCount) }}
        </button>

        <form class="composer" @submit.prevent="submit">
          <label class="visually-hidden" for="composer-input">{{ copy.live.composerPlaceholder }}</label>
          <textarea
            id="composer-input"
            v-model="draft"
            class="composer__input"
            rows="2"
            maxlength="1000"
            :placeholder="store.live ? copy.live.composerPlaceholder : copy.live.notStarted"
            :disabled="!store.live || store.ending"
            data-testid="composer-input"
            @keydown.enter.exact.prevent="submit"
          ></textarea>
          <div class="composer__foot">
            <span class="composer__hint" :class="{ 'composer__hint--warn': charCount > 900 }">
              {{ copy.common.charCount(charCount, 1000) }}
            </span>
            <button type="submit" class="btn btn--primary" :disabled="!canSend" data-testid="composer-send">
              <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path
                  d="M2.5 8.2L13.2 3.2 8.6 13.6 7.4 9.1z"
                  stroke="currentColor"
                  stroke-width="1.3"
                  stroke-linejoin="round"
                />
              </svg>
              {{ store.sending ? copy.live.sending : copy.live.send }}
            </button>
          </div>
        </form>
      </main>

      <TracePanel
        v-if="!compact"
        :runs="store.runs"
        :seats="store.seats"
        :active-message-id="activeMessageId"
        class="live__trace"
        @locate="locate"
      />
    </div>

    <AppDialog
      :open="endDialogOpen"
      :title="copy.live.endTitle"
      :description="copy.live.endBody"
      @close="endDialogOpen = false"
    >
      <div class="dialog-actions">
        <button type="button" class="btn" @click="endDialogOpen = false">{{ copy.common.cancel }}</button>
        <button type="button" class="btn btn--primary" data-testid="confirm-end" @click="confirmEnd">
          {{ copy.live.endConfirm }}
        </button>
      </div>
    </AppDialog>

    <div v-if="traceOpen" class="drawer" role="dialog" :aria-label="copy.live.traceTitle">
      <button type="button" class="drawer__backdrop" :aria-label="copy.common.close" @click="traceOpen = false"></button>
      <div class="drawer__panel">
        <header class="drawer__head">
          <h2 class="drawer__title">{{ copy.live.traceTitle }}</h2>
          <button type="button" class="btn btn--sm" @click="traceOpen = false">{{ copy.common.close }}</button>
        </header>
        <TracePanel
          :runs="store.runs"
          :seats="store.seats"
          :active-message-id="activeMessageId"
          class="drawer__body"
          @locate="locate"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
/* 圆桌页固定视口：三区各自滚动，输入区不随滚动移动（rules/20 §6） */
.live {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  padding: var(--space-4) var(--space-5);
  gap: var(--space-3);
}

.live__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: none;
}

.back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--space-6);
  height: var(--space-6);
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  color: var(--text-secondary);
  flex: none;
}

.back:hover {
  color: var(--text);
  border-color: var(--border-strong);
}

.headline {
  min-width: 0;
  flex: 1;
}

.headline__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.topic {
  font-size: var(--text-lg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.headline__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex: none;
}

.banner {
  flex: none;
}

.live__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--space-3);
  flex: 1;
  min-height: 0;
}

.live__rail {
  min-height: 0;
}

.flow {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-height: 0;
  position: relative;
}

.flow__list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: var(--space-1);
  scroll-behavior: smooth;
}

.flow__skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.skeleton-bubble {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
}

.skeleton-bubble__head {
  height: var(--text-sm);
  width: 30%;
}

.skeleton-bubble__line {
  height: var(--skeleton-line);
  width: 100%;
}

.skeleton-bubble__line--short {
  width: 58%;
}

.pill {
  position: absolute;
  left: 50%;
  bottom: calc(var(--space-7) * 2);
  transform: translateX(-50%);
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  border: 1px solid var(--primary);
  background: var(--card);
  color: var(--text);
  font-size: var(--text-xs);
  cursor: pointer;
  box-shadow: var(--shadow-2);
  animation: fade-in var(--dur-fast) var(--ease);
  z-index: 2;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  flex: none;
  padding: var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-1);
  transition:
    border-color var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease);
}

.composer:focus-within {
  border-color: var(--primary-line);
  box-shadow: 0 0 0 var(--focus-ring) var(--primary-soft);
}

.composer__input {
  width: 100%;
  resize: none;
  min-height: calc(var(--space-6) + var(--space-4));
  max-height: calc(var(--space-7) * 2);
  padding: var(--space-1) var(--space-2);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  font-family: inherit;
  font-size: var(--text-md);
  line-height: var(--leading-normal);
}

.composer__input:focus {
  outline: none;
}

.composer__input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.composer__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding-left: var(--space-2);
}

.composer__hint {
  font-size: var(--text-xs);
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.composer__hint--warn {
  color: var(--warning);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

.drawer {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  justify-content: flex-end;
}

.drawer__backdrop {
  position: absolute;
  inset: 0;
  border: none;
  background: rgba(0, 0, 0, 0.5);
  cursor: pointer;
}

.drawer__panel {
  position: relative;
  width: min(var(--content-max), 88vw);
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--bg);
  border-left: 1px solid var(--border);
  box-shadow: var(--shadow-3);
}

.drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: none;
}

.drawer__title {
  font-size: var(--text-md);
}

.drawer__body {
  flex: 1;
  min-height: 0;
}

@keyframes fade-in {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

/* 平板与桌面：三栏；宽度分配按 docs/PRD §8 */
@media (min-width: 1280px) {
  .live__grid {
    grid-template-columns: 240px minmax(0, 1fr) 320px;
    grid-template-rows: minmax(0, 1fr);
  }
}

@media (min-width: 1920px) {
  .live__grid {
    grid-template-columns: 260px minmax(0, 960px) 360px;
    justify-content: center;
  }
}

@media (max-width: 1279px) {
  .live__grid {
    grid-template-rows: auto minmax(0, 1fr);
  }
}

@media (max-width: 767px) {
  .live {
    padding: var(--space-3);
    gap: var(--space-2);
  }

  /* 窄屏把标题与操作拆成两行，否则按钮会压在议题上 */
  .live__head {
    flex-wrap: wrap;
    row-gap: var(--space-2);
  }

  .headline {
    flex: 1 1 100%;
    order: 2;
  }

  .headline__actions {
    flex: 1 1 100%;
    order: 3;
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: var(--space-1);
  }

  .back {
    order: 1;
  }

  .topic {
    font-size: var(--text-md);
    white-space: normal;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

@media (prefers-reduced-motion: reduce) {
  .flow__list {
    scroll-behavior: auto;
  }

  .pill {
    animation: none;
  }
}
</style>
