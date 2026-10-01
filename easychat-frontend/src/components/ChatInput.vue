<template>
  <div class="chat-input-container">
    <div class="attachments">
      <label><span>添加图片</span><input type="file" accept="image/png,image/jpeg,image/webp" :disabled="disabled || working || !vision" @change="readImage" /></label>
      <input v-model="imageUrl" class="image-url" :disabled="disabled || working || !vision" placeholder="或粘贴一张图片的 http(s) URL" />
      <label v-if="mediaEnabled"><span>{{ working ? '正在处理…' : '音视频转写' }}</span><input type="file" accept=".wav,.mp3,.m4a,.ogg,.flac,.mp4,.webm,.mov,.mkv" :disabled="disabled || working" @change="readMedia" /></label>
      <button v-if="imageData || imageUrl" :disabled="disabled || working" @click="imageData = ''; imageUrl = ''">移除图片</button>
      <small v-if="!vision">当前模型不支持图片</small>
    </div>
    <p v-if="imageData">已选择 1 张图片</p>
    <div class="input-row">
      <textarea v-model="message" :disabled="disabled || working" class="text-input" rows="2"
        placeholder="请输入消息（Enter 发送，Shift+Enter 换行）" @keydown.enter.exact.prevent="handleSend" />
      <el-button type="primary" :disabled="disabled || working || (!message.trim() && !imageData && !imageUrl.trim())" @click="handleSend">发送</el-button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { transcribe } from '@/api/media'
import { validImage } from '@/utils/validation'
const props = defineProps<{ disabled?: boolean; vision: boolean; mediaEnabled: boolean }>()
const emit = defineEmits<{ send: [message: string, images: string[]] }>()
const message = ref('')
const imageData = ref('')
const imageUrl = ref('')
const working = ref(false)
const controller = new AbortController()
function fileFrom(event: Event): File | undefined {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  return file
}
async function readImage(event: Event): Promise<void> {
  const file = fileFrom(event)
  if (!file) return
  working.value = true
  try {
    if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 1_500_000) throw new Error('请选择较小的 PNG、JPEG 或 WebP 图片，编码后不能超过 2,000,000 字符')
    const data = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = () => reject(new Error('读取图片失败'))
      reader.readAsDataURL(file)
    })
    if (!validImage(data)) throw new Error('图片格式或长度不符合要求')
    imageData.value = data
    imageUrl.value = ''
  } catch (error) { ElMessage.error((error as Error).message) }
  finally { working.value = false }
}
async function readMedia(event: Event): Promise<void> {
  const file = fileFrom(event)
  if (!file) return
  working.value = true
  try {
    const text = await transcribe(file, controller.signal)
    message.value = [message.value, text].filter(Boolean).join('\n')
    ElMessage.success('转写文字已填入，请确认后发送')
  } catch (error) { if (!controller.signal.aborted) ElMessage.error((error as Error).message) }
  finally { working.value = false }
}
function handleSend(): void {
  if (props.disabled || working.value) return
  const image = imageUrl.value.trim() || imageData.value
  if (image && (!props.vision || !validImage(image))) { ElMessage.warning('请使用视觉模型，并提供有效图片（最多 2,000,000 字符）'); return }
  if (!message.value.trim() && !image) return
  emit('send', message.value.trim(), image ? [image] : [])
}
function clear(): void { message.value = ''; imageData.value = ''; imageUrl.value = '' }
defineExpose({ clear })
onBeforeUnmount(() => controller.abort())
</script>
<style scoped>
.chat-input-container { padding: 16px; border-top: 1px solid #ddd; background: white; }
.input-row, .attachments { display: flex; align-items: center; gap: 10px; }
.attachments { flex-wrap: wrap; margin-bottom: 10px; font-size: 12px; color: #606266; }
.attachments input[type=file] { width: 180px; }
.image-url { flex: 1; min-width: 180px; padding: 6px; }
.text-input { flex: 1; resize: vertical; padding: 10px; font: inherit; border: 1px solid #ccc; border-radius: 6px; }
</style>
