export type AiAssistantMode = 'MINIMAX' | 'KNOWLEDGE_FALLBACK'

export interface AiAssistantTurn {
  role: 'user' | 'assistant'
  content: string
}

export interface AiAssistantSource {
  type: 'MANUAL' | 'SYSTEM_LOGIC'
  title: string
  summary: string
  articleId?: number | null
  slug?: string | null
  routePath?: string | null
}

export interface AiAssistantAnswer {
  answer: string
  mode: AiAssistantMode
  model: string
  sources: AiAssistantSource[]
  generatedAt: string
}

export interface AiAssistantStatus {
  modelConfigured: boolean
  provider: string
  model: string
  currentMode: string
  privacyNotice: string
}

export interface AskAiAssistantPayload {
  question: string
  routePath?: string
  pageName?: string
  history: AiAssistantTurn[]
}

interface ApiEnvelope<T> {
  data?: T
  message?: string
  code?: string
}

interface CsrfToken {
  token: string
  headerName?: string
}

export class AiAssistantApiError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AiAssistantApiError'
  }
}

function unwrap<T>(payload: T | ApiEnvelope<T>): T {
  if (payload && typeof payload === 'object' && 'data' in payload && (payload as ApiEnvelope<T>).data !== undefined) {
    return (payload as ApiEnvelope<T>).data as T
  }
  return payload as T
}

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text()
  let payload: T | ApiEnvelope<T> | null = null
  if (text) {
    try {
      payload = JSON.parse(text) as T | ApiEnvelope<T>
    } catch {
      throw new AiAssistantApiError('服务响应格式异常，请稍后重试')
    }
  }
  if (!response.ok) {
    const message = payload && typeof payload === 'object' && 'message' in payload
      ? String((payload as ApiEnvelope<T>).message || '')
      : ''
    throw new AiAssistantApiError(message || (response.status === 429 ? '提问过于频繁，请稍后再试' : 'AI助手暂时不可用'))
  }
  if (payload === null) {
    throw new AiAssistantApiError('服务未返回有效内容')
  }
  return unwrap(payload)
}

async function getCsrfHeader(): Promise<Record<string, string>> {
  const response = await fetch('/api/auth/csrf', { credentials: 'include' })
  const token = await parseResponse<CsrfToken>(response)
  return { [token.headerName || 'X-CSRF-TOKEN']: token.token }
}

export const aiAssistantApi = {
  async status(): Promise<AiAssistantStatus> {
    const response = await fetch('/api/ai-assistant/status', { credentials: 'include' })
    return parseResponse<AiAssistantStatus>(response)
  },
  async ask(payload: AskAiAssistantPayload): Promise<AiAssistantAnswer> {
    const csrfHeader = await getCsrfHeader()
    const response = await fetch('/api/ai-assistant/answers', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...csrfHeader },
      body: JSON.stringify(payload),
    })
    return parseResponse<AiAssistantAnswer>(response)
  },
}
