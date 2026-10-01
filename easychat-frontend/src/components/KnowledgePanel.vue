<template>
  <section>
    <p>上传仅表示已接收；状态为“可检索”后才能用于问答。切换数据集不会清空会话历史，需严格隔离时请新建会话。</p>
    <div class="toolbar">
      <el-input v-model="filter" placeholder="列表数据集（留空表示全部）" :disabled="busy" />
      <el-button :disabled="busy" @click="refresh(true)">查询</el-button>
      <el-input v-model="uploadDataset" placeholder="上传数据集，默认 default" :disabled="busy" />
      <input type="file" :disabled="busy" @change="upload" />
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <el-table :data="documents">
      <el-table-column prop="fileName" label="文件" />
      <el-table-column prop="dataset" label="数据集" />
      <el-table-column label="状态"><template #default="{ row }">{{ labels[row.status as KnowledgeDoc['status']] }}</template></el-table-column>
      <el-table-column prop="chunkCount" label="分块数" />
      <el-table-column prop="errorMsg" label="说明" />
      <el-table-column label="操作" width="190"><template #default="{ row }">
        <el-button v-if="['FAILED', 'INDEXED'].includes(row.status)" :disabled="busy" @click="mutate(row, false)">{{ row.status === 'FAILED' ? '重试' : '重建' }}</el-button>
        <el-button type="danger" :disabled="busy || row.status === 'DELETING'" @click="mutate(row, true)">{{ row.status === 'DELETE_FAILED' ? '重新删除' : '删除' }}</el-button>
      </template></el-table-column>
    </el-table>
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteDocument, getDocuments, getDocumentStatus, retryDocument, uploadDocument, type KnowledgeDoc } from '@/api/knowledge'
import { retryableReadError } from '@/api/request'
const props = defineProps<{ dataset: string }>()
const filter = ref('')
const uploadDataset = ref(props.dataset)
const documents = ref<KnowledgeDoc[]>([])
const busy = ref(false)
const error = ref('')
const labels = { PENDING: '已接收', INDEXING: '处理中', INDEXED: '可检索', FAILED: '处理失败', DELETING: '删除中', DELETE_FAILED: '删除失败' }
const controller = new AbortController()
let timer: ReturnType<typeof setTimeout> | undefined
let delay = 3000
let appliedFilter = ''
let retryFailure = false
function pending(): boolean { return documents.value.some(d => ['PENDING', 'INDEXING', 'DELETING'].includes(d.status)) }
function schedule(): void {
  clearTimeout(timer)
  if (!controller.signal.aborted && (error.value ? retryFailure : pending())) timer = setTimeout(() => void refresh(), delay)
}
async function refresh(applyFilter = false): Promise<void> {
  if (busy.value || controller.signal.aborted) return
  if (applyFilter) appliedFilter = filter.value.trim()
  if (document.hidden) { schedule(); return }
  busy.value = true
  try {
    documents.value = await getDocuments(appliedFilter, controller.signal)
    error.value = ''
    retryFailure = false
    delay = 3000
  } catch (cause) {
    if (!controller.signal.aborted) {
      error.value = (cause as Error).message
      retryFailure = retryableReadError(cause)
      delay = Math.min(delay * 2, 30000)
    }
  } finally { busy.value = false; schedule() }
}
async function upload(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || busy.value) return
  busy.value = true
  clearTimeout(timer)
  try {
    const code = await uploadDocument(file, uploadDataset.value, controller.signal)
    const doc = await getDocumentStatus(code, controller.signal)
    ElMessage.success(`已接收：${labels[doc.status]}`)
    if (doc.status === 'FAILED') ElMessage.warning(doc.errorMsg || '已有文档处理失败，请在列表中显式重试')
    filter.value = doc.dataset
    appliedFilter = doc.dataset
  } catch (cause) { if (!controller.signal.aborted) ElMessage.error((cause as Error).message) }
  finally { busy.value = false; await refresh() }
}
async function mutate(doc: KnowledgeDoc, remove: boolean): Promise<void> {
  if (busy.value) return
  if (remove) {
    try { await ElMessageBox.confirm(`删除“${doc.fileName}”？`, '删除知识文档', { type: 'warning' }) } catch { return }
  }
  busy.value = true
  clearTimeout(timer)
  try {
    if (remove) await deleteDocument(doc.docCode, controller.signal)
    else await retryDocument(doc.docCode, controller.signal)
  } catch (cause) { if (!controller.signal.aborted) ElMessage.error((cause as Error).message) }
  finally { busy.value = false; await refresh() }
}
function visibility(): void { if (!document.hidden) void refresh() }
onMounted(() => { void refresh(); document.addEventListener('visibilitychange', visibility) })
onBeforeUnmount(() => { controller.abort(); clearTimeout(timer); document.removeEventListener('visibilitychange', visibility) })
</script>
<style scoped>
.toolbar { display: flex; gap: 8px; margin: 16px 0; flex-wrap: wrap; }
.toolbar .el-input { width: 220px; }
p { margin: 8px 0; color: #606266; }
</style>
