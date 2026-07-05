import { ElMessageBox } from 'element-plus'

export async function confirmAction(message: string, title = '操作确认') {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      autofocus: false,
      closeOnClickModal: false,
      closeOnPressEscape: true,
      distinguishCancelAndClose: false,
      customClass: 'qherp-confirm-message-box',
    })
    return true
  } catch {
    return false
  }
}
