<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import SeatCard from '@/components/SeatCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { copy } from '@/lib/copy'
import { startDiscussion } from '@/lib/live'
import { usePanelStore } from '@/stores/panel'
import type { RoleType } from '@/lib/types'

const props = defineProps<{ id: string }>()
const panel = usePanelStore()
const router = useRouter()

const selectedRole = ref<RoleType | null>(null)

const showSkeleton = computed(() => panel.generating && panel.seats.length === 0)
const claimable = computed(() => panel.status === 'PANEL_READY' || panel.status === 'DRAFT')
const failure = computed(() => panel.error !== null && panel.seats.length === 0)
/** 还没生成席位：发起人先选座；其他人等待发起人。 */
const picking = computed(
  () => panel.status === 'DRAFT' && panel.seats.length === 0 && !panel.generating,
)
const canPick = computed(() => picking.value && panel.createdByMe)
const waitingForHost = computed(() => picking.value && !panel.createdByMe)
const roleChoices = computed(() =>
  copy.panel.roleOptions.slice(0, Math.max(1, Math.min(panel.panelSize - 1, copy.panel.roleOptions.length))),
)
const openSeats = computed(
  () => panel.seats.filter((seat) => seat.roleType !== 'HOST' && seat.status === 'ACTIVE').length,
)
const startLabel = computed(() =>
  panel.status === 'PANEL_READY' ? copy.panel.startNow : copy.panel.enter,
)

onMounted(() => void panel.init(props.id))
onBeforeUnmount(() => panel.dispose())

/** 选好席位后生成其余 AI 席位并自动开场；成功后直接进入现场。 */
async function startWithSeat(role: RoleType | null): Promise<void> {
  const ok = await panel.startWithSeat(role)
  if (ok) {
    await router.push(`/discussions/${props.id}`)
  }
}

async function enterDiscussion(): Promise<void> {
  // 已有席位时「开始讨论」= 主持人开场；未认领的席位继续由 AI 担任。
  try {
    await startDiscussion(props.id)
  } catch {
    // 已经开场或状态不允许时，以现场页的实际状态为准
  }
  await router.push(`/discussions/${props.id}`)
}
</script>

