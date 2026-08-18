update sys_permission
set name = '查看采购在途供给',
	updated_by = 'system',
	updated_at = now(),
	version = version + 1
where code = 'procurement:supply:view'
and name = '查看有效采购供给';

update sys_permission
set name = '导出采购在途供给',
	updated_by = 'system',
	updated_at = now(),
	version = version + 1
where code = 'procurement:supply:export'
and name = '导出有效采购供给';
