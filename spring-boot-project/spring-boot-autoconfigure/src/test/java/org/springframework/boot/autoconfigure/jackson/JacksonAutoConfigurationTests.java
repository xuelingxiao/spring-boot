/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.jackson;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.joda.cfg.FormatConfig;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.boot.jackson.JsonObjectSerializer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link JacksonAutoConfiguration}.
 *
 * @author Dave Syer
 * @author Oliver Gierke
 * @author Andy Wilkinson
 * @author Marcel Overdijk
 * @author Sebastien Deleuze
 * @author Johannes Edmeier
 * @author Grzegorz Poznachowski
 */
public class JacksonAutoConfigurationTests {

	private AnnotationConfigApplicationContext context;

	@Before
	public void setUp() {
		this.context = new AnnotationConfigApplicationContext();
	}

	@After
	public void tearDown() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void registersJodaModuleAutomatically() {
		this.context.register(JacksonAutoConfiguration.class);
		this.context.refresh();
		ObjectMapper objectMapper = this.context.getBean(ObjectMapper.class);
		assertThat(objectMapper.canSerialize(LocalDateTime.class)).isTrue();
	}

	@Test
	public void doubleModuleRegistration() throws Exception {
		this.context.register(DoubleModulesConfig.class,
				HttpMessageConvertersAutoConfiguration.class);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(mapper.writeValueAsString(new Foo())).isEqualTo("{\"foo\":\"bar\"}");
	}

	/*
	 * ObjectMapper does not contain method to get the date format of the mapper. See
	 * https://github.com/FasterXML/jackson-databind/issues/559 If such a method will be
	 * provided below tests can be simplified.
	 */

	@Test
	public void noCustomDateFormat() {
		this.context.register(JacksonAutoConfiguration.class);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(mapper.getDateFormat()).isInstanceOf(StdDateFormat.class);
	}

