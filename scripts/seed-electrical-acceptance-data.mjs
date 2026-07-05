#!/usr/bin/env node

import { spawnSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const BASE_URL = (process.env.QHERP_API_BASE_URL ?? 'http://127.0.0.1:18082').replace(/\/$/, '')
const ADMIN_USERNAME = process.env.QHERP_ADMIN_USERNAME ?? 'admin'
const ADMIN_PASSWORD = process.env.QHERP_ADMIN_PASSWORD ?? 'Qherp@2026!'
const DEMO_PASSWORD = process.env.QHERP_DEMO_PASSWORD ?? 'Qherp@2026!'
const SUMMARY_FILE = process.env.QHERP_SEED_SUMMARY_FILE

const cookies = new Map()
const summary = {
  environment: {
    apiBaseUrl: BASE_URL,
    databaseContainer: 'qherp-postgres',
  },
  accounts: [],
  masterData: {},
  boms: {},
  inventory: {},
  procurement: {},
  production: {},
  sales: {},
  finance: {},
  reversal: {},
  reports: {},
}

function log(message) {
  console.log(`[seed] ${message}`)
}

function runPsql(sql) {
  const result = spawnSync(
    'docker',
    ['exec', '-i', 'qherp-postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'qherp', '-d', 'qherp'],
    {
      encoding: 'utf8',
      input: sql,
      maxBuffer: 1024 * 1024 * 20,
    },
  )
  if (result.status !== 0) {
    throw new Error(`psql 执行失败：\n${result.stdout}\n${result.stderr}`)
  }
  return result.stdout
}

function storeCookies(response) {
  const setCookies =
    typeof response.headers.getSetCookie === 'function'
      ? response.headers.getSetCookie()
      : response.headers.get('set-cookie')
        ? [response.headers.get('set-cookie')]
        : []

  for (const header of setCookies) {
    const first = header.split(';', 1)[0]
    const eq = first.indexOf('=')
    if (eq > 0) {
      cookies.set(first.slice(0, eq), first.slice(eq + 1))
    }
  }
}

function cookieHeader() {
  return Array.from(cookies.entries())
    .map(([name, value]) => `${name}=${value}`)
    .join('; ')
}

function queryString(query) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  }
  const built = params.toString()
  return built ? `?${built}` : ''
}

async function request(method, path, body, options = {}) {
  const headers = {
    Accept: 'application/json',
    ...(options.headers ?? {}),
  }
  const currentCookie = cookieHeader()
  if (currentCookie) {
    headers.Cookie = currentCookie
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    body: body === undefined ? undefined : JSON.stringify(body),
    headers,
    method,
    redirect: 'manual',
  })
  storeCookies(response)
  const text = await response.text()
  let envelope
  try {
    envelope = text ? JSON.parse(text) : {}
  } catch (error) {
    throw new Error(`${method} ${path} 返回非 JSON：${response.status}\n${text}`)
  }
  if (!response.ok || envelope.success === false) {
    const code = envelope.code ?? 'HTTP_ERROR'
    const message = envelope.message ?? text
    throw new Error(`${method} ${path} 失败：${response.status} ${code} ${message}`)
  }
  return envelope.data
}

async function get(path, query) {
  return request('GET', `${path}${queryString(query)}`)
}

async function write(method, path, body) {
  const csrf = await get('/api/auth/csrf')
  return request(method, path, body, {
    headers: {
      [csrf.headerName]: csrf.token,
    },
  })
}

async function post(path, body) {
  return write('POST', path, body)
}

async function put(path, body = {}) {
  return write('PUT', path, body)
}

async function login() {
  const csrf = await get('/api/auth/csrf')
  await request(
    'POST',
    '/api/auth/login',
    {
      password: ADMIN_PASSWORD,
      username: ADMIN_USERNAME,
    },
    {
      headers: {
        [csrf.headerName]: csrf.token,
      },
    },
  )
}

function flattenPermissions(nodes, result = []) {
  for (const node of nodes) {
    result.push(node)
    flattenPermissions(node.children ?? [], result)
  }
  return result
}

async function resetAcceptanceData() {
  log('清理本地验收数据库中的历史业务数据和临时账号')
  runPsql(`
begin;

truncate table
  biz_reversal_link,
  fin_payment_allocation,
  fin_receipt_allocation,
  fin_payable_source,
  fin_receivable_source,
  fin_settlement_adjustment,
  fin_payment,
  fin_receipt,
  fin_payable,
  fin_receivable,
  sal_sales_return_line,
  sal_sales_return,
  proc_purchase_return_line,
  proc_purchase_return,
  mfg_material_supplement_line,
  mfg_material_supplement,
  mfg_material_return_line,
  mfg_material_return,
  mfg_cost_record,
  mfg_completion_receipt,
  mfg_work_report,
  mfg_material_issue_line,
  mfg_material_issue,
  mfg_work_order_material,
  mfg_work_order,
  sal_sales_shipment_line,
  sal_sales_shipment,
  sal_sales_order_line,
  sal_sales_order,
  proc_purchase_receipt_line,
  proc_purchase_receipt,
  proc_purchase_order_line,
  proc_purchase_order,
  inv_inventory_document_line,
  inv_inventory_document,
  inv_stock_movement,
  inv_stock_balance,
  mfg_bom_item,
  mfg_bom,
  mst_material,
  mst_material_category,
  mst_supplier,
  mst_customer,
  mst_warehouse,
  mst_unit,
  sys_audit_log
restart identity cascade;

delete from sys_user_role
where user_id <> (select id from sys_user where username = 'admin')
   or role_id <> (select id from sys_role where code = 'SYSTEM_ADMIN');

delete from sys_role_permission
where role_id <> (select id from sys_role where code = 'SYSTEM_ADMIN');

delete from sys_user
where username <> 'admin';

delete from sys_role
where code <> 'SYSTEM_ADMIN';

commit;
`)
}

