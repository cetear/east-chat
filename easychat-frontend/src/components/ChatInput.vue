<template>
  <div class="chat-input-container">
    <div class="input-row">
      <el-tooltip content="Upload file (coming soon)" placement="top">
        <button class="upload-button" disabled>
          &#x1F4CE;
        </button>
      </el-tooltip>
      <textarea
        v-model="message"
        placeholder="请输入消息... (Enter 发送，Shift+Enter 换行)"
        :disabled="disabled"
        class="text-input"
        rows="1"
        @keydown.enter.exact.prevent="handleSend"
      />
      <button
        class="send-button"
        :disabled="disabled"
        @click="handleSend"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['send']);
const message = ref('');

const handleSend = () => {
  if (props.disabled) return;
  if (!message.value.trim()) return;
  emit('send', message.value);
  message.value = '';
};
</script>

<style scoped>
.chat-input-container {
  width: 100%;
  padding: 15px;
  border-top: 1px solid #e0e0e0;
  background-color: #fff;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.upload-button {
  width: 40px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f5f7fa;
  cursor: not-allowed;
  font-size: 16px;
  opacity: 0.5;
  flex-shrink: 0;
}

.text-input {
  flex: 1;
  padding: 10px 15px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  outline: none;
  resize: none;
  min-height: 40px;
  max-height: 120px;
  overflow-y: auto;
  line-height: 1.5;
  font-family: inherit;
}

.text-input:focus {
  border-color: #409eff;
}

.send-button {
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 4px;
  background-color: #409eff;
  color: #fff;
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
}

.send-button:hover {
  background-color: #66b1ff;
}

.send-button:disabled {
  background-color: #a0cfff;
  cursor: not-allowed;
}
</style>
