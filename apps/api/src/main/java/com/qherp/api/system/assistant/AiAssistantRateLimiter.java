package com.qherp.api.system.assistant;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiAssistantRateLimiter {

	private final int requestsPerMinute;
	private final ConcurrentMap<Long, Deque<Long>> requestTimes = new ConcurrentHashMap<>();

	public AiAssistantRateLimiter(
		@Value("${qherp.ai.assistant.requests-per-minute:12}") int requestsPerMinute) {
		this.requestsPerMinute = Math.max(1, requestsPerMinute);
	}

	public boolean tryAcquire(Long userId) {
		long now = System.nanoTime();
		long cutoff = now - Duration.ofMinutes(1).toNanos();
		Deque<Long> times = requestTimes.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst() < cutoff) {
				times.removeFirst();
			}
			if (times.size() >= requestsPerMinute) {
				return false;
			}
			times.addLast(now);
			return true;
		}
	}
}
