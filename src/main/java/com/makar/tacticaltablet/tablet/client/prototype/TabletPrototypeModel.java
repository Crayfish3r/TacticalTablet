package com.makar.tacticaltablet.tablet.client.prototype;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Resource-worker preparation touches only JSON/immutable DTOs, never world objects. */
public final class TabletPrototypeModel extends SimplePreparableReloadListener<TabletPrototypeModel.Model> {
    public static final TabletPrototypeModel INSTANCE = new TabletPrototypeModel();
    private static final ResourceLocation RESOURCE = new ResourceLocation(TacticalTabletMod.MODID, "tablet/tt01.json");
    private volatile Model model;

    public record Vec(float x, float y, float z) {
        public Vec plus(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        public Vec lerp(Vec other, float t) {
            return new Vec(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t);
        }
    }
    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) { }
    public record DisplaySurface(List<Vec> corners, Vec normal, float width, float height) {
        public DisplaySurface { corners = List.copyOf(corners); }
    }
    public record Key(double time, Vec value) { }
    public record Clip(double length, List<Key> position, List<Key> rotation) {
        public Clip { position = List.copyOf(position); rotation = List.copyOf(rotation); }
        public static Vec sample(List<Key> keys, double seconds) {
            if (seconds <= keys.get(0).time) return keys.get(0).value;
            for (int i = 1; i < keys.size(); i++) {
                Key b = keys.get(i), a = keys.get(i - 1);
                if (seconds <= b.time) return a.value.lerp(b.value, (float) ((seconds - a.time) / (b.time - a.time)));
            }
            return keys.get(keys.size() - 1).value;
        }
    }
    public record Model(ResourceLocation texture, Vec rootRotation, List<Vertex> vertices, Clip draw, Clip holster,
                        DisplaySurface display, List<Vertex> staticDisplay,
                        Map<String, List<Vertex>> hardwareKeys, Map<String, List<Vertex>> indicators) {
        public Model {
            vertices = List.copyOf(vertices); staticDisplay = List.copyOf(staticDisplay);
            hardwareKeys = Map.copyOf(hardwareKeys); indicators = Map.copyOf(indicators);
        }
        public Clip clip(String name) { return "holster".equals(name) ? holster : draw; }
    }
    public Model model() { return model; }

