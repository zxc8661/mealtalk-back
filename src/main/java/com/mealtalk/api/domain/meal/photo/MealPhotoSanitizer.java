package com.mealtalk.api.domain.meal.photo;

import org.springframework.stereotype.Component;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;

/**
 * Turns arbitrary uploaded bytes into one safe, bounded, metadata-free JPEG.
 *
 * <p>Nothing about the request is trusted: not the filename, not the declared
 * content type. The bytes themselves are decoded, and only a genuine JPEG or PNG
 * raster survives. The order of the gates matters and is asserted by tests:
 *
 * <ol>
 *   <li>byte size, before anything is parsed;</li>
 *   <li>format, from the decoder that actually claims the stream;</li>
 *   <li>frame count, so an animated GIF/APNG cannot slip through;</li>
 *   <li>declared dimensions read from the <em>header</em>, so a decompression
 *       bomb is rejected before a full raster is ever allocated;</li>
 *   <li>only then the full decode.</li>
 * </ol>
 *
 * <p>Output is always re-encoded from the decoded pixels into a fresh JPEG
 * stream, which is what removes EXIF, GPS and every other ancillary segment:
 * the original container is discarded rather than filtered.
 */
@Component
public class MealPhotoSanitizer {
    /** Largest accepted upload. Matches the multipart limit the API advertises. */
    public static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;

    /** Largest accepted decoded raster, guarding heap against decompression bombs. */
    public static final long MAX_PIXELS = 40L * 1_000_000L;

    /** Longest edge of the stored image. */
    public static final int MAX_EDGE = 2048;

    public static final String OUTPUT_CONTENT_TYPE = "image/jpeg";

    private static final float JPEG_QUALITY = 0.85f;

    /**
     * @param uploadedBytes raw bytes exactly as received from the client
     * @throws MealPhotoValidationException when the bytes are not an acceptable photo
     */
    public SanitizedMealPhoto sanitize(byte[] uploadedBytes) {
        if (uploadedBytes == null || uploadedBytes.length == 0) {
            throw new MealPhotoValidationException(
                MealPhotoValidationException.Reason.EMPTY,
                "Uploaded photo carried no bytes"
            );
        }
        if (uploadedBytes.length > MAX_INPUT_BYTES) {
            throw new MealPhotoValidationException(
                MealPhotoValidationException.Reason.TOO_LARGE,
                "Uploaded photo is " + uploadedBytes.length + " bytes, above the " + MAX_INPUT_BYTES + " byte limit"
            );
        }

        BufferedImage decoded = decodeSafely(uploadedBytes);
        int orientation = ExifOrientation.read(uploadedBytes);
        BufferedImage oriented = ExifOrientation.apply(decoded, orientation);
        BufferedImage bounded = capLongestEdge(oriented);
        byte[] jpeg = encodeJpeg(bounded);

        return new SanitizedMealPhoto(
            jpeg,
            OUTPUT_CONTENT_TYPE,
            jpeg.length,
            bounded.getWidth(),
            bounded.getHeight(),
            sha256Hex(jpeg)
        );
    }

