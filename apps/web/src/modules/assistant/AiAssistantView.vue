<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  AiAssistantApiError,
  aiAssistantApi,
  type AiAssistantMode,
  type AiAssistantSource,
  type AiAssistantStatus,
  type AiAssistantTurn,
} from '../../shared/api/aiAssistantApi'
import { knowledgeBaseApi } from '../../shared/api/knowledgeBaseApi'
import { normalizedHelpRoutePath } from '../help/pageHelp'

interface ConversationMessage extends AiAssistantTurn {
  id: string
  mode?: AiAssistantMode
  model?: string
  sources?: AiAssistantSource[]
}

const STORAGE_KEY = 'qherp-ai-assistant-session-v2'
const MAX_STORED_MESSAGES = 12
const suggestions = [
  '采购请购审批通过后，下一步怎么操作？',
  '销售订单确认前需要满足哪些条件？',
  '物料导入模板应该怎么填写？',
  '生产工单从创建到完工入库是什么流程？',
]

const route = useRoute()
const question = ref('')
const messages = ref<ConversationMessage[]>(restoreMessages())
const status = ref<AiAssistantStatus | null>(null)
const contextPageName = ref('')
const loading = ref(false)
const statusLoading = ref(true)
const error = ref('')
const collapsed = ref(true)
const maximized = ref(false)
const sourceDialogVisible = ref(false)
const activeSources = ref<AiAssistantSource[]>([])

const contextRoute = computed(() => normalizedHelpRoutePath(route))
const canSubmit = computed(() => question.value.trim().length > 0 && question.value.trim().length <= 500 && !loading.value)

function restoreMessages(): ConversationMessage[] {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as ConversationMessage[]
    return Array.isArray(parsed) ? parsed.slice(-MAX_STORED_MESSAGES) : []
  } catch {
    return []
  }
}

function persistMessages() {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(messages.value.slice(-MAX_STORED_MESSAGES)))
}

function nextId(role: string) {
  return [role, Date.now(), Math.random().toString(16).slice(2)].join('-')
}

function apiErrorMessage(caught: unknown) {
  return caught instanceof AiAssistantApiError ? caught.message : 'AI助手暂时不可用，请稍后再试'
}

async function loadStatus() {
  statusLoading.value = true
  try {
    status.value = await aiAssistantApi.status()
  } catch (caught) {
    error.value = apiErrorMessage(caught)
  } finally {
    statusLoading.value = false
  }
}

async function loadContextPageName() {
  try {
    const page = await knowledgeBaseApi.help.byRoute(contextRoute.value, { page: 1, pageSize: 1 })
    const names = page.items[0]?.pageNames?.split(/\r?\n/).filter(Boolean) ?? []
    contextPageName.value = names.length > 0 ? names.join('、') : '当前业务页面'
  } catch {
    contextPageName.value = '当前业务页面'
  }
}

