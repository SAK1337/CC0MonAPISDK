package com.cc0mon.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed response records for the cc0mon API.
 *
 * <p>Every record carries a {@code raw} {@code Map<String,Object>} containing the
 * original parsed JSON, so callers can read fields the SDK does not yet model.
 */
public final class Models {

    private Models() {}

    /** Canonical, frozen set of energy types returned by {@code /registry}. */
    public static final Set<String> ENERGIES = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
        "Bug", "Celestial", "Dragon", "Earth", "Electric", "Fire",
        "Fossil", "Ghost", "Grass", "Ice", "Metal", "Mythic",
        "Ocean", "Rock", "Toxic", "Underworld"
    )));

    /** Canonical, frozen set of rarity tiers. */
    public static final Set<String> RARITIES = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
        "Common", "Uncommon", "Rare", "Legendary"
    )));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
            ? Collections.emptyMap()
            : MAPPER.convertValue(node, Map.class);
    }

    static String text(JsonNode parent, String... keys) {
        for (String k : keys) {
            JsonNode n = parent.get(k);
            if (n != null && !n.isNull()) return n.asText();
        }
        return null;
    }

    static Integer intOrNull(JsonNode parent, String... keys) {
        for (String k : keys) {
            JsonNode n = parent.get(k);
            if (n != null && !n.isNull() && n.canConvertToInt()) return n.asInt();
        }
        return null;
    }

    static int intOrZero(JsonNode parent, String... keys) {
        Integer v = intOrNull(parent, keys);
        return v == null ? 0 : v;
    }

    public record Token(
        int id,
        String name,
        String imageSvgUrl,
        String imagePngUrl,
        String owner,
        Map<String, Object> raw
    ) {
        public static Token fromJson(JsonNode n) {
            JsonNode images = n.get("images");
            String svg = text(n, "imageSvg", "image_svg_url");
            String png = text(n, "imagePng", "image_png_url");
            if (svg == null && images != null) svg = text(images, "svg");
            if (png == null && images != null) png = text(images, "png");
            return new Token(
                intOrZero(n, "tokenId", "id", "number"),
                text(n, "name"),
                svg,
                png,
                text(n, "owner", "holder"),
                toMap(n)
            );
        }
    }

    public record Metadata(
        String name,
        String description,
        String image,
        List<Map<String, Object>> attributes,
        Map<String, Object> raw
    ) {
        public static Metadata fromJson(JsonNode n) {
            List<Map<String, Object>> attrs = new ArrayList<>();
            JsonNode arr = n.get("attributes");
            if (arr != null && arr.isArray()) for (JsonNode a : arr) attrs.add(toMap(a));
            return new Metadata(text(n, "name"), text(n, "description"), text(n, "image"), attrs, toMap(n));
        }
    }

    public record Traits(
        int tokenId,
        String name,
        List<Map<String, Object>> attributes,
        Map<String, Object> raw
    ) {
        public static Traits fromJson(JsonNode n) {
            List<Map<String, Object>> attrs = new ArrayList<>();
            JsonNode arr = n.get("attributes");
            if (arr == null || !arr.isArray()) arr = n.get("traits");
            if (arr != null && arr.isArray()) for (JsonNode a : arr) attrs.add(toMap(a));
            return new Traits(intOrZero(n, "tokenId", "id"), text(n, "name"), attrs, toMap(n));
        }
    }

    public record OwnerInfo(String owner, Map<String, Object> raw) {
        public static OwnerInfo fromJson(JsonNode n) {
            return new OwnerInfo(text(n, "owner", "holder"), toMap(n));
        }
    }

    public record Contract(
        String name,
        String symbol,
        String address,
        String network,
        Integer totalSupply,
        Map<String, Object> raw
    ) {
        public static Contract fromJson(JsonNode n) {
            return new Contract(
                text(n, "name"),
                text(n, "symbol"),
                text(n, "address"),
                text(n, "network", "chain"),
                intOrNull(n, "totalSupply", "total_supply"),
                toMap(n)
            );
        }
    }

    public record Species(
        int number,
        String name,
        String energy,
        String rarity,
        Map<String, Object> raw
    ) {
        public static Species fromJson(JsonNode n) {
            return new Species(
                intOrZero(n, "number", "id"),
                text(n, "name"),
                text(n, "energy"),
                text(n, "rarity"),
                toMap(n)
            );
        }
    }

    public record SpeciesImage(
        String name,
        Integer tokenId,
        String svgUrl,
        String pngUrl,
        Map<String, Object> raw
    ) {
        public static SpeciesImage fromJson(JsonNode n) {
            return new SpeciesImage(
                text(n, "name"),
                intOrNull(n, "tokenId", "token_id"),
                text(n, "svg", "svgUrl", "svg_url"),
                text(n, "png", "pngUrl", "png_url"),
                toMap(n)
            );
        }
    }

    public record CollectorItem(
        int number,
        String name,
        String energy,
        String rarity,
        boolean collected,
        List<Integer> tokenIds,
        Map<String, Object> raw
    ) {
        public static CollectorItem fromJson(JsonNode n) {
            List<Integer> tokens = new ArrayList<>();
            JsonNode arr = n.get("tokenIds");
            if (arr == null || !arr.isArray()) arr = n.get("token_ids");
            if (arr != null && arr.isArray()) {
                for (JsonNode t : arr) if (t.canConvertToInt()) tokens.add(t.asInt());
            }
            boolean collected = n.has("collected") && n.get("collected").asBoolean(false);
            return new CollectorItem(
                intOrZero(n, "number", "id"),
                text(n, "name"),
                text(n, "energy"),
                text(n, "rarity"),
                collected,
                tokens,
                toMap(n)
            );
        }
    }

    public record Collector(
        String address,
        int totalCC0mon,
        int collected,
        int missing,
        String progress,
        int totalTokensHeld,
        Map<String, Object> byEnergy,
        List<CollectorItem> checklist,
        Map<String, Object> raw
    ) {
        /** Backwards-compatible alias for {@link #checklist()}. Deprecated. */
        @Deprecated
        public List<CollectorItem> items() { return checklist; }

        public static Collector fromJson(JsonNode n) {
            List<CollectorItem> checklist = new ArrayList<>();
            JsonNode arr = n.get("checklist");
            if (arr == null || !arr.isArray()) arr = n.get("items");
            if (arr == null || !arr.isArray()) arr = n.get("registry");
            if (arr != null && arr.isArray()) {
                for (JsonNode a : arr) checklist.add(CollectorItem.fromJson(a));
            }
            return new Collector(
                text(n, "address"),
                intOrZero(n, "totalCC0mon", "total_cc0mon"),
                intOrZero(n, "collected"),
                intOrZero(n, "missing"),
                text(n, "progress"),
                intOrZero(n, "totalTokensHeld", "total_tokens_held"),
                toMap(n.get("byEnergy") != null ? n.get("byEnergy") : n.get("by_energy")),
                checklist,
                toMap(n)
            );
        }
    }
}
