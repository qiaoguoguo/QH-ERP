package com.qherp.api.auth;

import com.qherp.api.common.ApiErrorCode;
import com.qherp.api.common.BusinessException;
import com.qherp.api.security.CurrentUser;
import com.qherp.api.security.CurrentUserService;
import com.qherp.api.system.user.SystemUser;
import com.qherp.api.system.user.SystemUserRepository;
import com.qherp.api.system.user.SystemUserStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AuthService {

	private final SystemUserRepository userRepository;

	private final CurrentUserService currentUserService;

	private final PasswordEncoder passwordEncoder;

	private final JdbcTemplate jdbcTemplate;

	public AuthService(SystemUserRepository userRepository, CurrentUserService currentUserService,
			PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
		this.userRepository = userRepository;
		this.currentUserService = currentUserService;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public AuthSession login(String username, String password) {
		SystemUser user = this.userRepository.findByUsername(username)
			.orElseThrow(() -> new BusinessException(ApiErrorCode.AUTH_UNAUTHORIZED));

		if (!this.passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new BusinessException(ApiErrorCode.AUTH_UNAUTHORIZED);
		}
		if (user.getStatus() == SystemUserStatus.DISABLED) {
			throw new BusinessException(ApiErrorCode.AUTH_ACCOUNT_DISABLED);
		}

		this.jdbcTemplate.update("update sys_user set last_login_at = ?, updated_at = ? where id = ?",
				OffsetDateTime.now(), OffsetDateTime.now(), user.getId());
		CurrentUser currentUser = this.currentUserService.findCurrentUser(user.getId())
			.orElseThrow(() -> new BusinessException(ApiErrorCode.AUTH_ACCOUNT_DISABLED));
		return AuthSession.from(currentUser);
	}

	public record AuthSession(CurrentUser.UserProfile user, List<CurrentUser.Role> roles,
			List<CurrentUser.Menu> menus, List<String> permissions) {

		public AuthSession {
			roles = roles == null ? List.of() : List.copyOf(roles);
			menus = menus == null ? List.of() : List.copyOf(menus);
			permissions = permissions == null ? List.of() : List.copyOf(permissions);
		}

		static AuthSession from(CurrentUser currentUser) {
			return new AuthSession(currentUser.user(), currentUser.roles(), currentUser.menus(),
					currentUser.permissions());
		}

	}

}