async function submit() {
  const content = question.value.trim()
  if (!canSubmit.value) return
  error.value = ''
  question.value = ''
  const history = messages.value.slice(-6).map(({ role, content: turnContent }) => ({ role, content: turnContent }))
  messages.value.push({ id: nextId('user'), role: 'user', content })
  persistMessages()
  loading.value = true
  try {
    const response = await aiAssistantApi.ask({
      question: content,
      routePath: contextRoute.value || undefined,
      pageName: contextPageName.value || undefined,
      history,
    })
    messages.value.push({
      id: nextId('assistant'),
      role: 'assistant',
      content: response.answer,
      mode: response.mode,
      model: response.model,
      sources: response.sources,
    })
    persistMessages()
  } catch (caught) {
    error.value = apiErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

async function useSuggestion(suggestion: string) {
  question.value = suggestion
  await submit()
}

function clearConversation() {
  messages.value = []
  error.value = ''
  sessionStorage.removeItem(STORAGE_KEY)
}

function sourceTypeLabel(type: AiAssistantSource['type']) {
  return type === 'MANUAL' ? '操作手册' : '系统逻辑'
}

function modeLabel(mode?: AiAssistantMode) {
  return mode === 'MINIMAX' ? 'MiniMax 回答' : '知识库回答'
}

function expandWidget() {
  collapsed.value = false
}

function collapseWidget() {
  collapsed.value = true
  maximized.value = false
}

function toggleMaximized() {
  maximized.value = !maximized.value
}

function showSources(sources: AiAssistantSource[]) {
  activeSources.value = sources
  sourceDialogVisible.value = true
}

onMounted(() => {
  void loadStatus()
  void loadContextPageName()
})

watch(contextRoute, () => {
  void loadContextPageName()
})
</script>

<template>
  <div class="assistant-root">
    <button
      v-if="collapsed"
      type="button"
      class="assistant-launcher"
      data-test="assistant-launcher"
      aria-label="打开 AI 助手"
      title="打开 AI 助手"
      @click="expandWidget"
    >
      <span class="assistant-launcher__mark">AI</span>
      <span class="assistant-launcher__label">智能助手</span>
    </button>

    <section
      v-else
      class="assistant-widget"
      :class="{ 'assistant-widget--maximized': maximized }"
      aria-label="AI 助手对话框"
    >
      <header class="assistant-header">
        <div class="assistant-header__identity">
          <span class="assistant-header__mark">AI</span>
          <div>
            <h2>AI 助手</h2>
            <p>{{ statusLoading ? '正在连接服务' : (status?.currentMode || '系统操作咨询') }}</p>
          </div>
        </div>
        <div class="assistant-header__actions">
          <button
            type="button"
            class="assistant-icon-button"
            :aria-label="maximized ? '恢复小窗' : '全屏显示'"
            :title="maximized ? '恢复小窗' : '全屏显示'"
            @click="toggleMaximized"
          >
            <span aria-hidden="true">{{ maximized ? '↙' : '□' }}</span>
          </button>
          <button
            type="button"
            class="assistant-icon-button"
            aria-label="收起 AI 助手"
            title="收起"
            @click="collapseWidget"
          >
            <span aria-hidden="true">−</span>
          </button>
        </div>
      </header>

      <div class="assistant-context-bar">
        <span>当前页面</span>
        <strong>{{ contextPageName || '页面名称加载中' }}</strong>
        <el-button link type="primary" :disabled="messages.length === 0" @click="clearConversation">清空会话</el-button>
      </div>

      <el-alert
        v-if="error"
        class="assistant-alert"
        type="error"
        :title="error"
        :closable="false"
        show-icon
      />

      <main class="assistant-conversation" aria-live="polite">
        <div v-if="messages.length === 0" class="assistant-empty">
          <div class="assistant-empty__mark">AI</div>
          <h3>咨询系统操作问题</h3>
          <p>可咨询页面操作、业务前置条件、状态含义和跨模块流程。助手不会替你操作系统。</p>
          <div class="assistant-suggestions">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion"
              type="button"
              class="assistant-suggestion"
              data-test="assistant-suggestion"
              @click="useSuggestion(suggestion)"
            >
              {{ suggestion }}
            </button>
          </div>
        </div>

        <article
          v-for="message in messages"
          :key="message.id"
          class="assistant-message"
          :class="['assistant-message', 'assistant-message--' + message.role]"
        >
          <div class="assistant-message__label">
            <strong>{{ message.role === 'user' ? '你' : 'AI 助手' }}</strong>
            <span v-if="message.role === 'assistant'">{{ modeLabel(message.mode) }}</span>
          </div>
          <div class="assistant-message__content">{{ message.content }}</div>
          <button
            v-if="message.sources?.length"
            type="button"
            class="assistant-source-trigger"
            @click="showSources(message.sources)"
          >
            查看回答依据（{{ message.sources.length }}）
          </button>
        </article>

        <div v-if="loading" class="assistant-loading">
          <span /><span /><span />
          <p>正在检索系统知识并组织回答</p>
        </div>
      </main>

      <footer class="assistant-composer">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          show-word-limit
          placeholder="请输入系统操作问题"
          aria-label="咨询问题"
          @keydown.enter.exact.prevent="submit"
        />
        <div class="assistant-composer__footer">
          <span>Enter 发送，Shift + Enter 换行</span>
          <el-button type="primary" :loading="loading" :disabled="!canSubmit" data-test="assistant-submit" @click="submit">
            发送
          </el-button>
        </div>
      </footer>
    </section>

    <el-dialog
      v-model="sourceDialogVisible"
      title="回答依据"
      width="min(720px, calc(100vw - 32px))"
      append-to-body
      :lock-scroll="false"
      class="assistant-source-dialog"
    >
      <div class="assistant-source-list">
        <article
          v-for="(source, index) in activeSources"
          :key="[source.type, source.title, index].join('-')"
          class="assistant-source-card"
        >
          <div class="assistant-source-card__header">
            <el-tag size="small" effect="plain">{{ sourceTypeLabel(source.type) }}</el-tag>
            <strong>{{ source.title }}</strong>
          </div>
          <p>{{ source.summary || '该依据未提供摘要。' }}</p>
        </article>
      </div>
      <template #footer>
        <el-button type="primary" @click="sourceDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.assistant-root {
  --assistant-ink: #17201d;
  --assistant-teal: #0f766e;
  --assistant-line: #d9e2de;
  --assistant-paper: #fbfcfa;
}

.assistant-launcher {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1900;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 54px;
  padding: 7px 18px 7px 7px;
  border: 0;
  border-radius: 999px;
  color: #fff;
  background: var(--assistant-ink);
  box-shadow: 0 14px 34px rgba(14, 31, 25, 0.26);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.assistant-launcher:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 38px rgba(14, 31, 25, 0.32);
}

.assistant-launcher__mark,
.assistant-header__mark,
.assistant-empty__mark {
  display: grid;
  place-items: center;
  color: #fff;
  background: var(--assistant-teal);
  font-family: Georgia, serif;
  font-weight: 700;
}

.assistant-launcher__mark {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  font-size: 14px;
}

.assistant-launcher__label {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.assistant-widget {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 1900;
  display: flex;
  flex-direction: column;
  width: min(420px, calc(100vw - 32px));
  height: min(680px, calc(100vh - 48px));
  min-height: 500px;
  overflow: hidden;
  color: var(--assistant-ink);
  border: 1px solid rgba(23, 32, 29, 0.16);
  border-radius: 18px;
  background: #f4f7f5;
  box-shadow: 0 24px 64px rgba(14, 31, 25, 0.24);
}

.assistant-widget--maximized {
  inset: 0;
  width: 100vw;
  height: 100vh;
  min-height: 0;
  border: 0;
  border-radius: 0;
}

.assistant-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 16px;
  color: #fff;
  background:
    linear-gradient(120deg, rgba(15, 118, 110, 0.92), rgba(23, 32, 29, 0.98)),
    var(--assistant-ink);
}

.assistant-header__identity,
.assistant-header__actions,
.assistant-context-bar,
.assistant-source-card__header {
  display: flex;
  align-items: center;
}

.assistant-header__identity { gap: 11px; min-width: 0; }
.assistant-header__mark { flex: 0 0 auto; width: 38px; height: 38px; border-radius: 12px 12px 12px 4px; background: rgba(255, 255, 255, 0.16); }
.assistant-header h2 { margin: 0; font-size: 17px; line-height: 1.25; }
.assistant-header p { margin: 3px 0 0; color: rgba(255, 255, 255, 0.72); font-size: 11px; }
.assistant-header__actions { gap: 7px; }

.assistant-icon-button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 9px;
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  font-size: 19px;
  line-height: 1;
  cursor: pointer;
}

