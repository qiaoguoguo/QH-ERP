import { afterEach, describe, expect, it, vi } from 'vitest'
import { triggerBrowserDownload } from './download'

describe('二进制下载 helper', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('创建对象 URL 触发下载后立即释放，避免页面长期持有 Blob', () => {
    const click = vi.fn()
    const anchor = {
      href: '',
      download: '',
      click,
      remove: vi.fn(),
    } as unknown as HTMLAnchorElement
    const createElement = vi.spyOn(document, 'createElement').mockReturnValue(anchor)
    const appendChild = vi.spyOn(document.body, 'appendChild').mockImplementation((node) => node)
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:download-url')
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)

    triggerBrowserDownload(new Blob(['result']), '导出结果.xlsx')

    expect(createElement).toHaveBeenCalledWith('a')
    expect(appendChild).toHaveBeenCalledWith(anchor)
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
    expect(anchor.href).toBe('blob:download-url')
    expect(anchor.download).toBe('导出结果.xlsx')
    expect(click).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:download-url')
  })
})
