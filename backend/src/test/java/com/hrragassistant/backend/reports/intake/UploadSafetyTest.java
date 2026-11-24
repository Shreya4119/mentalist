package com.hrragassistant.backend.reports.intake;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UploadSafetyTest {

    @TempDir
    Path dir;

    @Test
    void hugeImagesAreRefusedFromTheirHeaderWithoutDecoding() {
        assertThat(UploadSafety.imageProblem(pngHeader(4000, 3000))).isNull();          // 12 MP phone photo
        assertThat(UploadSafety.imageProblem(pngHeader(60000, 60000))).contains("too large"); // 3.6 GP bomb
        assertThat(UploadSafety.imageProblem("not an image".getBytes(StandardCharsets.UTF_8))).isNull();
    }

    @Test
    void zipBombsAreRefusedButNormalOfficeFilesPass() throws IOException {
        Path normal = zip("normal.xlsx", 2 * 1024 * 1024, false);
        assertThat(UploadSafety.zipProblem(normal)).isNull();
        Path bomb = zip("bomb.xlsx", 64 * 1024 * 1024, true); // 64 MB of zeros packs into ~64 KB
        assertThat(UploadSafety.zipProblem(bomb)).contains("unpacks to far more data");
        Path broken = Files.write(dir.resolve("broken.xlsx"), "PK not really".getBytes(StandardCharsets.UTF_8));
        assertThat(UploadSafety.zipProblem(broken)).contains("could not be opened");
    }

    private Path zip(String name, int size, boolean zeros) throws IOException {
        Path p = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(p); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            byte[] chunk = new byte[64 * 1024];
            java.util.Random random = new java.util.Random(1);
            for (int written = 0; written < size; written += chunk.length) {
                if (!zeros) {
                    random.nextBytes(chunk);
                }
                z.write(chunk);
            }
            z.closeEntry();
        }
        return p;
    }

    /** A PNG signature and IHDR chunk: enough for a reader to report the size. */
    static byte[] pngHeader(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes(StandardCharsets.US_ASCII)).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        out.writeBytes(ByteBuffer.allocate(4).putInt(13).array());
        out.writeBytes(ihdr.array());
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        return out.toByteArray();
    }
}
