<script setup lang="ts">
import PagePlaceholder from '@/components/PagePlaceholder.vue'
import { copy } from '@/lib/copy'
</script>

<template>
  <PagePlaceholder :title="copy.lobby.title" :subtitle="copy.lobby.subtitle" />
</template>
