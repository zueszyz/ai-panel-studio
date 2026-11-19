<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { copy } from '@/lib/copy'
import { relativeTime } from '@/lib/format'
import { useMinutesStore } from '@/stores/minutes'
import type { MinuteSectionKey } from '@/lib/types'

const props = defineProps<{ id: string }>()
const store = useMinutesStore()
const router = useRouter()

const ORDER: MinuteSectionKey[] = ['topic', 'claims', 'disputes', 'consensus', 'hypotheses', 'nextSteps']

const structured = computed(() => store.view?.structured ?? null)
const claims = computed(() => structured.value?.claims ?? [])
const disputes = computed(() => structured.value?.disputes ?? [])
const consensus = computed(() => structured.value?.consensus ?? [])
const hypotheses = computed(() => structured.value?.hypotheses ?? [])
const nextSteps = computed(() => structured.value?.nextSteps ?? [])

/** 流式到达但还没有落库版本时，直接把已到达的 Markdown 段渲染出来。 */
const streamedSections = computed(() =>
  ORDER.filter((key) => Boolean(store.sections[key])).map((key) => ({
    key,
    md: store.sections[key] as string,
  })),
)

const showResult = computed(() => Boolean(store.view) || store.hasSections)
const showEmpty = computed(() => !store.loading && !showResult.value && !store.generating)

onMounted(() => void store.init(props.id))
onBeforeUnmount(() => store.dispose())

function locate(messageId: string | undefined): void {
  if (!messageId) {
    return
  }
  void router.push({ path: `/discussions/${props.id}`, query: { messageId } })
}

function firstSource(ids: string[] | undefined): string | undefined {
  return ids && ids.length > 0 ? ids[0] : undefined
}

/** 空态下唯一的主操作：能生成就直接生成，否则回现场页先把讨论结束掉。 */
function onEmptyAction(): void {
  if (store.canGenerate) {
    void store.generate()
  } else {
    void router.push(`/discussions/${props.id}`)
  }
}
</script>

