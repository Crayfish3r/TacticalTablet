package com.makar.tacticaltablet.client.casino;

import com.makar.tacticaltablet.core.TacticalTabletMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the supplied OBJ on the resource preparation worker, never from render(). */
public final class CasinoMachineModel extends SimplePreparableReloadListener<List<CasinoMachineModel.Part>> {
    public static final CasinoMachineModel INSTANCE = new CasinoMachineModel();
    public static final ResourceLocation TEXTURE = new ResourceLocation(TacticalTabletMod.MODID, "textures/block/slot_machine_casino_seamless_texture_2048.png");
    private static final ResourceLocation MODEL = new ResourceLocation(TacticalTabletMod.MODID, "models/block/slot_machine_casino.obj");
    private volatile List<Part> parts = List.of();
    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) { }
    public record Part(int movingGroup, List<Vertex> vertices) {
        public Part { vertices = List.copyOf(vertices); }
    }
    public List<Part> parts() { return parts; }

    @Override protected List<Part> prepare(ResourceManager resources, ProfilerFiller profiler) {
        List<float[]> positions = new ArrayList<>(), uvs = new ArrayList<>(), normals = new ArrayList<>();
        List<Part> result = new ArrayList<>();
        List<Vertex> vertices = new ArrayList<>();
        int group = -1;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resources.open(MODEL), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 2) continue;
                switch (fields[0]) {
                    case "o" -> {
                        if (!vertices.isEmpty()) result.add(new Part(group, vertices));
                        vertices = new ArrayList<>();
                        String name = fields[1];
                        group = name.startsWith("reel_left_") ? 0 : name.startsWith("reel_center_") ? 1
                                : name.startsWith("reel_right_") ? 2
                                : name.equals("lever_arm") || name.startsWith("lever_knob_") ? 3 : -1;
                    }
                    case "v" -> positions.add(vector(fields, 3));
                    case "vt" -> uvs.add(vector(fields, 2));
                    case "vn" -> normals.add(vector(fields, 3));
                    case "f" -> {
                        if (fields.length != 5) throw new IllegalArgumentException("Casino OBJ requires quads");
                        for (int i = 1; i < 5; i++) {
                            String[] indices = fields[i].split("/");
                            float[] p = positions.get(Integer.parseInt(indices[0]) - 1);
                            float[] uv = uvs.get(Integer.parseInt(indices[1]) - 1);
                            float[] n = normals.get(Integer.parseInt(indices[2]) - 1);
                            vertices.add(new Vertex(p[0], p[1], p[2], uv[0], 1 - uv[1], n[0], n[1], n[2]));
                        }
                    }
                    default -> { }
                }
            }
            if (!vertices.isEmpty()) result.add(new Part(group, vertices));
            if (result.isEmpty()) throw new IllegalArgumentException("Empty casino OBJ");
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load casino model " + MODEL, exception);
        }
    }
    private static float[] vector(String[] fields, int count) {
        float[] result = new float[count];
        for (int i = 0; i < count; i++) result[i] = Float.parseFloat(fields[i + 1]);
        return result;
    }
    @Override protected void apply(List<Part> prepared, ResourceManager resources, ProfilerFiller profiler) { parts = prepared; }
}
