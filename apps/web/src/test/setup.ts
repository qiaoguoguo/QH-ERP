import { beforeEach, vi } from 'vitest'

const confirmActionMock = vi.hoisted(() => vi.fn())

export function useConfirmActionMock() {
  return confirmActionMock
}

vi.mock('../shared/ui/confirmDialog', () => ({
  confirmAction: confirmActionMock,
}))

beforeEach(() => {
  confirmActionMock.mockReset()
  confirmActionMock.mockResolvedValue(true)
})
