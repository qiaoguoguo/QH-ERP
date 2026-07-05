import { ElMessageBox } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.unmock('./confirmDialog')

vi.mock('element-plus', () => ({
  ElMessageBox: {
    confirm: vi.fn(),
  },
}))

const { confirmAction } = await import('./confirmDialog')
const confirmMock = vi.mocked(ElMessageBox.confirm)

describe('统一确认弹窗', () => {
  beforeEach(() => {
    confirmMock.mockReset()
  })

  it('使用系统弹窗确认操作并返回确认结果', async () => {
    confirmMock.mockResolvedValueOnce({} as Awaited<ReturnType<typeof ElMessageBox.confirm>>)

    await expect(confirmAction('确认停用 BOM“BOM-001”？')).resolves.toBe(true)

    expect(confirmMock).toHaveBeenCalledWith('确认停用 BOM“BOM-001”？', '操作确认', expect.objectContaining({
      cancelButtonText: '取消',
      confirmButtonText: '确定',
      customClass: 'qherp-confirm-message-box',
      type: 'warning',
    }))
  })

  it('用户取消或关闭时返回 false', async () => {
    confirmMock.mockRejectedValueOnce('cancel')

    await expect(confirmAction('确认取消？')).resolves.toBe(false)
  })
})
