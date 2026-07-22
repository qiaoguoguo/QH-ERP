import * as ts from 'typescript'

type RawSourceMap = Record<string, string>

export type RouteInventoryType =
  | 'deep-link'
  | 'detail'
  | 'forbidden'
  | 'form'
  | 'list'
  | 'login'
  | 'redirect'
  | 'report'
  | 'root'

export interface RouteInventoryRecord {
  path: string
  name: string | null
  aliases: string[]
  sourceFile: string
  line: number
  redirect: string | null
  componentImport: string | null
  componentSourceFile: string | null
  requiredPermissions: string[]
  routeType: RouteInventoryType
  isPlaceholder: boolean
  isReportConfig: boolean
}

export interface RouteAliasRecord {
  aliasPath: string
  canonicalPath: string
  canonicalName: string | null
  sourceFile: string
  line: number
  componentSourceFile: string | null
  requiredPermissions: string[]
  routeType: RouteInventoryType
}

export interface DynamicImportRecord {
  sourceFile: string
  line: number
  importPath: string
  resolvedKey: string
  exists: boolean
}

export interface RouteInventory {
  routes: RouteInventoryRecord[]
  aliasRoutes: RouteAliasRecord[]
  systemCompatibilityAliases: RouteAliasRecord[]
  duplicatePaths: Array<{ path: string; locations: string[] }>
  duplicateAliases: Array<{ aliasPath: string; locations: string[] }>
  redirects: RouteInventoryRecord[]
  missingRedirectTargets: RouteInventoryRecord[]
  dynamicImports: DynamicImportRecord[]
  missingDynamicImports: DynamicImportRecord[]
  reportConfigRoutes: RouteInventoryRecord[]
  reportComponentCases: string[]
  reportConfigsMissingComponentCase: RouteInventoryRecord[]
  topLevelCounts: Record<string, number>
}

const routeSourceModules = import.meta.glob<string>('../router/**/*.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

const reportSourceModules = import.meta.glob<string>('../modules/reports/reportPageHelpers.ts', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

const vueSourceModules = import.meta.glob<string>('../modules/**/*.vue', {
  eager: true,
  import: 'default',
  query: '?raw',
}) as RawSourceMap

function sourceLabel(key: string): string {
  return `apps/web/src/${key.replace(/^\.\.\//, '')}`
}

function normalizeGlobKey(pathText: string): string {
  const parts: string[] = []
  pathText.replace(/\\/g, '/').split('/').forEach((part) => {
    if (!part || part === '.') {
      return
    }
    if (part === '..' && parts.length > 0 && parts[parts.length - 1] !== '..') {
      parts.pop()
      return
    }
    parts.push(part)
  })
  return parts.join('/').replace(/^(\.\.\/)?/, '../')
}

function resolveImportKey(sourceKey: string, importPath: string): string {
  if (!importPath.startsWith('.')) {
    return importPath
  }
  const sourceDirectory = sourceKey.slice(0, sourceKey.lastIndexOf('/'))
  return normalizeGlobKey(`${sourceDirectory}/${importPath}`)
}

function isStringLike(node: ts.Node | null | undefined): node is ts.StringLiteral | ts.NoSubstitutionTemplateLiteral {
  if (!node) {
    return false
  }
  return ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)
}

function propertyNameText(name: ts.PropertyName): string | null {
  if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNoSubstitutionTemplateLiteral(name)) {
    return name.text
  }
  return null
}

function propertyInitializer(object: ts.ObjectLiteralExpression, name: string): ts.Expression | null {
  for (const property of object.properties) {
    if (!ts.isPropertyAssignment(property)) {
      continue
    }
    if (propertyNameText(property.name) === name) {
      return property.initializer
    }
  }
  return null
}

function stringProperty(object: ts.ObjectLiteralExpression, names: string[]): string | null {
  for (const name of names) {
    const initializer = propertyInitializer(object, name)
    if (isStringLike(initializer)) {
      return initializer.text
    }
  }
  return null
}

function stringArrayProperty(object: ts.ObjectLiteralExpression, name: string): string[] {
  const initializer = propertyInitializer(object, name)
  if (!initializer || !ts.isArrayLiteralExpression(initializer)) {
    return []
  }
  return initializer.elements.filter(isStringLike).map((item) => item.text)
}

function stringOrStringArrayProperty(object: ts.ObjectLiteralExpression, name: string): string[] {
  const initializer = propertyInitializer(object, name)
  if (isStringLike(initializer)) {
    return [initializer.text]
  }
  if (!initializer || !ts.isArrayLiteralExpression(initializer)) {
    return []
  }
  return initializer.elements.filter(isStringLike).map((item) => item.text)
}