<template>
  <section class="page">
    <div class="page__inner">
      <RouterLink class="back" :to="`/discussions/${id}`">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
          <path d="M9.5 3.5L5 8l4.5 4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
        {{ copy.minutes.backToLive }}
      </RouterLink>

      <header class="page-head">
        <div class="headline">
          <p class="eyebrow">{{ copy.minutes.eyebrow }}</p>
          <div class="headline__meta">
            <StatusBadge :status="store.status" />
            <span class="badge badge--muted">{{ copy.minutes.title }}</span>
            <span v-if="store.interim" class="badge badge--accent" data-testid="interim-badge">
              {{ copy.minutes.interimBadge }}
            </span>
            <span v-if="store.offline" class="badge badge--muted">{{ copy.minutes.offline }}</span>
          </div>
          <h1 class="topic" :title="store.topic">{{ store.topic || copy.common.loading }}</h1>
        </div>
        <div class="headline__actions">
          <label v-if="store.versions.length > 1" class="version">
            <span class="version__label">{{ copy.minutes.versionLabel }}</span>
            <select
              class="field version__select"
              :value="store.selectedVersion ?? undefined"
              data-testid="minutes-version"
              @change="store.selectVersion(Number(($event.target as HTMLSelectElement).value))"
            >
              <option v-for="version in store.versions" :key="version" :value="version">
                {{ copy.minutes.versionOption(version) }}
              </option>
            </select>
          </label>
          <button
            v-if="store.view"
            type="button"
            class="btn"
            :title="copy.minutes.copyHint"
            data-testid="copy-minutes"
            @click="store.copyMarkdown()"
          >
            {{ store.copied ? copy.minutes.copied : copy.minutes.copy }}
          </button>
          <button
            v-if="store.canGenerate"
            type="button"
            class="btn"
            :class="{ 'btn--primary': !store.view }"
            :disabled="store.generating"
            data-testid="generate-minutes"
            @click="store.generate()"
          >
            {{ store.generating ? copy.minutes.generating
              : store.isLive ? copy.minutes.generateInterim
                : store.view ? copy.minutes.regenerate : copy.minutes.generate }}
          </button>
        </div>
      </header>

      <p v-if="store.interim && store.hasSections" class="banner banner--info" data-testid="interim-hint">
        {{ copy.minutes.interimHint }}
      </p>
      <p v-if="store.offline" class="banner banner--info" data-testid="offline-hint">
        {{ copy.minutes.offlineHint }}
      </p>
      <p v-if="store.error" class="banner banner--error" data-testid="minutes-error">{{ store.error }}</p>
      <p v-if="store.generating" class="banner banner--info" data-testid="minutes-generating">
        {{ copy.minutes.generatingHint }}
      </p>

      <div v-if="store.loading" class="minutes-skeleton" aria-hidden="true">
        <div v-for="index in 3" :key="index" class="card skeleton-block">
          <div class="skeleton skeleton-block__title"></div>
          <div class="skeleton skeleton-block__line"></div>
          <div class="skeleton skeleton-block__line skeleton-block__line--short"></div>
        </div>
      </div>

      <EmptyState
        v-else-if="showEmpty"
        variant="empty"
        :title="store.notFound ? copy.minutes.empty : copy.minutes.errorTitle"
        :body="store.canGenerate ? copy.minutes.emptyBody : copy.minutes.needEndFirst"
        :action-label="store.canGenerate
          ? (store.isLive ? copy.minutes.generateInterim : copy.minutes.generate)
          : copy.minutes.backToLive"
        @action="onEmptyAction"
      />

      <template v-else-if="showResult">
        <!-- 已落库的版本：按结构化字段渲染，要点带溯源 -->
        <article v-if="store.view && structured" class="minutes" data-testid="minutes-content">
          <header class="minutes__meta">
            <span class="badge badge--accent">{{ copy.minutes.versionOption(store.view.version) }}</span>
            <span class="badge badge--muted">{{ copy.minutes.generatedAt(relativeTime(store.view.createdAt)) }}</span>
          </header>

          <section class="block">
            <h2 class="block__title">{{ copy.minutes.sections.topic }}</h2>
            <p class="block__goal">{{ structured.goal || structured.topic || store.topic }}</p>
          </section>

          <section class="block">
            <h2 class="block__title">{{ copy.minutes.sections.claims }}</h2>
            <ul v-if="claims.length" class="claim-list">
              <li v-for="claim in claims" :key="claim.seatId" class="claim">
                <div class="claim__head">
                  <span class="claim__name">{{ claim.name }}</span>
                  <button
                    v-if="firstSource(claim.sourceMessageIds)"
                    type="button"
                    class="trace"
                    :title="copy.minutes.traceHint"
                    data-testid="trace-claim"
                    @click="locate(firstSource(claim.sourceMessageIds))"
                  >
                    ↩ {{ copy.minutes.trace }}
                  </button>
                </div>
                <ul class="claim__points">
                  <li v-for="point in claim.points" :key="point">{{ point }}</li>
                </ul>
              </li>
            </ul>
            <p v-else class="block__empty">{{ copy.minutes.sectionEmpty }}</p>
          </section>

          <section class="block">
            <h2 class="block__title">{{ copy.minutes.sections.disputes }}</h2>
            <div v-if="disputes.length" class="dispute-list">
              <article v-for="dispute in disputes" :key="dispute.question" class="dispute">
                <h3 class="dispute__question">{{ dispute.question }}</h3>
                <dl class="dispute__sides">
                  <div class="dispute__side">
                    <dt>
                      {{ copy.minutes.sideA }}
                      <button
                        v-if="firstSource(dispute.sideA.sources)"
                        type="button"
                        class="trace"
                        :title="copy.minutes.traceHint"
                        @click="locate(firstSource(dispute.sideA.sources))"
                      >
                        ↩ {{ copy.minutes.trace }}
                      </button>
                    </dt>
                    <dd>{{ dispute.sideA.argument }}</dd>
                  </div>
                  <div class="dispute__side">
                    <dt>
                      {{ copy.minutes.sideB }}
                      <button
                        v-if="firstSource(dispute.sideB.sources)"
                        type="button"
                        class="trace"
                        :title="copy.minutes.traceHint"
                        @click="locate(firstSource(dispute.sideB.sources))"
                      >
                        ↩ {{ copy.minutes.trace }}
                      </button>
                    </dt>
                    <dd>{{ dispute.sideB.argument }}</dd>
                  </div>
                </dl>
                <p v-if="dispute.unresolvedBecause" class="dispute__unresolved">
                  {{ copy.minutes.unresolved }}：{{ dispute.unresolvedBecause }}
                </p>
              </article>
            </div>
            <p v-else class="block__empty">{{ copy.minutes.sectionEmpty }}</p>
          </section>

          <section class="block">
            <h2 class="block__title">{{ copy.minutes.sections.consensus }}</h2>
            <ul v-if="consensus.length" class="plain-list">
              <li v-for="item in consensus" :key="item">{{ item }}</li>
            </ul>
            <p v-else class="block__empty">{{ copy.minutes.sectionEmpty }}</p>
          </section>

          <section class="block">
            <h2 class="block__title">{{ copy.minutes.sections.hypotheses }}</h2>
            <ul v-if="hypotheses.length" class="plain-list">
              <li v-for="item in hypotheses" :key="item">{{ item }}</li>
            </ul>
            <p v-else class="block__empty">{{ copy.minutes.sectionEmpty }}</p>
          </section>

          <section class="block">
            <h2 class="block__title">{{ copy.minutes.sections.nextSteps }}</h2>
            <ul v-if="nextSteps.length" class="plain-list">
              <li v-for="step in nextSteps" :key="step.action">
                {{ step.action }}
                <span v-if="step.proposedBy" class="muted">· {{ copy.minutes.proposedBy(step.proposedBy) }}</span>
              </li>
            </ul>
            <p v-else class="block__empty">{{ copy.minutes.sectionEmpty }}</p>
          </section>
        </article>

        <!-- 生成中的中间态：分节到达即渲染 -->
        <article v-else class="minutes">
          <section v-for="section in streamedSections" :key="section.key" class="block">
            <pre class="block__md">{{ section.md }}</pre>
          </section>
          <div v-if="store.generating" class="card skeleton-block">
            <div class="skeleton skeleton-block__title"></div>
            <div class="skeleton skeleton-block__line"></div>
          </div>
        </article>
      </template>
    </div>
  </section>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  margin-bottom: var(--space-3);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}