async function createRolesAndUsers() {
  log('创建清晰的演示角色和演示账号')
  const permissions = flattenPermissions(await get('/api/admin/permissions/tree'))
  const permissionsByCode = new Map(permissions.map((permission) => [permission.code, permission]))
  const allMenuAndViewCodes = permissions
    .filter((permission) => permission.type === 'MENU' || permission.code.endsWith(':view'))
    .map((permission) => permission.code)
  const viewCodes = new Set([...allMenuAndViewCodes, 'business:reversal:view'])

  function ids(codes) {
    return [...new Set(codes)].map((code) => {
      const permission = permissionsByCode.get(code)
      if (!permission) {
        throw new Error(`权限不存在：${code}`)
      }
      return permission.id
    })
  }

  async function role(code, name, description, codes) {
    const created = await post('/api/admin/roles', {
      code,
      description,
      name,
      sortOrder: 100,
      status: 'ENABLED',
    })
    await put(`/api/admin/roles/${created.id}/permissions`, { permissionIds: ids(codes) })
    return created
  }

  async function user(username, displayName, roleIds) {
    const created = await post('/api/admin/users', {
      displayName,
      email: `${username}@qh-erp.local`,
      initialPassword: DEMO_PASSWORD,
      phone: '13800000000',
      roleIds,
      status: 'ENABLED',
      username,
    })
    summary.accounts.push({ displayName, password: DEMO_PASSWORD, roles: roleIds, username })
    return created
  }

  const readonly = await role('DEMO_ELEC_READONLY', '电气业务只读', '查看全部业务链和报表，不允许写入', [
    ...viewCodes,
  ])
  const procurement = await role('DEMO_ELEC_PROCUREMENT', '电气采购员', '维护供应商、采购订单、采购入库和采购退货', [
    'master',
    'master:supplier',
    'master:supplier:view',
    'master:warehouse:view',
    'master:unit:view',
    'material',
    'master:material',
    'master:material:view',
    'procurement',
    'procurement:order:view',
    'procurement:order:create',
    'procurement:order:update',
    'procurement:order:confirm',
    'procurement:order:cancel',
    'procurement:order:close',
    'procurement:receipt:view',
    'procurement:receipt:create',
    'procurement:receipt:update',
    'procurement:receipt:post',
    'procurement:return:view',
    'procurement:return:create',
    'procurement:return:update',
    'procurement:return:post',
    'procurement:return:cancel',
    'inventory:balance:view',
    'inventory:movement:view',
    'business:reversal:view',
  ])
  const sales = await role('DEMO_ELEC_SALES', '电气销售员', '维护客户、销售订单、销售出库和销售退货', [
    'master',
    'master:customer',
    'master:customer:view',
    'master:warehouse:view',
    'master:unit:view',
    'material',
    'master:material',
    'master:material:view',
    'sales',
    'sales:order:view',
    'sales:order:create',
    'sales:order:update',
    'sales:order:confirm',
    'sales:order:cancel',
    'sales:order:close',
    'sales:shipment:view',
    'sales:shipment:create',
    'sales:shipment:update',
    'sales:shipment:post',
    'sales:return:view',
    'sales:return:create',
    'sales:return:update',
    'sales:return:post',
    'sales:return:cancel',
    'inventory:balance:view',
    'inventory:movement:view',
    'business:reversal:view',
  ])
  const production = await role('DEMO_ELEC_PRODUCTION', '电气生产班组长', '维护生产工单、领料、报工、完工入库、退料和补料', [
    'material',
    'master:material:view',
    'material:bom:view',
    'inventory:balance:view',
    'inventory:movement:view',
    'production',
    'production:work-order:view',
    'production:work-order:create',
    'production:work-order:update',
    'production:work-order:release',
    'production:work-order:complete',
    'production:work-order:cancel',
    'production:issue:view',
    'production:issue:create',
    'production:issue:update',
    'production:issue:post',
    'production:report:view',
    'production:report:create',
    'production:report:update',
    'production:report:post',
    'production:receipt:view',
    'production:receipt:create',
    'production:receipt:update',
    'production:receipt:post',
    'production:material-return:view',
    'production:material-return:create',
    'production:material-return:update',
    'production:material-return:post',
    'production:material-return:cancel',
    'production:material-supplement:view',
    'production:material-supplement:create',
    'production:material-supplement:update',
    'production:material-supplement:post',
    'production:material-supplement:cancel',
    'business:reversal:view',
  ])
  const warehouse = await role('DEMO_ELEC_WAREHOUSE', '电气仓库主管', '维护库存期初、库存调整和库存流水查看', [
    'master:warehouse:view',
    'master:unit:view',
    'material',
    'master:material:view',
    'inventory',
    'inventory:balance:view',
    'inventory:movement:view',
    'inventory:document:view',
    'inventory:document:create',
    'inventory:document:update',
    'inventory:document:post',
  ])
  const finance = await role('DEMO_ELEC_FINANCE', '电气往来会计', '维护应收、应付、收付款、往来冲减和经营报表', [
    'sales:order:view',
    'sales:shipment:view',
    'procurement:order:view',
    'procurement:receipt:view',
    'finance',
    'finance:receivable:view',
    'finance:receivable:create',
    'finance:receivable:update',
    'finance:receivable:confirm',
    'finance:receivable:cancel',
    'finance:receivable:close',
    'finance:receipt:view',
    'finance:receipt:create',
    'finance:receipt:update',
    'finance:receipt:post',
    'finance:receipt:cancel',
    'finance:payable:view',
    'finance:payable:create',
    'finance:payable:update',
    'finance:payable:confirm',
    'finance:payable:cancel',
    'finance:payable:close',
    'finance:payment:view',
    'finance:payment:create',
    'finance:payment:update',
    'finance:payment:post',
    'finance:payment:cancel',
    'finance:settlement-adjustment:view',
    'finance:settlement-adjustment:create',
    'finance:settlement-adjustment:update',
    'finance:settlement-adjustment:post',
    'finance:settlement-adjustment:cancel',
    'report',
    'report:overview:view',
    'report:sales:view',
    'report:procurement:view',
    'report:inventory:view',
    'report:production:view',
    'report:cost:view',
    'report:settlement:view',
    'report:exception:view',
    'business:reversal:view',
  ])

  await user('demo_readonly', '周经营', [readonly.id])
  await user('demo_procurement', '赵采购', [procurement.id])
  await user('demo_sales', '孙销售', [sales.id])
  await user('demo_production', '李生产', [production.id])
  await user('demo_warehouse', '陈仓库', [warehouse.id])
  await user('demo_finance', '吴往来', [finance.id])
  await user('demo_no_permission', '钱无权', [])
}

