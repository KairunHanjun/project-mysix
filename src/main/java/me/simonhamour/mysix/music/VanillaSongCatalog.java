package me.simonhamour.mysix.music;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** Exact vanilla recording names; no fuzzy search or fallback to another song. */
public final class VanillaSongCatalog {
    private static final Map<String, Identifier> SONGS = load();

    private VanillaSongCatalog() {}

    public static Optional<Identifier> find(String name) {
        return Optional.ofNullable(SONGS.get(normalize(name)));
    }

    private static String normalize(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).strip()
                .replaceAll("\\s*[-–—]\\s*", " - ")
                .replaceAll("\\s+", " ");
    }

    private static Map<String, Identifier> load() {
        var stream = VanillaSongCatalog.class.getResourceAsStream("/data/mysix/vanilla_songs.json");
        if (stream == null) throw new IllegalStateException("Missing vanilla song catalog");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var songs = new LinkedHashMap<String, Identifier>();
            JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(entry ->
                    songs.put(normalize(entry.getKey()), Identifier.fromNamespaceAndPath(
                            "minecraft", entry.getValue().getAsString())));
            return Map.copyOf(songs);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load vanilla song catalog", error);
        }
    }
}
