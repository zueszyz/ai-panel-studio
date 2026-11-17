<script setup lang="ts">
import { copy } from '@/lib/copy'

defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <section class="page">
    <div class="page__inner">
      <header class="page-head">
        <div>
          <h1 class="page-title">{{ title }}</h1>
          <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
        </div>
      </header>
      <div class="placeholder card">
        <h2 class="placeholder__title">{{ copy.common.placeholderTitle }}</h2>
        <p class="placeholder__body">{{ copy.common.placeholderBody }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.placeholder {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-6);
  border-style: dashed;
  background: var(--surface);
}

.placeholder__title {
  font-size: var(--text-lg);
}

.placeholder__body {
  color: var(--text-secondary);
  font-size: var(--text-sm);
}
</style>
