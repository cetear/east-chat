<template>
  <main v-if="admin" class="admin">
    <header><h2>EasyChat 管理</h2><el-button @click="$router.push('/')">返回聊天</el-button></header>
    <p>模型参数按请求生效；修改配置不会中断正在运行的回答。渠道熔断信息是快照，路由统计可能为空或滞后。</p>
    <el-tabs v-model="tab" @tab-change="changeTab">
      <el-tab-pane label="模型" name="model" :disabled="busy" />
      <el-tab-pane label="渠道" name="provider" :disabled="busy" />
      <el-tab-pane label="路由绑定" name="route" :disabled="busy" />
      <el-tab-pane label="运行状态" name="ops" :disabled="busy" />
      <el-tab-pane v-if="esEnabled" label="ES 调试" name="es" :disabled="busy" />
    </el-tabs>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <template v-if="tab === 'model' || tab === 'provider' || tab === 'route'">
      <div class="toolbar">
        <el-select v-if="tab === 'route'" v-model="selectedProvider" placeholder="选择渠道查看绑定" :disabled="busy" @change="reload">
          <el-option v-for="item in providers" :key="String(item.providerCode)" :value="String(item.providerCode)" :label="String(item.providerCode)" />
        </el-select>
        <el-button :disabled="busy" @click="reload">刷新</el-button>
        <el-button type="primary" :disabled="busy" @click="openEditor()">新增</el-button>
      </div>
      <el-table :data="rows">
        <el-table-column v-for="key in columns" :key="key" :prop="key" :label="columnLabel(key)" min-width="140" />
        <el-table-column label="操作" fixed="right" width="180"><template #default="{ row }">
          <el-button :disabled="busy" @click="openEditor(row)">编辑</el-button>
          <el-button type="danger" :disabled="busy" @click="remove(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
    </template>
    <template v-else-if="tab === 'ops'">
      <el-button :disabled="busy" @click="reload">刷新当前进程状态</el-button>
      <p>以下为当前进程统计，不是集群聚合；esEnabled 仅表示启用开关。</p>
      <pre>{{ JSON.stringify(operations, null, 2) }}</pre>
    </template>
    <template v-else-if="tab === 'es'">
      <p>仅用于服务端白名单索引的管理员调试，不经过知识库解析、发布和用户归属流程。</p>
      <div class="toolbar">
        <el-input v-model="esIndex" placeholder="索引" /><el-input-number v-model="esSize" :min="1" :max="100" :precision="0" />
        <el-button :disabled="busy" @click="testConnection">测试连接</el-button>
        <el-button :disabled="busy" @click="readEs">读取文档</el-button>
      </div>
      <el-input v-model="esId" placeholder="可选文档 ID" />
      <el-input v-model="esDocument" type="textarea" :rows="5" placeholder="文档 JSON 对象" />
      <el-button type="primary" :disabled="busy" @click="writeEs">写入文档</el-button>
      <pre>{{ esResult }}</pre>
    </template>
    <el-dialog v-model="editor" :title="editing ? '编辑配置' : '新增配置'" width="600px" @closed="draft = {}">
      <el-form label-width="190px">
        <el-form-item v-for="field in fields[editKind]" :key="field.key" :label="field.label">
          <el-select v-if="field.kind === 'flag'" v-model="draft[field.key]">
            <el-option label="启用" value="1" /><el-option label="关闭" value="0" />
          </el-select>
          <el-input v-else v-model="draft[field.key]" :type="field.kind === 'secret' ? 'password' : field.kind === 'json' ? 'textarea' : 'text'"
            :show-password="field.kind === 'secret'" :disabled="editing && ['modelCode', 'providerCode'].includes(field.key)" autocomplete="off" />
        </el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </main>
  <main v-else class="admin"><el-alert title="需要现有身份系统授予 admin 角色。" type="error" :closable="false" /><router-link to="/">返回聊天</router-link></main>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { identity, isAdmin } from '@/auth'
import * as api from '@/api/admin'
import { fields, recordPayload } from '@/utils/adminForm'
const admin = computed(isAdmin)
const esEnabled = import.meta.env.VITE_KNOWLEDGE_ENABLED === 'true'
const tab = ref('model')
const rows = ref<api.AdminRecord[]>([])
const providers = ref<api.AdminRecord[]>([])
const selectedProvider = ref('')
const busy = ref(false)
const error = ref('')
const operations = ref<Record<string, unknown>>({})
const editor = ref(false)
const editing = ref(false)
const editKind = ref<api.AdminKind>('model')
const draft = ref<Record<string, string>>({})
const esIndex = ref('easychat')
const esSize = ref(100)
const esId = ref('')
const esDocument = ref('{"content":""}')
const esResult = ref('')
let revision = 0
const columns = computed(() => tab.value === 'model' ? ['modelCode', 'modelName', 'supportVision', 'enabled'] :
  tab.value === 'provider' ? ['providerCode', 'baseUrl', 'enabled', 'circuitStatus', 'failCount'] :
  ['providerCode', 'modelCode', 'priority', 'weight', 'timeoutMs', 'maxRetry', 'enabled'])
