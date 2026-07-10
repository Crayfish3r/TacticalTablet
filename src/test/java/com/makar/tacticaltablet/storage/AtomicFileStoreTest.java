package com.makar.tacticaltablet.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFileStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesNewFileAtomically() throws IOException {
        Path target = temporaryDirectory.resolve("nested/data.bin");
        byte[] expected = {0, 1, 2, 3, -1};

        FileSaveResult result = new AtomicFileStore().writeBytes(target, expected);

        assertEquals(FileSaveResult.Status.SUCCESS, result.status());
        assertEquals(target.toAbsolutePath().normalize(), result.target());
        assertArrayEquals(expected, Files.readAllBytes(target));
    }

    @Test
    void replacesExistingFile() throws IOException {
        Path target = temporaryDirectory.resolve("state.json");
        Files.writeString(target, "old");

        FileSaveResult result = new AtomicFileStore().writeUtf8(target, "new");

        assertEquals(FileSaveResult.Status.SUCCESS, result.status());
        assertEquals("new", Files.readString(target));
    }

    @Test
    void removesTemporaryFileWhenWriterFails() throws IOException {
        Path target = temporaryDirectory.resolve("state.json");

        FileSaveResult result = new AtomicFileStore().write(target, writer -> {
            writer.write("partial");
            throw new IOException("expected test failure");
        });

        assertEquals(FileSaveResult.Status.FAILED, result.status());
        assertTrue(result.exception().isPresent());
        try (var files = Files.list(temporaryDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
        assertFalse(Files.exists(target));
    }

    @Test
    void fallsBackWhenAtomicMoveIsUnsupported() throws IOException {
        Path target = temporaryDirectory.resolve("state.json");
        AtomicInteger moveCalls = new AtomicInteger();
        AtomicFileStore store = new AtomicFileStore((source, destination, options) -> {
            moveCalls.incrementAndGet();
            if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "test seam");
            }
            Files.move(source, destination, options);
        });

        FileSaveResult result = store.writeUtf8(target, "fallback");

        assertEquals(FileSaveResult.Status.SUCCESS, result.status());
        assertEquals(2, moveCalls.get());
        assertEquals("fallback", Files.readString(target));
    }
}