.assistant-icon-button:hover { background: rgba(255, 255, 255, 0.18); }

.assistant-context-bar {
  gap: 7px;
  min-height: 38px;
  padding: 0 14px;
  border-bottom: 1px solid var(--assistant-line);
  background: #fff;
  font-size: 11px;
}

.assistant-context-bar > span { color: #7a8681; }
.assistant-context-bar > strong { min-width: 0; overflow: hidden; color: var(--assistant-teal); text-overflow: ellipsis; white-space: nowrap; }
.assistant-context-bar .el-button { margin-left: auto; }
.assistant-alert { margin: 10px 12px 0; }

.assistant-conversation {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.05), transparent 42%),
    repeating-linear-gradient(0deg, rgba(23, 32, 29, 0.022) 0, rgba(23, 32, 29, 0.022) 1px, transparent 1px, transparent 28px),
    #f4f7f5;
}

.assistant-widget--maximized .assistant-conversation,
.assistant-widget--maximized .assistant-composer,
.assistant-widget--maximized .assistant-context-bar {
  padding-right: max(24px, calc((100vw - 960px) / 2));
  padding-left: max(24px, calc((100vw - 960px) / 2));
}

.assistant-empty { display: grid; place-items: center; margin: 24px auto 0; text-align: center; }
.assistant-empty__mark { width: 50px; height: 50px; border-radius: 15px 15px 15px 4px; font-size: 16px; }
.assistant-empty h3 { margin: 13px 0 7px; font-size: 18px; }
.assistant-empty p { max-width: 560px; margin: 0; color: #64716c; font-size: 13px; line-height: 1.65; }
.assistant-suggestions { display: grid; gap: 8px; width: 100%; margin-top: 18px; }

.assistant-suggestion {
  padding: 11px 13px;
  border: 1px solid var(--assistant-line);
  border-radius: 10px;
  color: #26332f;
  background: rgba(255, 255, 255, 0.88);
  text-align: left;
  line-height: 1.45;
  cursor: pointer;
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.assistant-suggestion:hover { border-color: var(--assistant-teal); transform: translateY(-1px); }
.assistant-message { max-width: 90%; margin-bottom: 16px; }
.assistant-message--user { margin-left: auto; }
.assistant-message__label { display: flex; align-items: center; gap: 8px; margin: 0 4px 6px; color: #6c7873; font-size: 11px; }
.assistant-message__content { padding: 12px 14px; border-radius: 4px 14px 14px 14px; background: #fff; box-shadow: 0 5px 16px rgba(27, 45, 38, 0.06); white-space: pre-wrap; line-height: 1.68; overflow-wrap: anywhere; }
.assistant-message--user .assistant-message__label { justify-content: flex-end; }
.assistant-message--user .assistant-message__content { color: #fff; border-radius: 14px 4px 14px 14px; background: var(--assistant-ink); }

.assistant-source-trigger {
  margin: 7px 4px 0;
  padding: 0;
  border: 0;
  color: var(--assistant-teal);
  background: transparent;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.assistant-source-trigger:hover { text-decoration: underline; }
.assistant-loading { display: flex; align-items: center; gap: 5px; color: #64716c; }
.assistant-loading span { width: 7px; height: 7px; border-radius: 50%; background: var(--assistant-teal); animation: assistant-pulse 1s infinite ease-in-out; }
.assistant-loading span:nth-child(2) { animation-delay: 0.15s; }
.assistant-loading span:nth-child(3) { animation-delay: 0.3s; }
.assistant-loading p { margin-left: 5px; font-size: 12px; }

.assistant-composer {
  flex: 0 0 auto;
  padding: 12px;
  border-top: 1px solid var(--assistant-line);
  background: #fff;
}

.assistant-composer__footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 8px; }
.assistant-composer__footer > span { color: #7a8681; font-size: 10px; }
.assistant-source-list { display: grid; gap: 12px; max-height: min(60vh, 560px); overflow-y: auto; padding-right: 4px; }

.assistant-source-card {
  padding: 15px;
  border: 1px solid var(--assistant-line, #d9e2de);
  border-radius: 12px;
  background: var(--assistant-paper, #fbfcfa);
}

.assistant-source-card__header { align-items: flex-start; gap: 10px; }
.assistant-source-card__header strong { line-height: 1.5; overflow-wrap: anywhere; }
.assistant-source-card p { margin: 10px 0 0; color: #61706a; line-height: 1.65; overflow-wrap: anywhere; }

@keyframes assistant-pulse {
  0%, 80%, 100% { opacity: 0.35; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

@media (max-width: 560px) {
  .assistant-launcher { right: 16px; bottom: 16px; }
  .assistant-widget { right: 8px; bottom: 8px; width: calc(100vw - 16px); height: calc(100vh - 16px); border-radius: 14px; }
  .assistant-widget--maximized { inset: 0; width: 100vw; height: 100vh; border-radius: 0; }
  .assistant-message { max-width: 96%; }
  .assistant-composer__footer > span { display: none; }
  .assistant-composer__footer { justify-content: flex-end; }
}
</style>
