<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import CreateDiscussionDialog from '@/components/CreateDiscussionDialog.vue'
import DiscussionCard from '@/components/DiscussionCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { copy } from '@/lib/copy'
import { useLobbyStore, type LobbyFilter } from '@/stores/lobby'

const lobby = useLobbyStore()
const router = useRouter()

const dialogOpen = ref(false)
const searchInput = ref(lobby.keyword)
let searchTimer: ReturnType<typeof setTimeout> | undefined

const filters: { key: LobbyFilter; label: string }[] = [
  { key: 'all', label: copy.lobby.filters.all },
  { key: 'ongoing', label: copy.lobby.filters.ongoing },
  { key: 'mine', label: copy.lobby.filters.mine },
  { key: 'ended', label: copy.lobby.filters.ended },
]

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
</script>

<template>
  <section class="page">
    <div class="page__inner">
      <header class="page-head">
        <div>
          <h1 class="page-title">{{ copy.lobby.title }}</h1>
          <p class="page-subtitle">{{ copy.lobby.subtitle }}</p>
        </div>
        <button
          type="button"
          class="btn btn--primary"
          data-testid="create-discussion"
          @click="dialogOpen = true"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M8 3v10M3 8h10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
          {{ copy.lobby.create }}
        </button>
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
          <DiscussionCard v-for="discussion in lobby.items" :key="discussion.id" :discussion="discussion" />
        </div>
        <div v-if="lobby.nextCursor" class="more">
          <button type="button" class="btn" :disabled="lobby.loadingMore" @click="lobby.loadMore()">
            {{ lobby.loadingMore ? copy.common.loading : copy.common.more }}
          </button>
        </div>
      </template>
    </div>

    <CreateDiscussionDialog :open="dialogOpen" @close="dialogOpen = false" @created="onCreated" />
  </section>
</template>

<style scoped>
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
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-pill);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  transition:
    background var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.tabs__item:hover {
  color: var(--text);
}

.tabs__item--active {
  background: var(--card);
  color: var(--text);
  box-shadow: var(--shadow-1);
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
  align-items: start;
  gap: var(--space-3);
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

@media (min-width: 1024px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .search {
    margin-left: 0;
    width: 100%;
  }

  .page-head {
    align-items: stretch;
  }
}
</style>
