<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    body: string
    actionLabel?: string
    variant?: 'empty' | 'error'
  }>(),
  { actionLabel: '', variant: 'empty' },
)

const emit = defineEmits<{ action: [] }>()
</script>

<template>
  <div class="empty" :class="`empty--${variant}`">
    <svg class="empty__art" width="64" height="48" viewBox="0 0 64 48" fill="none" aria-hidden="true">
      <ellipse cx="32" cy="26" rx="24" ry="12" stroke="currentColor" stroke-width="1.2" />
      <circle cx="20" cy="22" r="4" stroke="currentColor" stroke-width="1.2" />
      <circle cx="44" cy="30" r="3" stroke="currentColor" stroke-width="1.2" />
      <path v-if="variant === 'error'" d="M24 40l4 4 6-8" stroke="currentColor" stroke-width="1.4" />
      <path v-else d="M32 8v6M28 11h8" stroke="currentColor" stroke-width="1.2" />
    </svg>
    <h2 class="empty__title">{{ title }}</h2>
    <p class="empty__body">{{ body }}</p>
    <button v-if="actionLabel" type="button" class="btn" @click="emit('action')">
      {{ actionLabel }}
    </button>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-7) var(--space-4);
  text-align: center;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.empty__art {
  color: var(--text-muted);
}

.empty--error .empty__art {
  color: var(--danger);
}

.empty__title {
  font-size: var(--text-lg);
}

.empty__body {
  max-width: var(--copy-max);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}
</style>
