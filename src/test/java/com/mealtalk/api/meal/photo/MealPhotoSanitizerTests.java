package com.mealtalk.api.meal.photo;

import com.mealtalk.api.domain.meal.photo.MealPhotoValidationException;
import com.mealtalk.api.domain.meal.photo.MealPhotoSanitizer;
import com.mealtalk.api.domain.meal.photo.SanitizedMealPhoto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The sanitizer is the only thing standing between an arbitrary uploaded byte
 * array and the private bucket, so every rejection path is pinned here.
 */
class MealPhotoSanitizerTests {
    private final MealPhotoSanitizer sanitizer = new MealPhotoSanitizer();

    @Test
    @DisplayName("A valid PNG is re-encoded as bounded JPEG with a checksum over the output bytes")
    void sanitizesPngIntoBoundedJpeg() throws IOException {
        byte[] source = MealPhotoFixtures.png(3000, 1500);

        SanitizedMealPhoto result = sanitizer.sanitize(source);

        assertThat(result.contentType())
            .as("sanitized output must always be declared as JPEG")
            .isEqualTo("image/jpeg");
        assertThat(result.width()).as("longest edge is capped at 2048px").isEqualTo(2048);
        assertThat(result.height()).as("aspect ratio 2:1 must be preserved").isEqualTo(1024);
        assertThat(result.byteSize())
            .as("byteSize must match the actual output array length")
            .isEqualTo(result.jpegBytes().length);
        assertThat(result.checksumSha256())
            .as("checksum must be the lowercase hex SHA-256 of the FINAL output bytes")
            .isEqualTo(sha256Hex(result.jpegBytes()));
        assertThat(result.checksumSha256()).hasSize(64).matches("[0-9a-f]{64}");

        assertThat(formatOf(result.jpegBytes()))
            .as("output bytes must decode as JPEG")
            .isEqualTo("JPEG");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.jpegBytes()));
        assertThat(decoded).isNotNull();
        assertThat(decoded.getWidth()).isEqualTo(2048);
        assertThat(decoded.getHeight()).isEqualTo(1024);
    }

    @Test
    @DisplayName("A small image is never upscaled")
    void neverUpscalesSmallImages() {
        SanitizedMealPhoto result = sanitizer.sanitize(MealPhotoFixtures.png(320, 200));

        assertThat(result.width()).as("small input must keep its own width").isEqualTo(320);
        assertThat(result.height()).isEqualTo(200);
    }

    @Test
    @DisplayName("EXIF and GPS metadata do not survive sanitization")
    void stripsExifAndGpsMetadata() throws IOException {
        byte[] source = MealPhotoFixtures.jpegWithExifAndGps(800, 600);
        assertThat(containsExifSegment(source))
            .as("fixture must actually carry an APP1/Exif segment, otherwise this test proves nothing")
            .isTrue();

        SanitizedMealPhoto result = sanitizer.sanitize(source);

        assertThat(containsExifSegment(result.jpegBytes()))
            .as("no APP1/Exif segment may survive in the re-encoded output bytes")
            .isFalse();
        assertThat(metadataDump(result.jpegBytes()))
            .as("the output image metadata tree must not mention any EXIF or GPS node")
            .doesNotContainIgnoringCase("exif")
            .doesNotContainIgnoringCase("gps")
            .doesNotContainIgnoringCase("unknown");
    }

    @Test
    @DisplayName("EXIF orientation 6 is normalized into the pixel data")
    void normalizesExifOrientation() {
        SanitizedMealPhoto result = sanitizer.sanitize(MealPhotoFixtures.jpegWithOrientation6(800, 400));

        assertThat(result.width())
            .as("orientation 6 rotates 90 degrees, so a 800x400 source becomes 400x800")
            .isEqualTo(400);
        assertThat(result.height()).isEqualTo(800);
    }

    @Test
    @DisplayName("Zero-byte input is rejected")
    void rejectsZeroBytes() {
        MealPhotoValidationException exception = catchThrowableOfType(
            () -> sanitizer.sanitize(new byte[0]),
            MealPhotoValidationException.class
        );

        assertThat(exception).as("empty upload must raise the typed validation exception").isNotNull();
        assertThat(exception.getReason()).isEqualTo(MealPhotoValidationException.Reason.EMPTY);
    }

    @Test
    @DisplayName("Corrupt bytes are rejected as undecodable")
    void rejectsCorruptBytes() {
        assertThatThrownBy(() -> sanitizer.sanitize(MealPhotoFixtures.corrupt()))
            .isInstanceOf(MealPhotoValidationException.class)
            .extracting(thrown -> ((MealPhotoValidationException) thrown).getReason())
            .isEqualTo(MealPhotoValidationException.Reason.UNREADABLE);
    }

    @Test
    @DisplayName("A truncated JPEG header is rejected")
    void rejectsTruncatedJpeg() {
        assertThatThrownBy(() -> sanitizer.sanitize(MealPhotoFixtures.truncatedJpeg()))
            .isInstanceOf(MealPhotoValidationException.class);
    }

    @Test
    @DisplayName("An SVG payload is rejected even when a caller calls it image/jpeg")
    void rejectsSvg() {
        assertThatThrownBy(() -> sanitizer.sanitize(MealPhotoFixtures.svg()))
            .as("SVG is a script-bearing vector document and must never be accepted")
            .isInstanceOf(MealPhotoValidationException.class)
            .extracting(thrown -> ((MealPhotoValidationException) thrown).getReason())
            .isEqualTo(MealPhotoValidationException.Reason.UNREADABLE);
    }

    @Test
    @DisplayName("An animated GIF is rejected as an unsupported format")
    void rejectsAnimatedGif() {
        assertThatThrownBy(() -> sanitizer.sanitize(MealPhotoFixtures.animatedGif(64, 64)))
            .isInstanceOf(MealPhotoValidationException.class)
            .extracting(thrown -> ((MealPhotoValidationException) thrown).getReason())
            .isEqualTo(MealPhotoValidationException.Reason.UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("An animated PNG is rejected even though the JDK reader reports one frame")
    void rejectsAnimatedPng() {
        assertThatThrownBy(() -> sanitizer.sanitize(MealPhotoFixtures.animatedPng(64, 64)))
            .as("an APNG must not be silently flattened to its first frame")
            .isInstanceOf(MealPhotoValidationException.class)
            .extracting(thrown -> ((MealPhotoValidationException) thrown).getReason())
            .isEqualTo(MealPhotoValidationException.Reason.ANIMATED);
    }

    @Test
    @DisplayName("Input above 10 MiB is rejected before decoding")
    void rejectsOversizedBytes() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        // A valid JPEG signature proves the size gate runs before any format work.
        oversized[0] = (byte) 0xFF;
        oversized[1] = (byte) 0xD8;

        assertThatThrownBy(() -> sanitizer.sanitize(oversized))
            .isInstanceOf(MealPhotoValidationException.class)
            .extracting(thrown -> ((MealPhotoValidationException) thrown).getReason())
            .isEqualTo(MealPhotoValidationException.Reason.TOO_LARGE);
    }

    @Test
    @DisplayName("Exactly 10 MiB of undecodable bytes passes the size gate and fails on decoding")
    void sizeGateBoundaryIsInclusive() {
        byte[] atLimit = new byte[10 * 1024 * 1024];

        MealPhotoValidationException exception = catchThrowableOfType(
            () -> sanitizer.sanitize(atLimit),
            MealPhotoValidationException.class
        );

        assertThat(exception.getReason())
            .as("10 MiB exactly is within the limit, so the failure must come from decoding, not size")
            .isEqualTo(MealPhotoValidationException.Reason.UNREADABLE);
    }

    @Test
    @DisplayName("A 40MP+ decompression bomb is rejected from its header alone")
    void rejectsDecompressionBomb() {
        // 8000 x 6000 = 48 megapixels, but the file itself is tiny.
        byte[] bomb = MealPhotoFixtures.decompressionBombPng(8000, 6000);
        assertThat(bomb.length)
            .as("the bomb fixture must stay small, otherwise the byte-size gate would catch it instead")
            .isLessThan(10 * 1024 * 1024);

        MealPhotoValidationException exception = catchThrowableOfType(
            () -> sanitizer.sanitize(bomb),
            MealPhotoValidationException.class
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getReason())
            .as("dimensions must be checked from the header before the full raster is allocated")
            .isEqualTo(MealPhotoValidationException.Reason.TOO_MANY_PIXELS);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String formatOf(byte[] bytes) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return "unknown";
            }
            ImageReader reader = readers.next();
            try {
                return reader.getFormatName();
            } finally {
                reader.dispose();
            }
        }
    }

    /** Scans the JPEG marker stream for an APP1 segment whose payload starts with "Exif". */
    static boolean containsExifSegment(byte[] jpeg) {
        for (int index = 2; index + 4 < jpeg.length; ) {
            if ((jpeg[index] & 0xFF) != 0xFF) {
                index++;
                continue;
            }
            int marker = jpeg[index + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                index += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                return false; // start of scan: no more metadata segments
            }
            int length = ((jpeg[index + 2] & 0xFF) << 8) | (jpeg[index + 3] & 0xFF);
            if (marker == 0xE1 && index + 4 + 4 < jpeg.length) {
                if (jpeg[index + 4] == 'E' && jpeg[index + 5] == 'x'
                    && jpeg[index + 6] == 'i' && jpeg[index + 7] == 'f') {
                    return true;
                }
            }
            index += 2 + length;
        }
        return false;
    }

    /** Renders the decoded image's ImageIO metadata trees as text for assertions. */
    static String metadataDump(byte[] jpeg) throws IOException {
        StringBuilder dump = new StringBuilder();
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return "";
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true);
                javax.imageio.metadata.IIOMetadata metadata = reader.getImageMetadata(0);
                if (metadata != null) {
                    for (String format : metadata.getMetadataFormatNames()) {
                        appendNode(dump, metadata.getAsTree(format), 0);
                    }
                }
            } finally {
                reader.dispose();
            }
        }
        return dump.toString();
    }

    private static void appendNode(StringBuilder dump, org.w3c.dom.Node node, int depth) {
        if (node == null) {
            return;
        }
        dump.append(" ".repeat(depth)).append(node.getNodeName());
        org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            for (int index = 0; index < attributes.getLength(); index++) {
                org.w3c.dom.Node attribute = attributes.item(index);
                dump.append(' ').append(attribute.getNodeName()).append('=').append(attribute.getNodeValue());
            }
        }
        dump.append('\n');
        for (org.w3c.dom.Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            appendNode(dump, child, depth + 2);
        }
    }
}
