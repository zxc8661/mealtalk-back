package com.mealtalk.api.meal.photo;

import com.mealtalk.api.domain.meal.photo.MealPhotoSanitizer;
import com.mealtalk.api.domain.meal.photo.MealPhotoValidationException;
import com.mealtalk.api.domain.meal.photo.SanitizedMealPhoto;
import com.mealtalk.api.domain.meal.photo.StoredMealPhoto;
import com.mealtalk.api.domain.meal.photo.MealPhotoStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual-QA channel: runs a real EXIF/GPS-bearing photo through the sanitizer,
 * writes the output to a temp file, and prints the observable facts (file size,
 * decoded dimensions, metadata dump) so a human can read them in the evidence
 * file instead of trusting a green checkmark.
 *
 * <p>The assertions at the end are the binary observable: a decodable JPEG whose
 * longest edge is within the cap and whose metadata carries no EXIF or GPS tag.
 */
class MealPhotoManualQaTests {
    @Test
    @DisplayName("MANUAL QA: an EXIF+GPS JPEG is sanitized into a clean bounded JPEG on disk")
    void printsSanitizerObservables() throws IOException {
        MealPhotoSanitizer sanitizer = new MealPhotoSanitizer();
        InMemoryMealPhotoStorage storage = new InMemoryMealPhotoStorage();
        MealPhotoStore store = new MealPhotoStore(storage, sanitizer);

        byte[] exifJpeg = MealPhotoFixtures.jpegWithExifAndGps(4032, 3024);
        byte[] plainPng = MealPhotoFixtures.png(1200, 900);

        System.out.println("===== MealPhoto sanitizer manual QA =====");
        System.out.println("[input A] EXIF+GPS JPEG");
        System.out.println("  input bytes            : " + exifJpeg.length);
        System.out.println("  input declared size    : 4032x3024");
        System.out.println("  input has APP1/Exif    : " + MealPhotoSanitizerTests.containsExifSegment(exifJpeg));

        SanitizedMealPhoto sanitized = sanitizer.sanitize(exifJpeg);
        Path output = Files.createTempFile("mealtalk-manual-qa-", ".jpg");
        Files.write(output, sanitized.jpegBytes());

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(sanitized.jpegBytes()));
        String metadata = MealPhotoSanitizerTests.metadataDump(sanitized.jpegBytes());

        System.out.println("[output A] written to    : " + output.toAbsolutePath());
        System.out.println("  actual file size bytes : " + Files.size(output));
        System.out.println("  result byteSize field  : " + sanitized.byteSize());
        System.out.println("  decoded dimensions     : " + decoded.getWidth() + "x" + decoded.getHeight());
        System.out.println("  reported dimensions    : " + sanitized.width() + "x" + sanitized.height());
        System.out.println("  contentType            : " + sanitized.contentType());
        System.out.println("  sha256(output bytes)   : " + sanitized.checksumSha256());
        System.out.println("  output has APP1/Exif   : "
            + MealPhotoSanitizerTests.containsExifSegment(sanitized.jpegBytes()));
        System.out.println("  metadata mentions exif : " + metadata.toLowerCase().contains("exif"));
        System.out.println("  metadata mentions gps  : " + metadata.toLowerCase().contains("gps"));
        System.out.println("  ---- metadata dump ----");
        metadata.lines().forEach(line -> System.out.println("  | " + line));
        System.out.println("  -----------------------");

        System.out.println("[input B] plain PNG 1200x900 through the full store seam");
        StoredMealPhoto stored = store.store(4242L, 77L, plainPng);
        System.out.println("  object key             : " + stored.objectKey());
        System.out.println("  stored dimensions      : " + stored.width() + "x" + stored.height());
        System.out.println("  stored byteSize        : " + stored.byteSize());
        System.out.println("  stored contentType     : " + stored.contentType());
        System.out.println("  keys in fake bucket    : " + storage.keys());

        System.out.println("[input C] rejection matrix");
        printRejection("corrupt bytes      ", () -> sanitizer.sanitize(MealPhotoFixtures.corrupt()));
        printRejection("truncated JPEG     ", () -> sanitizer.sanitize(MealPhotoFixtures.truncatedJpeg()));
        printRejection("svg as image/jpeg  ", () -> sanitizer.sanitize(MealPhotoFixtures.svg()));
        printRejection("animated gif       ", () -> sanitizer.sanitize(MealPhotoFixtures.animatedGif(48, 48)));
        printRejection("zero bytes         ", () -> sanitizer.sanitize(new byte[0]));
        printRejection("10 MiB + 1 byte    ", () -> sanitizer.sanitize(new byte[10 * 1024 * 1024 + 1]));
        printRejection("8000x6000 bomb png ",
            () -> sanitizer.sanitize(MealPhotoFixtures.decompressionBombPng(8000, 6000)));
        System.out.println("  keys in fake bucket after rejections: " + storage.keys());
        System.out.println("===== end manual QA =====");

        Files.deleteIfExists(output);

        assertThat(decoded).as("output must be a decodable JPEG").isNotNull();
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight()))
            .as("longest edge must respect the 2048px cap")
            .isLessThanOrEqualTo(2048);
        assertThat(metadata.toLowerCase()).doesNotContain("exif").doesNotContain("gps");
    }

    private static void printRejection(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + " -> NOT REJECTED (this is a bug)");
        } catch (MealPhotoValidationException exception) {
            System.out.println("  " + label + " -> " + exception.getReason() + ": " + exception.getMessage());
        }
    }
}