<template>
  <section class="page">
    <div class="page__inner">
      <RouterLink class="back" to="/">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M9.5 3.5L5 8l4.5 4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
        {{ copy.common.back }}
      </RouterLink>

      <header class="page-head panel-head">
        <div class="headline">
          <p class="eyebrow">{{ copy.panel.eyebrow }}</p>
          <div class="headline__meta">
            <StatusBadge :status="panel.status" />
            <span class="badge badge--muted">{{ copy.lobby.panelSizeHint }}</span>
          </div>
          <h1 class="topic" :title="panel.topic">{{ panel.topic || copy.common.loading }}</h1>
        </div>
        <div class="headline__actions">
          <button
            v-if="panel.ready"
            type="button"
            class="btn"
            :disabled="panel.generating"
            data-testid="regenerate-panel"
            @click="panel.regenerate()"
          >
            {{ panel.regenerating ? copy.panel.regenerating : copy.panel.regenerate }}
          </button>
          <button
            v-if="panel.ready"
            type="button"
            class="btn btn--primary"
            data-testid="enter-discussion"
            :disabled="panel.starting"
            @click="enterDiscussion"
          >
            {{ startLabel }}
          </button>
        </div>
      </header>

      <section v-if="canPick" class="picker card" data-testid="seat-picker">
        <header class="picker__head">
          <p class="eyebrow">{{ copy.panel.eyebrow }}</p>
          <h2 class="picker__title">{{ copy.panel.pickTitle }}</h2>
          <p class="picker__body">{{ copy.panel.pickBody }}</p>
        </header>

        <div class="picker__host">
          <span class="picker__host-tag">{{ copy.seat.roleType.HOST }}</span>
          <span>{{ copy.panel.pickHost }}</span>
        </div>

        <div class="picker__roles" role="radiogroup" :aria-label="copy.panel.pickTitle">
          <button
            v-for="choice in roleChoices"
            :key="choice.role"
            type="button"
            role="radio"
            class="picker__role"
            :class="{ 'picker__role--active': selectedRole === choice.role }"
            :aria-checked="selectedRole === choice.role"
            :data-testid="`role-${choice.role}`"
            @click="selectedRole = choice.role"
          >
            <span class="picker__role-title">{{ copy.seat.roleType[choice.role] }}</span>
            <span class="picker__role-hint">{{ choice.hint }}</span>
            <span v-if="selectedRole === choice.role" class="picker__role-check">
              {{ copy.panel.pickSelected }}
            </span>
          </button>
        </div>

        <div class="picker__actions">
          <button
            type="button"
            class="btn btn--primary"
            data-testid="generate-with-seat"
            :disabled="!selectedRole || panel.starting"
            @click="startWithSeat(selectedRole)"
          >
            {{ panel.starting ? copy.panel.pickStarting : copy.panel.pickAction }}
          </button>
          <button
            type="button"
            class="btn"
            data-testid="generate-all-ai"
            :disabled="panel.starting || !panel.createdByMe"
            @click="startWithSeat(null)"
          >
            {{ copy.panel.pickAllAi }}
          </button>
        </div>
      </section>

      <div v-if="waitingForHost" class="failure card" data-testid="waiting-for-host">
        <h2 class="failure__title">{{ copy.panel.pickTitle }}</h2>
        <p class="failure__body">{{ copy.panel.pickWaiting }}</p>
        <div class="failure__actions">
          <button type="button" class="btn" @click="panel.init(props.id)">{{ copy.common.retry }}</button>
        </div>
      </div>

      <p
        v-if="panel.status === 'PANEL_READY' && panel.seats.length > 0"
        class="banner banner--info"
        data-testid="start-hint"
      >
        <span v-if="openSeats > 0">{{ copy.panel.openSeats(openSeats) }} · </span>
        {{ panel.createdByMe ? copy.panel.startHint : copy.panel.joiningHint }}
      </p>

      <section v-if="panel.analysis" class="analysis" data-testid="panel-analysis">
        <div class="analysis__goal">
          <span class="analysis__label">{{ copy.panel.goalLabel }}</span>
          <p class="analysis__goal-text">{{ panel.analysis.goal || copy.panel.analysisPending }}</p>
        </div>
        <div class="analysis__row">
          <span class="analysis__label">{{ copy.panel.disputesLabel }}</span>
          <ul class="analysis__tags">
            <li v-for="item in panel.analysis.disputes" :key="item" class="tag tag--dispute">{{ item }}</li>
          </ul>
        </div>
        <div class="analysis__row">
          <span class="analysis__label">{{ copy.panel.anglesLabel }}</span>
          <ul class="analysis__tags">
            <li v-for="item in panel.analysis.angles" :key="item" class="tag">{{ item }}</li>
          </ul>
        </div>
      </section>

      <div v-if="showSkeleton" class="grid" aria-hidden="true">
        <div v-for="index in 4" :key="index" class="card skeleton-seat">
          <div class="skeleton skeleton-seat__avatar"></div>
          <div class="skeleton skeleton-seat__line"></div>
          <div class="skeleton skeleton-seat__line skeleton-seat__line--long"></div>
        </div>
        <p class="skeleton-hint muted">{{ copy.panel.firstSeatPending }}</p>
      </div>

      <div v-else-if="panel.seats.length > 0" class="grid">
        <SeatCard
          v-for="seat in panel.seats"
          :key="seat.id"
          :seat="seat"
          :can-claim="claimable"
          :busy="panel.claimingSeatId === seat.id"
          @claim="panel.claim($event)"
          @revoke="panel.claim(null)"
        />
      </div>

      <div v-if="failure" class="failure card">
        <h2 class="failure__title">{{ copy.panel.failedTitle }}</h2>
        <p class="failure__body">{{ panel.error }}</p>
        <p v-if="panel.fallbackAvailable" class="failure__body muted">{{ copy.panel.presetHint }}</p>
        <div class="failure__actions">
          <button type="button" class="btn" @click="panel.retry()">{{ copy.panel.failedRetry }}</button>
          <button
            v-if="panel.fallbackAvailable"
            type="button"
            class="btn btn--primary"
            data-testid="use-preset"
            @click="panel.usePreset()"
          >
            {{ copy.panel.usePreset }}
          </button>
        </div>
      </div>

      <EmptyState
        v-else-if="panel.error && panel.seats.length > 0"
        variant="error"
        :title="copy.panel.failedTitle"
        :body="panel.error"
        :action-label="copy.common.retry"
        @action="panel.refreshSeats()"
      />

      <p v-if="panel.generating && panel.seats.length > 0" class="streaming-hint">
        <span class="streaming-hint__dot"></span>
        {{ copy.panel.seatsPending }}
      </p>
    </div>
  </section>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  border-radius: var(--radius-sm);
  transition: color var(--dur-fast) var(--ease);
}

