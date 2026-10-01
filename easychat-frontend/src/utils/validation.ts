export function normalizeDataset(value: string): string {
  const dataset = value.trim() || 'default'
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(dataset) || /^(con|prn|aux|nul|com[1-9]|lpt[1-9])$/i.test(dataset)) {
    throw new Error('数据集需为 1–64 位字母、数字、下划线或短横线，以字母或数字开头，不能使用 Windows 保留名')
  }
  return dataset
}
export function validateFile(file: File): void {
  if (file.size < 1 || file.size > 20 * 1024 * 1024) throw new Error('文件大小须为 1 字节至 20 MiB')
}
export function validImage(value: string): boolean {
  if (value.length > 2_000_000) return false
  if (/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$/.test(value)) return true
  try { return ['http:', 'https:'].includes(new URL(value).protocol) } catch { return false }
}
