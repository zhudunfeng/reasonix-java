<template>
  <!-- 可折叠的思考过程块，复刻桌面端 reasoning__head / reasoning__body -->
  <div class="reasoning">
    <!-- 折叠状态入口 -->
    <button
      v-if="!open"
      type="button"
      class="reasoning__head"
      @click="$emit('toggle')"
    >
      <BrainCircuit :size="13" class="reasoning__icon" />
      <span class="reasoning__label">思考过程</span>
      <span v-if="running" class="reasoning__meta">推理中…</span>
      <ChevronRight :size="12" class="reasoning__chevron" />
    </button>

    <!-- 展开状态 -->
    <template v-else>
      <button
        type="button"
        class="reasoning__head reasoning__head--expanded"
        @click="$emit('toggle')"
      >
        <BrainCircuit :size="13" class="reasoning__icon" />
        <span class="reasoning__label">思考过程</span>
        <span v-if="running" class="reasoning__meta">推理中…</span>
        <ChevronRight :size="12" class="reasoning__chevron reasoning__chevron--open" />
      </button>
      <div class="reasoning__body" v-text="reasoning"></div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { BrainCircuit, ChevronRight } from 'lucide-vue-next';

defineProps<{
  reasoning: string;
  open: boolean;
  running?: boolean;
}>();

defineEmits<{
  toggle: [];
}>();
</script>

<style scoped>
/* 展开状态的 head 样式 — 内联 label 辅助颜色 */
.reasoning__head--expanded {
  color: var(--fg-dim);
}
.reasoning__head--expanded:hover {
  color: var(--fg);
}
</style>
