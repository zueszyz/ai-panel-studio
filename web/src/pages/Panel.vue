<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
import { copy } from '@/lib/copy'
</script>

<template>
  <PagePlaceholder :title="copy.panel.title" :subtitle="copy.panel.claimHint" />
</template>
