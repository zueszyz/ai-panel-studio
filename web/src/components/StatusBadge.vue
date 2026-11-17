<script setup lang="ts">
import { computed } from 'vue'
import { copy } from '@/lib/copy'
import { statusDotColor } from '@/lib/seat'
import type { DiscussionStatus } from '@/lib/types'

const props = defineProps<{ status: DiscussionStatus }>()

const label = computed(() => copy.status[props.status] ?? props.status)
const dotStyle = computed(() => ({ '--dot-color': statusDotColor(props.status) }))
</script>

<template>
  <span class="badge status-badge" :class="{ 'status-badge--live': status === 'LIVE' }">
    <span class="dot" :style="dotStyle" aria-hidden="true"></span>
    {{ label }}
  </span>
</template>

<style scoped>
.status-badge .dot {
  background: var(--dot-color);
}

.status-badge--live .dot {
  animation: pulse 1.6s var(--ease) infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    box-shadow: 0 0 0 0 rgba(74, 222, 128, 0.28);
  }
  50% {
    opacity: 0.7;
    box-shadow: 0 0 0 var(--space-1) rgba(74, 222, 128, 0);
  }
}
</style>
