package com.qherp.api.security;

import com.qherp.api.system.user.SystemUserStatus;

import java.util.List;

public record CurrentUser(Long id, String username, String displayName, SystemUserStatus status, List<Role> roles,
		List<Menu> menus, List<String> permissions) {

	public CurrentUser {
		roles = roles == null ? List.of() : List.copyOf(roles);
		menus = menus == null ? List.of() : List.copyOf(menus);
		permissions = permissions == null ? List.of() : List.copyOf(permissions);
	}

	public UserProfile user() {
		return new UserProfile(this.id, this.username, this.displayName, this.status);
	}

	public record UserProfile(Long id, String username, String displayName, SystemUserStatus status) {
	}

	public record Role(Long id, String code, String name) {
	}

	public record Menu(Long id, String code, String name, String routePath, List<Menu> children) {

		public Menu {
			children = children == null ? List.of() : List.copyOf(children);
		}

	}

}
