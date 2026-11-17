<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { copy } from '@/lib/copy'
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#main">{{ copy.app.skipToContent }}</a>
    <header class="app-header">
      <RouterLink class="brand" to="/">
        <span class="brand__mark" aria-hidden="true">
          <svg
            width="20"
            height="20"
            viewBox="0 0 20 20"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
          >
            <circle cx="10" cy="10" r="7" />
            <circle cx="10" cy="3.6" r="1.6" fill="currentColor" stroke="none" />
            <circle cx="15.6" cy="13.4" r="1.6" fill="currentColor" stroke="none" />
            <circle cx="4.4" cy="13.4" r="1.6" fill="currentColor" stroke="none" />
          </svg>
        </span>
        <span class="brand__text">
          <span class="brand__name">{{ copy.app.name }}</span>
          <span class="brand__tagline">{{ copy.app.tagline }}</span>
        </span>
      </RouterLink>
      <nav class="app-nav" aria-label="主导航">
        <RouterLink class="nav-link" to="/">{{ copy.nav.lobby }}</RouterLink>
      </nav>
    </header>
    <main id="main" class="app-main">
      <RouterView />
    </main>
  </div>
</template>
