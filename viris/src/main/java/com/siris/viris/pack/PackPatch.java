package com.siris.viris.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.siris.viris.Viris;
import dev.vitrail.pack.option.EngineDefines;
import dev.vitrail.pack.source.DimensionSet;
import dev.vitrail.pack.source.IncludeExpander;
import dev.vitrail.pack.source.OpenedPack;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A shader pack's Voxy support, read the way Iris hands it to Voxy: {@code voxy.json} with
 * {@code voxy_opaque.glsl} / {@code voxy_translucent.glsl} / {@code voxy_taa.glsl} next to it in the
 * dimension's folder, includes expanded and conditionals settled with the player's pack settings (by
 * Vitrail's own expander), so only the lines that count are left.
 */
public record PackPatch(
    boolean excludeFromVanillaDepth,
    int[] opaqueTargets,
    int[] translucentTargets,
    Map<Integer, String> blending,
    String taa,
    List<String> uniforms,
    Map<String, String> samplers,
    float[] renderScale,
    boolean useViewportDims,
    boolean skipShaderDepthHackFix,
    boolean useDynamicFarPlane,
    String opaque,
    String translucent
) {

    /** Patch format Voxy 0.2.20 reads. */
    private static final int VERSION = 1;

    /** The pack's patch for its current dimension folder, or empty when the pack has none. */
    public static Optional<PackPatch> load(VitrailBridge.Pack pack) {
        try (OpenedPack opened = OpenedPack.open(pack.path(), pack.chosen(), pack.profile())) {
            IncludeExpander expander = new IncludeExpander(opened.source(), opened.settings());
            // Vitrail names the dimension (minecraft:overworld); the pack's folder for it is world0 or what
            // its dimension.properties says.
            String place = DimensionSet.discover(opened.source()).place(pack.world());
            String json = read(opened, expander, place, "voxy.json", false);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            JsonObject data = parse(json);
            if (data.has("version") && data.get("version").getAsInt() != VERSION) {
                Viris.LOGGER.warn("VIRIS: the pack's voxy.json is version {}, Voxy reads {}", data.get("version"), VERSION);
                return Optional.empty();
            }
            String opaque = read(opened, expander, place, "voxy_opaque.glsl", true);
            String translucent = read(opened, expander, place, "voxy_translucent.glsl", true);
            String taa = read(opened, expander, place, "voxy_taa.glsl", true);
            if (opaque == null) {
                opaque = string(data, "opaquePatchData");
            }
            if (translucent == null) {
                translucent = string(data, "translucentPatchData");
            }
            if (taa == null) {
                taa = string(data, "taaOffset");
            }
            if (opaque == null) {
                Viris.LOGGER.warn("VIRIS: the pack's voxy.json has no opaque patch");
                return Optional.empty();
            }
            return Optional.of(new PackPatch(
                bool(data, "excludeLodsFromVanillaDepth"),
                ints(data, "opaqueDrawBuffers"),
                ints(data, "translucentDrawBuffers"),
                blending(data),
                taa,
                strings(data, "uniforms"),
                samplers(data),
                floats(data, "renderScale"),
                bool(data, "useViewportDims"),
                bool(data, "skipShaderDepthHackFix"),
                bool(data, "useDynamicFarPlane"),
                opaque,
                translucent));
        } catch (IOException | RuntimeException e) {
            Viris.LOGGER.error("VIRIS: the pack's Voxy patch could not be read", e);
            return Optional.empty();
        }
    }

    /**
     * One file of the patch, expanded: the lines that are live under the pack's settings. GLSL keeps its
     * #define/#undef lines (the GL compiler still needs the macros); every other directive is settled.
     */
    private static String read(OpenedPack opened, IncludeExpander expander, String world, String name, boolean glsl)
        throws IOException {
        Optional<Path> file = Optional.empty();
        for (String place : List.of(world + "/" + name, "shaders/" + world + "/" + name)) {
            file = opened.source().file(place);
            if (file.isPresent()) {
                break;
            }
        }
        if (file.isEmpty()) {
            return null;
        }
        IncludeExpander.ExpandedUnit unit = expander.expand(file.get());
        StringBuilder text = new StringBuilder();
        if (glsl) {
            for (Map.Entry<String, String> define : EngineDefines.table(EngineDefines.machine()).entrySet()) {
                text.append("#define ").append(define.getKey()).append(' ').append(define.getValue()).append('\n');
            }
        }
        List<String> lines = unit.lines();
        for (int i = 0; i < lines.size(); i++) {
            if (!unit.isLive(i)) {
                continue;
            }
            String line = lines.get(i);
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("#")) {
                String directive = trimmed.substring(1).stripLeading();
                if (!glsl || !(directive.startsWith("define") || directive.startsWith("undef"))) {
                    continue;
                }
            }
            text.append(line).append('\n');
        }
        return text.toString();
    }

    /** voxy.json the way Voxy parses it: lenient, strings may span lines, quotes after // escaped. */
    private static JsonObject parse(String json) {
        json = json.replace("\\", "\\\\");
        StringBuilder fixed = new StringBuilder(json.length());
        for (String line : json.split("\n")) {
            int comment = line.indexOf("//");
            if (comment != -1) {
                fixed.append(line, 0, comment).append(line.substring(comment).replace("\"", "\\\""));
            } else {
                fixed.append(line);
            }
            fixed.append('\n');
        }
        String text = fixed.toString().replaceAll("void _cfi_ignoreMarker\\(\\) \\{\\}", "");
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setStrictness(Strictness.LENIENT);
        return JsonParser.parseReader(reader).getAsJsonObject();
    }

    private static String string(JsonObject data, String key) {
        JsonElement element = data.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static boolean bool(JsonObject data, String key) {
        JsonElement element = data.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static int[] ints(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || !element.isJsonArray()) {
            return new int[0];
        }
        JsonArray array = element.getAsJsonArray();
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsInt();
        }
        return out;
    }

    private static float[] floats(JsonObject data, String key) {
        JsonElement element = data.get(key);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
            return new float[] {1.0F, 1.0F};
        }
        JsonArray array = element.getAsJsonArray();
        float x = array.get(0).getAsFloat();
        float y = array.size() > 1 ? array.get(1).getAsFloat() : x;
        return new float[] {Math.max(0.01F, x), Math.max(0.01F, y)};
    }

    private static List<String> strings(JsonObject data, String key) {
        List<String> out = new ArrayList<>();
        JsonElement element = data.get(key);
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> out.add(item.getAsString()));
        }
        return out;
    }

    private static Map<String, String> samplers(JsonObject data) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonElement element = data.get("samplers");
        if (element != null && element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> out.put(entry.getKey(), entry.getValue().getAsString()));
        }
        return out;
    }

    /** Buffer index (-1 = all) -> "off" or "SRC DST SRC_A DST_A". */
    private static Map<Integer, String> blending(JsonObject data) {
        Map<Integer, String> out = new LinkedHashMap<>();
        JsonElement element = data.get("blending");
        if (element != null && element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> out.put(Integer.parseInt(entry.getKey().trim()), entry.getValue().getAsString()));
        }
        return out;
    }
}
