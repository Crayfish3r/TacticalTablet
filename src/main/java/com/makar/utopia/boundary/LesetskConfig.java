package com.makar.utopia.boundary;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.makar.utopia.Utopia;
import com.makar.utopia.network.MapNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

record LesetskConfig(ResourceLocation dimension, Polygon perimeter, Polygon hill, Polygon port,
                     ReturnPoint returnPoint, List<BlockPos> portLootSpots) {
    static final String FILE_NAME = "utopia-lesetsk.json";
    private static final int MAX_CONFIG_BYTES = 64 * 1024;
    private static final String DEFAULT_RESOURCE = "/defaults/utopia/lesetsk.json";

    record ReturnPoint(double x, Double y, double z) { }

    static LesetskConfig load(Path serverConfigDirectory) {
        Path file = serverConfigDirectory.resolve(FILE_NAME);
        try {
            if (Files.notExists(file)) createDefault(file);
            try (InputStream input = Files.newInputStream(file)) {
                byte[] data = input.readNBytes(MAX_CONFIG_BYTES + 1);
                if (data.length > MAX_CONFIG_BYTES) throw new IllegalArgumentException("Configuration exceeds 64 KiB");
                return parse(parseJson(data), file);
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load Utopia city configuration " + file + ": "
                    + exception.getMessage(), exception);
        }
    }

    private static void createDefault(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] defaults = readDefaults();
        Path temp = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
        try {
            Files.write(temp, defaults);
            Files.move(temp, file);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static byte[] readDefaults() throws IOException {
        try (InputStream defaults = Utopia.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (defaults == null) throw new IOException("Missing resource " + DEFAULT_RESOURCE);
            return defaults.readAllBytes();
        }
    }

    private static JsonObject parseJson(byte[] data) {
        return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static LesetskConfig parse(JsonObject json, Path file) throws IOException {
        JsonElement dimensionValue = json.get("dimension");
        if (dimensionValue == null || !dimensionValue.isJsonPrimitive()
                || !dimensionValue.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("dimension must be a resource ID");
        }
        String dimensionName = dimensionValue.getAsString();
        ResourceLocation dimension = dimensionName.length() <= MapNetwork.MAX_DIMENSION_LENGTH
                ? ResourceLocation.tryParse(dimensionName) : null;
        if (dimension == null || dimension.getPath().isEmpty()
                || dimension.toString().length() > MapNetwork.MAX_DIMENSION_LENGTH) {
            throw new IllegalArgumentException("Invalid dimension: " + dimensionName);
        }

        Polygon perimeter = readPolygon(json, "perimeter");
        Polygon hill = readPolygon(json, "hill");
        if (!perimeter.encloses(hill)) throw new IllegalArgumentException("hill must be entirely inside perimeter");
        Polygon port = readPort(json, file, dimension, perimeter, hill);
        List<BlockPos> lootSpots = readLootSpots(json, port);

        JsonArray fallback = json.getAsJsonArray("returnPoint");
        if (fallback == null || fallback.size() < 2 || fallback.size() > 3) {
            throw new IllegalArgumentException("returnPoint must be [X, Z] or [X, Y, Z]");
        }
        double x = number(fallback.get(0));
        double z = number(fallback.get(fallback.size() - 1));
        if (!perimeter.contains(x, z)) throw new IllegalArgumentException("returnPoint must be inside perimeter");
        Double y = fallback.size() == 3 ? number(fallback.get(1)) : null;
        return new LesetskConfig(dimension, perimeter, hill, port, new ReturnPoint(x, y, z), lootSpots);
    }

    private static Polygon readPort(JsonObject json, Path file, ResourceLocation dimension,
                                    Polygon perimeter, Polygon hill) throws IOException {
        if (json.has("port")) {
            Polygon port = readPolygon(json, "port");
            if (!perimeter.encloses(port)) throw new IllegalArgumentException("port must be entirely inside perimeter");
            if (hill.intersects(port)) throw new IllegalArgumentException("port must not touch or overlap hill");
            return port;
        }

        JsonObject defaults = parseJson(readDefaults());
        Polygon port = readPolygon(defaults, "port");
        if (dimension.toString().equals(defaults.get("dimension").getAsString())
                && perimeter.encloses(port) && !hill.intersects(port)) return port;
        Utopia.LOGGER.warn("Port is disabled for {}: set 'port' inside the configured perimeter and clear of hill in {}",
                dimension, file);
        return null;
    }

    private static List<BlockPos> readLootSpots(JsonObject json, Polygon port) {
        if (!json.has("portLootSpots")) return List.of();
        JsonElement element = json.get("portLootSpots");
        if (!element.isJsonArray()) throw new IllegalArgumentException("portLootSpots must be an array");
        var positions = new LinkedHashSet<BlockPos>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonArray() || entry.getAsJsonArray().size() != 3) {
                throw new IllegalArgumentException("portLootSpots entries must be [X, Y, Z]");
            }
            JsonArray coordinates = entry.getAsJsonArray();
            BlockPos pos = new BlockPos(blockCoordinate(coordinates.get(0)), blockCoordinate(coordinates.get(1)),
                    blockCoordinate(coordinates.get(2)));
            if (port == null || !port.contains(pos.getX(), pos.getZ())) {
                throw new IllegalArgumentException("portLootSpots position must be inside port: " + pos.toShortString());
            }
            if (!positions.add(pos)) {
                throw new IllegalArgumentException("Duplicate portLootSpots position: " + pos.toShortString());
            }
        }
        return List.copyOf(positions);
    }

    private static int blockCoordinate(JsonElement element) {
        double coordinate = number(element);
        if (coordinate != Math.rint(coordinate) || coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("portLootSpots coordinates must be integers");
        }
        return (int) coordinate;
    }

    private static Polygon readPolygon(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(key + " must be an array of [X, Z] vertices");
        }
        JsonArray vertices = value.getAsJsonArray();
        if (vertices.size() < 3 || vertices.size() > Polygon.MAX_VERTICES) {
            throw new IllegalArgumentException(key + " must have 3 to " + Polygon.MAX_VERTICES + " vertices");
        }
        try {
            List<Polygon.Point> points = new ArrayList<>(vertices.size());
            for (JsonElement vertex : vertices) {
                JsonArray pair = vertex.getAsJsonArray();
                if (pair.size() != 2) throw new IllegalArgumentException("Expected a vertex in [X, Z] format");
                points.add(new Polygon.Point(number(pair.get(0)), number(pair.get(1))));
            }
            return new Polygon(points);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(key + ": " + exception.getMessage(), exception);
        }
    }

    private static double number(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Coordinates must be JSON numbers");
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Coordinates must be finite");
        return number;
    }
}