async function createMasterData() {
  log('创建电气制造业基础资料')
  const units = {
    kg: await post('/api/admin/master/units', {
      code: 'EM_U_KG',
      name: '千克',
      precisionScale: 3,
      remark: '铜排等按重量管理',
      sortOrder: 30,
      status: 'ENABLED',
    }),
    meter: await post('/api/admin/master/units', {
      code: 'EM_U_M',
      name: '米',
      precisionScale: 2,
      remark: '二次线按米管理',
      sortOrder: 20,
      status: 'ENABLED',
    }),
    pcs: await post('/api/admin/master/units', {
      code: 'EM_U_PCS',
      name: '件',
      precisionScale: 0,
      remark: '元件、半成品、成品按件管理',
      sortOrder: 10,
      status: 'ENABLED',
    }),
  }

  const warehouses = {
    finished: await post('/api/admin/master/warehouses', {
      address: '一期成品库 A 区',
      code: 'EM_WH_FINISHED',
      managerName: '陈仓库',
      name: '电气成品仓',
      remark: '低压柜成品发货仓',
      status: 'ENABLED',
      warehouseType: 'FINISHED',
    }),
    raw: await post('/api/admin/master/warehouses', {
      address: '一期原料库',
      code: 'EM_WH_RAW',
      managerName: '陈仓库',
      name: '电气原材料仓',
      remark: '铜排、断路器、接触器、线材',
      status: 'ENABLED',
      warehouseType: 'RAW',
    }),
    wip: await post('/api/admin/master/warehouses', {
      address: '装配车间旁线边仓',
      code: 'EM_WH_WIP',
      managerName: '李生产',
      name: '电气半成品仓',
      remark: '控制抽屉模块临时入库',
      status: 'ENABLED',
      warehouseType: 'NORMAL',
    }),
  }

  const suppliers = {
    cabinet: await post('/api/admin/master/suppliers', {
      code: 'EM_SUP_CABINET',
      contactName: '邱工',
      contactPhone: '13900001003',
      name: '苏州精工钣金有限公司',
      remark: '低压柜体钣金供应商',
      status: 'ENABLED',
    }),
    components: await post('/api/admin/master/suppliers', {
      code: 'EM_SUP_COMPONENT',
      contactName: '陆经理',
      contactPhone: '13900001002',
      name: '正泰电气元件有限公司',
      remark: '断路器、接触器供应商',
      status: 'ENABLED',
    }),
    copper: await post('/api/admin/master/suppliers', {
      code: 'EM_SUP_COPPER',
      contactName: '黄经理',
      contactPhone: '13900001001',
      name: '华东铜排材料有限公司',
      remark: '紫铜排、二次线供应商',
      status: 'ENABLED',
    }),
  }

  const customers = {
    automation: await post('/api/admin/master/customers', {
      code: 'EM_CUS_AUTOMATION',
      contactName: '周工',
      contactPhone: '13800002001',
      name: '星河智能装备有限公司',
      remark: '智能产线低压柜客户',
      status: 'ENABLED',
    }),
    water: await post('/api/admin/master/customers', {
      code: 'EM_CUS_WATER',
      contactName: '蒋经理',
      contactPhone: '13800002002',
      name: '江南水务工程有限公司',
      remark: '水处理泵站控制柜客户',
      status: 'ENABLED',
    }),
  }

  const categories = {
    aux: await post('/api/admin/master/material-categories', {
      code: 'EM_CAT_AUX',
      name: '电气辅料',
      remark: '线材、铭牌等辅料',
      sortOrder: 30,
      status: 'ENABLED',
    }),
    finished: await post('/api/admin/master/material-categories', {
      code: 'EM_CAT_FINISHED',
      name: '电气成品',
      remark: '对外销售的低压控制柜',
      sortOrder: 50,
      status: 'ENABLED',
    }),
    raw: await post('/api/admin/master/material-categories', {
      code: 'EM_CAT_RAW',
      name: '电气原材料',
      remark: '铜排、柜体、低压电器元件',
      sortOrder: 10,
      status: 'ENABLED',
    }),
    semi: await post('/api/admin/master/material-categories', {
      code: 'EM_CAT_SEMI',
      name: '电气半成品',
      remark: '车间自制控制抽屉模块',
      sortOrder: 40,
      status: 'ENABLED',
    }),
  }

  async function material(payload) {
    return post('/api/admin/master/materials', { status: 'ENABLED', ...payload })
  }

  const materials = {
    breaker: await material({
      categoryId: categories.raw.id,
      code: 'EM_RAW_BREAKER_250A',
      materialType: 'RAW_MATERIAL',
      name: '250A 塑壳断路器',
      remark: '低压柜主回路保护元件',
      sourceType: 'PURCHASED',
      specification: '3P 250A',
      unitId: units.pcs.id,
    }),
    cabinetShell: await material({
      categoryId: categories.raw.id,
      code: 'EM_RAW_CABINET_SHELL',
      materialType: 'RAW_MATERIAL',
      name: '低压柜钣金柜体',
      remark: '总装柜体',
      sourceType: 'PURCHASED',
      specification: '2200x800x600',
      unitId: units.pcs.id,
    }),
    contactor: await material({
      categoryId: categories.raw.id,
      code: 'EM_RAW_CONTACTOR_65A',
      materialType: 'RAW_MATERIAL',
      name: '65A 交流接触器',
      remark: '抽屉模块控制元件',
      sourceType: 'PURCHASED',
      specification: 'AC220V 65A',
      unitId: units.pcs.id,
    }),
    copper: await material({
      categoryId: categories.raw.id,
      code: 'EM_RAW_COPPER_BAR',
      materialType: 'RAW_MATERIAL',
      name: 'T2 紫铜排',
      remark: '主母排与支路铜排',
      sourceType: 'PURCHASED',
      specification: 'TMY 30x5',
      unitId: units.kg.id,
    }),
    finishedCabinet: await material({
      categoryId: categories.finished.id,
      code: 'EM_FG_MCC_400',
      materialType: 'FINISHED_GOOD',
      name: 'QH-MCC-400 智能低压控制柜',
      remark: '本次演示主产品',
      sourceType: 'SELF_MADE',
      specification: '400A 智能采集型',
      unitId: units.pcs.id,
    }),
    nameplate: await material({
      categoryId: categories.aux.id,
      code: 'EM_AUX_NAMEPLATE',
      materialType: 'AUXILIARY',
      name: '控制柜标识铭牌',
      remark: '成品柜铭牌',
      sourceType: 'PURCHASED',
      specification: '铝制丝印',
      unitId: units.pcs.id,
    }),
    semiModule: await material({
      categoryId: categories.semi.id,
      code: 'EM_SF_DRAWER_MODULE',
      materialType: 'SEMI_FINISHED',
      name: 'QH-CTRL-100 控制抽屉模块',
      remark: '低压柜内部控制抽屉',
      sourceType: 'SELF_MADE',
      specification: '100A 智能控制抽屉',
      unitId: units.pcs.id,
    }),
    wire: await material({
      categoryId: categories.aux.id,
      code: 'EM_AUX_WIRE_2_5',
      materialType: 'AUXILIARY',
      name: '2.5 平方二次线',
      remark: '二次控制回路布线',
      sourceType: 'PURCHASED',
      specification: 'BV 2.5mm2 红蓝双色',
      unitId: units.meter.id,
    }),
  }

  summary.masterData = { categories, customers, materials, suppliers, units, warehouses }
  return { categories, customers, materials, suppliers, units, warehouses }
}

