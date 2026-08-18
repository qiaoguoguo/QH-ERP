<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  content?: string | null
}>()

type Block =
  | { type: 'heading'; level: 1 | 2; text: string; id: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; ordered: boolean; items: string[] }

function headingId(text: string, index: number) {
  return `section-${index}-${text.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-').replace(/(^-)|(-$)/g, '') || 'item'}`
}

const blocks = computed<Block[]>(() => {
  const result: Block[] = []
  let activeList: { ordered: boolean; items: string[] } | null = null
  let headingIndex = 0

  const flushList = () => {
    if (activeList) {
      result.push({ type: 'list', ordered: activeList.ordered, items: activeList.items })
      activeList = null
    }
  }

  for (const rawLine of (props.content || '').replace(/\r\n/g, '\n').split('\n')) {
    const line = rawLine.trim()
    if (!line) {
      flushList()
      continue
    }

    const heading = /^(#{1,2})\s+(.+)$/.exec(line)
    if (heading) {
      flushList()
      headingIndex += 1
      result.push({
        type: 'heading',
        level: heading[1].length === 1 ? 1 : 2,
        text: heading[2],
        id: headingId(heading[2], headingIndex),
      })
      continue
    }

    const unordered = /^-\s+(.+)$/.exec(line)
    const ordered = /^\d+\.\s+(.+)$/.exec(line)
    if (unordered || ordered) {
      const isOrdered = Boolean(ordered)
      if (!activeList || activeList.ordered !== isOrdered) {
        flushList()
        activeList = { ordered: isOrdered, items: [] }
      }
      activeList.items.push((ordered || unordered)?.[1] ?? line)
      continue
    }

    flushList()
    result.push({ type: 'paragraph', text: line })
  }

  flushList()
  return result
})
</script>

<template>
  <div class="knowledge-content">
    <template v-if="blocks.length">
      <template v-for="(block, index) in blocks" :key="index">
        <h2 v-if="block.type === 'heading' && block.level === 1" :id="block.id">
          {{ block.text }}
        </h2>
        <h3 v-else-if="block.type === 'heading'" :id="block.id">
          {{ block.text }}
        </h3>
        <p v-else-if="block.type === 'paragraph'">
          {{ block.text }}
        </p>
        <ol v-else-if="block.ordered">
          <li v-for="item in block.items" :key="item">{{ item }}</li>
        </ol>
        <ul v-else>
          <li v-for="item in block.items" :key="item">{{ item }}</li>
        </ul>
      </template>
    </template>
    <el-empty v-else description="暂无正文内容" />
  </div>
</template>

<style scoped>
.knowledge-content {
  display: grid;
  gap: 12px;
  line-height: 1.75;
  color: #1f2937;
}

.knowledge-content h2 {
  margin: 10px 0 0;
  color: #111827;
  font-size: 20px;
}

.knowledge-content h3 {
  margin: 6px 0 0;
  color: #111827;
  font-size: 16px;
}

.knowledge-content p,
.knowledge-content ul,
.knowledge-content ol {
  margin: 0;
}

.knowledge-content ul,
.knowledge-content ol {
  padding-left: 24px;
}
</style>