    @Override protected Model prepare(ResourceManager resources, ProfilerFiller profiler) {
        try (Reader reader = resources.openAsReader(RESOURCE)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unsupported TT01 resource format");
            List<Vertex> vertices = new ArrayList<>();
            List<Vertex> staticDisplay = new ArrayList<>();
            JsonObject controls = json.getAsJsonObject("controls");
            Map<String, String> keyNames = namedParts(controls.getAsJsonObject("keys"));
            Map<String, String> indicatorNames = namedParts(controls.getAsJsonObject("indicators"));
            Map<String, List<Vertex>> hardwareKeys = new HashMap<>(), indicators = new HashMap<>();
            JsonObject surface = json.getAsJsonObject("displaySurface");
            String displayCube = surface.get("cube").getAsString();
            int firstDisplayVertex = surface.get("firstVertex").getAsInt();
            for (var cube : json.getAsJsonArray("cubes")) {
                JsonObject part = cube.getAsJsonObject();
                String id = part.get("id").getAsString();
                List<Vertex> partVertices = new ArrayList<>();
                int vertexIndex = 0;
                for (var element : part.getAsJsonArray("vertices")) {
                    int index = vertexIndex++;
                    // The dynamic quad replaces this face; never draw the TACTICAL atlas tile underneath it.
                    boolean displayFace = part.get("id").getAsString().equals(displayCube)
                            && index >= firstDisplayVertex && index < firstDisplayVertex + 4;
                    JsonArray v = element.getAsJsonArray();
                    if (v.size() != 8) throw new IllegalArgumentException("Malformed TT01 vertex");
                    Vertex vertex = new Vertex(number(v,0), number(v,1), number(v,2), number(v,3),
                            number(v,4), number(v,5), number(v,6), number(v,7));
                    partVertices.add(vertex);
                    if (displayFace) staticDisplay.add(vertex);
                    else if (!indicatorNames.containsKey(id)) vertices.add(vertex);
                }
                if (keyNames.containsKey(id)) hardwareKeys.put(keyNames.get(id), List.copyOf(partVertices));
                if (indicatorNames.containsKey(id)) indicators.put(indicatorNames.get(id), List.copyOf(partVertices));
            }
            if (vertices.isEmpty() || vertices.size() % 4 != 0 || staticDisplay.size() != 4)
                throw new IllegalArgumentException("Empty/incomplete TT01 geometry or display face");
            for (TabletPage page : TabletPage.PAGES) {
                if (hardwareKeys.getOrDefault(page.hardwareKey, List.of()).size() != 24
                        || indicators.getOrDefault(page.indicator, List.of()).size() != 24)
                    throw new IllegalArgumentException("Missing complete hardware parts for " + page);
            }
            JsonObject animations = json.getAsJsonObject("animations");
            Clip draw = clip(animations.getAsJsonObject("draw")), holster = clip(animations.getAsJsonObject("holster"));
            if (draw.length != 0.65 || holster.length != 0.45) throw new IllegalArgumentException("Unexpected TT01 durations");
            List<Vec> corners = new ArrayList<>();
            Vec normal = vec(surface.getAsJsonArray("normal"), 0);
            for (var corner : surface.getAsJsonArray("corners")) {
                Vec point = vec(corner.getAsJsonArray(), 0);
                // A small outward offset prevents coplanar contact with the glass rim.
                corners.add(new Vec(point.x + normal.x * 0.0001F, point.y + normal.y * 0.0001F,
                        point.z + normal.z * 0.0001F));
            }
            if (corners.size() != 4) throw new IllegalArgumentException("Display requires four ordered corners");
            float width = distance(corners.get(0), corners.get(3)), height = distance(corners.get(0), corners.get(1));
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Degenerate display");
            return new Model(new ResourceLocation(json.get("texture").getAsString()),
                    vec(json.getAsJsonArray("rootRotation"), 0), vertices, draw, holster,
                    new DisplaySurface(corners, normal, width, height), staticDisplay, hardwareKeys, indicators);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare tablet prototype " + RESOURCE, exception);
        }
    }

    private static Map<String, String> namedParts(JsonObject parts) {
        Map<String, String> names = new HashMap<>();
        for (var entry : parts.entrySet())
            if (names.put(entry.getValue().getAsString(), entry.getKey()) != null)
                throw new IllegalArgumentException("Duplicate hardware part");
        if (names.size() != 3) throw new IllegalArgumentException("Expected three named hardware parts");
        return names;
    }

    private static Clip clip(JsonObject json) {
        double length = json.get("length").getAsDouble();
        return new Clip(length, keys(json.getAsJsonArray("position"), length), keys(json.getAsJsonArray("rotation"), length));
    }
    private static float distance(Vec a, Vec b) {
        float x = b.x - a.x, y = b.y - a.y, z = b.z - a.z;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }
    private static List<Key> keys(JsonArray array, double length) {
        List<Key> result = new ArrayList<>();
        double previous = -1;
        for (var element : array) {
            JsonArray key = element.getAsJsonArray();
            double time = key.get(0).getAsDouble();
            if (!Double.isFinite(time) || time <= previous || time < 0 || time > length)
                throw new IllegalArgumentException("Invalid TT01 keyframe time");
            result.add(new Key(time, vec(key, 1)));
            previous = time;
        }
        if (result.isEmpty() || result.get(0).time != 0 || previous != length)
            throw new IllegalArgumentException("Incomplete TT01 track");
        return result;
    }
    private static Vec vec(JsonArray array, int start) {
        return new Vec(number(array,start), number(array,start+1), number(array,start+2));
    }
    private static float number(JsonArray array, int index) {
        float value = array.get(index).getAsFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite TT01 value");
        return value;
    }
    @Override protected void apply(Model prepared, ResourceManager resources, ProfilerFiller profiler) {
        model = prepared;
        TabletDisplayTarget.release();
        TabletTerminalTheme.reload(resources);
        TabletPrototypeClient.reset();
        TacticalTabletMod.LOGGER.info("Prepared TT01 tablet prototype: {} vertices", prepared.vertices.size());
    }
}
