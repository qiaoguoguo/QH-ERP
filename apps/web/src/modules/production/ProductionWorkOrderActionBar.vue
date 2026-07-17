<script setup lang="ts">
defineProps<{
  actionLoading: boolean
  canEdit: boolean
  canRelease: boolean
  canCreateIssue: boolean
  canCreateReport: boolean
  canCreateReceipt: boolean
  canComplete: boolean
  canCancel: boolean
}>()

const emit = defineEmits<{
  back: []
  edit: []
  release: []
  createIssue: []
  createReport: []
  createReceipt: []
  complete: []
  cancel: []
}>()
</script>

<template>
  <el-button data-test="back-production-work-order-list" @click="emit('back')">返回列表</el-button>
  <el-button v-if="canEdit" data-test="edit-production-work-order-detail" type="primary" @click="emit('edit')">
    编辑
  </el-button>
  <el-button v-if="canRelease" type="success" :loading="actionLoading" :disabled="actionLoading" @click="emit('release')">
    发布
  </el-button>
  <el-button v-if="canCreateIssue" @click="emit('createIssue')">领料</el-button>
  <el-button v-if="canCreateReport" @click="emit('createReport')">报工</el-button>
  <el-button v-if="canCreateReceipt" @click="emit('createReceipt')">完工入库</el-button>
  <el-button v-if="canComplete" type="success" :loading="actionLoading" :disabled="actionLoading" @click="emit('complete')">
    完成
  </el-button>
  <el-button v-if="canCancel" type="danger" plain :loading="actionLoading" :disabled="actionLoading" @click="emit('cancel')">
    取消
  </el-button>
</template>
