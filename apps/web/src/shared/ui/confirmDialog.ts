import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'

export interface ConfirmActionOptions {
  title?: string
  type?: 'success' | 'warning' | 'info' | 'error'
  risk?: string
}

export async function confirmAction(message: string, titleOrOptions: string | ConfirmActionOptions = '操作确认') {
  const options = typeof titleOrOptions === 'string'
    ? { title: titleOrOptions }
    : titleOrOptions
  try {
    await ElMessageBox.confirm(message, options.title ?? '操作确认', {
      type: options.type ?? 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      autofocus: false,
      closeOnClickModal: false,
      closeOnPressEscape: true,
      distinguishCancelAndClose: false,
      customClass: ['qherp-confirm-message-box', options.risk ? `qherp-confirm-${options.risk}` : ''].filter(Boolean).join(' '),
    })
    return true
  } catch {
    return false
  }
}
