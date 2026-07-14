alter table inv_stock_reservation
	add column cost_layer_id bigint;

do $$
begin
	if exists (
		with candidates as (
			select r.id, count(distinct b.cost_layer_id) as candidate_count
			from inv_stock_reservation r
			join mst_material m on m.id = r.material_id
			left join inv_stock_balance b
				on b.warehouse_id = r.warehouse_id
				and b.material_id = r.material_id
				and b.quality_status = r.quality_status
				and (
					(m.tracking_method in ('BATCH', 'SERIAL') and r.batch_id is null and r.serial_id is null)
					or (b.batch_id is not distinct from r.batch_id and b.serial_id is not distinct from r.serial_id)
				)
				and b.ownership_type = 'PROJECT'
				and b.project_id = r.project_id
				and b.cost_layer_id is not null
				and b.quantity_on_hand > 0
			where r.status = 'ACTIVE'
			  and r.ownership_type = 'PROJECT'
			  and r.cost_layer_id is null
			group by r.id
		)
		select 1
		from candidates
		where candidate_count <> 1
	) then
		raise exception 'active project reservation cost layer is ambiguous';
	end if;
end $$;

update inv_stock_reservation r
set cost_layer_id = candidate.cost_layer_id
from (
	select r.id, min(b.cost_layer_id) as cost_layer_id
	from inv_stock_reservation r
	join mst_material m on m.id = r.material_id
	join inv_stock_balance b
		on b.warehouse_id = r.warehouse_id
		and b.material_id = r.material_id
		and b.quality_status = r.quality_status
		and (
			(m.tracking_method in ('BATCH', 'SERIAL') and r.batch_id is null and r.serial_id is null)
			or (b.batch_id is not distinct from r.batch_id and b.serial_id is not distinct from r.serial_id)
		)
		and b.ownership_type = 'PROJECT'
		and b.project_id = r.project_id
		and b.cost_layer_id is not null
		and b.quantity_on_hand > 0
	where r.status = 'ACTIVE'
	  and r.ownership_type = 'PROJECT'
	  and r.cost_layer_id is null
	group by r.id
) candidate
where r.id = candidate.id;

update inv_stock_balance
set locked_quantity = 0
where quality_status = 'QUALIFIED';

with active_locks as (
	select r.warehouse_id, r.material_id, r.quality_status, r.batch_id, r.serial_id, r.ownership_type,
	       r.project_id, r.cost_layer_id,
	       sum(quantity - released_quantity - consumed_quantity) as locked_quantity
	from inv_stock_reservation r
	join mst_material m on m.id = r.material_id
	where r.status = 'ACTIVE'
	  and (
		(m.tracking_method = 'NONE' and r.batch_id is null and r.serial_id is null)
		or (m.tracking_method = 'BATCH' and r.batch_id is not null and r.serial_id is null)
		or (m.tracking_method = 'SERIAL' and r.serial_id is not null)
	  )
	group by r.warehouse_id, r.material_id, r.quality_status, r.batch_id, r.serial_id, r.ownership_type,
	         r.project_id, r.cost_layer_id
)
update inv_stock_balance b
set locked_quantity = active_locks.locked_quantity
from active_locks
where b.warehouse_id = active_locks.warehouse_id
  and b.material_id = active_locks.material_id
  and b.quality_status = active_locks.quality_status
  and b.batch_id is not distinct from active_locks.batch_id
  and b.serial_id is not distinct from active_locks.serial_id
  and b.ownership_type = active_locks.ownership_type
  and b.project_id is not distinct from active_locks.project_id
  and b.cost_layer_id is not distinct from active_locks.cost_layer_id;

alter table inv_stock_reservation
	add constraint fk_inv_stock_reservation_cost_layer
		foreign key (cost_layer_id) references inv_project_cost_layer (id),
	add constraint ck_inv_stock_reservation_cost_layer_identity check (
		(ownership_type = 'PUBLIC' and project_id is null and cost_layer_id is null)
		or (
			ownership_type = 'PROJECT'
			and project_id is not null
			and (status <> 'ACTIVE' or cost_layer_id is not null)
		)
	);

create index idx_inv_stock_reservation_cost_layer
	on inv_stock_reservation (cost_layer_id)
	where cost_layer_id is not null;

create index idx_inv_stock_reservation_identity
	on inv_stock_reservation (
		warehouse_id, material_id, quality_status, batch_id, serial_id,
		ownership_type, project_id, cost_layer_id, status
	);
