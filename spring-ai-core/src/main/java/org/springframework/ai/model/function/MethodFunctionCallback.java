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
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

/**
 * A {@link FunctionCallback} that invokes methods on objects via reflection, supporting:
 * <ul>
 * <li>Static and non-static methods</li>
 * <li>Any number of parameters (including none)</li>
 * <li>Any parameter/return types (primitives, objects, collections)</li>
 * <li>Special handling for {@link ToolContext} parameters</li>
 * </ul>
 * Automatically infers the input parameters JSON schema from method's argument types.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class MethodFunctionCallback implements FunctionCallback {

	private static Logger logger = LoggerFactory.getLogger(MethodFunctionCallback.class);

	/**
	 * Object instance that contains the method to be invoked. If the method is static
	 * this object can be null.
	 */
	private final Object functionObject;

	/**
	 * The method to be invoked.
	 */
	private final Method method;

	/**
	 * Description to help the LLM model to understand worth the method does and when to
	 * use it.
	 */
	private final String description;

	/**
	 * Internal ObjectMapper used to serialize/deserialize the method input and output.
	 */
	private final ObjectMapper mapper;

	/**
	 * The JSON schema generated from the method input parameters.
	 */
	private final String inputSchema;

	/**
	 * Flag indicating if the method accepts a {@link ToolContext} as input parameter.
	 */
	private boolean isToolContextMethod = false;

	/**
	 * Creates a new {@link MethodFunctionCallback} by looking up a bean and method from a
	 * Spring {@link ApplicationContext}. This factory method supports both Spring AOP
	 * proxies and regular beans, and can resolve beans either by name or type.
	 * @param applicationContext The Spring ApplicationContext to look up beans from
	 * @param beanNameOrType Either a String bean name or a Class type to look up the
	 * bean. If a type is provided and multiple beans of that type exist, it will prefer
	 * an AOP proxy bean if available.
	 * @param methodName The name of the method to invoke on the bean
	 * @param description A description of what the function does (used for LLM context)
	 * @param mapper The ObjectMapper to use for JSON serialization/deserialization
	 * @return A new MethodFunctionCallback instance configured with the resolved bean and
	 * method
	 * @throws IllegalArgumentException if: - any parameter is null - the bean cannot be
	 * found - the method cannot be found - the beanNameOrType is neither a String nor a
	 * Class
	 * @see ApplicationContext#getBean(String)
	 * @see ApplicationContext#getBean(Class)
	 */
	public static MethodFunctionCallback fromSpringContext(ApplicationContext applicationContext, Object beanNameOrType,
			String methodName, String description, ObjectMapper mapper) {

		Assert.notNull(applicationContext, "ApplicationContext must not be null");
		Assert.notNull(beanNameOrType, "Bean name or type must not be null");
		Assert.hasText(methodName, "Method name must not be empty");

		Object bean;
		Class<?> targetClass;
		Method method;

		if (beanNameOrType instanceof String) {
			String beanName = (String) beanNameOrType;
			try {
				bean = applicationContext.getBean(beanName);
				targetClass = AopUtils.getTargetClass(bean);
			}
			catch (BeansException e) {
				throw new IllegalArgumentException(
						String.format("No bean named '%s' found in application context", beanName), e);
			}
		}
		else if (beanNameOrType instanceof Class<?> type) {
			try {
				// Get all matching bean names first
				String[] beanNames = applicationContext.getBeanNamesForType(type);
				if (beanNames.length == 0) {
					throw new IllegalArgumentException(
							String.format("No bean of type '%s' found in application context", type.getName()));
				}
				// Prefer the proxy bean if available, otherwise take the first one
				String beanName = null;
				for (String name : beanNames) {
					Object candidateBean = applicationContext.getBean(name);
					if (AopUtils.isAopProxy(candidateBean)) {
						beanName = name;
						break;
					}
				}
				if (beanName == null) {
					beanName = beanNames[0];
				}
				bean = applicationContext.getBean(beanName);
				targetClass = type;
			}
			catch (BeansException e) {
				throw new IllegalArgumentException(
						String.format("Error resolving bean of type '%s' in application context", type.getName()), e);
			}
		}
		else {
			throw new IllegalArgumentException(
					"beanNameOrType must be either a String (bean name) or Class (bean type)");
		}

		if (AopUtils.isAopProxy(bean)) {
			Class<?>[] interfaces = bean.getClass().getInterfaces();
			method = findMethodInInterfaces(interfaces, methodName);
		}
		else {
			method = findMethod(targetClass, methodName);
		}

		if (method == null) {
			throw new IllegalArgumentException(
					String.format("Method '%s' not found in class '%s'", methodName, targetClass.getName()));
		}

		return builder().functionObject(bean).method(method).description(description).mapper(mapper).build();
	}

	private static Method findMethodInInterfaces(Class<?>[] interfaces, String methodName) {
		for (Class<?> iface : interfaces) {
			Method method = findMethod(iface, methodName);
			if (method != null) {
				return method;
			}
		}
		return null;
	}

	private static Method findMethod(Class<?> beanClass, String methodName) {
		Method[] methods = ReflectionUtils.getAllDeclaredMethods(beanClass);
		return Stream.of(methods).filter(m -> m.getName().equals(methodName)).findFirst().orElse(null);
	}

	public MethodFunctionCallback(Object functionObject, Method method, String description, ObjectMapper mapper) {

		Assert.notNull(method, "Method must not be null");
		Assert.notNull(mapper, "ObjectMapper must not be null");
		Assert.hasText(description, "Description must not be empty");

		this.method = method;
		this.description = description;
		this.mapper = mapper;
		this.functionObject = functionObject;

		Assert.isTrue(this.functionObject != null || Modifier.isStatic(this.method.getModifiers()),
				"Function object must be provided for non-static methods!");

		// Generate the JSON schema from the method input parameters
		Map<String, Class<?>> methodParameters = Stream.of(method.getParameters())
			.collect(Collectors.toMap(param -> param.getName(), param -> param.getType()));

		this.inputSchema = this.generateJsonSchema(methodParameters);

		logger.info("Generated JSON Schema: \n:" + this.inputSchema);
	}

	@Override
	public String getName() {
		return this.method.getName();
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public String getInputTypeSchema() {
		return this.inputSchema;
	}

	@Override
	public String call(String functionInput) {
		return this.call(functionInput, null);
	}

	public String call(String functionInput, ToolContext toolContext) {

		try {

			// If the toolContext is not empty but the method does not accept ToolContext
			// as
			// input parameter then throw an exception.
			if (toolContext != null && !CollectionUtils.isEmpty(toolContext.getContext())
					&& !this.isToolContextMethod) {
				throw new IllegalArgumentException("Configured method does not accept ToolContext as input parameter!");
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> map = this.mapper.readValue(functionInput, Map.class);

			// ReflectionUtils.findMethod
			Object[] methodArgs = Stream.of(this.method.getParameters()).map(parameter -> {
				Class<?> type = parameter.getType();
				if (ClassUtils.isAssignable(type, ToolContext.class)) {
					return toolContext;
				}
				Object rawValue = map.get(parameter.getName());
				return this.toJavaType(rawValue, type);
			}).toArray();

			Object response = ReflectionUtils.invokeMethod(this.method, this.functionObject, methodArgs);

			var returnType = this.method.getReturnType();
			if (returnType == Void.TYPE) {
				return "Done";
			}
			else if (returnType == Class.class || returnType.isRecord() || returnType == List.class
					|| returnType == Map.class) {
				return ModelOptionsUtils.toJsonString(response);

			}
			return "" + response;
		}
		catch (Exception e) {
			ReflectionUtils.handleReflectionException(e);
			return null;
		}
	}

	/**
	 * Generates a JSON schema from the given named classes.
	 * @param namedClasses The named classes to generate the schema from.
	 * @return The generated JSON schema.
	 */
	protected String generateJsonSchema(Map<String, Class<?>> namedClasses) {
		try {
			JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(this.mapper);

			ObjectNode rootNode = this.mapper.createObjectNode();
			rootNode.put("$schema", "https://json-schema.org/draft/2020-12/schema");
			rootNode.put("type", "object");
			ObjectNode propertiesNode = rootNode.putObject("properties");

			for (Map.Entry<String, Class<?>> entry : namedClasses.entrySet()) {
				String className = entry.getKey();
				Class<?> clazz = entry.getValue();

				if (ClassUtils.isAssignable(clazz, ToolContext.class)) {
					// Skip the ToolContext class from the schema generation.
					this.isToolContextMethod = true;
					continue;
				}

				JsonSchema schema = schemaGen.generateSchema(clazz);
				JsonNode schemaNode = this.mapper.valueToTree(schema);
				propertiesNode.set(className, schemaNode);
			}

			return this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Converts the given value to the specified Java type.
	 * @param value The value to convert.
	 * @param javaType The Java type to convert to.
	 * @return Returns the converted value.
	 */
	protected Object toJavaType(Object value, Class<?> javaType) {

		if (value == null) {
			return null;
		}

		javaType = ClassUtils.resolvePrimitiveIfNecessary(javaType);

		if (javaType == String.class) {
			return value.toString();
		}
		else if (javaType == Integer.class) {
			return Integer.parseInt(value.toString());
		}
		else if (javaType == Long.class) {
			return Long.parseLong(value.toString());
		}
		else if (javaType == Double.class) {
			return Double.parseDouble(value.toString());
		}
		else if (javaType == Float.class) {
			return Float.parseFloat(value.toString());
		}
		else if (javaType == Boolean.class) {
			return Boolean.parseBoolean(value.toString());
		}
		else if (javaType.isEnum()) {
			return Enum.valueOf((Class<Enum>) javaType, value.toString());
		}
		// else if (type == Class.class || type.isRecord()) {
		// return ModelOptionsUtils.mapToClass((Map<String, Object>) value, type);
		// }

		try {
			String json = this.mapper.writeValueAsString(value);
			return this.mapper.readValue(json, javaType);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates a new {@link Builder} for the {@link MethodFunctionCallback}.
	 * @return The builder.
	 */
	public static MethodFunctionCallback.Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for the {@link MethodFunctionCallback}.
	 */
	public static class Builder {

		private Method method;

		private String description;

		private ObjectMapper mapper = ModelOptionsUtils.OBJECT_MAPPER;

		private Object functionObject = null;

		public MethodFunctionCallback.Builder functionObject(Object functionObject) {
			this.functionObject = functionObject;
			return this;
		}

		public MethodFunctionCallback.Builder method(Method method) {
			Assert.notNull(method, "Method must not be null");
			this.method = method;
			return this;
		}

		public MethodFunctionCallback.Builder description(String description) {
			Assert.hasText(description, "Description must not be empty");
			this.description = description;
			return this;
		}

		public MethodFunctionCallback.Builder mapper(ObjectMapper mapper) {
			this.mapper = mapper;
			return this;
		}

		public MethodFunctionCallback build() {
			return new MethodFunctionCallback(this.functionObject, this.method, this.description, this.mapper);
		}

	}

}
