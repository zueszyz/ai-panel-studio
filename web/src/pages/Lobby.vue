<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppDialog from '@/components/AppDialog.vue'
import CreateDiscussionDialog from '@/components/CreateDiscussionDialog.vue'
import DiscussionCard from '@/components/DiscussionCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { copy } from '@/lib/copy'
import type { DiscussionSummary } from '@/lib/types'
import { useLobbyStore, type LobbyFilter } from '@/stores/lobby'

const lobby = useLobbyStore()
const router = useRouter()

const dialogOpen = ref(false)
const pendingDelete = ref<DiscussionSummary | null>(null)
const deleting = ref(false)
const searchInput = ref(lobby.keyword)
let searchTimer: ReturnType<typeof setTimeout> | undefined

const filters: { key: LobbyFilter; label: string }[] = [
  { key: 'all', label: copy.lobby.filters.all },
  { key: 'preparing', label: copy.lobby.filters.preparing },
  { key: 'ongoing', label: copy.lobby.filters.ongoing },
  { key: 'mine', label: copy.lobby.filters.mine },
  { key: 'ended', label: copy.lobby.filters.ended },
]

const facts = copy.lobby.facts

const showSkeleton = computed(() => lobby.loading && lobby.items.length === 0)

watch(searchInput, (value) => {
  lobby.setKeyword(value)
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => void lobby.load(), 300)
})

onMounted(() => void lobby.load())

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})

function onCreated(id: string): void {
  dialogOpen.value = false
  void router.push(`/discussions/${id}/panel`)
}

async function confirmDelete(): Promise<void> {
  const target = pendingDelete.value
  if (!target || deleting.value) {
    return
  }
  deleting.value = true
  const ok = await lobby.remove(target.id)
  deleting.value = false
  if (ok) {
    pendingDelete.value = null
  }
}
</script>

<template>
  <section class="page">
    <div class="page__inner page__inner--wide">
      <header class="hero">
        <div class="hero__copy">
          <p class="eyebrow">{{ copy.lobby.eyebrow }}</p>
          <h1 class="hero__title">{{ copy.lobby.heroTitle }}</h1>
          <p class="hero__sub">{{ copy.lobby.heroBody }}</p>
          <div class="hero__actions">
            <button
              type="button"
              class="btn btn--primary btn--lg"
              data-testid="create-discussion"
              @click="dialogOpen = true"
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
              </svg>
              {{ copy.lobby.create }}
            </button>
            <span class="hero__hint">{{ copy.lobby.heroHint }}</span>
          </div>
        </div>
        <ul class="hero__facts">
          <li v-for="fact in facts" :key="fact.label" class="fact">
            <span class="fact__value">{{ fact.value }}</span>
            <span class="fact__label">{{ fact.label }}</span>
          </li>
        </ul>
      </header>

      <div class="toolbar">
        <div class="tabs" role="tablist" :aria-label="copy.lobby.filterLabel">
          <button
            v-for="item in filters"
            :key="item.key"
            type="button"
            role="tab"
            class="tabs__item"
            :class="{ 'tabs__item--active': lobby.filter === item.key }"
            :aria-selected="lobby.filter === item.key"
            @click="lobby.setFilter(item.key)"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="search">
          <svg class="search__icon" width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <circle cx="7" cy="7" r="4.4" stroke="currentColor" stroke-width="1.4" />
            <path d="M10.5 10.5L14 14" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
          </svg>
          <input
            v-model="searchInput"
            class="field search__input"
            type="search"
            :placeholder="copy.lobby.searchPlaceholder"
            :aria-label="copy.lobby.searchPlaceholder"
            data-testid="lobby-search"
          />
        </div>
      </div>

      <div v-if="showSkeleton" class="grid" aria-hidden="true">
        <div v-for="index in 4" :key="index" class="card skeleton-card">
          <div class="skeleton skeleton-card__badge"></div>
          <div class="skeleton skeleton-card__title"></div>
          <div class="skeleton skeleton-card__title skeleton-card__title--short"></div>
          <div class="skeleton-card__foot">
            <div class="skeleton skeleton-card__avatar"></div>
            <div class="skeleton skeleton-card__line"></div>
          </div>
        </div>
      </div>

      <EmptyState
        v-else-if="lobby.error"
        variant="error"
        :title="copy.lobby.errorTitle"
        :body="lobby.error"
        :action-label="copy.common.retry"
        @action="lobby.load()"
      />

      <EmptyState
        v-else-if="lobby.isEmpty && lobby.isFiltered"
        :title="copy.lobby.noResultTitle"
        :body="copy.lobby.noResultBody"
        :action-label="copy.lobby.clearFilters"
        @action="lobby.clearFilters()"
      />

      <EmptyState
        v-else-if="lobby.isEmpty"
        :title="copy.lobby.emptyTitle"
        :body="copy.lobby.emptyBody"
        :action-label="copy.lobby.create"
        @action="dialogOpen = true"
      />

      <template v-else>
        <div class="grid" data-testid="discussion-grid">
          <DiscussionCard
            v-for="discussion in lobby.items"
            :key="discussion.id"
            :discussion="discussion"
            @delete="pendingDelete = $event"
          />
        </div>
        <div v-if="lobby.nextCursor" class="more">
          <button type="button" class="btn" :disabled="lobby.loadingMore" @click="lobby.loadMore()">
            {{ lobby.loadingMore ? copy.common.loading : copy.common.more }}
          </button>
        </div>
      </template>
    </div>

    <CreateDiscussionDialog :open="dialogOpen" @close="dialogOpen = false" @created="onCreated" />

    <AppDialog
      :open="pendingDelete !== null"
      :title="copy.lobby.deleteTitle"
      :description="copy.lobby.deleteBody"
      @close="pendingDelete = null"
    >
      <p class="delete-topic">{{ pendingDelete?.topic }}</p>
      <template #footer>
        <button type="button" class="btn btn--ghost" @click="pendingDelete = null">
          {{ copy.common.cancel }}
        </button>
        <button
          type="button"
          class="btn btn--danger"
          data-testid="confirm-delete-discussion"
          :disabled="deleting"
          @click="confirmDelete"
        >
          {{ deleting ? copy.lobby.deleting : copy.lobby.deleteConfirm }}
        </button>
      </template>
    </AppDialog>
  </section>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 0.6fr);
  gap: var(--space-6);
  align-items: end;
  padding: var(--space-6);
  margin-bottom: var(--space-6);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  background:
    radial-gradient(720px 280px at 0% 0%, var(--primary-soft), transparent 72%),
    var(--surface);
  animation: hero-in var(--dur-slow) var(--ease);
}