    /**
     * Reads the header first, rejects anything unacceptable, and only then
     * materialises the raster.
     */
    private BufferedImage decodeSafely(byte[] bytes) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                throw unreadable("no image input stream could be opened for the uploaded bytes");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                // SVG, PDF, text, random noise: nothing claims the stream.
                throw unreadable("no image decoder recognised the uploaded bytes");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, false);

                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!"JPEG".equals(format) && !"PNG".equals(format)) {
                    throw new MealPhotoValidationException(
                        MealPhotoValidationException.Reason.UNSUPPORTED_FORMAT,
                        "Uploaded photo decoded as " + format + "; only JPEG and PNG are accepted"
                    );
                }

                // getNumImages(true) walks the container; a multi-page stream
                // reports more than one frame here.
                int frames = reader.getNumImages(true);
                if (frames > 1) {
                    throw new MealPhotoValidationException(
                        MealPhotoValidationException.Reason.ANIMATED,
                        "Uploaded photo carries " + frames + " frames; only a single still image is accepted"
                    );
                }
                // The JDK's PNG reader ignores APNG animation chunks and reports a
                // single frame, so an animated PNG would otherwise be silently
                // flattened to its first frame. Detect it explicitly and refuse.
                if ("PNG".equals(format) && isAnimatedPng(bytes)) {
                    throw new MealPhotoValidationException(
                        MealPhotoValidationException.Reason.ANIMATED,
                        "Uploaded photo is an animated PNG; only a single still image is accepted"
                    );
                }

                // Header-only dimension read. No raster is allocated yet.
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width * height > MAX_PIXELS) {
                    throw new MealPhotoValidationException(
                        MealPhotoValidationException.Reason.TOO_MANY_PIXELS,
                        "Uploaded photo declares " + width + "x" + height
                            + " pixels, above the " + MAX_PIXELS + " pixel limit"
                    );
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw unreadable("the image decoder returned no raster");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IIOException exception) {
            throw unreadable("the uploaded bytes could not be decoded: " + exception.getMessage());
        } catch (IOException | ArrayIndexOutOfBoundsException | NegativeArraySizeException exception) {
            // Malformed streams make some decoders throw raw runtime errors.
            throw unreadable("the uploaded bytes could not be decoded: " + exception.getMessage());
        } catch (OutOfMemoryError error) {
            throw new MealPhotoValidationException(
                MealPhotoValidationException.Reason.TOO_MANY_PIXELS,
                "Decoding the uploaded photo exhausted the available heap"
            );
        }
    }

    /**
     * Detects the APNG {@code acTL} chunk, which marks a PNG as animated.
     *
     * <p>{@code acTL} must appear before the first {@code IDAT}, so the scan stops
     * there rather than walking the whole (possibly large) pixel payload.
     */
    private static boolean isAnimatedPng(byte[] bytes) {
        final int signature = 8;
        int index = signature;
        while (index + 8 <= bytes.length) {
            int length = ((bytes[index] & 0xFF) << 24)
                | ((bytes[index + 1] & 0xFF) << 16)
                | ((bytes[index + 2] & 0xFF) << 8)
                | (bytes[index + 3] & 0xFF);
            if (length < 0) {
                return false;
            }
            String type = new String(bytes, index + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if ("acTL".equals(type)) {
                return true;
            }
            if ("IDAT".equals(type) || "IEND".equals(type)) {
                return false;
            }
            index += 12 + length; // length + type + data + crc
        }
        return false;
    }

    /** Scales down so the longest edge is at most {@link #MAX_EDGE}. Never upscales. */
    private BufferedImage capLongestEdge(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_EDGE) {
            return source;
        }
        double scale = (double) MAX_EDGE / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * Writes a fresh JPEG stream from the pixels alone.
     *
     * <p>The image is first flattened onto opaque RGB: the JPEG writer cannot
     * represent an alpha channel, and a PNG with transparency would otherwise
     * produce inverted colours.
     */
    private byte[] encodeJpeg(BufferedImage image) {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new IllegalStateException("this JVM has no JPEG writer");
            }
            javax.imageio.ImageWriter writer = writers.next();
            try (javax.imageio.stream.ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
                writer.setOutput(output);
                javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(JPEG_QUALITY);
                }
                // Null metadata: the writer emits only JFIF, never a copied
                // EXIF/GPS segment from the source container.
                writer.write(null, new javax.imageio.IIOImage(rgb, null, null), param);
            } finally {
                writer.dispose();
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Re-encoding the sanitized photo failed", exception);
        }
    }

    private static MealPhotoValidationException unreadable(String message) {
        return new MealPhotoValidationException(MealPhotoValidationException.Reason.UNREADABLE, message);
    }

    static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every JVM", exception);
        }
    }

    /**
     * Minimal EXIF orientation support.
     *
     * <p>Only the {@code Orientation} tag is read, and only to bake the rotation
     * into the pixels. Everything else in the EXIF block is deliberately ignored
     * and then discarded with the original container.
     */
    static final class ExifOrientation {
        private ExifOrientation() {
        }

        /** @return the EXIF orientation value 1-8, or 1 when absent/unparseable */
        static int read(byte[] jpeg) {
            if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
                return 1; // not a JPEG container, so no EXIF
            }
            int index = 2;
            while (index + 4 <= jpeg.length) {
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
                    return 1; // start of scan: metadata segments are behind us
                }
                int length = ((jpeg[index + 2] & 0xFF) << 8) | (jpeg[index + 3] & 0xFF);
                if (length < 2 || index + 2 + length > jpeg.length) {
                    return 1;
                }
                if (marker == 0xE1 && length >= 8
                    && jpeg[index + 4] == 'E' && jpeg[index + 5] == 'x'
                    && jpeg[index + 6] == 'i' && jpeg[index + 7] == 'f') {
                    int orientation = readOrientationFromTiff(jpeg, index + 10, index + 2 + length);
                    if (orientation >= 1 && orientation <= 8) {
                        return orientation;
                    }
                    return 1;
                }
                index += 2 + length;
            }
            return 1;
        }

        private static int readOrientationFromTiff(byte[] bytes, int tiffStart, int limit) {
            if (tiffStart + 8 > limit) {
                return 1;
            }
            boolean bigEndian;
            if (bytes[tiffStart] == 'M' && bytes[tiffStart + 1] == 'M') {
                bigEndian = true;
            } else if (bytes[tiffStart] == 'I' && bytes[tiffStart + 1] == 'I') {
                bigEndian = false;
            } else {
                return 1;
            }
            int ifdOffset = readInt(bytes, tiffStart + 4, bigEndian);
            int ifd = tiffStart + ifdOffset;
            if (ifd + 2 > limit) {
                return 1;
            }
            int entries = readShort(bytes, ifd, bigEndian);
            for (int entry = 0; entry < entries; entry++) {
                int offset = ifd + 2 + entry * 12;
                if (offset + 12 > limit) {
                    return 1;
                }
                if (readShort(bytes, offset, bigEndian) == 0x0112) {
                    return readShort(bytes, offset + 8, bigEndian);
                }
            }
            return 1;
        }

        private static int readShort(byte[] bytes, int offset, boolean bigEndian) {
            int first = bytes[offset] & 0xFF;
            int second = bytes[offset + 1] & 0xFF;
            return bigEndian ? (first << 8) | second : (second << 8) | first;
        }

        private static int readInt(byte[] bytes, int offset, boolean bigEndian) {
            int a = bytes[offset] & 0xFF;
            int b = bytes[offset + 1] & 0xFF;
            int c = bytes[offset + 2] & 0xFF;
            int d = bytes[offset + 3] & 0xFF;
            return bigEndian
                ? (a << 24) | (b << 16) | (c << 8) | d
                : (d << 24) | (c << 16) | (b << 8) | a;
        }

        /** Rewrites the pixels so the upright image needs no orientation tag. */
        static BufferedImage apply(BufferedImage source, int orientation) {
            if (orientation <= 1 || orientation > 8) {
                return source;
            }
            int width = source.getWidth();
            int height = source.getHeight();
            boolean swapsAxes = orientation >= 5;
            int targetWidth = swapsAxes ? height : width;
            int targetHeight = swapsAxes ? width : height;

            AffineTransform transform = new AffineTransform();
            switch (orientation) {
                case 2 -> { // mirror horizontal
                    transform.scale(-1, 1);
                    transform.translate(-width, 0);
                }
                case 3 -> { // rotate 180
                    transform.translate(width, height);
                    transform.rotate(Math.PI);
                }
                case 4 -> { // mirror vertical
                    transform.scale(1, -1);
                    transform.translate(0, -height);
                }
                case 5 -> { // mirror horizontal + rotate 270 CW
                    transform.rotate(-Math.PI / 2);
                    transform.scale(-1, 1);
                }
                case 6 -> { // rotate 90 CW
                    transform.translate(height, 0);
                    transform.rotate(Math.PI / 2);
                }
                case 7 -> { // mirror horizontal + rotate 90 CW
                    transform.scale(-1, 1);
                    transform.translate(-height, 0);
                    transform.rotate(Math.PI / 2);
                    transform.scale(1, 1);
                }
                case 8 -> { // rotate 270 CW
                    transform.translate(0, width);
                    transform.rotate(-Math.PI / 2);
                }
                default -> {
                    return source;
                }
            }

            BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );
                graphics.drawImage(source, transform, null);
            } finally {
                graphics.dispose();
            }
            return target;
        }
    }
}