async function createBoms(master) {
  log('创建并启用两级 BOM')
  const { materials, units } = master
  const semiBom = await post('/api/admin/boms', {
    baseQuantity: 1,
    baseUnitId: units.pcs.id,
    bomCode: 'EM_BOM_SF_DRAWER',
    effectiveFrom: '2026-01-01',
    name: '控制抽屉模块标准 BOM',
    parentMaterialId: materials.semiModule.id,
    remark: '抽屉模块由断路器、接触器、铜排和二次线组成',
    versionCode: 'V1.0',
    items: [
      { childMaterialId: materials.breaker.id, lineNo: 1, quantity: 1, remark: '主回路保护', unitId: units.pcs.id },
      { childMaterialId: materials.contactor.id, lineNo: 2, quantity: 2, remark: '控制回路执行', unitId: units.pcs.id },
      { childMaterialId: materials.copper.id, lineNo: 3, quantity: 1.5, remark: '抽屉内部铜排', unitId: units.kg.id },
      { childMaterialId: materials.wire.id, lineNo: 4, quantity: 6, remark: '二次线束', unitId: units.meter.id },
    ],
  })
  const enabledSemiBom = await put(`/api/admin/boms/${semiBom.id}/enable`)

  const oldFinishedBom = await post('/api/admin/boms', {
    baseQuantity: 1,
    baseUnitId: units.pcs.id,
    bomCode: 'EM_BOM_FG_MCC_OLD',
    effectiveFrom: '2025-01-01',
    effectiveTo: '2025-12-31',
    name: '智能低压控制柜旧版 BOM',
    parentMaterialId: materials.finishedCabinet.id,
    remark: '演示 BOM 启停状态的旧版本',
    versionCode: 'V0.9',
    items: [
      { childMaterialId: materials.semiModule.id, lineNo: 1, quantity: 1, unitId: units.pcs.id },
      { childMaterialId: materials.cabinetShell.id, lineNo: 2, quantity: 1, unitId: units.pcs.id },
    ],
  })
  const enabledOldFinishedBom = await put(`/api/admin/boms/${oldFinishedBom.id}/enable`)
  await put(`/api/admin/boms/${enabledOldFinishedBom.id}/disable`)

  const finishedBom = await post('/api/admin/boms', {
    baseQuantity: 1,
    baseUnitId: units.pcs.id,
    bomCode: 'EM_BOM_FG_MCC',
    effectiveFrom: '2026-01-01',
    name: '智能低压控制柜标准 BOM',
    parentMaterialId: materials.finishedCabinet.id,
    remark: '总装 BOM，包含半成品抽屉模块、柜体、铜排和铭牌',
    versionCode: 'V1.0',
    items: [
      { childMaterialId: materials.semiModule.id, lineNo: 1, quantity: 1, remark: '控制抽屉模块', unitId: units.pcs.id },
      { childMaterialId: materials.cabinetShell.id, lineNo: 2, quantity: 1, remark: '钣金柜体', unitId: units.pcs.id },
      { childMaterialId: materials.copper.id, lineNo: 3, quantity: 2, remark: '主母排与支路铜排', unitId: units.kg.id },
      { childMaterialId: materials.nameplate.id, lineNo: 4, quantity: 1, remark: '铭牌', unitId: units.pcs.id },
    ],
  })
  const enabledFinishedBom = await put(`/api/admin/boms/${finishedBom.id}/enable`)
  const copiedFinishedBom = await post(`/api/admin/boms/${enabledFinishedBom.id}/copy`, {
    bomCode: 'EM_BOM_FG_MCC_TRIAL',
    name: '智能低压控制柜试制 BOM',
    remark: '演示 BOM 复制能力，未启用',
    versionCode: 'V1.1-DRAFT',
  })

  summary.boms = {
    copiedFinishedBom,
    disabledOldFinishedBom: enabledOldFinishedBom,
    enabledFinishedBom,
    enabledSemiBom,
  }
  return { finishedBom: enabledFinishedBom, semiBom: enabledSemiBom }
}

async function createInventory(master) {
  log('创建期初库存和盘点调整')
  const { materials, units, warehouses } = master
  const opening = await post('/api/admin/inventory/documents', {
    businessDate: '2026-07-01',
    documentType: 'OPENING',
    reason: '2026 年 7 月电气车间期初库存',
    remark: '用于演示采购前已有安全库存',
    lines: [
      { lineNo: 1, materialId: materials.breaker.id, quantity: '10.000000', unitId: units.pcs.id, warehouseId: warehouses.raw.id },
      { lineNo: 2, materialId: materials.contactor.id, quantity: '20.000000', unitId: units.pcs.id, warehouseId: warehouses.raw.id },
      { lineNo: 3, materialId: materials.copper.id, quantity: '50.000000', unitId: units.kg.id, warehouseId: warehouses.raw.id },
      { lineNo: 4, materialId: materials.wire.id, quantity: '100.000000', unitId: units.meter.id, warehouseId: warehouses.raw.id },
      { lineNo: 5, materialId: materials.cabinetShell.id, quantity: '5.000000', unitId: units.pcs.id, warehouseId: warehouses.raw.id },
      { lineNo: 6, materialId: materials.nameplate.id, quantity: '20.000000', unitId: units.pcs.id, warehouseId: warehouses.raw.id },
    ],
  })
  const postedOpening = await put(`/api/admin/inventory/documents/${opening.id}/post`)

  const adjustment = await post('/api/admin/inventory/documents', {
    businessDate: '2026-07-02',
    documentType: 'ADJUSTMENT',
    reason: '电气车间盘点调整',
    remark: '二次线盘盈，铭牌发现破损',
    lines: [
      {
        adjustmentDirection: 'INCREASE',
        lineNo: 1,
        materialId: materials.wire.id,
        quantity: '20.000000',
        remark: '线盘复点盘盈',
        unitId: units.meter.id,
        warehouseId: warehouses.raw.id,
      },
      {
        adjustmentDirection: 'DECREASE',
        lineNo: 2,
        materialId: materials.nameplate.id,
        quantity: '2.000000',
        remark: '铭牌破损报损',
        unitId: units.pcs.id,
        warehouseId: warehouses.raw.id,
      },
    ],
  })
  const postedAdjustment = await put(`/api/admin/inventory/documents/${adjustment.id}/post`)

  summary.inventory = { postedAdjustment, postedOpening }
  return { postedAdjustment, postedOpening }
}