.back:hover {
  color: var(--text);
}

.headline {
  min-width: 0;
}

.headline__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  flex-wrap: wrap;
}

.badge--muted {
  color: var(--text-muted);
}

.topic {
  font-size: var(--text-2xl);
  line-height: var(--leading-tight);
  max-width: var(--reading-max);
  margin-top: var(--space-1);
  text-wrap: balance;
}

.headline__actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.version {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.version__select {
  height: var(--space-6);
  padding: 0 var(--space-2);
  font-size: var(--text-sm);
}

.banner {
  margin-bottom: var(--space-4);
}

.minutes {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: var(--content-max);
}

.minutes__meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.block {
  position: relative;
  padding: var(--space-5) var(--space-5) var(--space-5) var(--space-6);
  background-color: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
}

.block::before {
  content: '';
  position: absolute;
  left: var(--space-4);
  top: var(--space-5);
  bottom: var(--space-5);
  width: 2px;
  border-radius: var(--radius-pill);
  background: var(--primary-line);
  opacity: 0.5;
}

.block__title {
  font-size: var(--text-md);
  color: var(--text-secondary);
  margin-bottom: var(--space-3);
}

.block__goal {
  font-size: var(--text-md);
  line-height: var(--leading-loose);
  white-space: pre-wrap;
}

.block__empty {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.block__md {
  margin: 0;
  font-family: inherit;
  font-size: var(--text-md);
  line-height: var(--leading-loose);
  white-space: pre-wrap;
  color: var(--text);
}

.claim-list,
.plain-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.plain-list {
  padding-left: var(--space-4);
  list-style: disc;
  color: var(--text-secondary);
  font-size: var(--text-md);
  line-height: var(--leading-loose);
}

.claim__head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.claim__name {
  font-weight: 600;
}

.claim__points {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  margin: var(--space-2) 0 0;
  padding-left: var(--space-4);
  list-style: disc;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: var(--leading-loose);
}

.claim__points li::marker {
  color: var(--primary-line);
}

.trace {
  padding: 0 var(--space-2);
  border-radius: var(--radius-pill);
  border: 1px solid var(--border);
  color: var(--text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
  transition:
    color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease);
}

.trace:hover {
  color: var(--primary);
  border-color: var(--primary);
}

.dispute-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.dispute {
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-left: 2px solid var(--seat-amber);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.dispute__question {
  font-size: var(--text-sm);
  margin-bottom: var(--space-2);
}

.dispute__sides {
  display: grid;
  gap: var(--space-2);
  margin: 0;
}

.dispute__side dt {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.dispute__side dd {
  margin: var(--space-1) 0 0;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: var(--leading-normal);
}

.dispute__unresolved {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.minutes-skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.skeleton-block {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-5);
}

.skeleton-block__title {
  height: var(--text-md);
  width: 30%;
}

.skeleton-block__line {
  height: var(--skeleton-line);
  width: 100%;
}

.skeleton-block__line--short {
  width: 62%;
}

@media (min-width: 1024px) {
  .dispute__sides {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 767px) {
  .topic {
    font-size: var(--text-lg);
  }

  .block {
    padding: var(--space-4);
  }
}
</style>
