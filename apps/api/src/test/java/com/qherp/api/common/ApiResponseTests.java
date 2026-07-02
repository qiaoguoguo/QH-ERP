package com.qherp.api.common;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTests {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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

}
