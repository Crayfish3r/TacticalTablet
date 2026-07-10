package com.makar.tacticaltablet.storage;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Writes a complete replacement file without exposing a partially written target. */
public final class AtomicFileStore {

    @FunctionalInterface
    public interface ContentWriter {
        void write(Writer writer) throws IOException;
    }

    @FunctionalInterface
    public interface MoveOperation {
        void move(Path source, Path target, CopyOption... options) throws IOException;
    }

    @FunctionalInterface
    private interface TemporaryFileWriter {
        void write(Path temporary) throws IOException;
    }

    private final MoveOperation moveOperation;

    public AtomicFileStore() {
        this(Files::move);
    }

    public AtomicFileStore(MoveOperation moveOperation) {
        this.moveOperation = Objects.requireNonNull(moveOperation, "moveOperation");
    }

    public FileSaveResult writeUtf8(Path target, String content) {
        Objects.requireNonNull(content, "content");
        return write(target, writer -> writer.write(content));
    }

    public FileSaveResult writeBytes(Path target, byte[] content) {
        Objects.requireNonNull(content, "content");
        return writeTemporary(target, temporary -> Files.write(temporary, content));
    }

    public FileSaveResult write(Path target, ContentWriter contentWriter) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(contentWriter, "contentWriter");
        return writeTemporary(target, temporary -> {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                contentWriter.write(writer);
            }
        });
    }

    private FileSaveResult writeTemporary(Path target, TemporaryFileWriter temporaryWriter) {
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null || normalizedTarget.getFileName() == null) {
            return FileSaveResult.failure(normalizedTarget, "Target must name a file below a parent directory", null);
        }

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, normalizedTarget.getFileName() + ".", ".tmp");
            temporaryWriter.write(temporary);
            forceFile(temporary);
            moveReplacing(temporary, normalizedTarget);
            forceFile(normalizedTarget);
            return FileSaveResult.success(normalizedTarget);
        } catch (IOException | RuntimeException exception) {
            deleteTemporary(temporary, exception);
            return FileSaveResult.failure(normalizedTarget, "Failed to atomically write " + normalizedTarget, exception);
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            moveOperation.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            moveOperation.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTemporary(Path temporary, Throwable originalFailure) {
        if (temporary == null) return;
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }
}