function lineNumber(sourceFile: ts.SourceFile, position: number): number {
  return sourceFile.getLineAndCharacterOfPosition(position).line + 1
}

function firstImportPath(node: ts.Node): string | null {
  let result: string | null = null
  const visit = (current: ts.Node) => {
    if (result) {
      return
    }
    if (
      ts.isCallExpression(current)
      && current.expression.kind === ts.SyntaxKind.ImportKeyword
      && isStringLike(current.arguments[0])
    ) {
      result = current.arguments[0].text
      return
    }
    current.forEachChild(visit)
  }
  visit(node)
  return result
}

function metaPermissions(object: ts.ObjectLiteralExpression): string[] {
  const meta = propertyInitializer(object, 'meta')
  if (!meta || !ts.isObjectLiteralExpression(meta)) {
    return []
  }
  const requiredPermission = stringProperty(meta, ['requiredPermission'])
  return [
    ...(requiredPermission ? [requiredPermission] : []),
    ...stringArrayProperty(meta, 'requiredPermissions'),
  ]
}

function classifyRoute(record: Omit<RouteInventoryRecord, 'routeType'>): RouteInventoryType {
  if (record.redirect) {
    return 'redirect'
  }
  if (record.path === '/login') {
    return 'login'
  }
  if (record.path === '/forbidden') {
    return 'forbidden'
  }
  if (record.path === '/' || record.name?.endsWith('-root') || record.isPlaceholder) {
    return 'root'
  }
  if (record.isReportConfig || record.path.startsWith('/reports/')) {
    return 'report'
  }
  if (record.path.includes('/create') || record.path.endsWith('/edit')) {
    return 'form'
  }
  if (record.path.includes(':')) {
    return 'detail'
  }
  if (record.path.includes('/')) {
    return 'list'
  }
  return 'deep-link'
}

