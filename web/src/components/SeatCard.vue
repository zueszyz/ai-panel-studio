<script setup lang="ts">
import { computed } from 'vue'
import SeatAvatar from './SeatAvatar.vue'
import { copy } from '@/lib/copy'
import { seatColor } from '@/lib/seat'
import type { Seat } from '@/lib/types'

const props = defineProps<{
  seat: Seat
  canClaim: boolean
  busy: boolean
}>()

const emit = defineEmits<{ claim: [seatId: string]; revoke: [] }>()

const isHost = computed(() => props.seat.roleType === 'HOST')
const mine = computed(() => props.seat.claimedByMe)
/** 认领后统一用「你」的 emerald，避免同一席位在不同位置出现两种颜色 */
const style = computed(() => ({
  '--seat-color': seatColor(mine.value ? 'emerald' : props.seat.colorToken),
}))
const replaced = computed(() => props.seat.status === 'REPLACED_BY_USER')
</script>

<template>
  <article
    class="seat card"
    :class="{ 'seat--mine': mine, 'seat--host': isHost }"
    :style="style"
    data-testid="seat-card"
  >
    <header class="seat__head">
      <SeatAvatar :name="seat.name" :color-token="seat.colorToken" :you="mine" size="lg" />
      <div class="seat__ident">
        <h3 class="seat__name" :title="seat.name">{{ mine ? copy.panel.you : seat.name }}</h3>
        <p class="seat__title">{{ seat.title }}</p>
      </div>
    </header>

    <p class="seat__stance">{{ seat.stance }}</p>

    <ul class="seat__focus">
      <li v-for="item in seat.focus" :key="item" class="badge">{{ item }}</li>
    </ul>

    <p class="seat__voice">
      <span class="seat__voice-label">{{ copy.panel.voiceLabel }}</span>
      {{ seat.voice }}
    </p>

    <footer class="seat__foot">
      <span v-if="mine" class="seat__state">{{ copy.panel.claimed }}</span>
      <span v-else-if="replaced" class="seat__state">{{ copy.seat.status.REPLACED_BY_USER }}</span>
      <span v-else-if="isHost" class="seat__state muted">{{ copy.panel.claimHint }}</span>
      <span class="spacer"></span>
      <button
        v-if="mine"
        type="button"
        class="btn btn--sm"
        :disabled="busy"
        data-testid="revoke-seat"
        @click="emit('revoke')"
      >
        {{ copy.panel.revoke }}
      </button>
      <button
        v-else-if="canClaim && !isHost && !replaced"
        type="button"
        class="btn btn--sm seat__claim"
        :disabled="busy"
        data-testid="claim-seat"
        @click="emit('claim', seat.id)"
      >
        {{ busy ? copy.panel.claiming : copy.panel.claim }}
      </button>
    </footer>
  </article>
</template>

<style scoped>
.seat {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-4);
  padding-left: var(--space-4);
  border-left: var(--border-accent) solid var(--seat-color);
  animation: seat-in var(--dur-slow) var(--ease);
}

@keyframes seat-in {
  from {
    opacity: 0;
    transform: translateY(var(--space-2));
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.seat--mine {
  background: var(--card-hover);
}

.seat--host {
  background: var(--surface);
}

.seat__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.seat__ident {
  min-width: 0;
}

.seat__name {
  font-size: var(--text-lg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.seat__title {
  color: var(--text-secondary);
  font-size: var(--text-xs);
}

.seat__stance {
  font-size: var(--text-md);
  line-height: var(--leading-normal);
  color: var(--text);
}

.seat__focus {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
}

.seat__voice {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.seat__voice-label {
  margin-right: var(--space-2);
  color: var(--text-muted);
}

.seat__foot {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.seat__state {
  font-size: var(--text-xs);
  color: var(--seat-color);
}

.seat__claim {
  border-color: var(--seat-color);
  color: var(--seat-color);
}

.seat__claim:hover:not(:disabled) {
  background: color-mix(in srgb, var(--seat-color) 14%, transparent);
  border-color: var(--seat-color);
}
</style>
