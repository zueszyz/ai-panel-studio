<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
import { copy } from '@/lib/copy'
</script>

<template>
  <PagePlaceholder :title="copy.live.title" :subtitle="copy.live.notStartedBody" />
</template>
