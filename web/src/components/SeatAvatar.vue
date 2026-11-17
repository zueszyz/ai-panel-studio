<script setup lang="ts">
import { computed } from 'vue'
import { initialOf, seatColor } from '@/lib/seat'

const props = withDefaults(
  defineProps<{
    name: string
    colorToken?: string
    size?: 'sm' | 'md' | 'lg'
    you?: boolean
    muted?: boolean
  }>(),
  { colorToken: 'slate', size: 'md', you: false, muted: false },
)

const style = computed(() => ({ '--seat-color': seatColor(props.colorToken) }))
const label = computed(() => (props.you ? '你' : initialOf(props.name)))
</script>

<template>
  <span
    class="avatar"
    :class="[`avatar--${size}`, { 'avatar--you': you, 'avatar--muted': muted }]"
    :style="style"
    :title="name"
  ><span class="avatar__text">{{ label }}</span></span>
</template>

<style scoped>
.avatar {
  display: inline-grid;
  place-items: center;
  width: var(--avatar-size);
  height: var(--avatar-size);
  border-radius: var(--radius-pill);
  background: var(--surface);
  border: 1px solid var(--seat-color);
  color: var(--seat-color);
  flex: none;
  user-select: none;
}

.avatar__text {
  font-size: var(--text-sm);
  font-weight: 600;
  line-height: 1;
}

.avatar--sm {
  width: var(--space-5);
  height: var(--space-5);
}

.avatar--sm .avatar__text {
  font-size: var(--text-xs);
}

.avatar--lg {
  width: var(--avatar-size-lg);
  height: var(--avatar-size-lg);
}

.avatar--lg .avatar__text {
  font-size: var(--text-md);
}

.avatar--you {
  background: var(--seat-emerald);
  border-color: var(--seat-emerald);
  color: var(--bg);
}

.avatar--muted {
  opacity: 0.45;
}
</style>
