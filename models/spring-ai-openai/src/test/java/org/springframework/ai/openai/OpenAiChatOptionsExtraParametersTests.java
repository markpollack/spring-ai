/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.openai;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.ExtraParameters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiChatOptions} ExtraParameters integration.
 *
 * @author Mark Pollack
 */
class OpenAiChatOptionsExtraParametersTests {

	@Test
	void testExtraParametersIntegration() {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("gpt-4")
			.temperature(0.7)
			.extra(extra -> extra.header("X-Custom-Header", "custom-value")
				.query("custom_param", "param-value")
				.body("custom_field", "field-value"))
			.build();

		assertThat(options.getModel()).isEqualTo("gpt-4");
		assertThat(options.getTemperature()).isEqualTo(0.7);

		ExtraParameters extraParams = options.getExtraParameters();
		assertThat(extraParams).isNotNull();
		assertThat(extraParams.getHeaders()).containsEntry("X-Custom-Header", "custom-value");
		assertThat(extraParams.getQuery()).containsEntry("custom_param", "param-value");
		assertThat(extraParams.getBody()).containsEntry("custom_field", "field-value");
	}

	@Test
	void testExtraParametersCopy() {
		OpenAiChatOptions original = OpenAiChatOptions.builder()
			.model("gpt-4")
			.extra(extra -> extra.header("X-Test", "test-value").body("test_field", "test-data"))
			.build();

		OpenAiChatOptions copied = original.copy();

		assertThat(copied.getExtraParameters()).isNotNull();
		assertThat(copied.getExtraParameters().getHeaders()).containsEntry("X-Test", "test-value");
		assertThat(copied.getExtraParameters().getBody()).containsEntry("test_field", "test-data");

		// Verify defensive copying
		assertThat(copied.getExtraParameters()).isNotSameAs(original.getExtraParameters());
	}

	@Test
	void testFromOptions() {
		OpenAiChatOptions original = OpenAiChatOptions.builder()
			.model("gpt-3.5-turbo")
			.extra(extra -> extra.header("Authorization", "Bearer token").body("custom_param", 42))
			.build();

		OpenAiChatOptions fromOptions = OpenAiChatOptions.fromOptions(original);

		assertThat(fromOptions.getModel()).isEqualTo("gpt-3.5-turbo");
		assertThat(fromOptions.getExtraParameters()).isNotNull();
		assertThat(fromOptions.getExtraParameters().getHeaders()).containsEntry("Authorization", "Bearer token");
		assertThat(fromOptions.getExtraParameters().getBody()).containsEntry("custom_param", 42);

		// Verify defensive copying
		assertThat(fromOptions.getExtraParameters()).isNotSameAs(original.getExtraParameters());
	}

	@Test
	void testNullExtraParameters() {
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-4").temperature(0.5).build();

		assertThat(options.getExtraParameters()).isNull();

		OpenAiChatOptions copied = options.copy();
		assertThat(copied.getExtraParameters()).isNull();
	}

}