	@Test
	public void customDateFormat() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.date-format:yyyyMMddHHmmss")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		DateFormat dateFormat = mapper.getDateFormat();
		assertThat(dateFormat).isInstanceOf(SimpleDateFormat.class);
		assertThat(((SimpleDateFormat) dateFormat).toPattern())
				.isEqualTo("yyyyMMddHHmmss");
	}

	@Test
	public void customJodaDateTimeFormat() throws Exception {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues
				.of("spring.jackson.date-format:yyyyMMddHHmmss",
						"spring.jackson.joda-date-time-format:yyyy-MM-dd HH:mm:ss")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		DateTime dateTime = new DateTime(1988, 6, 25, 20, 30, DateTimeZone.UTC);
		assertThat(mapper.writeValueAsString(dateTime))
				.isEqualTo("\"1988-06-25 20:30:00\"");
		Date date = dateTime.toDate();
		assertThat(mapper.writeValueAsString(date)).isEqualTo("\"19880625203000\"");
	}

	@Test
	public void customDateFormatClass() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues
				.of("spring.jackson.date-format:org.springframework.boot.autoconfigure.jackson.JacksonAutoConfigurationTests.MyDateFormat")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(mapper.getDateFormat()).isInstanceOf(MyDateFormat.class);
	}

	@Test
	public void noCustomPropertyNamingStrategy() {
		this.context.register(JacksonAutoConfiguration.class);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(mapper.getPropertyNamingStrategy()).isNull();
	}

	@Test
	public void customPropertyNamingStrategyField() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.property-naming-strategy:SNAKE_CASE")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(mapper.getPropertyNamingStrategy())
				.isInstanceOf(SnakeCaseStrategy.class);
	}

	@Test
	public void customPropertyNamingStrategyClass() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues
				.of("spring.jackson.property-naming-strategy:com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(mapper.getPropertyNamingStrategy())
				.isInstanceOf(SnakeCaseStrategy.class);
	}

	@Test
	public void enableSerializationFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.serialization.indent_output:true")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(SerializationFeature.INDENT_OUTPUT.enabledByDefault()).isFalse();
		assertThat(mapper.getSerializationConfig()
				.hasSerializationFeatures(SerializationFeature.INDENT_OUTPUT.getMask()))
						.isTrue();
	}

	@Test
	public void disableSerializationFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues
				.of("spring.jackson.serialization.write_dates_as_timestamps:false")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS.enabledByDefault())
				.isTrue();
		assertThat(mapper.getSerializationConfig().hasSerializationFeatures(
				SerializationFeature.WRITE_DATES_AS_TIMESTAMPS.getMask())).isFalse();
	}

	@Test
	public void enableDeserializationFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues
				.of("spring.jackson.deserialization.use_big_decimal_for_floats:true")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS.enabledByDefault())
				.isFalse();
		assertThat(mapper.getDeserializationConfig().hasDeserializationFeatures(
				DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS.getMask())).isTrue();
	}

	@Test
	public void disableDeserializationFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues
				.of("spring.jackson.deserialization.fail-on-unknown-properties:false")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES.enabledByDefault())
				.isTrue();
		assertThat(mapper.getDeserializationConfig().hasDeserializationFeatures(
				DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES.getMask())).isFalse();
	}

	@Test
	public void enableMapperFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.mapper.require_setters_for_getters:true")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS.enabledByDefault())
				.isFalse();
		assertThat(mapper.getSerializationConfig()
				.hasMapperFeatures(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS.getMask()))
						.isTrue();
		assertThat(mapper.getDeserializationConfig()
				.hasMapperFeatures(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS.getMask()))
						.isTrue();
	}

	@Test
	public void disableMapperFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.mapper.use_annotations:false")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(MapperFeature.USE_ANNOTATIONS.enabledByDefault()).isTrue();
		assertThat(mapper.getDeserializationConfig()
				.hasMapperFeatures(MapperFeature.USE_ANNOTATIONS.getMask())).isFalse();
		assertThat(mapper.getSerializationConfig()
				.hasMapperFeatures(MapperFeature.USE_ANNOTATIONS.getMask())).isFalse();
	}

	@Test
	public void enableParserFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.parser.allow_single_quotes:true")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(JsonParser.Feature.ALLOW_SINGLE_QUOTES.enabledByDefault()).isFalse();
		assertThat(mapper.getFactory().isEnabled(JsonParser.Feature.ALLOW_SINGLE_QUOTES))
				.isTrue();
	}

	@Test
	public void disableParserFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.parser.auto_close_source:false")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(JsonParser.Feature.AUTO_CLOSE_SOURCE.enabledByDefault()).isTrue();
		assertThat(mapper.getFactory().isEnabled(JsonParser.Feature.AUTO_CLOSE_SOURCE))
				.isFalse();
	}

	@Test
	public void enableGeneratorFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.generator.write_numbers_as_strings:true")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS.enabledByDefault())
				.isFalse();
		assertThat(mapper.getFactory()
				.isEnabled(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS)).isTrue();
	}

	@Test
	public void disableGeneratorFeature() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.generator.auto_close_target:false")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(JsonGenerator.Feature.AUTO_CLOSE_TARGET.enabledByDefault()).isTrue();
		assertThat(mapper.getFactory().isEnabled(JsonGenerator.Feature.AUTO_CLOSE_TARGET))
				.isFalse();
	}

	@Test
	public void defaultObjectMapperBuilder() {
		this.context.register(JacksonAutoConfiguration.class);
		this.context.refresh();
		Jackson2ObjectMapperBuilder builder = this.context
				.getBean(Jackson2ObjectMapperBuilder.class);
		ObjectMapper mapper = builder.build();
		assertThat(MapperFeature.DEFAULT_VIEW_INCLUSION.enabledByDefault()).isTrue();
		assertThat(mapper.getDeserializationConfig()
				.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)).isFalse();
		assertThat(MapperFeature.DEFAULT_VIEW_INCLUSION.enabledByDefault()).isTrue();
		assertThat(mapper.getDeserializationConfig()
				.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)).isFalse();
		assertThat(mapper.getSerializationConfig()
				.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)).isFalse();
		assertThat(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES.enabledByDefault())
				.isTrue();
		assertThat(mapper.getDeserializationConfig()
				.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
	}

	@Test
	public void moduleBeansAndWellKnownModulesAreRegisteredWithTheObjectMapperBuilder() {
		this.context.register(ModuleConfig.class, JacksonAutoConfiguration.class);
		this.context.refresh();
		ObjectMapper objectMapper = this.context
				.getBean(Jackson2ObjectMapperBuilder.class).build();
		assertThat(this.context.getBean(CustomModule.class).getOwners())
				.contains((ObjectCodec) objectMapper);
		assertThat(objectMapper.canSerialize(LocalDateTime.class)).isTrue();
		assertThat(objectMapper.canSerialize(Baz.class)).isTrue();
	}

	@Test
	public void defaultSerializationInclusion() {
		this.context.register(JacksonAutoConfiguration.class);
		this.context.refresh();
		ObjectMapper objectMapper = this.context
				.getBean(Jackson2ObjectMapperBuilder.class).build();
		assertThat(objectMapper.getSerializationConfig().getDefaultPropertyInclusion()
				.getValueInclusion()).isEqualTo(JsonInclude.Include.USE_DEFAULTS);
	}

	@Test
	public void customSerializationInclusion() {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.default-property-inclusion:non_null")
				.applyTo(this.context);
		this.context.refresh();
		ObjectMapper objectMapper = this.context
				.getBean(Jackson2ObjectMapperBuilder.class).build();
		assertThat(objectMapper.getSerializationConfig().getDefaultPropertyInclusion()
				.getValueInclusion()).isEqualTo(JsonInclude.Include.NON_NULL);
	}

	@Test
	public void customTimeZoneFormattingADateTime() throws JsonProcessingException {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.time-zone:America/Los_Angeles")
				.applyTo(this.context);
		TestPropertyValues.of("spring.jackson.date-format:zzzz").applyTo(this.context);
		TestPropertyValues.of("spring.jackson.locale:en").applyTo(this.context);
		this.context.refresh();
		ObjectMapper objectMapper = this.context
				.getBean(Jackson2ObjectMapperBuilder.class).build();
		DateTime dateTime = new DateTime(1436966242231L, DateTimeZone.UTC);
		assertThat(objectMapper.writeValueAsString(dateTime))
				.isEqualTo("\"Pacific Daylight Time\"");
	}

	@Test
	public void customTimeZoneFormattingADate() throws JsonProcessingException {
		this.context.register(JacksonAutoConfiguration.class);
		TestPropertyValues.of("spring.jackson.time-zone:GMT+10").applyTo(this.context);
		TestPropertyValues.of("spring.jackson.date-format:z").applyTo(this.context);
		this.context.refresh();
		ObjectMapper objectMapper = this.context
				.getBean(Jackson2ObjectMapperBuilder.class).build();
		Date date = new Date(1436966242231L);
		assertThat(objectMapper.writeValueAsString(date)).isEqualTo("\"GMT+10:00\"");
	}

	@Test
	public void customLocaleWithJodaTime() throws JsonProcessingException {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.register(JacksonAutoConfiguration.class);
		TestPropertyValues
				.of("spring.jackson.locale:de_DE", "spring.jackson.date-format:zzzz",
						"spring.jackson.serialization.write-dates-with-zone-id:true")
				.applyTo(context);
		context.refresh();
		ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
		DateTime jodaTime = new DateTime(1478424650000L,
				DateTimeZone.forID("Europe/Rome"));
		assertThat(objectMapper.writeValueAsString(jodaTime))
				.startsWith("\"Mitteleuropäische ");
	}

	@Test
	public void additionalJacksonBuilderCustomization() {
		this.context.register(JacksonAutoConfiguration.class,
				ObjectMapperBuilderCustomConfig.class);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		assertThat(mapper.getDateFormat()).isInstanceOf(MyDateFormat.class);
	}

	@Test
	public void parameterNamesModuleIsAutoConfigured() {
		assertParameterNamesModuleCreatorBinding(Mode.DEFAULT,
				JacksonAutoConfiguration.class);
	}

	@Test
	public void customParameterNamesModuleCanBeConfigured() {
		assertParameterNamesModuleCreatorBinding(Mode.DELEGATING,
				ParameterNamesModuleConfig.class, JacksonAutoConfiguration.class);
	}

	@Test
	public void writeDatesAsTimestampsDefault() throws Exception {
		this.context.register(JacksonAutoConfiguration.class);
		this.context.refresh();
		ObjectMapper mapper = this.context.getBean(ObjectMapper.class);
		DateTime dateTime = new DateTime(1988, 6, 25, 20, 30, DateTimeZone.UTC);
		String expected = FormatConfig.DEFAULT_DATETIME_PRINTER.rawFormatter()
				.withZone(DateTimeZone.UTC)
				.print(dateTime);
		assertThat(mapper.writeValueAsString(dateTime)).isEqualTo("\"" + expected + "\"");
	}

	private void assertParameterNamesModuleCreatorBinding(Mode expectedMode,
			Class<?>... configClasses) {
		this.context.register(configClasses);
		this.context.refresh();
		DeserializationConfig deserializationConfig = this.context
				.getBean(ObjectMapper.class).getDeserializationConfig();
		AnnotationIntrospector annotationIntrospector = deserializationConfig
				.getAnnotationIntrospector().allIntrospectors().iterator().next();
		assertThat(ReflectionTestUtils.getField(annotationIntrospector, "creatorBinding"))
				.isEqualTo(expectedMode);
	}

	public static class MyDateFormat extends SimpleDateFormat {

		public MyDateFormat() {
			super("yyyy-MM-dd HH:mm:ss");
		}

	}

	@Configuration
	protected static class MockObjectMapperConfig {

		@Bean
		@Primary
		public ObjectMapper objectMapper() {
			return mock(ObjectMapper.class);
		}

	}

	@Configuration
	@Import(BazSerializer.class)
	protected static class ModuleConfig {

		@Bean
		public CustomModule jacksonModule() {
			return new CustomModule();
		}

	}

	@Configuration
	protected static class DoubleModulesConfig {

		@Bean
		public Module jacksonModule() {
			SimpleModule module = new SimpleModule();
			module.addSerializer(Foo.class, new JsonSerializer<Foo>() {

				@Override
				public void serialize(Foo value, JsonGenerator jgen,
						SerializerProvider provider)
								throws IOException {
					jgen.writeStartObject();
					jgen.writeStringField("foo", "bar");
					jgen.writeEndObject();
				}
			});
			return module;
		}

		@Bean
		@Primary
		public ObjectMapper objectMapper() {
			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(jacksonModule());
			return mapper;
		}

	}

	@Configuration
	protected static class ParameterNamesModuleConfig {

		@Bean
		public ParameterNamesModule parameterNamesModule() {
			return new ParameterNamesModule(JsonCreator.Mode.DELEGATING);
		}

	}

	@Configuration
	protected static class ObjectMapperBuilderCustomConfig {

		@Bean
		public Jackson2ObjectMapperBuilderCustomizer customDateFormat() {
			return (builder) -> builder.dateFormat(new MyDateFormat());
		}

	}

	protected static final class Foo {

		private String name;

		private Foo() {
		}

		static Foo create() {
			return new Foo();
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

	protected static class Bar {

		private String propertyName;

		public String getPropertyName() {
			return this.propertyName;
		}

		public void setPropertyName(String propertyName) {
			this.propertyName = propertyName;
		}

	}

	@JsonComponent
	private static class BazSerializer extends JsonObjectSerializer<Baz> {

		@Override
		protected void serializeObject(Baz value, JsonGenerator jgen,
				SerializerProvider provider) {
		}

	}

	private static class Baz {

	}

	private static class CustomModule extends SimpleModule {

		private Set<ObjectCodec> owners = new HashSet<>();

		@Override
		public void setupModule(SetupContext context) {
			this.owners.add(context.getOwner());
		}

		Set<ObjectCodec> getOwners() {
			return this.owners;
		}

	}

}
