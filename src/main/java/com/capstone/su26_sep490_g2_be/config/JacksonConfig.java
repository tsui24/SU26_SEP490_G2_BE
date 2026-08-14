package com.capstone.su26_sep490_g2_be.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Mặc định Jackson coi {@code DeserializationFeature.ACCEPT_FLOAT_AS_INT} là bật — nghĩa là một số
 * thập phân gửi cho field {@code Integer}/{@code int} (VD {@code maxParticipants: 2.5}) bị âm thầm
 * cắt về phần nguyên (2) thay vì báo lỗi. Tắt cờ này để request như vậy trả về 400 (bắt bởi
 * {@link com.capstone.su26_sep490_g2_be.exception.GlobalExceptionHandler#handleMalformedBody}) thay
 * vì lưu một giá trị mà client không hề nhập.
 *
 * <p>Dùng {@code JsonMapperBuilderCustomizer} (Jackson 3, {@code tools.jackson.*}) chứ không phải
 * {@code Jackson2ObjectMapperBuilderCustomizer} — {@code spring-boot-starter-jackson} của Boot 4 kéo
 * theo {@code tools.jackson.core:jackson-databind}, Jackson 2 cổ điển chỉ còn lại nhờ phụ thuộc bắc
 * cầu của {@code jjwt-jackson} nên không phải bộ mà Spring MVC dùng để đọc request body.
 */
@Configuration
public class JacksonConfig {

	@Bean
	public JsonMapperBuilderCustomizer disableFloatToIntCoercion() {
		return builder -> builder.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
	}
}