async function createProcurement(master) {
  log('创建采购订单、采购入库、应付和付款')
  const { materials, suppliers, units, warehouses } = master
  const order = await post('/api/admin/procurement/orders', {
    expectedArrivalDate: '2026-07-08',
    orderDate: '2026-07-03',
    remark: '星河订单备料采购',
    supplierId: suppliers.components.id,
    lines: [
      { expectedArrivalDate: '2026-07-08', lineNo: 1, materialId: materials.breaker.id, quantity: '80.000000', remark: '塑壳断路器', unitId: units.pcs.id, unitPrice: '120.000000' },
      { expectedArrivalDate: '2026-07-08', lineNo: 2, materialId: materials.contactor.id, quantity: '120.000000', remark: '交流接触器', unitId: units.pcs.id, unitPrice: '48.000000' },
      { expectedArrivalDate: '2026-07-08', lineNo: 3, materialId: materials.wire.id, quantity: '600.000000', remark: '二次线', unitId: units.meter.id, unitPrice: '2.200000' },
      { expectedArrivalDate: '2026-07-08', lineNo: 4, materialId: materials.copper.id, quantity: '250.000000', remark: 'T2 紫铜排', unitId: units.kg.id, unitPrice: '76.000000' },
      { expectedArrivalDate: '2026-07-08', lineNo: 5, materialId: materials.cabinetShell.id, quantity: '30.000000', remark: '钣金柜体', unitId: units.pcs.id, unitPrice: '420.000000' },
      { expectedArrivalDate: '2026-07-08', lineNo: 6, materialId: materials.nameplate.id, quantity: '80.000000', remark: '铭牌', unitId: units.pcs.id, unitPrice: '12.000000' },
    ],
  })
  const confirmedOrder = await put(`/api/admin/procurement/orders/${order.id}/confirm`)
  const receipt = await post(`/api/admin/procurement/orders/${order.id}/receipts`, {
    businessDate: '2026-07-05',
    remark: '供应商一次性到货入库',
    warehouseId: warehouses.raw.id,
    lines: confirmedOrder.lines.map((line) => ({
      lineNo: line.lineNo,
      materialId: line.materialId,
      orderLineId: line.id,
      quantity: String(line.quantity),
      remark: `${line.materialName} 到货`,
      unitId: line.unitId,
    })),
  })
  const postedReceipt = await put(`/api/admin/procurement/receipts/${receipt.id}/post`)
  const payable = await post('/api/admin/finance/payables', {
    dueDate: '2026-08-05',
    remark: '采购入库生成应付',
    sourceId: postedReceipt.id,
    sourceType: 'PURCHASE_RECEIPT',
  })
  const confirmedPayable = await put(`/api/admin/finance/payables/${payable.id}/confirm`)
  const payment = await post(`/api/admin/finance/payables/${payable.id}/payments`, {
    amount: '25000.00',
    method: 'BANK_TRANSFER',
    paymentDate: '2026-07-20',
    remark: '采购预付款和首批货款',
  })
  const postedPayment = await put(`/api/admin/finance/payments/${payment.id}/post`)

  summary.procurement = { confirmedOrder, confirmedPayable, postedPayment, postedReceipt }
  return { confirmedOrder, confirmedPayable, postedPayment, postedReceipt }
}

function workOrderMaterialByCode(workOrder, code) {
  const material = workOrder.materials.find((item) => item.materialCode === code)
  if (!material) {
    throw new Error(`工单 ${workOrder.workOrderNo} 中找不到物料 ${code}`)
  }
  return material
}

