/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.prompt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.util.Assert;

/**
 * Fluent-style class for configuring extra HTTP headers, query parameters, and body
 * fields that can be passed to Spring AI model providers.
 *
 * <p>
 * Example usage: <pre>{@code
 * chatClient.call(prompt)
 *     .options(opts -> opts
 *         .model("gpt-4")
 *         .extra(extra -> extra
 *             .header("Authorization", "Bearer x")
 *             .query("api-version", "2024-02-15-preview")
 *             .body("enable_thinking", true)
 *         )
 *     );
 * }</pre>
 *
 * @author Mark Pollack
 * @since 1.0.0
 */
public class ExtraParameters {

	private final Map<String, String> headers;

	private final Map<String, String> query;

	private final Map<String, Object> body;

	/**
	 * Creates a new ExtraParameters instance with empty collections.
	 */
	public ExtraParameters() {
		this.headers = new LinkedHashMap<>();
		this.query = new LinkedHashMap<>();
		this.body = new LinkedHashMap<>();
	}

	/**
	 * Private constructor for creating copies.
	 */
	private ExtraParameters(Map<String, String> headers, Map<String, String> query, Map<String, Object> body) {
		this.headers = new LinkedHashMap<>(headers);
		this.query = new LinkedHashMap<>(query);
		this.body = new LinkedHashMap<>(body);
	}

	/**
	 * Adds an HTTP header to be included in requests.
	 * @param key the header name, must not be null or empty
	 * @param value the header value, must not be null
	 * @return this ExtraParameters instance for fluent chaining
	 * @throws IllegalArgumentException if key is null/empty or value is null
	 */
	public ExtraParameters header(String key, String value) {
		Assert.hasText(key, "Header key must not be null or empty");
		Assert.notNull(value, "Header value must not be null");
		this.headers.put(key, value);
		return this;
	}

	/**
	 * Adds a query parameter to be included in requests.
	 * @param key the query parameter name, must not be null or empty
	 * @param value the query parameter value, must not be null
	 * @return this ExtraParameters instance for fluent chaining
	 * @throws IllegalArgumentException if key is null/empty or value is null
	 */
	public ExtraParameters query(String key, String value) {
		Assert.hasText(key, "Query parameter key must not be null or empty");
		Assert.notNull(value, "Query parameter value must not be null");
		this.query.put(key, value);
		return this;
	}

	/**
	 * Adds a body field to be included in request bodies.
	 * @param key the body field name, must not be null or empty
	 * @param value the body field value, must not be null
	 * @return this ExtraParameters instance for fluent chaining
	 * @throws IllegalArgumentException if key is null/empty or value is null
	 */
	public ExtraParameters body(String key, Object value) {
		Assert.hasText(key, "Body field key must not be null or empty");
		Assert.notNull(value, "Body field value must not be null");
		this.body.put(key, value);
		return this;
	}

	/**
	 * Returns an unmodifiable view of the headers map.
	 * @return unmodifiable map of headers
	 */
	public Map<String, String> getHeaders() {
		return Collections.unmodifiableMap(this.headers);
	}

	/**
	 * Returns an unmodifiable view of the query parameters map.
	 * @return unmodifiable map of query parameters
	 */
	public Map<String, String> getQuery() {
		return Collections.unmodifiableMap(this.query);
	}

	/**
	 * Returns an unmodifiable view of the body fields map.
	 * @return unmodifiable map of body fields
	 */
	public Map<String, Object> getBody() {
		return Collections.unmodifiableMap(this.body);
	}

	/**
	 * Creates a defensive copy of this ExtraParameters instance.
	 * @return a new ExtraParameters instance with the same content
	 */
	public ExtraParameters copy() {
		return new ExtraParameters(this.headers, this.query, this.body);
	}

	/**
	 * Checks if this ExtraParameters instance has any configured parameters.
	 * @return true if headers, query, or body maps are not empty
	 */
	public boolean isEmpty() {
		return this.headers.isEmpty() && this.query.isEmpty() && this.body.isEmpty();
	}

	@Override
	public String toString() {
		return "ExtraParameters{" + "headers=" + headers + ", query=" + query + ", body=" + body + '}';
	}

}