function collectRouteRecords(sourceKey: string, sourceText: string): RouteInventoryRecord[] {
  const sourceFile = ts.createSourceFile(sourceKey, sourceText, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const records: RouteInventoryRecord[] = []
  const isReportConfig = sourceKey.endsWith('/reportPageHelpers.ts')

  const visit = (node: ts.Node) => {
    if (ts.isObjectLiteralExpression(node)) {
      const path = stringProperty(node, ['path'])
      if (path) {
        const component = propertyInitializer(node, 'component')
        const componentImport = component ? firstImportPath(component) : null
        const componentResolvedKey = componentImport ? resolveImportKey(sourceKey, componentImport) : null
        const recordWithoutType = {
          path,
          name: stringProperty(node, ['name', 'routeName']),
          aliases: stringOrStringArrayProperty(node, 'alias'),
          sourceFile: sourceLabel(sourceKey),
          line: lineNumber(sourceFile, node.getStart(sourceFile)),
          redirect: stringProperty(node, ['redirect']),
          componentImport,
          componentSourceFile: componentResolvedKey && vueSourceModules[componentResolvedKey]
            ? sourceLabel(componentResolvedKey)
            : null,
          requiredPermissions: metaPermissions(node),
          isPlaceholder: component?.getText(sourceFile).includes('placeholder(') ?? false,
          isReportConfig,
        }
        records.push({
          ...recordWithoutType,
          routeType: classifyRoute(recordWithoutType),
        })
      }
    }
    node.forEachChild(visit)
  }

  visit(sourceFile)
  return records
}

function collectDynamicImports(sourceKey: string, sourceText: string): DynamicImportRecord[] {
  const sourceFile = ts.createSourceFile(sourceKey, sourceText, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const imports: DynamicImportRecord[] = []
  const visit = (node: ts.Node) => {
    if (
      ts.isCallExpression(node)
      && node.expression.kind === ts.SyntaxKind.ImportKeyword
      && isStringLike(node.arguments[0])
    ) {
      const importPath = node.arguments[0].text
      const resolvedKey = resolveImportKey(sourceKey, importPath)
      imports.push({
        sourceFile: sourceLabel(sourceKey),
        line: lineNumber(sourceFile, node.getStart(sourceFile)),
        importPath,
        resolvedKey,
        exists: Boolean(vueSourceModules[resolvedKey]),
      })
    }
    node.forEachChild(visit)
  }
  visit(sourceFile)
  return imports
}

function collectReportComponentCases(routeIndexText: string): string[] {
  return Array.from(routeIndexText.matchAll(/case\s+['"]([^'"]+)['"]\s*:/g))
    .map((match) => match[1])
    .sort((left, right) => left.localeCompare(right))
}

function topLevelPath(pathText: string): string {
  if (pathText === '/') {
    return '/'
  }
  const [, firstSegment] = pathText.split('/')
  return `/${firstSegment}`
}

export function buildRouteInventory(): RouteInventory {
  const routeSources = Object.entries(routeSourceModules)
    .filter(([key]) => !key.endsWith('.spec.ts'))
    .sort(([left], [right]) => left.localeCompare(right))
  const reportSources = Object.entries(reportSourceModules).sort(([left], [right]) => left.localeCompare(right))
  const allSources = [...routeSources, ...reportSources]
  const routes = allSources.flatMap(([key, sourceText]) => collectRouteRecords(key, sourceText))
  const pathLocations = new Map<string, string[]>()
  const aliasLocations = new Map<string, string[]>()
  routes.forEach((route) => {
    const locations = pathLocations.get(route.path) ?? []
    locations.push(`${route.sourceFile}:${route.line}`)
    pathLocations.set(route.path, locations)
    route.aliases.forEach((aliasPath) => {
      const aliasRouteLocations = aliasLocations.get(aliasPath) ?? []
      aliasRouteLocations.push(`${route.sourceFile}:${route.line} -> ${route.path}`)
      aliasLocations.set(aliasPath, aliasRouteLocations)
    })
  })

  const aliasRoutes = routes.flatMap((route) => route.aliases.map((aliasPath) => ({
    aliasPath,
    canonicalPath: route.path,
    canonicalName: route.name,
    sourceFile: route.sourceFile,
    line: route.line,
    componentSourceFile: route.componentSourceFile,
    requiredPermissions: route.requiredPermissions,
    routeType: route.routeType,
  }))).sort((left, right) => left.aliasPath.localeCompare(right.aliasPath))

  const duplicatePaths = Array.from(pathLocations.entries())
    .filter(([, locations]) => locations.length > 1)
    .map(([path, locations]) => ({ path, locations }))
    .sort((left, right) => left.path.localeCompare(right.path))
  const duplicateAliases = Array.from(aliasLocations.entries())
    .filter(([, locations]) => locations.length > 1)
    .map(([aliasPath, locations]) => ({ aliasPath, locations }))
    .sort((left, right) => left.aliasPath.localeCompare(right.aliasPath))

  const pathSet = new Set(routes.map((route) => route.path))
  const redirects = routes.filter((route) => route.redirect)
  const reportConfigRoutes = routes.filter((route) => route.isReportConfig)
  const routeIndexText = routeSourceModules['../router/index.ts'] ?? ''
  const reportComponentCases = collectReportComponentCases(routeIndexText)
  const reportComponentCaseSet = new Set(reportComponentCases)
  const dynamicImports = routeSources.flatMap(([key, sourceText]) => collectDynamicImports(key, sourceText))
  const topLevelCounts = routes.reduce<Record<string, number>>((counts, route) => {
    const key = topLevelPath(route.path)
    counts[key] = (counts[key] ?? 0) + 1
    return counts
  }, {})

  return {
    routes: routes.sort((left, right) => left.path.localeCompare(right.path)),
    aliasRoutes,
    systemCompatibilityAliases: aliasRoutes.filter((route) => route.aliasPath.startsWith('/system/')),
    duplicatePaths,
    duplicateAliases,
    redirects,
    missingRedirectTargets: redirects.filter((route) => route.redirect && !pathSet.has(route.redirect)),
    dynamicImports: dynamicImports.sort((left, right) => (
      `${left.sourceFile}:${left.line}`.localeCompare(`${right.sourceFile}:${right.line}`)
    )),
    missingDynamicImports: dynamicImports.filter((item) => !item.exists),
    reportConfigRoutes,
    reportComponentCases,
    reportConfigsMissingComponentCase: reportConfigRoutes.filter((route) => (
      route.name ? !reportComponentCaseSet.has(route.name) : true
    )),
    topLevelCounts: Object.fromEntries(Object.entries(topLevelCounts).sort(([left], [right]) => left.localeCompare(right))),
  }
}

export function summarizeRouteInventory(inventory: RouteInventory): string {
  return [
    `生产路由 ${inventory.routes.length} 个`,
    `兼容别名 ${inventory.aliasRoutes.length} 个`,
    `重复路径 ${inventory.duplicatePaths.length} 个`,
    `重定向 ${inventory.redirects.length} 个`,
    `动态 import ${inventory.dynamicImports.length} 个`,
    `报表配置 ${inventory.reportConfigRoutes.length} 个`,
  ].join('；')
}
