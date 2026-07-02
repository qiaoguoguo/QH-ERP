package com.qherp.api.auth;

import com.qherp.api.common.ApiResponse;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.security.CurrentUserService;
import com.qherp.api.security.SessionCurrentUserFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	private final CurrentUserService currentUserService;

	public AuthController(AuthService authService, CurrentUserService currentUserService) {
		this.authService = authService;
		this.currentUserService = currentUserService;
	}

	@PostMapping("/login")
	public ApiResponse<AuthService.AuthSession> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest) {
		AuthService.AuthSession sessionUser = this.authService.login(request.username(), request.password());
		servletRequest.getSession(true)
			.setAttribute(SessionCurrentUserFilter.SESSION_USER_ID, sessionUser.user().id());
		return ApiResponse.ok(sessionUser);
	}

	@PostMapping("/logout")
	public ApiResponse<Map<String, Object>> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ApiResponse.ok(Map.of());
	}

	@GetMapping("/me")
	public ApiResponse<CurrentUser> me() {
		return ApiResponse.ok(this.currentUserService.requireCurrentUser());
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

}