async function createProduction(master, boms) {
  log('创建半成品和成品两级生产执行链')
  const { materials, units, warehouses } = master

  const semiOrder = await post('/api/admin/production/work-orders', {
    bomId: boms.semiBom.id,
    issueWarehouseId: warehouses.raw.id,
    plannedFinishDate: '2026-07-12',
    plannedQuantity: '20.000000',
    plannedStartDate: '2026-07-07',
    productMaterialId: materials.semiModule.id,
    receiptWarehouseId: warehouses.wip.id,
    remark: '先生产 20 件控制抽屉模块',
  })
  const releasedSemiOrder = await put(`/api/admin/production/work-orders/${semiOrder.id}/release`)
  const semiIssue = await post(`/api/admin/production/work-orders/${semiOrder.id}/material-issues`, {
    businessDate: '2026-07-07',
    reason: '半成品工单领料',
    remark: '控制抽屉模块领料',
    lines: releasedSemiOrder.materials.map((material, index) => ({
      lineNo: index + 1,
      quantity: String(material.requiredQuantity),
      remark: `${material.materialName} 标准领用`,
      warehouseId: warehouses.raw.id,
      workOrderMaterialId: material.id,
    })),
  })
  const postedSemiIssue = await put(`/api/admin/production/work-orders/${semiOrder.id}/material-issues/${semiIssue.id}/post`)
  const semiReport = await post(`/api/admin/production/work-orders/${semiOrder.id}/reports`, {
    businessDate: '2026-07-10',
    defectiveQuantity: '0.000000',
    qualifiedQuantity: '20.000000',
    remark: '控制抽屉模块装配完成',
    reporterName: '李生产',
  })
  const postedSemiReport = await put(`/api/admin/production/work-orders/${semiOrder.id}/reports/${semiReport.id}/post`)
  const semiReceipt = await post(`/api/admin/production/work-orders/${semiOrder.id}/completion-receipts`, {
    businessDate: '2026-07-10',
    quantity: '20.000000',
    receiptWarehouseId: warehouses.wip.id,
    remark: '控制抽屉模块完工入半成品仓',
  })
  const postedSemiReceipt = await put(`/api/admin/production/work-orders/${semiOrder.id}/completion-receipts/${semiReceipt.id}/post`)
  const completedSemiOrder = await put(`/api/admin/production/work-orders/${semiOrder.id}/complete`)
  const semiLabor = await post('/api/admin/cost/records', {
    amount: '1800.00',
    basisType: 'MANUAL_AMOUNT',
    businessDate: '2026-07-10',
    costType: 'LABOR',
    remark: '控制抽屉模块装配人工',
    sourceDocumentNo: 'EM-LABOR-SF-202607',
    workOrderId: semiOrder.id,
  })

  const finishedOrder = await post('/api/admin/production/work-orders', {
    bomId: boms.finishedBom.id,
    issueWarehouseId: warehouses.raw.id,
    plannedFinishDate: '2026-07-18',
    plannedQuantity: '10.000000',
    plannedStartDate: '2026-07-13',
    productMaterialId: materials.finishedCabinet.id,
    receiptWarehouseId: warehouses.finished.id,
    remark: '总装 10 台智能低压控制柜',
  })
  const releasedFinishedOrder = await put(`/api/admin/production/work-orders/${finishedOrder.id}/release`)
  const finishedIssueLines = releasedFinishedOrder.materials.map((material, index) => ({
    lineNo: index + 1,
    quantity: String(material.requiredQuantity),
    remark: `${material.materialName} 总装领用`,
    warehouseId: material.materialCode === materials.semiModule.code ? warehouses.wip.id : warehouses.raw.id,
    workOrderMaterialId: material.id,
  }))
  const finishedIssue = await post(`/api/admin/production/work-orders/${finishedOrder.id}/material-issues`, {
    businessDate: '2026-07-13',
    reason: '成品总装领料',
    remark: '低压控制柜总装领料',
    lines: finishedIssueLines,
  })
  const postedFinishedIssue = await put(
    `/api/admin/production/work-orders/${finishedOrder.id}/material-issues/${finishedIssue.id}/post`,
  )

  const copperIssueLine = postedFinishedIssue.lines.find((line) => line.materialCode === materials.copper.code)
  const copperReturn = await post('/api/admin/production/material-returns', {
    businessDate: '2026-07-14',
    clientRequestId: 'em-production-material-return-copper',
    remark: '母排裁切后余料退回',
    sourceIssueId: postedFinishedIssue.id,
    lines: [{ quantity: '1.000000', reason: '铜排余料退回原材料仓', sourceIssueLineId: copperIssueLine.id }],
  })
  const postedCopperReturn = await put(`/api/admin/production/material-returns/${copperReturn.id}/post`)

  const nameplateMaterial = workOrderMaterialByCode(releasedFinishedOrder, materials.nameplate.code)
  const nameplateSupplement = await post('/api/admin/production/material-supplements', {
    businessDate: '2026-07-15',
    clientRequestId: 'em-production-material-supplement-nameplate',
    remark: '现场发现铭牌刮花，补领 2 件',
    warehouseId: warehouses.raw.id,
    workOrderId: finishedOrder.id,
    lines: [{ quantity: '2.000000', reason: '铭牌刮花补料', workOrderMaterialId: nameplateMaterial.id }],
  })
  const postedNameplateSupplement = await put(
    `/api/admin/production/material-supplements/${nameplateSupplement.id}/post`,
  )

  const finishedReport = await post(`/api/admin/production/work-orders/${finishedOrder.id}/reports`, {
    businessDate: '2026-07-17',
    defectiveQuantity: '0.000000',
    qualifiedQuantity: '10.000000',
    remark: '低压柜总装、通电测试完成',
    reporterName: '李生产',
  })
  const postedFinishedReport = await put(
    `/api/admin/production/work-orders/${finishedOrder.id}/reports/${finishedReport.id}/post`,
  )
  const finishedReceipt = await post(`/api/admin/production/work-orders/${finishedOrder.id}/completion-receipts`, {
    businessDate: '2026-07-17',
    quantity: '10.000000',
    receiptWarehouseId: warehouses.finished.id,
    remark: '智能低压控制柜完工入成品仓',
  })
  const postedFinishedReceipt = await put(
    `/api/admin/production/work-orders/${finishedOrder.id}/completion-receipts/${finishedReceipt.id}/post`,
  )
  const completedFinishedOrder = await put(`/api/admin/production/work-orders/${finishedOrder.id}/complete`)
  const finishedOverhead = await post('/api/admin/cost/records', {
    amount: '2600.00',
    basisType: 'MANUAL_AMOUNT',
    businessDate: '2026-07-17',
    costType: 'MANUFACTURING_OVERHEAD',
    remark: '低压柜总装制造费用',
    sourceDocumentNo: 'EM-OH-FG-202607',
    workOrderId: finishedOrder.id,
  })

  summary.production = {
    completedFinishedOrder,
    completedSemiOrder,
    finishedOverhead,
    postedCopperReturn,
    postedFinishedIssue,
    postedFinishedReceipt,
    postedFinishedReport,
    postedNameplateSupplement,
    postedSemiIssue,
    postedSemiReceipt,
    postedSemiReport,
    semiLabor,
  }
  return summary.production
}

async function createPurchaseReturnAndPayableAdjustment(master, procurement) {
  log('创建采购退货并同步应付冲减')
  const contactorLine = procurement.postedReceipt.lines.find((line) => line.materialCode === master.materials.contactor.code)
  const purchaseReturn = await post('/api/admin/procurement/returns', {
    businessDate: '2026-07-16',
    clientRequestId: 'em-purchase-return-contactor',
    remark: '接触器抽检发现 5 件外壳破损，退回供应商',
    sourceReceiptId: procurement.postedReceipt.id,
    lines: [{ quantity: '5.000000', reason: '来料外观破损', sourceReceiptLineId: contactorLine.id }],
  })
  const postedPurchaseReturn = await put(`/api/admin/procurement/returns/${purchaseReturn.id}/post`)
  summary.reversal.postedPurchaseReturn = postedPurchaseReturn
  return postedPurchaseReturn
}

