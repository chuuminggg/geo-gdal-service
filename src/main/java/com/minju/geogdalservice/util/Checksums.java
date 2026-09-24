package com.minju.geogdalservice.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Checksums {

    private Checksums() {
    }

    /**
     * 스트림을 파일로 복사하면서 SHA-256 을 함께 계산한다. (파일을 두 번 읽지 않음)
     */
    public static String copyWithSha256(InputStream input, Path target) {
        try (DigestInputStream digestStream = new DigestInputStream(input, MessageDigest.getInstance("SHA-256"))) {
            Files.copy(digestStream, target, StandardCopyOption.REPLACE_EXISTING);
            return HexFormat.of().formatHex(digestStream.getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
