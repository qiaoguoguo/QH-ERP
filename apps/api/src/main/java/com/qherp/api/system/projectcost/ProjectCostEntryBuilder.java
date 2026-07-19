package com.qherp.api.system.projectcost;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectCostEntryBuilder {

	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

	public EntryBuildResult build(List<ProjectCostSourceCollector.SourceLineDraft> sources,
			List<ProjectCostSourceCollector.VarianceDraft> variances) {
		BigDecimal ordinary = sumOrdinary(sources);
		BigDecimal direct = sumDirect(sources);
		List<EntryDraft> entries = new ArrayList<>();
		if (ordinary.compareTo(BigDecimal.ZERO) != 0) {
			entries.add(new EntryDraft("SOURCE_TO_WIP", null, "WIP", "INCREASE", ordinary.abs(), "来源归集至在制"));
			entries.add(new EntryDraft("WIP_TO_FINISHED", null, "FINISHED", "INCREASE", ordinary.abs(),
					"在制转完工"));
			entries.add(new EntryDraft("FINISHED_TO_DELIVERED", null, "DELIVERED", "INCREASE", ordinary.abs(),
					"完工转发货"));
		}
		if (direct.compareTo(BigDecimal.ZERO) != 0) {
			entries.add(new EntryDraft("PROJECT_DIRECT", null, "DIRECT_PROJECT", "INCREASE", direct.abs(),
					"项目直接成本"));
		}
		BigDecimal varianceAmount = variances.stream()
			.map(ProjectCostSourceCollector.VarianceDraft::varianceAmount)
			.filter((amount) -> amount != null)
			.reduce(ZERO, BigDecimal::add)
			.setScale(2, RoundingMode.HALF_UP);
		if (varianceAmount.compareTo(BigDecimal.ZERO) != 0) {
			entries.add(new EntryDraft("COST_VARIANCE", null, "DIRECT_PROJECT", "INCREASE", varianceAmount,
					"成本差异"));
		}
		return new EntryBuildResult(entries, ordinary, direct);
	}

	private BigDecimal sumOrdinary(List<ProjectCostSourceCollector.SourceLineDraft> sources) {
		return sources.stream()
			.filter((source) -> !"DIRECT_PROJECT".equals(source.costStage()))
			.map(ProjectCostSourceCollector.SourceLineDraft::calculatedAmount)
			.reduce(ZERO, BigDecimal::add)
			.setScale(2, RoundingMode.HALF_UP);
	}

	private BigDecimal sumDirect(List<ProjectCostSourceCollector.SourceLineDraft> sources) {
		return sources.stream()
			.filter((source) -> "DIRECT_PROJECT".equals(source.costStage()))
			.map(ProjectCostSourceCollector.SourceLineDraft::calculatedAmount)
			.reduce(ZERO, BigDecimal::add)
			.setScale(2, RoundingMode.HALF_UP);
	}

	public record EntryBuildResult(List<EntryDraft> entries, BigDecimal ordinaryCost, BigDecimal directProjectCost) {
	}

	public record EntryDraft(String entryType, String costCategory, String costStage, String direction,
			BigDecimal amount, String description) {
	}

}