async function createSalesAndReceivable(master) {
  log('创建销售订单、销售出库、应收和收款')
  const { customers, materials, units, warehouses } = master
  const order = await post('/api/admin/sales/orders', {
    customerId: customers.automation.id,
    expectedShipDate: '2026-07-25',
    orderDate: '2026-07-18',
    remark: '星河智能装备 8 台低压柜订单',
    lines: [
      {
        expectedShipDate: '2026-07-25',
        lineNo: 1,
        materialId: materials.finishedCabinet.id,
        quantity: '8.000000',
        remark: '智能低压控制柜',
        unitId: units.pcs.id,
        unitPrice: '6800.000000',
      },
    ],
  })
  const confirmedOrder = await put(`/api/admin/sales/orders/${order.id}/confirm`)
  const shipment = await post(`/api/admin/sales/orders/${order.id}/shipments`, {
    businessDate: '2026-07-22',
    remark: '8 台低压柜发往星河智能装备',
    warehouseId: warehouses.finished.id,
    lines: [
      {
        lineNo: 1,
        materialId: materials.finishedCabinet.id,
        orderLineId: confirmedOrder.lines[0].id,
        quantity: '8.000000',
        remark: '成品发货',
        unitId: units.pcs.id,
      },
    ],
  })
  const postedShipment = await put(`/api/admin/sales/shipments/${shipment.id}/post`)
  const receivable = await post('/api/admin/finance/receivables', {
    dueDate: '2026-08-20',
    remark: '销售出库生成应收',
    sourceId: postedShipment.id,
    sourceType: 'SALES_SHIPMENT',
  })
  const confirmedReceivable = await put(`/api/admin/finance/receivables/${receivable.id}/confirm`)
  const receipt = await post(`/api/admin/finance/receivables/${receivable.id}/receipts`, {
    amount: '30000.00',
    method: 'BANK_TRANSFER',
    receiptDate: '2026-07-28',
    remark: '客户支付首款',
  })
  const postedReceipt = await put(`/api/admin/finance/receipts/${receipt.id}/post`)
  summary.sales = { confirmedOrder, postedShipment }
  summary.finance.confirmedReceivable = confirmedReceivable
  summary.finance.postedReceipt = postedReceipt
  return { confirmedOrder, confirmedReceivable, postedReceipt, postedShipment }
}

async function createSalesReturnAndSettlementAdjustment(master, sales) {
  log('创建销售退货和客户退款式往来冲减')
  const shipmentLine = sales.postedShipment.lines[0]
  const salesReturn = await post('/api/admin/sales/returns', {
    businessDate: '2026-07-29',
    clientRequestId: 'em-sales-return-cabinet',
    remark: '客户现场发现 1 台柜门运输变形，退回维修',
    sourceShipmentId: sales.postedShipment.id,
    lines: [{ quantity: '1.000000', reason: '运输变形退回', sourceShipmentLineId: shipmentLine.id }],
  })
  const postedSalesReturn = await put(`/api/admin/sales/returns/${salesReturn.id}/post`)
  const refundAdjustment = await post('/api/admin/finance/settlement-adjustments', {
    adjustmentType: 'REFUND',
    amount: '1200.00',
    businessDate: '2026-07-30',
    clientRequestId: 'em-settlement-refund-receipt',
    remark: '客户退货后约定退回部分已收款',
    settlementSide: 'RECEIVABLE',
    sourceId: sales.postedReceipt.id,
    sourceType: 'RECEIPT',
    targetId: sales.confirmedReceivable.id,
  })
  const postedRefundAdjustment = await put(`/api/admin/finance/settlement-adjustments/${refundAdjustment.id}/post`)
  summary.reversal.postedRefundAdjustment = postedRefundAdjustment
  summary.reversal.postedSalesReturn = postedSalesReturn
  return { postedRefundAdjustment, postedSalesReturn }
}

async function createPayableRefundAdjustment(procurement) {
  log('创建供应商退款式应付冲减')
  const adjustment = await post('/api/admin/finance/settlement-adjustments', {
    adjustmentType: 'REFUND',
    amount: '800.00',
    businessDate: '2026-07-31',
    clientRequestId: 'em-settlement-refund-payment',
    remark: '供应商返还部分预付款',
    settlementSide: 'PAYABLE',
    sourceId: procurement.postedPayment.id,
    sourceType: 'PAYMENT',
    targetId: procurement.confirmedPayable.id,
  })
  const postedAdjustment = await put(`/api/admin/finance/settlement-adjustments/${adjustment.id}/post`)
  summary.reversal.postedPayableRefundAdjustment = postedAdjustment
  return postedAdjustment
}

async function verifyReports() {
  log('核对经营报表可读取并包含净额口径')
  const dateFrom = '2026-07-01'
  const dateTo = '2026-07-31'
  const overview = await get('/api/admin/reports/overview', { dateFrom, dateTo })
  const sales = await get('/api/admin/reports/sales-summary', { dateFrom, dateTo, page: 1, pageSize: 20 })
  const procurement = await get('/api/admin/reports/procurement-summary', { dateFrom, dateTo, page: 1, pageSize: 20 })
  const inventory = await get('/api/admin/reports/inventory-stock-flow', { dateFrom, dateTo, page: 1, pageSize: 50 })
  const production = await get('/api/admin/reports/production-execution', { dateFrom, dateTo, page: 1, pageSize: 20 })
  const cost = await get('/api/admin/reports/cost-collection', { dateFrom, dateTo, page: 1, pageSize: 20 })
  const settlement = await get('/api/admin/reports/settlement-summary', { dateFrom, dateTo, page: 1, pageSize: 20 })
  const exceptions = await get('/api/admin/reports/exceptions', { dateFrom, dateTo, page: 1, pageSize: 20 })

  summary.reports = {
    costItems: cost.items?.length ?? 0,
    exceptionItems: exceptions.items?.length ?? 0,
    inventoryItems: inventory.items?.length ?? 0,
    overview,
    procurementItems: procurement.items?.length ?? 0,
    productionItems: production.items?.length ?? 0,
    salesItems: sales.items?.length ?? 0,
    settlementItems: settlement.items?.length ?? 0,
  }
}

async function refreshFinalDocuments(procurement, sales) {
  log('刷新最终业务单据状态')
  summary.procurement.finalOrder = await get(`/api/admin/procurement/orders/${procurement.confirmedOrder.id}`)
  summary.sales.finalOrder = await get(`/api/admin/sales/orders/${sales.confirmedOrder.id}`)
  summary.finance.finalPayable = await get(`/api/admin/finance/payables/${procurement.confirmedPayable.id}`)
  summary.finance.finalReceivable = await get(`/api/admin/finance/receivables/${sales.confirmedReceivable.id}`)
}