function columnLabel(key: string): string {
  return ({ circuitStatus: '熔断状态（快照）', failCount: '失败次数（快照）' } as Record<string, string>)[key] || Object.values(fields).flat().find(f => f.key === key)?.label || key
}
async function perform(action: () => Promise<void>): Promise<void> {
  if (busy.value || !admin.value) return
  const requestedIdentity = identity.value
  busy.value = true
  error.value = ''
  try { await action() } catch (cause) { if (identity.value === requestedIdentity) error.value = (cause as Error).message }
  finally { if (identity.value === requestedIdentity) busy.value = false }
}
async function load(): Promise<void> {
  const version = ++revision
  const current = tab.value
  if (current === 'ops') {
    const result = await api.getOperations()
    if (version === revision) operations.value = result
    return
  }
  if (current === 'es') return
  let result: api.AdminRecord[]
  if (current === 'model') result = await api.listModels()
  else if (current === 'provider') result = await api.listProviders()
  else {
    const list = await api.listProviders()
    if (version !== revision) return
    providers.value = list
    if (!list.some(p => p.providerCode === selectedProvider.value)) selectedProvider.value = String(list[0]?.providerCode ?? '')
    result = selectedProvider.value ? await api.listRoutes(selectedProvider.value) : []
  }
  if (version === revision) rows.value = result
}
async function reload(): Promise<void> { await perform(load) }
function changeTab(): void { ++revision; rows.value = []; void reload() }
function openEditor(item?: api.AdminRecord): void {
  editKind.value = tab.value as api.AdminKind
  editing.value = !!item
  draft.value = { enabled: '1', ...(editKind.value === 'model' ? { modelType: 'chat', supportVision: '0' } : {}),
    ...(editKind.value === 'route' ? { providerCode: selectedProvider.value, priority: '0', weight: '1', timeoutMs: '60000', maxRetry: '0' } : {}) }
  if (item) for (const field of fields[editKind.value]) draft.value[field.key] = field.kind === 'secret' ? '' : String(item[field.key] ?? '')
  editor.value = true
}
async function save(): Promise<void> {
  await perform(async () => {
    const payload = recordPayload(editKind.value, draft.value)
    await api.saveRecord(editKind.value, payload, editing.value)
    draft.value = {}
    editor.value = false
    ElMessage.success('已保存')
    await load()
  })
}
async function remove(item: api.AdminRecord): Promise<void> {
  const kind = tab.value as api.AdminKind
  try { await ElMessageBox.confirm(kind === 'route' ? '删除这条路由绑定？' : '删除该配置及关联绑定？', '确认删除', { type: 'warning' }) } catch { return }
  await perform(async () => { await api.removeRecord(kind, item); await load() })
}
async function testConnection(): Promise<void> { await perform(async () => { esResult.value = await api.testEs() }) }
function validateEs(): void {
  if (!esIndex.value.trim() || !Number.isInteger(esSize.value) || esSize.value < 1 || esSize.value > 100) throw new Error('请填写索引，读取数量须为 1–100')
}
async function readEs(): Promise<void> { await perform(async () => { validateEs(); esResult.value = JSON.stringify(await api.getEsDocuments(esIndex.value.trim(), esSize.value), null, 2) }) }
async function writeEs(): Promise<void> {
  await perform(async () => {
    validateEs()
    let document: unknown
    try { document = JSON.parse(esDocument.value) } catch { throw new Error('文档必须是有效 JSON') }
    if (!document || typeof document !== 'object' || Array.isArray(document)) throw new Error('文档必须是 JSON 对象')
    esResult.value = await api.indexEsDocument(esIndex.value.trim(), esId.value.trim(), document as Record<string, unknown>)
  })
}
watch(identity, () => {
  ++revision
  busy.value = false
  error.value = ''
  rows.value = []; providers.value = []; operations.value = {}; esResult.value = ''; draft.value = {}; editor.value = false
  if (admin.value) void reload()
}, { immediate: true })
onBeforeUnmount(() => { ++revision; draft.value = {} })
</script>
<style scoped>
.admin { padding: 24px; max-width: 1400px; margin: auto; }
header, .toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
header { justify-content: space-between; }
p { color: #606266; margin: 16px 0; }
.error { color: #c00; }
.toolbar .el-input, .toolbar .el-select { width: 240px; }
pre { padding: 16px; margin-top: 16px; background: #f0f2f5; white-space: pre-wrap; overflow-wrap: anywhere; }
.el-textarea { margin: 12px 0; }
</style>
