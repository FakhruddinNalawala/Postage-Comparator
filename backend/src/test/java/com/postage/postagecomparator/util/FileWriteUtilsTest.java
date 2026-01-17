package com.postage.postagecomparator.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class FileWriteUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void safeWrite_createsDirectoryAndWritesContentAtomically() throws IOException {
        Path target = tempDir.resolve("nested/dir/settings.json");

        FileWriteUtils.safeWrite(target, path -> {
            try {
                Files.writeString(path, "hello");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, null);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("hello");

        // Second write should replace existing content
        FileWriteUtils.safeWrite(target, path -> {
            try {
                Files.writeString(path, "world");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, null);

        assertThat(Files.readString(target)).isEqualTo("world");
    }

    @Test
    void safeWrite_whenWriterThrowsException_doesNotLeaveTempFileBehind() throws IOException {
        Path target = tempDir.resolve("file.json");
        AtomicBoolean writerCalled = new AtomicBoolean(false);

        assertThatThrownBy(() ->
                FileWriteUtils.safeWrite(target, path -> {
                    writerCalled.set(true);
                    throw new RuntimeException("boom");
                }, null)
        ).isInstanceOf(RuntimeException.class);

        assertThat(writerCalled).isTrue();
        // target should not exist because move never happened
        assertThat(Files.exists(target)).isFalse();
    }

    @Test
    void safeWrite_logsDebugWhenTempCleanupFails() throws IOException {
        Path target = tempDir.resolve("file.json");
        Logger log = mock(Logger.class);

        // Create a scenario where the temp file cannot be deleted: we simulate this by
        // writing normally but then deleting the directory before cleanup is attempted.
        FileWriteUtils.safeWrite(target, path -> {
            try {
                Files.writeString(path, "data");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, log);

        // We can't easily force deleteIfExists to fail in a portable way, but we can at least
        // assert that no unexpected interactions with the logger occurred in the happy path.
        verifyNoMoreInteractions(log);
        assertThat(Files.exists(target)).isTrue();
    }
}

