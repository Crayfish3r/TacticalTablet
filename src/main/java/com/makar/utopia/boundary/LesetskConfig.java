package com.makar.utopia.boundary;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.makar.utopia.Utopia;
import com.makar.utopia.network.MapNetwork;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record LesetskConfig(ResourceLocation dimension, Polygon perimeter, Polygon hill, ReturnPoint returnPoint) {
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
                return parse(JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot load Utopia city configuration " + file + ": "
                    + exception.getMessage(), exception);
        }
    }

    private static void createDefault(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (InputStream defaults = Utopia.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (defaults == null) throw new IOException("Missing resource " + DEFAULT_RESOURCE);
            Path temp = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
            try {
                Files.write(temp, defaults.readAllBytes());
                Files.move(temp, file);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static LesetskConfig parse(JsonObject json) {
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

        JsonArray fallback = json.getAsJsonArray("returnPoint");
        if (fallback == null || fallback.size() < 2 || fallback.size() > 3) {
            throw new IllegalArgumentException("returnPoint must be [X, Z] or [X, Y, Z]");
        }
        double x = number(fallback.get(0));
        double z = number(fallback.get(fallback.size() - 1));
        if (!perimeter.contains(x, z)) throw new IllegalArgumentException("returnPoint must be inside perimeter");
        Double y = fallback.size() == 3 ? number(fallback.get(1)) : null;
        return new LesetskConfig(dimension, perimeter, hill, new ReturnPoint(x, y, z));
    }

    private static Polygon readPolygon(JsonObject json, String key) {
        JsonArray vertices = json.getAsJsonArray(key);
        if (vertices == null || vertices.size() < 3 || vertices.size() > Polygon.MAX_VERTICES) {
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
