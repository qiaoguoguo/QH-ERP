package com.qherp.api.system.knowledge.logic;

import com.qherp.api.system.knowledge.logic.SystemLogicModels.RetrievalContext;

public interface AiKnowledgeContextProvider {

	RetrievalContext retrieve(String query, String routePath, int limit);

}
