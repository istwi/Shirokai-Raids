package com.shirokai.raids;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/** Literal, single-pass substitutions: values never become templates themselves. */
final class RaidMessages {
    private static final Map<String, String> DEFAULTS = defaults();
    private static volatile Map<String, String> current = DEFAULTS;
    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");
    private RaidMessages() {}
    private static Map<String, String> defaults() {
        try (var stream = Objects.requireNonNull(RaidMessages.class.getResourceAsStream("/shirokai/messages.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var json = new com.google.gson.Gson().fromJson(reader, JsonObject.class);
            var values = new LinkedHashMap<String, String>();
            for (var entry : json.entrySet()) values.put(entry.getKey(), entry.getValue().getAsString());
            return Collections.unmodifiableMap(values);
        } catch (IOException error) { throw new IllegalStateException("Catálogo de mensagens ausente", error); }
    }
    static Map<String, String> load(Path path) throws IOException {
        JsonFiles.defaultFile(path, DEFAULTS);
        var json = JsonFiles.read(path, JsonObject.class);
        var values = new LinkedHashMap<>(DEFAULTS);
        for (var entry : json.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                throw new IOException("messages.json: texto inválido em " + entry.getKey());
            values.put(entry.getKey(), entry.getValue().getAsString());
        }
        return Collections.unmodifiableMap(values);
    }
    static void install(Map<String, String> values) { current = Map.copyOf(values); }
    static String msg(String key, Object... arguments) { return render(current, key, arguments); }
    static String render(Map<String, String> values, String key, Object... arguments) {
        if (arguments.length % 2 != 0) throw new IllegalArgumentException("Variáveis devem ser pares nome/valor");
        String template = values.getOrDefault(key, DEFAULTS.get(key));
        if (template == null) throw new IllegalArgumentException("Mensagem inexistente: " + key);
        var replacements = new HashMap<String, String>();
        for (int i = 0; i < arguments.length; i += 2) replacements.put(arguments[i].toString(), String.valueOf(arguments[i+1]));
        var matcher = TOKEN.matcher(template);
        return matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(replacements.getOrDefault(match.group(1), match.group())));
    }
}
