import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { copy, errorMessage } from '@/lib/copy'
import { ApiError } from '@/lib/api'
import { createDiscussion, deleteDiscussion, fetchDiscussions } from '@/lib/discussions'
import type { DiscussionSummary } from '@/lib/types'

export type LobbyFilter = 'all' | 'ongoing' | 'mine' | 'ended'

const statusByFilter: Record<LobbyFilter, string | undefined> = {
  all: undefined,
  ongoing: 'LIVE,PANEL_READY,SUMMARIZING,INTERRUPTED',
  mine: undefined,
  ended: 'CLOSED',
}

/** 大厅状态：列表、筛选、搜索、创建。切换页面后重新拉取，前端不缓存业务数据。 */
export const useLobbyStore = defineStore('lobby', () => {
  const items = ref<DiscussionSummary[]>([])
  const nextCursor = ref<string | null>(null)
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref<string | null>(null)
  const filter = ref<LobbyFilter>('all')
  const keyword = ref('')

  const isEmpty = computed(() => !loading.value && !error.value && items.value.length === 0)
  const isFiltered = computed(() => filter.value !== 'all' || keyword.value.trim().length > 0)

  async function load(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const response = await fetchDiscussions({
        status: statusByFilter[filter.value],
        q: keyword.value.trim() || undefined,
        mine: filter.value === 'mine',
        limit: 20,
      })
      items.value = response.items
      nextCursor.value = response.nextCursor
    } catch (err) {
      items.value = []
      nextCursor.value = null
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
    } finally {
      loading.value = false
    }
  }

  async function loadMore(): Promise<void> {
    if (!nextCursor.value || loadingMore.value) {
      return
    }
    loadingMore.value = true
    try {
      const response = await fetchDiscussions({
        status: statusByFilter[filter.value],
        q: keyword.value.trim() || undefined,
        mine: filter.value === 'mine',
        cursor: nextCursor.value,
        limit: 20,
      })
      items.value = [...items.value, ...response.items]
      nextCursor.value = response.nextCursor
    } catch {
      // 追加失败不打断已有列表，保留当前内容即可
    } finally {
      loadingMore.value = false
    }
  }

  function setFilter(next: LobbyFilter): void {
    if (filter.value === next) {
      return
    }
    filter.value = next
    void load()
  }

  function setKeyword(next: string): void {
    keyword.value = next
  }

  function clearFilters(): void {
    filter.value = 'all'
    keyword.value = ''
    void load()
  }

  async function create(topic: string, panelSize: number): Promise<string> {
    const created = await createDiscussion({ topic: topic.trim(), panelSize })
    return created.id
  }

  async function remove(id: string): Promise<boolean> {
    try {
      await deleteDiscussion(id)
      await load()
      return true
    } catch (err) {
      error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
      return false
    }
  }

  return {
    items,
    nextCursor,
    loading,
    loadingMore,
    error,
    filter,
    keyword,
    isEmpty,
    isFiltered,
    load,
    loadMore,
    setFilter,
    setKeyword,
    clearFilters,
    create,
    remove,
  }
})
