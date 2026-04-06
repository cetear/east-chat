<template>
  <el-dialog
    v-model="dialogVisible"
    title="模型配置"
    width="500px"
  >
    <el-form :model="modelForm" label-width="100px">
      <el-form-item label="模型类型">
        <el-select v-model="modelForm.modelCode" placeholder="选择模型类型">
          <el-option
            v-for="model in availableModels"
            :key="model.code"
            :label="model.name"
            :value="model.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="模型名称">
        <el-input v-model="modelForm.modelName" placeholder="模型名称" />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="modelForm.apiKey" type="password" placeholder="API Key" />
      </el-form-item>
      <el-form-item label="Base URL">
        <el-input v-model="modelForm.baseUrl" placeholder="API基础URL" />
      </el-form-item>
      <el-form-item label="模型名称(API)">
        <el-input v-model="modelForm.modelNameApi" placeholder="API中使用的模型名称" />
      </el-form-item>
      <el-form-item label="温度">
        <el-input-number v-model="modelForm.temperature" :min="0" :max="1" :step="0.1" />
      </el-form-item>
      <el-form-item label="最大 tokens">
        <el-input-number v-model="modelForm.maxTokens" :min="1" :max="10000" />
      </el-form-item>
      <el-form-item label="是否启用">
        <el-switch v-model="modelForm.isEnabled" />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { getModels, getModelConfig, saveModelConfig } from '../api/config';

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  modelCode: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['update:visible', 'config-updated']);

const dialogVisible = ref(props.visible);
const availableModels = ref([]);
const modelForm = ref({
  modelCode: '',
  modelName: '',
  apiKey: '',
  baseUrl: '',
  modelNameApi: '',
  temperature: 0.7,
  maxTokens: 2048,
  isEnabled: true
});

const loadModels = async () => {
  try {
    const models = await getModels();
    availableModels.value = models;
  } catch (error) {
    console.error('Failed to load models:', error);
  }
};

const loadModelConfig = async () => {
  if (props.modelCode) {
    try {
      const config = await getModelConfig(props.modelCode);
      modelForm.value = config;
    } catch (error) {
      console.error('Failed to load model config:', error);
    }
  }
};

const saveConfig = async () => {
  try {
    await saveModelConfig(modelForm.value);
    emit('config-updated');
    dialogVisible.value = false;
  } catch (error) {
    console.error('Failed to save model config:', error);
  }
};

onMounted(async () => {
  await loadModels();
  await loadModelConfig();
  if (props.modelCode) {
    modelForm.value.modelCode = props.modelCode;
  }
});

// 监听visible变化
watch(() => props.visible, (newVal) => {
  dialogVisible.value = newVal;
});

// 监听dialogVisible变化
watch(dialogVisible, (newVal) => {
  emit('update:visible', newVal);
});
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.el-button {
  transition: all 0.3s ease;
}

.el-button:hover {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.el-form-item {
  margin-bottom: 15px;
}

.el-input:hover,
.el-select:hover,
.el-input-number:hover {
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.el-input,
.el-select,
.el-input-number {
  transition: all 0.3s ease;
}
</style>