@keyframes hero-in {
  from {
    opacity: 0;
    transform: translateY(var(--space-2));
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.hero__title {
  margin-top: var(--space-3);
  font-size: var(--text-3xl);
  line-height: 1.18;
  letter-spacing: -0.02em;
  text-wrap: balance;
}

.hero__sub {
  margin-top: var(--space-3);
  max-width: var(--copy-max);
  color: var(--text-secondary);
  line-height: var(--leading-relaxed);
}

.hero__actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
  flex-wrap: wrap;
}

.hero__hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.hero__facts {
  display: grid;
  gap: var(--space-2);
  align-content: end;
}

.fact {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--card) 65%, transparent);
}

.fact__value {
  font-size: var(--text-lg);
  font-weight: 600;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.fact__label {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
  flex-wrap: wrap;
}

.tabs {
  display: inline-flex;
  gap: var(--space-1);
  padding: var(--space-1);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-pill);
}

.tabs__item {
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-pill);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  transition:
    background-color var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease);
}

.tabs__item:hover {
  color: var(--text);
  background: var(--card);
}

.tabs__item--active {
  background: var(--primary-soft);
  color: var(--text);
  box-shadow: inset 0 0 0 1px var(--primary-line);
}

.search {
  position: relative;
  margin-left: auto;
  min-width: calc(var(--space-7) * 5);
}

.search__icon {
  position: absolute;
  left: var(--space-3);
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-muted);
  pointer-events: none;
}

.search__input {
  height: var(--space-7);
  padding-left: calc(var(--space-5) + var(--space-2));
  font-size: var(--text-sm);
}

.grid {
  display: grid;
  grid-template-columns: 1fr;
  align-items: stretch;
  gap: var(--space-4);
}

.skeleton-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  min-height: calc(var(--space-7) * 4);
}

.skeleton-card__badge {
  width: var(--space-7);
  height: var(--space-5);
  border-radius: var(--radius-pill);
}

.skeleton-card__title {
  height: var(--text-lg);
  width: 92%;
}

.skeleton-card__title--short {
  width: 64%;
}

.skeleton-card__foot {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: auto;
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.skeleton-card__avatar {
  width: var(--space-5);
  height: var(--space-5);
  border-radius: var(--radius-pill);
}

.skeleton-card__line {
  height: var(--skeleton-line);
  flex: 1;
}

.more {
  display: flex;
  justify-content: center;
  margin-top: var(--space-5);
}

.page__inner--wide {
  max-width: var(--content-wide);
}

@media (max-width: 1023px) {
  .hero {
    grid-template-columns: minmax(0, 1fr);
    align-items: start;
    gap: var(--space-5);
    padding: var(--space-5);
  }

  .hero__facts {
    grid-template-columns: repeat(3, minmax(0, 1fr));
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

.delete-topic {
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
  word-break: break-word;
}

@media (max-width: 767px) {
  .hero {
    padding: var(--space-4);
    margin-bottom: var(--space-5);
  }

  .hero__title {
    font-size: var(--text-2xl);
  }

  .hero__facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .search {
    margin-left: 0;
    width: 100%;
  }
}
</style>
