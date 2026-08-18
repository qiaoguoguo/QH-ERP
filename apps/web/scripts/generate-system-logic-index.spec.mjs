import assert from 'node:assert/strict'
import test from 'node:test'
import { buildIndexFromSources, extractEvidence, sanitizeText } from './generate-system-logic-index.mjs'

test('脱敏源码中的凭据赋值', () => {
  assert.equal(sanitizeText('secret = "actual-value"'), 'secret = "<已脱敏>"')
})

test('抽取核心逻辑证据类型', () => {
  const java = `
    @RequestMapping("/api/orders")
    class OrderController {
      enum Status { DRAFT, CONFIRMED }
      @PostMapping("/{id}/confirm")
      void confirm(@NotNull Long id) {
        if (!hasAuthority("sales:order:confirm")) throw new BusinessException(ApiErrorCode.FORBIDDEN);
        status = Status.CONFIRMED;
      }
      @Test void confirmsDraftOrder() {}
    }
  `
  const types = new Set(extractEvidence('apps/api/src/main/java/com/qherp/api/system/sales/OrderController.java', java).map((item) => item.type))
  for (const expected of ['API', 'VALIDATION', 'PERMISSION', 'ENUM', 'STATE_TRANSITION', 'ERROR', 'TEST_BEHAVIOR']) {
    assert.ok(types.has(expected), `缺少 ${expected}`)
  }
})

test('同一内容生成稳定摘要', () => {
  const sources = [{ path: 'apps/web/src/router/demo.ts', content: `export default [{ path: '/demo', name: 'Demo' }]` }]
  const first = buildIndexFromSources(sources, '2026-01-01T00:00:00.000Z')
  const second = buildIndexFromSources(sources, '2026-02-01T00:00:00.000Z')
  assert.equal(first.sourceDigest, second.sourceDigest)
  assert.deepEqual(first.evidence, second.evidence)
  assert.equal(first.evidence[0].routePath, '/demo')
})