.back:hover {
  color: var(--text);
}

.headline {
  min-width: 0;
}

.headline__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: var(--space-3) 0 var(--space-2);
}

.topic {
  font-size: var(--text-2xl);
  line-height: var(--leading-tight);
  max-width: var(--reading-max);
  text-wrap: balance;
}

.headline__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

/* 议题分析：像一张"议题卡"，目标是主句，争议与视角是两种标签 */
.analysis {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-5);
  margin-bottom: var(--space-5);
  border: 1px solid var(--border);
  border-left: var(--border-accent) solid var(--primary);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(520px 180px at 0% 0%, var(--primary-soft), transparent 72%),
    var(--surface);
}

.analysis__goal {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.analysis__goal-text {
  font-size: var(--text-lg);
  line-height: var(--leading-normal);
  color: var(--text);
}

.analysis__row {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.analysis__label {
  flex: none;
  color: var(--text-muted);
  font-size: var(--text-xs);
  letter-spacing: 0.08em;
  min-width: calc(var(--space-7) + var(--space-3));
}

.analysis__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.tag {
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--text-secondary);
  font-size: var(--text-xs);
}

.tag--dispute {
  border-color: color-mix(in srgb, var(--seat-amber) 38%, transparent);
  background: color-mix(in srgb, var(--seat-amber) 10%, transparent);
  color: var(--seat-amber);
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  align-items: stretch;
  gap: var(--space-4);
}

.skeleton-seat {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  min-height: calc(var(--space-7) * 3);
}

.skeleton-seat__avatar {
  width: var(--avatar-size-lg);
  height: var(--avatar-size-lg);
  border-radius: var(--radius-pill);
}

.skeleton-seat__line {
  height: var(--skeleton-line);
  width: 70%;
}

.skeleton-seat__line--long {
  width: 100%;
}

.skeleton-hint {
  grid-column: 1 / -1;
  font-size: var(--text-xs);
}

.failure {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5);
  border-color: var(--danger);
}

.failure__title {
  font-size: var(--text-lg);
}

.failure__body {
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.failure__actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.streaming-hint {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-4);
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.streaming-hint__dot {
  width: var(--status-dot);
  height: var(--status-dot);
  border-radius: var(--radius-pill);
  background: var(--seat-emerald);
  animation: breathe 1s var(--ease) infinite;
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

/* 选座：主持人固定，专家角色单选；选中后才允许生成其余席位 */
.picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-5);
  margin-bottom: var(--space-5);
}

.picker__title {
  font-size: var(--text-xl);
  margin-top: var(--space-1);
}

.picker__body {
  margin-top: var(--space-2);
  max-width: var(--reading-max);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
}

.picker__host {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.picker__host-tag {
  flex: none;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--surface);
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.picker__roles {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-2);
}

.picker__role {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--card);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--dur-fast) var(--ease),
    background-color var(--dur-fast) var(--ease);
}

.picker__role:hover {
  border-color: color-mix(in srgb, var(--primary) 55%, transparent);
}

.picker__role--active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.picker__role-title {
  font-weight: 600;
}

.picker__role-hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.picker__role-check {
  color: var(--primary);
  font-size: var(--text-xs);
}

.picker__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (min-width: 768px) {
  .picker__roles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1024px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1600px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .headline__actions {
    width: 100%;
    justify-content: flex-start;
  }

  .topic {
    font-size: var(--text-xl);
  }

  .analysis {
    padding: var(--space-4);
  }

  .analysis__label {
    min-width: 0;
  }
}
</style>
