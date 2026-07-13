package com.qherp.api.common;

import com.qherp.api.security.ApiAccessDeniedHandler;
import com.qherp.api.security.ApiAuthenticationEntryPoint;
import com.qherp.api.system.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApiResponseTests.ProtectedController.class)
@AutoConfigureMockMvc
@Import({ ApiResponseTests.TestSecurityConfiguration.class, ApiAuthenticationEntryPoint.class,
		ApiAccessDeniedHandler.class })
class ApiResponseTests {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private AuditService auditService;

	@Test
	void successResponseContainsStableEnvelopeFields() {
		var data = Map.of("id", 1);

		ApiResponse<Map<String, Integer>> response = ApiResponse.ok(data);

		assertThat(response.success()).isTrue();
		assertThat(response.code()).isEqualTo("OK");
		assertThat(response.message()).isEqualTo("成功");
		assertThat(response.data()).isEqualTo(data);
		assertThat(response.details()).isEmpty();
		assertThat(response.traceId()).isNotBlank();
		assertThat(response.timestamp()).isNotNull();
	}

	@Test
	void businessExceptionResponseUsesErrorCodeAndHttpStatus() {
		var exception = new BusinessException(ApiErrorCode.AUTH_USERNAME_EXISTS, "用户名已存在", HttpStatus.CONFLICT,
				List.of(new ApiErrorDetail("username", "用户名已存在")));

		var response = this.handler.handleBusinessException(exception, new MockHttpServletRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().code()).isEqualTo("AUTH_USERNAME_EXISTS");
		assertThat(response.getBody().message()).isEqualTo("用户名已存在");
		assertThat(response.getBody().data()).isNull();
		assertThat(response.getBody().details()).containsExactly(new ApiErrorDetail("username", "用户名已存在"));
		assertThat(response.getBody().traceId()).isNotBlank();
		assertThat(response.getBody().timestamp()).isNotNull();
	}

	@Test
	void accountDisabledErrorCodeUsesForbiddenStatus() {
		assertThat(ApiErrorCode.AUTH_ACCOUNT_DISABLED.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void documentedAccountPermissionErrorCodesAreAvailable() {
		assertThat(ApiErrorCode.AUTH_INVALID_PASSWORD_RULE.code()).isEqualTo("AUTH_INVALID_PASSWORD_RULE");
		assertThat(ApiErrorCode.AUTH_ROLE_DISABLED.code()).isEqualTo("AUTH_ROLE_DISABLED");
		assertThat(ApiErrorCode.AUTH_PERMISSION_NOT_FOUND.code()).isEqualTo("AUTH_PERMISSION_NOT_FOUND");
		assertThat(ApiErrorCode.AUTH_INIT_ADMIN_FAILED.code()).isEqualTo("AUTH_INIT_ADMIN_FAILED");
	}

	@Test
	void unauthenticatedProtectedRequestReturnsUnifiedEnvelope() throws Exception {
		var response = this.mockMvc.perform(get("/api/protected"))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse();

		JsonNode body = this.objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("code").asText()).isEqualTo("AUTH_UNAUTHORIZED");
		assertThat(body.get("message").asText()).isEqualTo("未认证或登录已过期");
		assertThat(body.get("traceId").asText()).isNotBlank();
		assertThat(body.get("timestamp").asText()).isNotBlank();
		assertThat(body.get("details").isArray()).isTrue();
	}

	@Test
	void accessDeniedHandlerReturnsUnifiedEnvelope() throws IOException {
		var accessDeniedHandler = new ApiAccessDeniedHandler(this.objectMapper);
		var response = new MockHttpServletResponse();

		accessDeniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

		assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
		JsonNode body = this.objectMapper.readTree(response.getContentAsString());
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("code").asText()).isEqualTo("AUTH_FORBIDDEN");
		assertThat(body.get("message").asText()).isEqualTo("无访问权限");
		assertThat(body.get("traceId").asText()).isNotBlank();
		assertThat(body.get("timestamp").asText()).isNotBlank();
	}

	@Test
	void validationErrorResponseContainsFieldDetails() throws NoSuchMethodException {
		var bindingResult = new BeanPropertyBindingResult(new ValidationRequest(""), "request");
		bindingResult.rejectValue("username", "NotBlank", "用户名不能为空");
		Method method = ApiResponseTests.class.getDeclaredMethod("validationTarget", ValidationRequest.class);
		var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

		var response = this.handler.handleMethodArgumentNotValidException(exception, new MockHttpServletRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
		assertThat(response.getBody().message()).isEqualTo("参数校验失败");
		assertThat(response.getBody().details()).containsExactly(new ApiErrorDetail("username", "用户名不能为空"));
	}

	@Test
	void unexpectedExceptionReturnsSystemErrorWithoutLeakingStackTrace() {
		var exception = new IllegalStateException("database password leaked");

		var response = this.handler.handleUnexpectedException(exception, new MockHttpServletRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isFalse();
		assertThat(response.getBody().code()).isEqualTo("SYSTEM_ERROR");
		assertThat(response.getBody().message()).isEqualTo("系统异常");
		assertThat(response.getBody().message()).doesNotContain("database password leaked");
		assertThat(response.getBody().details()).isEmpty();
		assertThat(response.getBody().timestamp()).isNotNull();
	}

	@SuppressWarnings("unused")
	private void validationTarget(ValidationRequest request) {
	}

	private record ValidationRequest(String username) {
	}

	@RestController
	static class ProtectedController {

		@GetMapping("/api/protected")
		String protectedEndpoint() {
			return "ok";
		}

	}

	@TestConfiguration
	static class TestSecurityConfiguration {

		@Bean
		SecurityFilterChain securityFilterChain(HttpSecurity http, ApiAuthenticationEntryPoint authenticationEntryPoint,
				ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
			return http
				.csrf((csrf) -> csrf.disable())
				.formLogin((formLogin) -> formLogin.disable())
				.authorizeHttpRequests((authorize) -> authorize.anyRequest().authenticated())
				.exceptionHandling((exceptions) -> exceptions.authenticationEntryPoint(authenticationEntryPoint)
					.accessDeniedHandler(accessDeniedHandler))
				.httpBasic(Customizer.withDefaults())
				.build();
		}

	}

}
