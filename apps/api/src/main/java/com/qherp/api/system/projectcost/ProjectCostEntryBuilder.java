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
		BigDecimal wip = sumStage(sources, "WIP");
		BigDecimal finished = sumStage(sources, "FINISHED");
		BigDecimal delivered = sumStage(sources, "DELIVERED");
		BigDecimal direct = sumDirect(sources);
		BigDecimal ordinary = wip.add(finished).add(delivered).setScale(2, RoundingMode.HALF_UP);
		BigDecimal finishedTransfer = finished.add(delivered).setScale(2, RoundingMode.HALF_UP);
		List<EntryDraft> entries = new ArrayList<>();
		if (ordinary.compareTo(BigDecimal.ZERO) != 0) {
			entries.add(entry("SOURCE_TO_WIP", "WIP", ordinary, "来源归集至在制"));
		}
		if (finishedTransfer.compareTo(BigDecimal.ZERO) != 0) {
			entries.add(entry("WIP_TO_FINISHED", "FINISHED", finishedTransfer, "在制转完工"));
		}
		if (delivered.compareTo(BigDecimal.ZERO) != 0) {
			entries.add(entry("FINISHED_TO_DELIVERED", "DELIVERED", delivered, "完工转发货"));
		}
		if (direct.compareTo(BigDecimal.ZERO) != 0) {
			entries.add(entry("PROJECT_DIRECT", "DIRECT_PROJECT", direct, "项目直接成本"));
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

	private EntryDraft entry(String entryType, String stage, BigDecimal amount, String description) {
		return new EntryDraft(entryType, null, stage, amount.compareTo(BigDecimal.ZERO) < 0 ? "DECREASE" : "INCREASE",
				amount.abs().setScale(2, RoundingMode.HALF_UP), description);
	}

	private BigDecimal sumStage(List<ProjectCostSourceCollector.SourceLineDraft> sources, String stage) {
		return sources.stream()
			.filter((source) -> stage.equals(source.costStage()))
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
