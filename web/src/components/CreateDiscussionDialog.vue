<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppDialog from './AppDialog.vue'
import { errorMessage, copy } from '@/lib/copy'
import { ApiError } from '@/lib/api'
import { useLobbyStore } from '@/stores/lobby'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; created: [id: string] }>()

const lobby = useLobbyStore()
const topic = ref('')
const panelSize = ref(5)
const submitting = ref(false)
const error = ref<string | null>(null)

const length = computed(() => topic.value.trim().length)
const tooShort = computed(() => length.value > 0 && length.value < 8)
const tooLong = computed(() => length.value > 200)
const valid = computed(() => length.value >= 8 && length.value <= 200)
const sizeOptions = [3, 4, 5, 6]

watch(
  () => props.open,
  (open) => {
    if (open) {
      topic.value = ''
      panelSize.value = 5
      error.value = null
      submitting.value = false
    }
  },
)

async function submit(): Promise<void> {
  if (!valid.value || submitting.value) {
    return
  }
  submitting.value = true
  error.value = null
  try {
    const id = await lobby.create(topic.value, panelSize.value)
    emit('created', id)
  } catch (err) {
    error.value = err instanceof ApiError ? errorMessage(err.code, err.message) : copy.errors.network
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <AppDialog
    :open="open"
    :title="copy.lobby.createTitle"
    :description="copy.lobby.createHint"
    @close="emit('close')"
  >
    <form class="form" @submit.prevent="submit">
      <label class="form__label" for="topic-input">{{ copy.lobby.topicLabel }}</label>
      <textarea
        id="topic-input"
        v-model="topic"
        class="field form__topic"
        :class="{ 'field--invalid': tooLong }"
        rows="3"
        :placeholder="copy.lobby.topicPlaceholder"
        :aria-describedby="'topic-hint'"
        data-testid="topic-input"
      ></textarea>
      <div id="topic-hint" class="form__meta">
        <span v-if="tooShort" class="form__hint form__hint--warn">{{ copy.lobby.topicTooShort }}</span>
        <span v-else-if="tooLong" class="form__hint form__hint--warn">{{ copy.lobby.topicTooLong }}</span>
        <span v-else class="form__hint">{{ copy.lobby.panelSizeHint }}</span>
        <span class="form__count mono">{{ copy.common.charCount(length, 200) }}</span>
      </div>

      <fieldset class="form__sizes">
        <legend class="form__label">{{ copy.lobby.panelSizeLabel }}</legend>
        <div class="segmented">
          <button
            v-for="size in sizeOptions"
            :key="size"
            type="button"
            class="segmented__item"
            :class="{ 'segmented__item--active': panelSize === size }"
            :aria-pressed="panelSize === size"
            @click="panelSize = size"
          >
            {{ size }} 人
          </button>
        </div>
      </fieldset>

      <p v-if="error" class="form__error">{{ error }}</p>
    </form>

    <template #footer>
      <button type="button" class="btn btn--ghost" @click="emit('close')">{{ copy.common.cancel }}</button>
      <button
        type="button"
        class="btn btn--primary"
        :disabled="!valid || submitting"
        data-testid="submit-discussion"
        @click="submit"
      >
        {{ submitting ? copy.lobby.submitting : copy.lobby.submit }}
      </button>
    </template>
  </AppDialog>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.form__label {
  color: var(--text-secondary);
  font-size: var(--text-sm);
  padding: 0;
}

.form__topic {
  min-height: calc(var(--space-7) * 2);
  resize: vertical;
}

.form__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  min-height: var(--space-5);
}

.form__hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.form__hint--warn {
  color: var(--danger);
}

.form__count {
  color: var(--text-muted);
  white-space: nowrap;
}

.form__sizes {
  margin: var(--space-3) 0 0;
  padding: 0;
  border: none;
}

.segmented {
  display: inline-flex;
  gap: var(--space-1);
  margin-top: var(--space-2);
  padding: var(--space-1);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.segmented__item {
  padding: var(--space-2) var(--space-4);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  transition:
    background var(--dur-fast) var(--ease),
    color var(--dur-fast) var(--ease);
}

.segmented__item:hover {
  color: var(--text);
  background: var(--card);
}

.segmented__item--active {
  background: var(--primary-soft);
  color: var(--text);
}

.form__error {
  margin-top: var(--space-2);
  color: var(--danger);
  font-size: var(--text-sm);
}
</style>
