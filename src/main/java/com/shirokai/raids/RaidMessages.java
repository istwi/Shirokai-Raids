package com.shirokai.raids;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
            String value = entry.getValue().getAsString();
            values.put(entry.getKey(), upgradeDefault(entry.getKey(), value));
        }
        return Collections.unmodifiableMap(values);
    }
    static String upgradeDefault(String key, String value) {
        String previous = switch (key) {
            case "chat.prefixo" -> "[Raids] {texto}";
            case "bossbar.proxima" -> "&b&lPróxima raid &r&f| {tempo}";
            case "bossbar.titulo" -> "{boss} | {vida}/{vida_maxima} HP | {tempo}";
            case "captura.nome" -> "&e{boss} &6[Captura]";
            case "captura.bossbar" -> "&eCaptura: {boss} &f| {tempo} &7| {quantidade} bolas";
            case "recompensas.entregues" -> "[Raids] Itens entregues: {quantidade}. Use /raid recompensas para tentar resgatar pendências.";
            default -> null;
        };
        String recent = switch (key) {
            case "chat.prefixo" -> "&f✦ &lSHIROKAI&r &c&lRAID&r &8» &f{texto}";
            case "bossbar.proxima" -> "&f&lSHIROKAI&r &8✦ &c&lRAID&r &8┃ &fPróxima em &e{tempo}";
            case "bossbar.titulo" -> "&f&lSHIROKAI&r &8┃ &c{boss} &8┃ &f{vida}&7/&f{vida_maxima} &c♥ &8┃ &e{tempo}";
            case "captura.bossbar" -> "&f&lSHIROKAI&r &8┃ &b&lCAPTURA&r &8┃ &b{boss} &8┃ &e{tempo} &8┃ &f{quantidade} bolas";
            case "captura.inicio" -> "&eSua cópia de {boss} está brilhando na arena! &fEnvie seu Pokémon, mire na cópia e pressione R. Você tem {quantidade} bolas e 2 minutos desde a vitória. Chance por bola: {chance}%.";
            case "vitoria.titulo" -> "&f&lSHIROKAI &8✦ &a&lVITÓRIA!";
            default -> null;
        };
        return value.equals(previous) || value.equals(recent) ? DEFAULTS.get(key) : value;
    }
    static Component chat(String text) { return component(msg("chat.prefixo", "texto", text)); }
    static void install(Map<String, String> values) { current = Map.copyOf(values); }
    /** Legacy Minecraft formatting, including &#RRGGBB. Unknown codes remain literal. */
    static Component component(String text) {
        var result = Component.empty();
        var style = Style.EMPTY;
        var part = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch == '&' || ch == '§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                Integer rgb = null;
                if (code == '#' && i + 7 < text.length()) {
                    try { rgb = Integer.parseInt(text.substring(i + 2, i + 8), 16); }
                    catch (NumberFormatException ignored) { }
                }
                var format = ChatFormatting.getByCode(code);
                if (rgb != null || format != null) {
                    if (part.length() > 0) { result.append(Component.literal(part.toString()).setStyle(style)); part.setLength(0); }
                    if (rgb != null) { style = Style.EMPTY.withColor(rgb); i += 7; }
                    else {
                        style = format == ChatFormatting.RESET ? Style.EMPTY
                            : format.isColor() ? Style.EMPTY.applyFormat(format) : style.applyFormat(format);
                        i++;
                    }
                    continue;
                }
            }
            part.append(ch);
        }
        if (part.length() > 0) result.append(Component.literal(part.toString()).setStyle(style));
        return result;
    }
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
