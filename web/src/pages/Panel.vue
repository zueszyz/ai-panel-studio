<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import SeatCard from '@/components/SeatCard.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { copy } from '@/lib/copy'
import { usePanelStore } from '@/stores/panel'

const props = defineProps<{ id: string }>()
const panel = usePanelStore()
const router = useRouter()

const showSkeleton = computed(() => panel.generating && panel.seats.length === 0)
const claimable = computed(() => panel.status === 'PANEL_READY' || panel.status === 'DRAFT')
const failure = computed(() => panel.error !== null && panel.seats.length === 0)

onMounted(() => void panel.init(props.id))
onBeforeUnmount(() => panel.dispose())

function enterDiscussion(): void {
  void router.push(`/discussions/${props.id}`)
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

      <header class="page-head">
        <div class="headline">
          <div class="headline__meta">
            <StatusBadge :status="panel.status" />
            <span class="badge">{{ copy.panel.title }}</span>
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
            @click="enterDiscussion"
          >
            {{ copy.panel.enter }}
          </button>
        </div>
      </header>

      <p v-if="!panel.ready" class="hint muted">{{ copy.panel.regenLimitHint }}</p>

      <section v-if="panel.analysis" class="analysis card">
        <h2 class="analysis__title">{{ copy.panel.analysisTitle }}</h2>
        <div v-if="panel.analysis.goal" class="analysis__group">
          <span class="analysis__label">{{ copy.panel.goalLabel }}</span>
          <span class="analysis__goal">{{ panel.analysis.goal }}</span>
        </div>
        <div class="analysis__group">
          <span class="analysis__label">{{ copy.panel.disputesLabel }}</span>
          <span v-for="item in panel.analysis.disputes" :key="item" class="badge">{{ item }}</span>
        </div>
        <div class="analysis__group">
          <span class="analysis__label">{{ copy.panel.anglesLabel }}</span>
          <span v-for="item in panel.analysis.angles" :key="item" class="badge">{{ item }}</span>
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
  margin-bottom: var(--space-2);
}

.topic {
  font-size: var(--text-xl);
  line-height: var(--leading-normal);
  max-width: 46ch;
}

.headline__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.hint {
  margin-bottom: var(--space-4);
  font-size: var(--text-xs);
}

.analysis {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-4);
  margin-bottom: var(--space-5);
  background: var(--surface);
}

.analysis__title {
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.analysis__goal {
  font-size: var(--text-md);
}

.analysis__group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.analysis__label {
  color: var(--text-muted);
  font-size: var(--text-xs);
  min-width: var(--space-7);
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  align-items: start;
  gap: var(--space-3);
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

@media (min-width: 1024px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .headline__actions {
    width: 100%;
    justify-content: flex-start;
  }

  .topic {
    font-size: var(--text-lg);
  }
}
</style>
