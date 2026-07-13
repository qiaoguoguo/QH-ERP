import { computed, onUnmounted, type Ref, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/authStore'
import type { ResourceId } from '../api/documentPlatformApi'

const terminalStatuses = new Set(['READY_TO_COMMIT', 'VALIDATION_FAILED', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'])

export interface DocumentTaskPollingOptions {
  intervalMs?: number
}

export function isDocumentTaskTerminalStatus(status: string | null | undefined): boolean {
  return Boolean(status && terminalStatuses.has(status))
}

export function useDocumentTaskPolling<TTask extends { status?: string | null }>(
  taskId: Ref<ResourceId | null | undefined>,
  loader: (taskId: ResourceId) => Promise<TTask>,
  options: DocumentTaskPollingOptions = {},
) {
  const authStore = useAuthStore()
  const intervalMs = options.intervalMs ?? 2500
  const running = ref(false)
  const loading = ref(false)
  const error = ref('')
  const latestTask = ref<TTask | null>(null)
  const authKey = computed(() => String(authStore.currentUser?.id ?? 'anonymous'))
  let timer: ReturnType<typeof setTimeout> | null = null
  let requestId = 0

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function stop() {
    running.value = false
    clearTimer()
    requestId += 1
  }

  function schedule() {
    clearTimer()
    if (!running.value || taskId.value === null || taskId.value === undefined) {
      return
    }
    timer = setTimeout(() => {
      void poll()
    }, intervalMs)
  }

  async function poll() {
    const currentTaskId = taskId.value
    if (!running.value || currentTaskId === null || currentTaskId === undefined) {
      return
    }
    const currentRequestId = ++requestId
    loading.value = true
    error.value = ''
    try {
      const task = await loader(currentTaskId)
      if (currentRequestId !== requestId) {
        return
      }
      latestTask.value = task
      if (isDocumentTaskTerminalStatus(task.status)) {
        stop()
        return
      }
      schedule()
    } catch (caught) {
      if (currentRequestId !== requestId) {
        return
      }
      error.value = caught instanceof Error ? caught.message : '任务刷新失败'
      schedule()
    } finally {
      if (currentRequestId === requestId) {
        loading.value = false
      }
    }
  }

  function start() {
    if (taskId.value === null || taskId.value === undefined) {
      return
    }
    running.value = true
    schedule()
  }

  watch(taskId, () => {
    if (running.value) {
      schedule()
    }
  })

  watch(authKey, () => {
    stop()
  })

  onUnmounted(stop)

  return {
    running,
    loading,
    error,
    latestTask,
    start,
    stop,
    poll,
  }
}
