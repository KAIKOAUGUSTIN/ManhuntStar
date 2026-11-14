package dev.manhuntstar.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writing files that the server owner keeps (config.yml, languages, bots.yml) without leaving half of one. */
public final class SafeFiles {

    private SafeFiles() {
    }

    /**
     * Puts a finished file in place of {@code target}: in one step where the file system can, otherwise
     * with a plain replace (some file systems don't move atomically).
     */
    public static void replace(Path finished, Path target) throws IOException {
        try {
            Files.move(finished, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(finished, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
