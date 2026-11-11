package com.commercesettle.commerce.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 빌드에 포함한 정식 OpenAPI(docs/openapi.json)의 스키마로 실제 요청·응답 JSON을 검사한다. */
public final class OpenApiContracts {
  private static final String CANONICAL = "urn:commerce:canonical";
  private static final SchemaRegistry CONTRACTS = load();

  private OpenApiContracts() {}

  public static void matchesContract(String schema, String json) {
    assertThat(
            CONTRACTS
                .getSchema(SchemaLocation.of(CANONICAL + "#/components/schemas/" + schema))
                .validate(
                    json,
                    InputFormat.JSON,
                    context ->
                        context.executionConfig(config -> config.formatAssertionsEnabled(true))))
        .as(schema)
        .isEmpty();
  }

  private static SchemaRegistry load() {
    try (InputStream input =
        OpenApiContracts.class.getResourceAsStream("/contracts/openapi.json")) {
      assertThat(input).as("정식 OpenAPI 테스트 리소스").isNotNull();
      String document = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      return SchemaRegistry.withDefaultDialect(
          SpecificationVersion.DRAFT_2020_12,
          builder -> builder.schemas(Map.of(CANONICAL, document)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
