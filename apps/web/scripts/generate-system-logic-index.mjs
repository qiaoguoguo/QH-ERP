import { createHash } from 'node:crypto'
import { readFile, readdir, mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const SCHEMA_VERSION = 1
const GENERATOR_VERSION = '1.1.0'
const MAX_SOURCE_BYTES = 2 * 1024 * 1024
const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = path.resolve(SCRIPT_DIR, '../../..')
const OUTPUT_FILE = path.join(REPO_ROOT, 'apps/api/src/main/resources/system-logic/system-logic-index.json')

const SOURCE_ROOTS = [
  'apps/web/src/router',
  'apps/web/src/navigation',
  'apps/web/src/modules',
  'apps/web/src/test',
  'apps/api/src/main/java/com/qherp/api/system',
  'apps/api/src/main/java/com/qherp/api/common/ApiErrorCode.java',
  'apps/api/src/main/java/com/qherp/api/security/PermissionAuthorizationManager.java',
  'apps/api/src/main/resources/db/migration',
  'apps/api/src/test/java/com/qherp/api/system',
]

const EVIDENCE_TYPES = new Set([
  'ROUTE',
  'MENU',
  'PAGE_ELEMENT',
  'API',
  'VALIDATION',
  'PERMISSION',
  'ENUM',
  'STATE_TRANSITION',
  'ERROR',
  'DATABASE_CONSTRAINT',
  'TEST_BEHAVIOR',
])

const excludedSegments = new Set(['node_modules', 'target', 'dist', '.git', 'coverage', 'specs'])
const supportedExtensions = new Set(['.ts', '.tsx', '.vue', '.java', '.sql'])

function compareText(left, right) {
  return left < right ? -1 : left > right ? 1 : 0
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function toRepoPath(filePath) {
  return path.relative(REPO_ROOT, filePath).split(path.sep).join('/')
}

function isExcluded(filePath) {
  return toRepoPath(filePath).split('/').some((segment) => excludedSegments.has(segment))
}

export function sanitizeText(value) {
  return String(value ?? '')
    .replace(/[\u0000-\u001f\u007f]/g, ' ')
    .replace(/((?:password|secret|token|access[-_]?key)\s*[:=]\s*)['"`][^'"`]*['"`]/gi, '$1"<已脱敏>"')
    .replace(/-----BEGIN [A-Z ]+PRIVATE KEY-----[\s\S]*?-----END [A-Z ]+PRIVATE KEY-----/g, '<私钥已脱敏>')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 800)
}

function businessDomain(sourcePath) {
  const normalized = sourcePath.replaceAll('\\', '/')
  const backend = normalized.match(/\/system\/([^/]+)/)
  if (backend) return backend[1]
  const frontend = normalized.match(/\/modules\/([^/]+)/)
  if (frontend) return frontend[1]
  if (normalized.includes('/navigation/')) return 'navigation'
  if (normalized.includes('/router/')) return 'router'
  if (normalized.includes('/db/migration/')) return 'database'
  if (normalized.includes('ApiErrorCode.java')) return 'common'
  if (normalized.includes('PermissionAuthorizationManager.java')) return 'security'
  return 'system'
}

function lineOf(source, index) {
  return source.slice(0, Math.max(0, index)).split('\n').length
}

function evidenceDigest(item) {
  return sha256([
    item.type,
    item.domain,
    item.title,
    item.summary,
    item.keywords,
    item.routePath ?? '',
    item.httpMethod ?? '',
    item.permissionCode ?? '',
    item.symbol ?? '',
    item.sourcePath,
    String(item.sourceLine),
    String(item.confidence),
  ].join('\0'))
}

function makeEvidence(sourcePath, source, item) {
  if (!EVIDENCE_TYPES.has(item.type)) throw new Error(`未知证据类型: ${item.type}`)
  const normalized = {
    type: item.type,
    domain: item.domain ?? businessDomain(sourcePath),
    title: sanitizeText(item.title),
    summary: sanitizeText(item.summary),
    keywords: sanitizeText(item.keywords ?? item.title),
    routePath: item.routePath ? sanitizeText(item.routePath) : null,
    httpMethod: item.httpMethod ? sanitizeText(item.httpMethod).toUpperCase() : null,
    permissionCode: item.permissionCode ? sanitizeText(item.permissionCode) : null,
    symbol: item.symbol ? sanitizeText(item.symbol) : null,
    sourcePath,
    sourceLine: item.sourceLine ?? lineOf(source, item.index ?? 0),
    confidence: item.confidence,
  }
  normalized.digest = evidenceDigest(normalized)
  normalized.key = sha256(`${normalized.type}\0${normalized.sourcePath}\0${normalized.sourceLine}\0${normalized.title}`).slice(0, 32)
  return normalized
}

function quotedValue(node) {
  return ts.isStringLiteralLike(node) || ts.isNoSubstitutionTemplateLiteral(node) ? node.text : null
}

function propertyName(node) {
  return node.name && (ts.isIdentifier(node.name) || ts.isStringLiteralLike(node.name)) ? node.name.text : null
}

function objectStringProperty(node, name) {
  if (!ts.isObjectLiteralExpression(node)) return null
  for (const property of node.properties) {
    if (ts.isPropertyAssignment(property) && propertyName(property) === name) return quotedValue(property.initializer)
  }
  return null
}

function extractTsEvidence(sourcePath, source) {
  const scriptKind = sourcePath.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS
  const ast = ts.createSourceFile(sourcePath, source, ts.ScriptTarget.Latest, true, scriptKind)
  const output = []
  function visit(node) {
    if (ts.isObjectLiteralExpression(node)) {
      const routePath = objectStringProperty(node, 'path')
      const label = objectStringProperty(node, 'label') ?? objectStringProperty(node, 'title')
      const name = objectStringProperty(node, 'name')
      if (routePath && routePath.startsWith('/')) {
        output.push(makeEvidence(sourcePath, source, {
          type: 'ROUTE',
          title: label ? `页面路由：${label}` : `页面路由：${routePath}`,
          summary: `系统定义页面路由 ${routePath}${name ? `，路由名称为 ${name}` : ''}。`,
          keywords: `${label ?? ''} ${name ?? ''} ${routePath}`,
          routePath,
          symbol: name,
          sourceLine: ast.getLineAndCharacterOfPosition(node.getStart(ast)).line + 1,
          confidence: 0.9,
        }))
        if (label) {
          output.push(makeEvidence(sourcePath, source, {
            type: 'MENU',
            title: `菜单：${label}`,
            summary: `菜单“${label}”导航到 ${routePath}。`,
            keywords: `${label} ${routePath}`,
            routePath,
            sourceLine: ast.getLineAndCharacterOfPosition(node.getStart(ast)).line + 1,
            confidence: 0.9,
          }))
        }
      }
    }
    ts.forEachChild(node, visit)
  }
  visit(ast)
  return output
}

function collectRegex(sourcePath, source, regex, factory) {
  const items = []
  for (const match of source.matchAll(regex)) items.push(makeEvidence(sourcePath, source, factory(match)))
  return items
}

function extractVueEvidence(sourcePath, source) {
  return collectRegex(sourcePath, source, /<(?:button|a|label|h[1-4])\b[^>]*>([^<>{}]{1,80})<\//g, (match) => ({
    type: 'PAGE_ELEMENT',
    title: `页面元素：${match[1].trim()}`,
    summary: `页面可见元素包含“${match[1].trim()}”。`,
    keywords: match[1],
    index: match.index,
    confidence: 0.75,
  }))
}

function extractJavaEvidence(sourcePath, source) {
  const output = []
  const classMapping = source.match(/@RequestMapping\(\s*["']([^"']+)["']/)?.[1] ?? ''
  output.push(...collectRegex(sourcePath, source,
    /@(Get|Post|Put|Patch|Delete)Mapping(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["'][^)]*\))?/g,
    (match) => {
      const method = match[1].toUpperCase()
      const endpoint = `${classMapping}${match[2] ?? ''}` || '/'
      return {
        type: 'API',
        title: `${method} ${endpoint}`,
        summary: `后端提供 ${method} ${endpoint} 接口。`,
        keywords: `${method} ${endpoint}`,
        routePath: endpoint,
        httpMethod: method,
        index: match.index,
        confidence: 1,
      }
    }))
  output.push(...collectRegex(sourcePath, source,
    /@(NotNull|NotBlank|NotEmpty|Size|Min|Max|DecimalMin|DecimalMax|Positive|PositiveOrZero|Pattern)\b([^\n]*)/g,
    (match) => ({
      type: 'VALIDATION',
      title: `参数校验：${match[1]}`,
      summary: `代码声明 ${sanitizeText(match[0])} 校验。`,
      keywords: `参数 校验 ${match[1]}`,
      index: match.index,
      confidence: 0.9,
    })))
	output.push(...collectRegex(sourcePath, source,
		/(?:hasAuthority|hasAnyAuthority|hasRole|hasAnyRole)\(\s*["']([^"']+)["']/g,
    (match) => ({
      type: 'PERMISSION',
      title: `权限要求：${match[1]}`,
      summary: `访问逻辑显式要求权限 ${match[1]}。`,
      keywords: `权限 ${match[1]}`,
      permissionCode: match[1],
      index: match.index,
			confidence: 1,
		})))
	const containsPermissionDefinitions = sourcePath.endsWith('/AccountPermissionInitializer.java')
		|| sourcePath.endsWith('/PermissionAuthorizationManager.java')
		|| source.includes('@PreAuthorize')
	if (containsPermissionDefinitions) {
		output.push(...collectRegex(sourcePath, source,
			/["']([a-z][a-z0-9_-]*(?::[a-z][a-z0-9_-]*){1,3})["']/g,
			(match) => ({
				type: 'PERMISSION',
				title: `权限编码：${match[1]}`,
				summary: `系统定义或使用权限编码 ${match[1]}。`,
				keywords: `权限 ${match[1]}`,
				permissionCode: match[1],
				index: match.index,
				confidence: 1,
			})))
	}
  output.push(...collectRegex(sourcePath, source,
    /ApiErrorCode\.([A-Z][A-Z0-9_]+)/g,
    (match) => ({
      type: 'ERROR',
      title: `错误码：${match[1]}`,
      summary: `当前逻辑可能返回错误码 ${match[1]}。`,
      keywords: `错误 异常 ${match[1]}`,
      symbol: match[1],
      index: match.index,
      confidence: 0.9,
    })))
	for (const enumMatch of source.matchAll(/\benum\s+([A-Za-z0-9_]+)\s*\{([\s\S]*?)\}/g)) {
    const constants = enumMatch[2]
      .split(/[;,\n]/)
      .map((item) => item.trim().match(/^([A-Z][A-Z0-9_]*)\b/)?.[1])
      .filter(Boolean)
      .slice(0, 100)
    if (constants.length) {
      output.push(makeEvidence(sourcePath, source, {
        type: 'ENUM',
        title: `枚举：${enumMatch[1]}`,
        summary: `枚举 ${enumMatch[1]} 包含：${constants.join('、')}。`,
        keywords: `${enumMatch[1]} ${constants.join(' ')}`,
        symbol: enumMatch[1],
        index: enumMatch.index,
        confidence: 1,
      }))
    }
  }
  output.push(...collectRegex(sourcePath, source,
	/\b(?:status|state)\s*=\s*([A-Za-z][A-Za-z0-9_.]*\.[A-Z][A-Z0-9_]*|[A-Z][A-Z0-9_]*)/g,
    (match) => ({
      type: 'STATE_TRANSITION',
      title: `状态设置：${match[1]}`,
      summary: `业务逻辑显式设置状态为 ${match[1]}。`,
      keywords: `状态 流转 ${match[1]}`,
      symbol: match[1],
      index: match.index,
      confidence: 0.75,
    })))
  output.push(...collectRegex(sourcePath, source,
    /@Test[\s\S]{0,300}?\b(?:void|[A-Za-z0-9_<>]+)\s+([a-zA-Z][a-zA-Z0-9_]*)\s*\(/g,
    (match) => ({
      type: 'TEST_BEHAVIOR',
      title: `测试行为：${match[1]}`,
      summary: `自动化测试覆盖行为 ${match[1]}。`,
      keywords: `测试 ${match[1]}`,
      symbol: match[1],
      index: match.index,
      confidence: 0.9,
    })))
  return output
}

function extractSqlEvidence(sourcePath, source) {
  return collectRegex(sourcePath, source,
    /^\s*([^\n]*(?:constraint|foreign key|references|check\s*\(|unique\s*(?:\(|index)|not null)[^\n]*)/gim,
    (match) => ({
      type: 'DATABASE_CONSTRAINT',
      title: '数据库约束',
      summary: `数据库定义约束：${sanitizeText(match[1])}`,
      keywords: `数据库 约束 ${match[1]}`,
      index: match.index,
      confidence: 1,
    }))
}

export function extractEvidence(sourcePath, source) {
  const extension = path.extname(sourcePath).toLowerCase()
  if (extension === '.ts' || extension === '.tsx') return extractTsEvidence(sourcePath, source)
  if (extension === '.vue') return extractVueEvidence(sourcePath, source)
  if (extension === '.java') return extractJavaEvidence(sourcePath, source)
  if (extension === '.sql') return extractSqlEvidence(sourcePath, source)
  return []
}

async function walk(target) {
  const stat = await import('node:fs/promises').then(({ stat }) => stat(target))
  if (stat.isFile()) return [target]
  const entries = await readdir(target, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const child = path.join(target, entry.name)
    if (isExcluded(child)) return []
    return entry.isDirectory() ? walk(child) : [child]
  }))
  return nested.flat()
}

export async function loadAllowedSources(repoRoot = REPO_ROOT) {
  const files = (await Promise.all(SOURCE_ROOTS.map((root) => walk(path.join(repoRoot, root)))))
    .flat()
    .filter((file) => supportedExtensions.has(path.extname(file).toLowerCase()))
    .filter((file) => !isExcluded(file))
    .sort((a, b) => compareText(toRepoPath(a), toRepoPath(b)))
  const sources = []
  for (const file of files) {
    const content = await readFile(file, 'utf8')
    if (Buffer.byteLength(content, 'utf8') > MAX_SOURCE_BYTES) throw new Error(`源码文件超过限制: ${toRepoPath(file)}`)
    sources.push({ path: toRepoPath(file), content })
  }
  return sources
}

export function buildIndexFromSources(sources, generatedAt = new Date().toISOString()) {
  const sourceFiles = sources
    .map((source) => ({ path: source.path.replaceAll('\\', '/'), sha256: sha256(source.content) }))
    .sort((a, b) => compareText(a.path, b.path))
  const sourceDigest = sha256(sourceFiles.map((source) => `${source.path}\0${source.sha256}\n`).join(''))
  const deduplicated = new Map()
  for (const source of sources) {
    for (const item of extractEvidence(source.path.replaceAll('\\', '/'), source.content)) deduplicated.set(item.key, item)
  }
  const evidence = [...deduplicated.values()].sort((a, b) =>
    compareText(a.sourcePath, b.sourcePath) || a.sourceLine - b.sourceLine || compareText(a.type, b.type))
  return { schemaVersion: SCHEMA_VERSION, generatorVersion: GENERATOR_VERSION, generatedAt, sourceDigest, sourceFiles, evidence }
}

function stableFingerprint(index) {
  return JSON.stringify({ ...index, generatedAt: null })
}

async function main() {
  const index = buildIndexFromSources(await loadAllowedSources())
  const checkOnly = process.argv.includes('--check')
  if (checkOnly) {
    const existing = JSON.parse(await readFile(OUTPUT_FILE, 'utf8'))
    if (stableFingerprint(existing) !== stableFingerprint(index)) {
      throw new Error('系统逻辑索引不是当前源码生成的最新版本，请运行 npm run knowledge:index')
    }
    process.stdout.write(`系统逻辑索引有效：${index.sourceFiles.length} 个源文件，${index.evidence.length} 条证据\n`)
    return
  }
  await mkdir(path.dirname(OUTPUT_FILE), { recursive: true })
  await writeFile(OUTPUT_FILE, `${JSON.stringify(index, null, 2)}\n`, 'utf8')
  process.stdout.write(`已生成系统逻辑索引：${index.sourceFiles.length} 个源文件，${index.evidence.length} 条证据\n`)
}

if (path.resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    process.stderr.write(`${error.stack ?? error.message}\n`)
    process.exitCode = 1
  })
}
