package com.qherp.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SessionCurrentUserFilter extends OncePerRequestFilter {

	public static final String SESSION_USER_ID = "QHERP_CURRENT_USER_ID";

	private final CurrentUserService currentUserService;

	public SessionCurrentUserFilter(CurrentUserService currentUserService) {
		this.currentUserService = currentUserService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			Object userId = session.getAttribute(SESSION_USER_ID);
			if (userId instanceof Long id) {
				this.currentUserService.findCurrentUser(id).ifPresentOrElse(this::authenticate, session::invalidate);
			}
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(CurrentUser currentUser) {
		var authorities = currentUser.permissions()
			.stream()
			.map(SimpleGrantedAuthority::new)
			.toList();
		var authentication = new UsernamePasswordAuthenticationToken(currentUser, null, authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

}
