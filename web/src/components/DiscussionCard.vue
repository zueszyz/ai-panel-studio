<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import SeatAvatar from './SeatAvatar.vue'
import StatusBadge from './StatusBadge.vue'
import { copy } from '@/lib/copy'
import { relativeTime, turnLabel } from '@/lib/format'
import { statusDotColor } from '@/lib/seat'
import type { DiscussionSummary } from '@/lib/types'

const props = defineProps<{ discussion: DiscussionSummary }>()
const emit = defineEmits<{ delete: [discussion: DiscussionSummary] }>()

/** 筹备阶段去组局页（可加入 / 可开始），已开场才进现场。 */
const target = computed(() =>
  props.discussion.status === 'DRAFT' || props.discussion.status === 'PANEL_READY'
    ? `/discussions/${props.discussion.id}/panel`
    : `/discussions/${props.discussion.id}`,
)

const joinable = computed(() =>
  (props.discussion.status === 'DRAFT' || props.discussion.status === 'PANEL_READY')
  && props.discussion.openSeatCount > 0,
)

const accentStyle = computed(() => ({ '--status-color': statusDotColor(props.discussion.status) }))
</script>

<template>
  <RouterLink
    :to="target"
    class="card card--interactive discussion"
    :class="`discussion--${discussion.status.toLowerCase()}`"
    :style="accentStyle"
    data-testid="discussion-card"
  >
    <header class="discussion__head">
      <StatusBadge :status="discussion.status" />
      <span v-if="joinable" class="badge badge--accent" data-testid="joinable-badge">
        {{ copy.lobby.joinable }}
      </span>
      <span v-if="discussion.turnIndex > 0" class="badge">{{ turnLabel(discussion.turnIndex) }}</span>
      <span class="spacer"></span>
      <time class="discussion__time" :datetime="discussion.updatedAt">
        {{ relativeTime(discussion.updatedAt) }}
      </time>
      <button
        v-if="discussion.createdByMe"
        type="button"
        class="discussion__delete"
        data-testid="delete-discussion"
        :aria-label="copy.lobby.delete"
        :title="copy.lobby.delete"
        @click.prevent.stop="emit('delete', discussion)"
      >
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path
            d="M3 4.5h10M6.5 4.5V3h3v1.5M4.5 4.5l.6 8h5.8l.6-8M7 7v3.5M9 7v3.5"
            stroke="currentColor"
            stroke-width="1.3"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </header>

    <h3 class="discussion__topic truncate-2" :title="discussion.topic">{{ discussion.topic }}</h3>

    <footer class="discussion__foot">
      <div class="discussion__row">
        <div class="discussion__seats">
          <SeatAvatar
            v-for="seat in discussion.seats.slice(0, 4)"
            :key="seat.id"
            :name="seat.name"
            :color-token="seat.colorToken"
            :you="seat.claimedByMe"
            :muted="seat.status === 'RETIRED'"
            size="sm"
          />
          <span class="discussion__count">
            {{ joinable ? copy.lobby.openSeats(discussion.openSeatCount)
              : copy.lobby.seatCount(discussion.seats.length) }}
          </span>
        </div>
        <span class="discussion__go" aria-hidden="true">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M6 3.5L10.5 8 6 12.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </span>
      </div>

      <p v-if="discussion.lastMessage" class="discussion__last">
        <span class="discussion__speaker">{{ discussion.lastMessage.speaker }}</span>
        <span class="discussion__preview truncate-2">{{ discussion.lastMessage.preview }}</span>
      </p>
      <p v-else class="discussion__last muted">{{ copy.live.emptyFlow }}</p>
    </footer>
  </RouterLink>
</template>

<style scoped>
.discussion {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-4) var(--space-4) var(--space-5);
  min-height: calc(var(--space-7) * 4);
  overflow: hidden;
}

/* 状态色只出现在左侧细条与悬停光晕上，不抢卡片内容的层级 */
.discussion::before {
  content: '';
  position: absolute;
  left: 0;
  top: var(--space-4);
  bottom: var(--space-4);
  width: var(--border-accent);
  border-radius: 0 var(--radius-pill) var(--radius-pill) 0;
  background: var(--status-color);
  opacity: 0.55;
  transition: opacity var(--dur-fast) var(--ease);
}

.discussion:hover::before {
  opacity: 1;
}

.discussion__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.discussion__time {
  color: var(--text-muted);
  font-size: var(--text-xs);
  white-space: nowrap;
}

/* 删除入口只在创建者的卡片上出现；点击阻止 RouterLink 跳转 */
.discussion__delete {
  display: grid;
  place-items: center;
  width: var(--space-6);
  height: var(--space-6);
  flex: none;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  color: var(--text-muted);
  opacity: 0;
  transition:
    opacity var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease),
    background var(--dur-fast) var(--ease);
}

.discussion:hover .discussion__delete,
.discussion__delete:focus-visible {
  opacity: 1;
}

.discussion__delete:hover {
  color: var(--danger);
  background: var(--danger-soft);
}

.discussion__topic {
  font-size: var(--text-lg);
  line-height: var(--leading-normal);
  color: var(--text);
}

.discussion__foot {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.discussion__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.discussion__seats {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.discussion__seats > * + * {
  margin-left: calc(var(--space-1) * -1);
}

.discussion__seats .discussion__count {
  margin-left: var(--space-3);
}

.discussion__count {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.discussion__go {
  display: grid;
  place-items: center;
  width: var(--space-5);
  height: var(--space-5);
  border-radius: var(--radius-pill);
  color: var(--text-muted);
  opacity: 0;
  transform: translateX(calc(var(--space-1) * -1));
  transition:
    opacity var(--dur-fast) var(--ease),
    transform var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.discussion:hover .discussion__go {
  opacity: 1;
  transform: none;
  color: var(--status-color);
}

.discussion__last {
  display: flex;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--text-secondary);
  line-height: var(--leading-normal);
}

.discussion__speaker {
  color: var(--text-muted);
  white-space: nowrap;
  flex: none;
}

.discussion__preview {
  min-width: 0;
}
</style>
