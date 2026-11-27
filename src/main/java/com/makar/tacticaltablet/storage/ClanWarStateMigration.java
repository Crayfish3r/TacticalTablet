package com.makar.tacticaltablet.storage;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Retires only clan-war mode fields; preserves the rest of each JSON document verbatim as data. */
public final class ClanWarStateMigration {
    private static final AtomicFileStore STORE = new AtomicFileStore();
    private ClanWarStateMigration() { }

    public static boolean migrate(Path path) throws IOException {
        if (!Files.exists(path)) return false;
        byte[] original = Files.readAllBytes(path);
        JsonObject json = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
        boolean current = enabled(json, "clanWarSet"), next = enabled(json, "nextSetClanWar");
        if (!current && !next) return false;

        // Content-addressed backups are reusable on retry, and never overwrite a different original.
        Path backup = path.resolveSibling(path.getFileName() + ".pre-clan-war-removal." + digest(original) + ".bak");
        if (Files.exists(backup)) {
            if (!Arrays.equals(original, Files.readAllBytes(backup))) throw new IOException("Invalid migration backup: " + backup);
        } else requireSuccess(STORE.writeBytes(backup, original));
        if (current) {
            json.addProperty("clanWarRetired", true); // Allows old aggregate stats to survive later reloads too.
            json.addProperty("clanWarSet", false);
            json.addProperty("competitiveSet", false);
            if (json.has("setMode")) json.addProperty("setMode", "CASUAL");
        }
        if (next) {
            json.addProperty("nextSetClanWar", false);
            json.addProperty("nextSetCompetitive", false);
            if (json.has("nextSetMode")) json.addProperty("nextSetMode", "CASUAL");
        }
        requireSuccess(STORE.writeUtf8(path, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json)));
        return true;
    }

    private static boolean enabled(JsonObject json, String field) {
        return json.has(field) && !json.get(field).isJsonNull() && json.get(field).getAsBoolean();
    }
    private static void requireSuccess(FileSaveResult result) throws IOException {
        if (result.status() != FileSaveResult.Status.SUCCESS)
            throw new IOException(result.diagnostic(), result.exception().orElse(null));
    }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
