<template>
  <div class="model-selector">
    <el-select
      v-model="currentModel"
      placeholder="选择模型"
      @change="handleModelChange"
      class="model-select"
    >
      <el-option
        v-for="model in models"
        :key="model.code"
        :label="model.name"
        :value="model.code"
      />
    </el-select>
    <span class="model-label">当前模型: {{ getCurrentModelName() }}</span>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getModels, getCurrentModel, setCurrentModel } from '../api/config';

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['model-change']);

const models = ref([]);
const currentModel = ref('');

const loadModels = async () => {
  try {
    const data = await getModels();
    models.value = data;
  } catch (error) {
    console.error('Failed to load models:', error);
  }
};

const loadCurrentModel = async () => {
  try {
    const data = await getCurrentModel();
    currentModel.value = data.code;
  } catch (error) {
    console.error('Failed to load current model:', error);
  }
};

const handleModelChange = async (model) => {
  try {
    await setCurrentModel(model);
    emit('model-change', model);
  } catch (error) {
    console.error('Failed to set model:', error);
  }
};

const getCurrentModelName = () => {
  const model = models.value.find(m => m.code === currentModel.value);
  return model ? model.name : '未选择';
};

onMounted(async () => {
  await loadModels();
  await loadCurrentModel();
});
</script>

<style scoped>
.model-selector {
  display: flex;
  align-items: center;
  gap: 10px;
}

.model-select {
  width: 180px;
  transition: all 0.3s ease;
}

.model-select:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.model-label {
  font-size: 14px;
  color: #606266;
  background-color: #f5f7fa;
  padding: 4px 12px;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s ease;
}

.model-label:hover {
  background-color: #ecf5ff;
  border-color: #c6e2ff;
  color: #409eff;
}
</style>