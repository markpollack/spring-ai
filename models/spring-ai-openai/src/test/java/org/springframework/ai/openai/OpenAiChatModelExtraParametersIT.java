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

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiChatModel} ExtraParameters functionality using
 * MockWebServer. Validates that extra HTTP headers and body parameters are properly sent
 * to the OpenAI API.
 *
 * @author Mark Pollack
 * @since 1.1.0
 */
class OpenAiChatModelExtraParametersIT {

	private MockWebServer mockWebServer;

	private OpenAiChatModel chatModel;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start();

		// Create OpenAiApi pointing to mock server
		OpenAiApi openAiApi = OpenAiApi.builder()
			.baseUrl(mockWebServer.url("/").toString())
			.apiKey("test-api-key")
			.build();

		// Create OpenAiChatModel with required dependencies
		OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder().build();
		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
		RetryTemplate retryTemplate = RetryTemplate.builder().build();
		ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		chatModel = new OpenAiChatModel(openAiApi, defaultOptions, toolCallingManager, retryTemplate,
				observationRegistry);
	}

	@AfterEach
	void tearDown() throws IOException {
		mockWebServer.shutdown();
	}

	@Test
	void testExtraHeadersAreSentInHttpRequest() throws InterruptedException, IOException {
		// Mock successful response from OpenAI API
		MockResponse mockResponse = new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("""
					{
						"id": "chatcmpl-12345",
						"object": "chat.completion",
						"created": 1677858242,
						"model": "gpt-3.5-turbo",
						"choices": [
							{
								"index": 0,
								"message": {
									"role": "assistant",
									"content": "Hello! How can I help you today?"
								},
								"finish_reason": "stop"
							}
						],
						"usage": {
							"prompt_tokens": 10,
							"completion_tokens": 10,
							"total_tokens": 20
						}
					}
					""");
		mockWebServer.enqueue(mockResponse);

		// Create chat options with extra headers
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-3.5-turbo").extra(extra -> {
			extra.header("X-Custom-Header", "test-header-value");
			extra.header("X-Request-ID", "12345");
		}).build();

		// Debug: Check if ExtraParameters are set correctly
		System.out.println("DEBUG TEST: ExtraParameters = " + options.getExtraParameters());
		if (options.getExtraParameters() != null) {
			System.out.println("DEBUG TEST: Extra headers = " + options.getExtraParameters().getHeaders());
		}

		// Make the chat request
		Prompt prompt = new Prompt("Hello", options);
		ChatResponse response = chatModel.call(prompt);

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		// Verify the HTTP request contains extra headers
		RecordedRequest recordedRequest = mockWebServer.takeRequest();

		// Debug: Print all headers
		System.out.println("=== DEBUG: All Headers ===");
		for (String headerName : recordedRequest.getHeaders().names()) {
			System.out.println(headerName + ": " + recordedRequest.getHeader(headerName));
		}
		System.out.println("=== END DEBUG ===");

		assertThat(recordedRequest.getHeader("X-Custom-Header")).isEqualTo("test-header-value");
		assertThat(recordedRequest.getHeader("X-Request-ID")).isEqualTo("12345");
		assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
		assertThat(recordedRequest.getHeader("Content-Type")).isEqualTo("application/json");
	}

	@Test
	void testExtraBodyParametersAreSentInHttpRequest() throws InterruptedException, IOException {
		// Mock successful response from OpenAI API
		MockResponse mockResponse = new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("""
					{
						"id": "chatcmpl-12345",
						"object": "chat.completion",
						"created": 1677858242,
						"model": "gpt-3.5-turbo",
						"choices": [
							{
								"index": 0,
								"message": {
									"role": "assistant",
									"content": "Hello! How can I help you today?"
								},
								"finish_reason": "stop"
							}
						],
						"usage": {
							"prompt_tokens": 10,
							"completion_tokens": 10,
							"total_tokens": 20
						}
					}
					""");
		mockWebServer.enqueue(mockResponse);

		// Create chat options with extra body parameters
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-3.5-turbo").extra(extra -> {
			extra.body("custom_field", "test-body-value");
			extra.body("debug_mode", true);
			extra.body("priority", 42);
		}).build();

		// Make the chat request
		Prompt prompt = new Prompt("Hello", options);
		ChatResponse response = chatModel.call(prompt);

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		// Verify the HTTP request body contains extra parameters
		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		String requestBody = recordedRequest.getBody().readUtf8();

		// Debug: Print the request body
		System.out.println("=== DEBUG: Request Body ===");
		System.out.println(requestBody);
		System.out.println("=== END DEBUG ===");

		// Parse the JSON request body
		var requestJson = objectMapper.readTree(requestBody);

		// Verify standard parameters are present
		assertThat(requestJson.get("model").asText()).isEqualTo("gpt-3.5-turbo");
		assertThat(requestJson.get("messages")).isNotNull();

		// Verify extra body parameters are merged into the request
		assertThat(requestJson.get("custom_field").asText()).isEqualTo("test-body-value");
		assertThat(requestJson.get("debug_mode").asBoolean()).isTrue();
		assertThat(requestJson.get("priority").asInt()).isEqualTo(42);
	}

	@Test
	void testCombinedExtraHeadersAndBodyParameters() throws InterruptedException, IOException {
		// Mock successful response from OpenAI API
		MockResponse mockResponse = new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("""
					{
						"id": "chatcmpl-12345",
						"object": "chat.completion",
						"created": 1677858242,
						"model": "gpt-3.5-turbo",
						"choices": [
							{
								"index": 0,
								"message": {
									"role": "assistant",
									"content": "Hello! How can I help you today?"
								},
								"finish_reason": "stop"
							}
						],
						"usage": {
							"prompt_tokens": 10,
							"completion_tokens": 10,
							"total_tokens": 20
						}
					}
					""");
		mockWebServer.enqueue(mockResponse);

		// Create chat options with both extra headers and body parameters
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-3.5-turbo").temperature(0.7).extra(extra -> {
			// Extra headers
			extra.header("X-Client-Version", "1.0.0");
			extra.header("X-Trace-ID", "trace-12345");
			// Extra body parameters
			extra.body("experimental_feature", "enabled");
			extra.body("timeout_seconds", 30);
		}).build();

		// Make the chat request
		Prompt prompt = new Prompt("Tell me a joke", options);
		ChatResponse response = chatModel.call(prompt);

		// Verify response
		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		// Verify the HTTP request
		RecordedRequest recordedRequest = mockWebServer.takeRequest();

		// Verify extra headers
		assertThat(recordedRequest.getHeader("X-Client-Version")).isEqualTo("1.0.0");
		assertThat(recordedRequest.getHeader("X-Trace-ID")).isEqualTo("trace-12345");

		// Verify request body contains both standard and extra parameters
		String requestBody = recordedRequest.getBody().readUtf8();
		var requestJson = objectMapper.readTree(requestBody);

		// Standard parameters
		assertThat(requestJson.get("model").asText()).isEqualTo("gpt-3.5-turbo");
		assertThat(requestJson.get("temperature").asDouble()).isEqualTo(0.7);
		assertThat(requestJson.get("messages")).isNotNull();

		// Extra body parameters
		assertThat(requestJson.get("experimental_feature").asText()).isEqualTo("enabled");
		assertThat(requestJson.get("timeout_seconds").asInt()).isEqualTo(30);
	}

}
