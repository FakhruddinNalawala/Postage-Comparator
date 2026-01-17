package com.postage.postagecomparator.util;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Utility functions for safely writing files using a temp file + atomic move pattern.
 */
public final class FileWriteUtils {

    private FileWriteUtils() {
    }

    /**
     * Safely writes content to the given target path by writing to a temp file first,
     * then atomically moving it into place.
     *
     * @param target the final file path
     * @param writer a function that writes the desired content to the provided temp file path
     * @param log    optional logger for debug messages about cleanup failures (may be null)
     */
    public static void safeWrite(Path target, Consumer<Path> writer, Logger log) {
        var dir = target.getParent();
        try {
            if (dir != null && !Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create directory " + dir, e);
        }

        Path tempFile = null;
        try {
            var prefix = target.getFileName() != null ? target.getFileName().toString() : "temp";
            tempFile = Files.createTempFile(dir, prefix, ".tmp");

            // Let caller write the content
            writer.accept(tempFile);

            Files.move(
                    tempFile,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write file " + target, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupEx) {
                    if (log != null) {
                        log.debug("Failed to delete temp file {}", tempFile, cleanupEx);
                    }
                }
            }
        }
    }
}
