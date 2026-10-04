package com.genzenglish.partner.speech;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Luna's paid clips (MP3), kept on disk by what was said and how, so a sentence is only ever made once: lesson lines
 * repeat for every learner and every replay. A missing or unwritable folder just means clips are made again.
 */
@Component
public class SpeechCache {

    private static final Logger log = LoggerFactory.getLogger(SpeechCache.class);

    private final Path dir;

    public SpeechCache(@Value("${app.speech.cache-dir:data/speech-cache}") String dir) {
        this.dir = Path.of(dir);
    }

    /** A file-safe key for everything that changes how a clip sounds. */
    public static String key(Object... parts) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (Object part : parts) {
                sha.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<byte[]> get(String key) {
        Path file = file(key);
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void put(String key, byte[] mp3) {
        Path file = file(key);
        try {
            Files.createDirectories(file.getParent());
            Path partial = file.resolveSibling(file.getFileName() + ".part");
            Files.write(partial, mp3);
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UncheckedIOException e) {
            log.warn("Couldn't keep a speech clip in {}: {}", dir, e.getMessage());
        }
    }

    private Path file(String key) {
        return dir.resolve(key.substring(0, 2)).resolve(key + ".mp3");
    }
}
