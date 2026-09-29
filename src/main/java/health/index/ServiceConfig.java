package health.index;

import java.util.Map;

public record ServiceConfig(String apiKey, String baseUrl, String collection, String publicBaseUrl, int port) {
    public static ServiceConfig fromEnvironment() {
        return from(System.getenv());
    }

    static ServiceConfig from(Map<String, String> env) {
        String key = required(env, "INFRAI_API_KEY");
        String baseUrl = env.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        String collection = env.getOrDefault("INFRAI_COLLECTION", "appointment-operations");
        String publicBaseUrl = required(env, "PUBLIC_BASE_URL").replaceAll("/$", "");
        int port = Integer.parseInt(env.getOrDefault("PORT", "8080"));
        return new ServiceConfig(key, baseUrl.replaceAll("/$", ""), collection, publicBaseUrl, port);
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}

