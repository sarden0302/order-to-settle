package com.commercesettle.internalauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 빌드에 포함한 OpenAPI 명세의 작업. 요청의 메서드·경로가 어느 작업인지와 그 작업의 소유 서비스·허용 발신자를 알려 준다. */
public final class InternalOperations {
  private static final Set<String> METHODS =
      Set.of("get", "put", "post", "delete", "patch", "head", "options");
  private static final Pattern VARIABLE = Pattern.compile("\\{[^/{}]+}");

  private final List<Operation> operations;

  private InternalOperations(List<Operation> operations) {
    // 같은 요청에 여러 경로가 맞으면 변수가 적은(더 구체적인) 경로를 먼저 고른다.
    this.operations =
        operations.stream().sorted(Comparator.comparingInt(Operation::variables)).toList();
  }

  /** 명세에 {@code x-owner-service}·{@code x-actor}가 없는 작업은 호출할 수 없으므로 읽지 않는다. */
  public static InternalOperations load(InputStream specification, ObjectMapper mapper)
      throws IOException {
    JsonNode paths = mapper.readTree(specification).path("paths");
    List<Operation> loaded = new ArrayList<>();
    paths
        .properties()
        .forEach(
            path ->
                path.getValue()
                    .properties()
                    .forEach(
                        entry -> {
                          String method = entry.getKey().toLowerCase(Locale.ROOT);
                          JsonNode operation = entry.getValue();
                          String owner = operation.path("x-owner-service").asText("");
                          String actor = operation.path("x-actor").asText("");
                          if (METHODS.contains(method) && !owner.isEmpty() && !actor.isEmpty()) {
                            loaded.add(Operation.of(method, path.getKey(), owner, actor));
                          }
                        }));
    if (loaded.isEmpty()) {
      throw new IllegalStateException("OpenAPI specification has no owned operations");
    }
    return new InternalOperations(loaded);
  }

  /** 메서드와 경로가 모두 맞는 작업. 경로만 맞고 메서드가 다르면 없는 작업이다. */
  public Optional<Operation> find(String method, String path) {
    String normalized = method.toLowerCase(Locale.ROOT);
    return operations.stream()
        .filter(operation -> operation.method().equals(normalized) && operation.matches(path))
        .findFirst();
  }

  /**
   * 명세의 작업 하나.
   *
   * @param actors {@code x-actor}를 {@code |}로 나눈 값. 예: {@code BUYER}, {@code SERVICE:settlement}
   */
  public record Operation(
      String method, String template, Pattern pattern, String owner, Set<String> actors) {
    static Operation of(String method, String template, String owner, String actor) {
      Matcher variables = VARIABLE.matcher(template);
      StringBuilder regex = new StringBuilder();
      int last = 0;
      while (variables.find()) {
        regex.append(Pattern.quote(template.substring(last, variables.start()))).append("[^/]+");
        last = variables.end();
      }
      regex.append(Pattern.quote(template.substring(last)));
      Set<String> actors =
          Arrays.stream(actor.split("\\|"))
              .map(String::trim)
              .filter(value -> !value.isEmpty())
              .collect(Collectors.toUnmodifiableSet());
      return new Operation(method, template, Pattern.compile(regex.toString()), owner, actors);
    }

    boolean matches(String path) {
      return pattern.matcher(path).matches();
    }

    int variables() {
      return (int) VARIABLE.matcher(template).results().count();
    }

    /** 공개 작업은 신원 헤더를 쓰지 않는다. */
    public boolean isPublic() {
      return actors.contains("PUBLIC");
    }
  }
}
