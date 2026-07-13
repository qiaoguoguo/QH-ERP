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

type PollingTaskIds = ResourceId | ResourceId[] | null | undefined

export function useDocumentTaskPolling<TTask extends { status?: string | null }>(
  taskId: Ref<PollingTaskIds>,
  loader: (taskId: ResourceId) => Promise<TTask>,
  options: DocumentTaskPollingOptions = {},
) {
  const authStore = useAuthStore()
  const intervalMs = options.intervalMs ?? 2500
  const running = ref(false)
  const loading = ref(false)
  const error = ref('')
  const latestTask = ref<TTask | null>(null)
  const latestTasks = ref<TTask[]>([])
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

  function currentTaskIds(): ResourceId[] {
    const value = taskId.value
    if (Array.isArray(value)) {
      return value.filter((item) => item !== null && item !== undefined)
    }
    return value === null || value === undefined ? [] : [value]
  }

  function schedule() {
    clearTimer()
    if (!running.value || currentTaskIds().length === 0) {
      return
    }
    timer = setTimeout(() => {
      void poll()
    }, intervalMs)
  }

  async function poll() {
    const currentTaskIdsSnapshot = currentTaskIds()
    if (!running.value || currentTaskIdsSnapshot.length === 0) {
      return
    }
    const currentRequestId = ++requestId
    loading.value = true
    error.value = ''
    try {
      const tasks = await Promise.all(currentTaskIdsSnapshot.map((id) => loader(id)))
      if (currentRequestId !== requestId) {
        return
      }
      latestTasks.value = tasks
      latestTask.value = tasks.at(-1) ?? null
      if (tasks.every((task) => isDocumentTaskTerminalStatus(task.status))) {
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
    if (currentTaskIds().length === 0) {
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
    latestTasks,
    start,
    stop,
    poll,
  }
}
