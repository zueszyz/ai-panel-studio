<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { copy } from '@/lib/copy'

const props = defineProps<{
  open: boolean
  title: string
  description?: string
}>()

const emit = defineEmits<{ close: [] }>()

const panel = ref<HTMLElement | null>(null)

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    emit('close')
  }
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      document.body.style.overflow = 'hidden'
      window.addEventListener('keydown', onKeydown)
      await nextTick()
      panel.value?.querySelector<HTMLElement>('textarea, input, button')?.focus()
    } else {
      document.body.style.overflow = ''
      window.removeEventListener('keydown', onKeydown)
    }
  },
)

onBeforeUnmount(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div v-if="open" class="backdrop" role="presentation" @click.self="emit('close')">
        <div
          ref="panel"
          class="dialog"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
          :aria-describedby="description ? 'dialog-desc' : undefined"
        >
          <header class="dialog__head">
            <div>
              <h2 class="dialog__title">{{ title }}</h2>
              <p v-if="description" id="dialog-desc" class="dialog__desc">{{ description }}</p>
            </div>
            <button
              type="button"
              class="dialog__close"
              :aria-label="copy.common.close"
              @click="emit('close')"
            >
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path
                  d="M4 4l8 8M12 4l-8 8"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                />
              </svg>
            </button>
          </header>
          <div class="dialog__body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="dialog__foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: grid;
  place-items: center;
  padding: var(--space-4);
  background: var(--overlay);
  backdrop-filter: blur(2px);
}

.dialog {
  width: 100%;
  max-width: var(--reading-max);
  max-height: calc(100dvh - var(--space-7));
  display: flex;
  flex-direction: column;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-3);
  overflow: hidden;
}

.dialog__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-5) var(--space-5) var(--space-3);
}

.dialog__title {
  font-size: var(--text-lg);
}

.dialog__desc {
  margin-top: var(--space-1);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.dialog__close {
  display: grid;
  place-items: center;
  width: var(--space-6);
  height: var(--space-6);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  transition:
    background var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.dialog__close:hover {
  background: var(--card-hover);
  color: var(--text);
}

.dialog__body {
  padding: 0 var(--space-5) var(--space-4);
  overflow-y: auto;
}

.dialog__foot {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5) var(--space-5);
  border-top: 1px solid var(--border);
}

.dialog-enter-active,
.dialog-leave-active {
  transition: opacity var(--dur-base) var(--ease);
}

.dialog-enter-from,
.dialog-leave-to {
  opacity: 0;
}

.dialog-enter-active .dialog {
  transition: transform var(--dur-base) var(--ease);
}

.dialog-enter-from .dialog {
  transform: translateY(var(--space-2));
}
</style>
