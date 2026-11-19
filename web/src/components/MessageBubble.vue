<script setup lang="ts">
import { computed } from 'vue'
import SeatAvatar from './SeatAvatar.vue'
import { copy } from '@/lib/copy'
import { relativeTime } from '@/lib/format'
import { seatColor } from '@/lib/seat'
import type { LiveMessage } from '@/lib/types'

const props = defineProps<{
  message: LiveMessage
  highlighted?: boolean
  showRound?: boolean
}>()

const emit = defineEmits<{ locate: [messageId: string]; retry: [messageId: string] }>()

const mine = computed(() => props.message.role === 'USER')
const isDigest = computed(() => props.message.kind === 'DIGEST')
const isOpening = computed(() => props.message.kind === 'OPENING')
const streaming = computed(() => props.message.status === 'STREAMING' || props.message.status === 'PENDING')
const failed = computed(() => props.message.status === 'FAILED')
const partial = computed(() => props.message.status === 'PARTIAL')
const style = computed(() => ({ '--seat-color': seatColor(props.message.seatColorToken) }))

const meta = computed(() => {
  const round = props.message.roundNo > 0 ? `第 ${props.message.roundNo} 回合` : '开场'
  return `${round} · ${relativeTime(props.message.createdAt)}`
})

const kindLabel = computed(() => {
  if (props.message.kind === 'DIGEST') {
    return copy.live.digestLabel
  }
  if (props.message.kind === 'OPENING') {
    return copy.live.openingLabel
  }
  return ''
})
</script>

<template>
  <article
    class="bubble"
    :class="{
      'bubble--mine': mine,
      'bubble--digest': isDigest,
      'bubble--opening': isOpening,
      'bubble--failed': failed,
      'bubble--highlight': highlighted,
    }"
    :style="style"
    :data-testid="'message-bubble'"
    :data-message-id="message.id"
    :data-name="mine ? copy.panel.you : message.seatName"
    :data-status="message.status"
  >
    <header class="bubble__head">
      <SeatAvatar v-if="!mine" :name="message.seatName" :color-token="message.seatColorToken" size="sm" />
      <span class="bubble__name">{{ mine ? copy.panel.you : message.seatName }}</span>
      <span v-if="kindLabel" class="badge bubble__kind">{{ kindLabel }}</span>
      <span class="bubble__meta">{{ meta }}</span>
    </header>

    <button
      v-if="message.quoteMessageId"
      type="button"
      class="bubble__quote"
      data-testid="quote-badge"
      @click="emit('locate', message.quoteMessageId!)"
    >
      <span aria-hidden="true">↩</span>
      {{ copy.live.quotePrefix }}{{ message.quotePreview || copy.live.quoteMissing }}
    </button>

    <p v-if="message.content || streaming" class="bubble__content" :class="{ 'bubble__content--streaming': streaming }">
      {{ message.content }}<span v-if="streaming" class="bubble__caret" aria-hidden="true"></span>
    </p>
    <p v-else-if="failed" class="bubble__content bubble__content--empty">
      {{ message.error || copy.live.messageEmpty }}
    </p>

    <footer v-if="failed || partial || message.attempt > 1" class="bubble__foot">
      <span v-if="failed && message.content" class="bubble__error">{{ copy.live.messageFailed }}</span>
      <span v-else-if="partial" class="bubble__error">{{ copy.live.messagePartial }}</span>
      <span v-if="message.attempt > 1" class="bubble__attempt">{{ copy.live.attempt(message.attempt) }}</span>
      <span class="spacer"></span>
      <button v-if="failed" type="button" class="btn btn--sm" @click="emit('retry', message.id)">
        {{ copy.common.retry }}
      </button>
    </footer>
  </article>
</template>

<style scoped>
.bubble {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background-color: var(--card);
  border-left: var(--border-accent) solid var(--seat-color);
  scroll-margin: var(--space-5);
  transition:
    background-color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease),
    box-shadow var(--dur-base) var(--ease);
}

.bubble:hover {
  background-color: var(--card-hover);
  border-color: var(--border-strong);
}

.bubble--opening {
  background-color: var(--surface);
}

/* 失败且没有正文时收紧间距，避免卡片中间空出一大块 */
.bubble--failed {
  gap: var(--space-1);
}

.bubble--digest {
  background-color: var(--surface);
  border-style: dashed;
}

.bubble--mine {
  margin-left: auto;
  max-width: min(100%, 42rem);
  background-color: color-mix(in srgb, var(--seat-emerald) 8%, var(--card));
  border-color: color-mix(in srgb, var(--seat-emerald) 32%, var(--border));
}

.bubble--mine:hover {
  background-color: color-mix(in srgb, var(--seat-emerald) 11%, var(--card-hover));
}

.bubble--highlight {
  box-shadow: 0 0 0 2px var(--primary);
  background-color: var(--card-hover);
}

.bubble__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.bubble__name {
  color: var(--text);
  font-size: var(--text-sm);
  font-weight: 600;
}

.bubble__kind {
  color: var(--seat-color);
  border-color: var(--seat-color);
  background: transparent;
}

.bubble__meta {
  margin-left: auto;
}

.bubble__quote {
  display: block;
  width: 100%;
  text-align: left;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  border: none;
  background: var(--surface);
  color: var(--text-secondary);
  font-size: var(--text-xs);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bubble__quote:hover {
  color: var(--text);
  background: var(--card-hover);
}

.bubble__content {
  font-size: var(--text-md);
  line-height: var(--leading-relaxed);
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble__content--empty {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.bubble__caret {
  display: inline-block;
  width: var(--space-1);
  height: 1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--seat-color);
  animation: caret-blink 1s steps(2, start) infinite;
}

@keyframes caret-blink {
  to {
    visibility: hidden;
  }
}

.bubble__foot {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--border);
}

.bubble__error {
  font-size: var(--text-xs);
  color: var(--danger);
}

.bubble__attempt {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--text-muted);
}
</style>
