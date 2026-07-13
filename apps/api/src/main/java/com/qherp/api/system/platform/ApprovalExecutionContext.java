package com.qherp.api.system.platform;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ApprovalExecutionContext {

	private final ThreadLocal<ApprovalScope> scope = new ThreadLocal<>();

	public boolean allows(String sceneCode, Long businessObjectId) {
		ApprovalScope current = this.scope.get();
		return current != null && current.sceneCode().equals(sceneCode)
				&& current.businessObjectId().equals(businessObjectId);
	}

	public <T> T run(String sceneCode, Long businessObjectId, Supplier<T> action) {
		ApprovalScope previous = this.scope.get();
		this.scope.set(new ApprovalScope(sceneCode, businessObjectId));
		try {
			return action.get();
		}
		finally {
			if (previous == null) {
				this.scope.remove();
			}
			else {
				this.scope.set(previous);
			}
		}
	}

	private record ApprovalScope(String sceneCode, Long businessObjectId) {
	}

}
