package com.vogella.eclipse.mcp.server.internal;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.dialect.Dialects;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The schema validator, with the meta-schema lookup made to work under OSGi and its messages in English.
 * <p>
 * The underlying library reads its bundled meta-schemas through the context class loader
 * and only falls back to its own class loader when there is none. Inside Equinox the
 * context class loader cannot see the library's resources, so it is cleared for the
 * duration of every call.
 * <p>
 * The messages follow the JVM's locale otherwise, and an application started with {@code -nl de} then tells a
 * model in German which argument it got wrong.
 */
public final class BundleJsonSchemaValidator implements JsonSchemaValidator {

	private final JsonMapper jsonMapper = JsonMapper.shared();

	private final Map<Map<String, Object>, Schema> schemas = new ConcurrentHashMap<>();

	private final SchemaRegistry registry;

	private final Schema metaSchema;

	public BundleJsonSchemaValidator() {
		registry = withoutContextClassLoader(() -> SchemaRegistry.withDefaultDialect(Dialects.getDraft202012(),
				builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build())));
		metaSchema = withoutContextClassLoader(
				() -> registry.getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"))); //$NON-NLS-1$
	}

	@Override
	public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
		return withoutContextClassLoader(() -> {
			try {
				JsonNode content = structuredContent instanceof String text ? jsonMapper.readTree(text)
						: jsonMapper.valueToTree(structuredContent);
				Schema compiled = schemas.computeIfAbsent(schema,
						key -> registry.getSchema((JsonNode) jsonMapper.valueToTree(key)));
				List<com.networknt.schema.Error> errors = compiled.validate(content);
				return errors.isEmpty() ? ValidationResponse.asValid(content.toString())
						: ValidationResponse.asInvalid("Validation failed: JSON schema validation errors: " + errors); //$NON-NLS-1$
			} catch (RuntimeException e) {
				return ValidationResponse.asInvalid("Unexpected validation error: " + e.getMessage()); //$NON-NLS-1$
			}
		});
	}

	@Override
	public ValidationResponse validateSchema(Map<String, Object> schema) {
		Object dialect = schema.get("$schema"); //$NON-NLS-1$
		if (dialect != null && !McpSchema.JSON_SCHEMA_DIALECT_2020_12.equals(dialect.toString())) {
			return ValidationResponse.asValid(null);
		}
		return withoutContextClassLoader(() -> {
			try {
				List<com.networknt.schema.Error> errors = metaSchema.validate((JsonNode) jsonMapper.valueToTree(schema));
				return errors.isEmpty() ? ValidationResponse.asValid(null)
						: ValidationResponse.asInvalid("Schema does not conform to JSON Schema 2020-12: " + errors); //$NON-NLS-1$
			} catch (RuntimeException e) {
				return ValidationResponse.asInvalid("Failed to validate schema definition: " + e.getMessage()); //$NON-NLS-1$
			}
		});
	}

	private static <T> T withoutContextClassLoader(Supplier<T> supplier) {
		Thread thread = Thread.currentThread();
		ClassLoader previous = thread.getContextClassLoader();
		thread.setContextClassLoader(null);
		try {
			return supplier.get();
		} finally {
			thread.setContextClassLoader(previous);
		}
	}
}
