/*
 * Copyright 2023-2024 the original author or authors.
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
package org.springframework.ai.model.function;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.aop.support.NameMatchMethodPointcutAdvisor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SpringContextMethodFunctionCallbackTests {

	public enum Unit {

		CELSIUS, FAHRENHEIT

	}

	// Simple advice to track method invocations
	static class MethodTrackingAdvice implements MethodBeforeAdvice {

		private final List<String> methodCalls = new ArrayList<>();

		@Override
		public void before(Method method, Object[] args, Object target) {
			methodCalls.add(method.getName());
		}

		public List<String> getMethodCalls() {
			return methodCalls;
		}

	}

	@Configuration
	static class TestConfig {

		@Bean
		public WeatherServiceBeanImpl weatherServiceImpl() {
			return new WeatherServiceBeanImpl();
		}

		@Bean
		public ObjectMapper objectMapper() {
			return new ObjectMapper();
		}

		@Bean
		public MethodTrackingAdvice methodTrackingAdvice() {
			return new MethodTrackingAdvice();
		}

		@Bean
		public NameMatchMethodPointcutAdvisor weatherServiceAdvisor(MethodTrackingAdvice advice) {
			NameMatchMethodPointcutAdvisor advisor = new NameMatchMethodPointcutAdvisor(advice);
			advisor.setMappedName("getWeather");
			return advisor;
		}

		@Bean
		public WeatherServiceBean weatherService(WeatherServiceBeanImpl target,
				NameMatchMethodPointcutAdvisor advisor) {
			ProxyFactoryBean factory = new ProxyFactoryBean();
			factory.setTarget(target);
			factory.addAdvisor(advisor);
			factory.setInterfaces(WeatherServiceBean.class);
			return (WeatherServiceBean) factory.getObject();
		}

	}

	@Test
	void shouldResolveByBeanName() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);
		ObjectMapper mapper = context.getBean(ObjectMapper.class);
		MethodTrackingAdvice advice = context.getBean(MethodTrackingAdvice.class);

		MethodFunctionCallback callback = MethodFunctionCallback.fromSpringContext(context, "weatherService",
				"getWeather", "Get weather information for a city", mapper);

		String result = callback.call("""
				{
				    "city": "Barcelona",
				    "unit": "CELSIUS"
				}
				""");

		assertThat(result).isEqualTo("Weather in Barcelona: 23°CELSIUS");
		assertThat(advice.getMethodCalls()).contains("getWeather");
	}

	@Test
	void shouldResolveByBeanType() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);
		ObjectMapper mapper = context.getBean(ObjectMapper.class);
		MethodTrackingAdvice advice = context.getBean(MethodTrackingAdvice.class);

		MethodFunctionCallback callback = MethodFunctionCallback.fromSpringContext(context, WeatherServiceBean.class,
				"getWeather", "Get weather information for a city", mapper);

		String result = callback.call("""
				{
				    "city": "London",
				    "unit": "FAHRENHEIT"
				}
				""");

		assertThat(result).isEqualTo("Weather in London: 23°FAHRENHEIT");
		assertThat(advice.getMethodCalls()).contains("getWeather");
	}

	@Test
	void shouldFailWithInvalidBeanName() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);
		ObjectMapper mapper = context.getBean(ObjectMapper.class);

		assertThatThrownBy(() -> {
			MethodFunctionCallback.fromSpringContext(context, "nonExistentBean", "getWeather",
					"Get weather information for a city", mapper);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No bean named 'nonExistentBean' found");
	}

	@Test
	void shouldFailWithInvalidMethodName() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class);
		ObjectMapper mapper = context.getBean(ObjectMapper.class);

		assertThatThrownBy(() -> {
			MethodFunctionCallback.fromSpringContext(context, WeatherServiceBean.class, "nonExistentMethod",
					"Get weather information for a city", mapper);
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Method 'nonExistentMethod' not found");
	}

}