async function verifyDatabaseCounts() {
  const output = runPsql(`
select 'users' as name, count(*) from sys_user
union all select 'roles', count(*) from sys_role
union all select 'materials', count(*) from mst_material
union all select 'boms', count(*) from mfg_bom
union all select 'stock_balances', count(*) from inv_stock_balance
union all select 'stock_movements', count(*) from inv_stock_movement
union all select 'purchase_orders', count(*) from proc_purchase_order
union all select 'purchase_receipts', count(*) from proc_purchase_receipt
union all select 'purchase_returns', count(*) from proc_purchase_return
union all select 'work_orders', count(*) from mfg_work_order
union all select 'material_returns', count(*) from mfg_material_return
union all select 'material_supplements', count(*) from mfg_material_supplement
union all select 'sales_orders', count(*) from sal_sales_order
union all select 'sales_shipments', count(*) from sal_sales_shipment
union all select 'sales_returns', count(*) from sal_sales_return
union all select 'receivables', count(*) from fin_receivable
union all select 'receipts', count(*) from fin_receipt
union all select 'payables', count(*) from fin_payable
union all select 'payments', count(*) from fin_payment
union all select 'settlement_adjustments', count(*) from fin_settlement_adjustment
order by name;
`)
  summary.databaseCounts = output
}

function pick(value, keys) {
  if (!value) {
    return undefined
  }
  return Object.fromEntries(keys.filter((key) => value[key] !== undefined).map((key) => [key, value[key]]))
}

function listNamed(values, keys) {
  return Object.values(values ?? {}).map((value) => pick(value, keys))
}

function buildConsoleSummary() {
  return {
    environment: summary.environment,
    accounts: summary.accounts.map(({ displayName, password, username }) => ({ displayName, password, username })),
    masterData: {
      customers: listNamed(summary.masterData.customers, ['code', 'name', 'status']),
      materials: listNamed(summary.masterData.materials, ['code', 'name', 'materialType', 'sourceType', 'status']),
      suppliers: listNamed(summary.masterData.suppliers, ['code', 'name', 'status']),
      warehouses: listNamed(summary.masterData.warehouses, ['code', 'name', 'status']),
    },
    documents: {
      boms: {
        finishedBom: pick(summary.boms.enabledFinishedBom, ['bomCode', 'name', 'versionCode', 'status']),
        semiBom: pick(summary.boms.enabledSemiBom, ['bomCode', 'name', 'versionCode', 'status']),
      },
      finance: {
        payable: pick(summary.finance.finalPayable, [
          'payableNo',
          'sourceType',
          'status',
          'totalAmount',
          'paidAmount',
          'adjustedAmount',
          'unpaidAmount',
        ]),
        payment: pick(summary.procurement.postedPayment, ['paymentNo', 'status', 'amount']),
        receivable: pick(summary.finance.finalReceivable, [
          'receivableNo',
          'sourceType',
          'status',
          'totalAmount',
          'receivedAmount',
          'adjustedAmount',
          'unreceivedAmount',
        ]),
        receipt: pick(summary.finance.postedReceipt, ['receiptNo', 'status', 'amount']),
      },
      inventory: {
        adjustment: pick(summary.inventory.postedAdjustment, ['documentNo', 'documentType', 'status']),
        opening: pick(summary.inventory.postedOpening, ['documentNo', 'documentType', 'status']),
      },
      procurement: {
        order: pick(summary.procurement.finalOrder, ['orderNo', 'status', 'totalAmount']),
        purchaseReturn: pick(summary.reversal.postedPurchaseReturn, ['returnNo', 'status', 'totalAmount']),
        receipt: pick(summary.procurement.postedReceipt, ['receiptNo', 'status']),
      },
      production: {
        finishedWorkOrder: pick(summary.production.completedFinishedOrder, [
          'workOrderNo',
          'status',
          'plannedQuantity',
          'qualifiedQuantity',
          'receivedQuantity',
        ]),
        materialReturn: pick(summary.production.postedCopperReturn, ['returnNo', 'status']),
        materialSupplement: pick(summary.production.postedNameplateSupplement, ['supplementNo', 'status']),
        semiWorkOrder: pick(summary.production.completedSemiOrder, [
          'workOrderNo',
          'status',
          'plannedQuantity',
          'qualifiedQuantity',
          'receivedQuantity',
        ]),
      },
      reversal: {
        payableRefund: pick(summary.reversal.postedPayableRefundAdjustment, [
          'adjustmentNo',
          'settlementSide',
          'adjustmentType',
          'sourceType',
          'amount',
          'status',
        ]),
        purchaseReturnOffset: pick(summary.reversal.postedPurchaseReturn?.settlementAdjustment, [
          'adjustmentNo',
          'settlementSide',
          'adjustmentType',
          'sourceType',
          'amount',
          'status',
        ]),
        receivableRefund: pick(summary.reversal.postedRefundAdjustment, [
          'adjustmentNo',
          'settlementSide',
          'adjustmentType',
          'sourceType',
          'amount',
          'status',
        ]),
        salesReturn: pick(summary.reversal.postedSalesReturn, ['returnNo', 'status', 'totalAmount']),
      },
      sales: {
        order: pick(summary.sales.finalOrder, ['orderNo', 'status', 'totalAmount']),
        salesReturn: pick(summary.reversal.postedSalesReturn, ['returnNo', 'status', 'totalAmount']),
        shipment: pick(summary.sales.postedShipment, ['shipmentNo', 'status']),
      },
    },
    reports: summary.reports,
    databaseCounts: summary.databaseCounts?.trim(),
  }
}

function emitSummary() {
  if (SUMMARY_FILE) {
    writeFileSync(SUMMARY_FILE, JSON.stringify(summary, null, 2), 'utf8')
    log(`完整执行明细已写入：${SUMMARY_FILE}`)
  }
  console.log(JSON.stringify(buildConsoleSummary(), null, 2))
}

async function main() {
  log(`目标 API：${BASE_URL}`)
  await resetAcceptanceData()
  await login()
  await createRolesAndUsers()
  const master = await createMasterData()
  const boms = await createBoms(master)
  await createInventory(master)
  const procurement = await createProcurement(master)
  await createProduction(master, boms)
  await createPurchaseReturnAndPayableAdjustment(master, procurement)
  const sales = await createSalesAndReceivable(master)
  await createSalesReturnAndSettlementAdjustment(master, sales)
  await createPayableRefundAdjustment(procurement)
  await refreshFinalDocuments(procurement, sales)
  await verifyReports()
  await verifyDatabaseCounts()
  log('电气制造业验收数据重建完成')
  emitSummary()
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
