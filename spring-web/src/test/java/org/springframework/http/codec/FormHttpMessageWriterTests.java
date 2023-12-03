/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.http.codec;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.testfixture.io.buffer.AbstractLeakCheckingTests;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.testfixture.http.server.reactive.MockServerHttpResponse;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

/**
 * @author Sebastien Deleuze
 */
public class FormHttpMessageWriterTests extends AbstractLeakCheckingTests {

	private FormHttpMessageWriter writer;

	@BeforeEach
	public void createWriter() {
		writer = new FormHttpMessageWriter();
	}

	@Test
	public void canWrite() {
		assertThat(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, String.class),
				APPLICATION_FORM_URLENCODED)).isTrue();

		// No generic information
		assertThat(this.writer.canWrite(
				ResolvableType.forInstance(new LinkedMultiValueMap<String, String>()),
				APPLICATION_FORM_URLENCODED)).isTrue();

		assertThat(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, Object.class),
				null)).isFalse();

		assertThat(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(MultiValueMap.class, Object.class, String.class),
				null)).isFalse();

		assertThat(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(Map.class, String.class, String.class),
				APPLICATION_FORM_URLENCODED)).isFalse();

		assertThat(this.writer.canWrite(
				ResolvableType.forClassWithGenerics(MultiValueMap.class, String.class, String.class),
				MediaType.MULTIPART_FORM_DATA)).isFalse();
	}

	private static Stream<Arguments> writeFormArguments() {
		return Stream.of(
				Arguments.of(null, UTF_8, null, new MediaType(APPLICATION_FORM_URLENCODED, UTF_8), null),
				Arguments.of(UTF_8, UTF_8, null, new MediaType(APPLICATION_FORM_URLENCODED, UTF_8), null),
				Arguments.of(null, UTF_8, null, MediaType.APPLICATION_FORM_URLENCODED, true),
				Arguments.of(UTF_8, UTF_8, null, MediaType.APPLICATION_FORM_URLENCODED, true),
				Arguments.of(null, UTF_8, null, new MediaType(APPLICATION_FORM_URLENCODED, UTF_8), false),
				Arguments.of(UTF_8, UTF_8, null, new MediaType(APPLICATION_FORM_URLENCODED, UTF_8), false),
				Arguments.of(UTF_8, UTF_8, APPLICATION_FORM_URLENCODED, new MediaType(APPLICATION_FORM_URLENCODED, UTF_8), null),
				Arguments.of(UTF_8, UTF_8, APPLICATION_FORM_URLENCODED, APPLICATION_FORM_URLENCODED, true),
				Arguments.of(UTF_8, UTF_8, APPLICATION_FORM_URLENCODED, new MediaType(APPLICATION_FORM_URLENCODED, UTF_8), false),
				Arguments.of(ISO_8859_1, UTF_8, null, new MediaType(APPLICATION_FORM_URLENCODED, UTF_8), null),
				Arguments.of(ISO_8859_1, UTF_8, null, APPLICATION_FORM_URLENCODED, true),
				Arguments.of(ISO_8859_1, UTF_8, null, new MediaType(APPLICATION_FORM_URLENCODED, UTF_8), false),
				Arguments.of(ISO_8859_1, ISO_8859_1, APPLICATION_FORM_URLENCODED, new MediaType(APPLICATION_FORM_URLENCODED, ISO_8859_1), null),
				Arguments.of(ISO_8859_1, ISO_8859_1, APPLICATION_FORM_URLENCODED, new MediaType(APPLICATION_FORM_URLENCODED, ISO_8859_1), true),
				Arguments.of(ISO_8859_1, ISO_8859_1, APPLICATION_FORM_URLENCODED, new MediaType(APPLICATION_FORM_URLENCODED, ISO_8859_1), false),
				Arguments.of(null, UTF_8, MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), null),
				Arguments.of(UTF_8, UTF_8, MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), null),
				Arguments.of(null, UTF_8, MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; custom-param=true"), true),
				Arguments.of(UTF_8, UTF_8, MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; custom-param=true"), true),
				Arguments.of(null, UTF_8, MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), false),
				Arguments.of(UTF_8, UTF_8, MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), MediaType.parseMediaType(APPLICATION_FORM_URLENCODED_VALUE + "; charset=UTF-8; custom-param=true"), false)
		);
	}

	@ParameterizedTest
	@MethodSource("writeFormArguments")
	public void writeForm(Charset givenDefaultCharset, Charset expectedCharset, MediaType givenMediaType, MediaType expectedContentType, Boolean strictCompliance) {
		// given

		final MultiValueMap<String, String> formParameters = new LinkedMultiValueMap<>();
		formParameters.set("name 1", "value 1");
		formParameters.add("name 2", "value 2+1");
		formParameters.add("name 2", "value 2+2");
		formParameters.add("name 3", null);
		formParameters.add("name 4", "äöüß"); // these umlauts

		Map<String, Object> hints = null;
		if (strictCompliance != null) {
			hints = Hints.from(FormHttpMessageWriter.STRICT_CHARSET_COMPLIANCE_HINT, strictCompliance);
		}

		// when

		if (givenDefaultCharset != null) {
			writer.setDefaultCharset(givenDefaultCharset);
		}

		MockServerHttpResponse response = new MockServerHttpResponse(this.bufferFactory);
		this.writer.write(Mono.just(formParameters), null, givenMediaType, response, hints).block();

		// then

		StringBuilder sb = new StringBuilder();
		formParameters.forEach((name, valueList) -> {
			valueList.forEach(value -> {
				if (!sb.isEmpty()) {
					sb.append("&");
				}
				sb.append(URLEncoder.encode(name, expectedCharset));
				if (value != null) {
					sb.append("=").append(URLEncoder.encode(value, expectedCharset));
				}
			});
		});

		String expected = sb.toString();

		StepVerifier.create(response.getBody())
				.consumeNextWith(expectResponseBody(expected, expectedCharset))
				.expectComplete()
				.verify();
		HttpHeaders headers = response.getHeaders();
		assertThat(headers.getContentType()).isEqualTo(expectedContentType);
		assertThat(headers.getContentLength()).isEqualTo(expected.getBytes(expectedCharset).length);
	}

	private Consumer<DataBuffer> expectResponseBody(String expected, Charset charset) {
		return dataBuffer -> {
			String value = dataBuffer.toString(charset);
			DataBufferUtils.release(dataBuffer);
			assertThat(value).isEqualTo(expected);
		};
	}


}
