import type { DownloadedFile } from '../api/documentPlatformApi'

export function triggerBrowserDownload(blob: Blob, fileName: string): void {
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  document.body.appendChild(anchor)
  try {
    anchor.click()
  } finally {
    anchor.remove()
    URL.revokeObjectURL(objectUrl)
  }
}

export function downloadFile(file: DownloadedFile): void {
  triggerBrowserDownload(file.blob, file.fileName)
}
