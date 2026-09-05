package com.mealtalk.api.meal.photo;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Image fixtures built programmatically so no binary blob is committed.
 *
 * <p>Only genuinely non-image payloads (SVG, corrupt bytes) are inline literals.
 */
final class MealPhotoFixtures {
    private MealPhotoFixtures() {
    }

    /** A deterministic PNG of the requested size with recognizable colour blocks. */
    static byte[] png(int width, int height) {
        return encode(image(width, height), "png");
    }

    /** A deterministic baseline JPEG of the requested size. */
    static byte[] jpeg(int width, int height) {
        return encode(image(width, height), "jpeg");
    }

    /**
     * A JPEG carrying an APP1/Exif segment plus a JFIF-comment marker.
     *
     * <p>ImageIO's JPEG writer will not synthesise Exif for us, so the segment is
     * spliced in directly after SOI: that is exactly how a phone camera file looks
     * to a decoder, and it is what the sanitizer must strip.
     */
    static byte[] jpegWithExifAndGps(int width, int height) {
        byte[] base = jpeg(width, height);
        byte[] exif = exifApp1SegmentWithGps();
        byte[] out = new byte[base.length + exif.length];
        // SOI (2 bytes) | APP1 Exif | rest of the original stream
        System.arraycopy(base, 0, out, 0, 2);
        System.arraycopy(exif, 0, out, 2, exif.length);
        System.arraycopy(base, 2, out, 2 + exif.length, base.length - 2);
        return out;
    }

    /** A JPEG whose EXIF orientation tag says "rotate 90 CW" (orientation 6). */
    static byte[] jpegWithOrientation6(int width, int height) {
        byte[] base = jpeg(width, height);
        byte[] exif = exifApp1SegmentWithOrientation(6);
        byte[] out = new byte[base.length + exif.length];
        System.arraycopy(base, 0, out, 0, 2);
        System.arraycopy(exif, 0, out, 2, exif.length);
        System.arraycopy(base, 2, out, 2 + exif.length, base.length - 2);
        return out;
    }

    /** A real multi-frame (animated) GIF. */
    static byte[] animatedGif(int width, int height) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("gif");
            if (!writers.hasNext()) {
                throw new IllegalStateException("no GIF writer available in this JDK");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(buffer)) {
                writer.setOutput(stream);
                writer.prepareWriteSequence(null);
                for (int frame = 0; frame < 3; frame++) {
                    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    graphics.setColor(frame % 2 == 0 ? Color.RED : Color.BLUE);
                    graphics.fillRect(0, 0, width, height);
                    graphics.dispose();
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    IIOMetadata metadata =
                        writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(image), param);
                    String format = metadata.getNativeMetadataFormatName();
                    IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                    IIOMetadataNode control = new IIOMetadataNode("GraphicControlExtension");
                    control.setAttribute("disposalMethod", "none");
                    control.setAttribute("userInputFlag", "FALSE");
                    control.setAttribute("transparentColorFlag", "FALSE");
                    control.setAttribute("delayTime", "10");
                    control.setAttribute("transparentColorIndex", "0");
                    root.appendChild(control);
                    metadata.setFromTree(format, root);
                    writer.writeToSequence(new IIOImage(image, null, metadata), param);
                }
                writer.endWriteSequence();
            } finally {
                writer.dispose();
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * A real APNG: an ordinary PNG with an {@code acTL} animation-control chunk.
     *
     * <p>The JDK's PNG reader ignores APNG chunks and reports a single frame, so
     * this fixture exists to prove the sanitizer refuses it explicitly instead of
     * silently storing only the first frame.
     */
    static byte[] animatedPng(int width, int height) {
        byte[] png = png(width, height);
        // Signature (8) + IHDR chunk (4 length + 4 type + 13 data + 4 crc = 25).
        final int afterIhdr = 8 + 25;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, afterIhdr);
        ByteArrayOutputStream actl = new ByteArrayOutputStream();
        actl.writeBytes(intBytes(2)); // num_frames
        actl.writeBytes(intBytes(0)); // num_plays (infinite)
        writeChunk(out, "acTL", actl.toByteArray());
        out.write(png, afterIhdr, png.length - afterIhdr);
        return out.toByteArray();
    }

    /** An SVG document; a decoder must never accept it as a raster image. */
    static byte[] svg() {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\">"
            + "<rect width=\"100\" height=\"100\" fill=\"red\"/></svg>")
            .getBytes(StandardCharsets.UTF_8);
    }

    /** Random-looking non-image bytes. */
    static byte[] corrupt() {
        return new byte[] {0x00, 0x01, 0x02, (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF, 0x7F, 0x10, 0x22};
    }

    /** A JPEG SOI/APP0 header with the entropy-coded data cut away. */
    static byte[] truncatedJpeg() {
        byte[] full = jpeg(64, 64);
        byte[] head = new byte[20];
        System.arraycopy(full, 0, head, 0, head.length);
        return head;
    }

    /**
     * A PNG whose declared dimensions exceed 40 megapixels while the file stays small.
     *
     * <p>Written as a raw single-colour PNG so the fixture itself is cheap: the
     * header alone is enough for a reader to report the dimensions, which is
     * exactly the decompression-bomb shape the sanitizer must reject early.
     */
    static byte[] decompressionBombPng(int width, int height) {
        // Zero-filled IDAT data compresses to almost nothing.
        byte[] raw = new byte[(width + 1) * height];
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] chunk = new byte[64 * 1024];
        while (!deflater.finished()) {
            int written = deflater.deflate(chunk);
            compressed.write(chunk, 0, written);
        }
        deflater.end();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        ihdr.writeBytes(intBytes(width));
        ihdr.writeBytes(intBytes(height));
        ihdr.write(8); // bit depth
        ihdr.write(0); // greyscale
        ihdr.write(0);
        ihdr.write(0);
        ihdr.write(0);
        writeChunk(out, "IHDR", ihdr.toByteArray());
        writeChunk(out, "IDAT", compressed.toByteArray());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static BufferedImage image(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(20, 140, 90));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(240, 200, 40));
        graphics.fillRect(0, 0, Math.max(1, width / 2), Math.max(1, height / 2));
        graphics.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, buffer)) {
                throw new IllegalStateException("no writer for " + format);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] exifApp1SegmentWithGps() {
        byte[] tiff = tiffWithOrientationAndGps();
        return app1(tiff);
    }

    private static byte[] exifApp1SegmentWithOrientation(int orientation) {
        byte[] tiff = tiffWithOrientationOnly(orientation);
        return app1(tiff);
    }

    private static byte[] app1(byte[] tiff) {
        byte[] header = "Exif\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
        int length = 2 + header.length + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(header);
        out.writeBytes(tiff);
        return out.toByteArray();
    }

    /** Big-endian TIFF header, IFD0 with Orientation + a GPS IFD pointer. */
    private static byte[] tiffWithOrientationAndGps() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {'M', 'M', 0x00, 0x2A});
        out.writeBytes(intBytes(8)); // IFD0 offset

        // IFD0: 2 entries
        out.writeBytes(shortBytes(2));
        writeEntry(out, 0x0112, 3, 1, shortBytes(6)); // Orientation = 6 (rotate 90 CW)
        writeEntry(out, 0x8825, 4, 1, intBytes(8 + 2 + 24 + 4)); // GPS IFD pointer
        out.writeBytes(intBytes(0)); // no IFD1

        // GPS IFD: version + latitude ref
        out.writeBytes(shortBytes(2));
        writeEntry(out, 0x0000, 1, 4, new byte[] {2, 3, 0, 0}); // GPSVersionID
        writeEntry(out, 0x0001, 2, 2, new byte[] {'N', 0, 0, 0}); // GPSLatitudeRef
        out.writeBytes(intBytes(0));
        return out.toByteArray();
    }

    private static byte[] tiffWithOrientationOnly(int orientation) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {'M', 'M', 0x00, 0x2A});
        out.writeBytes(intBytes(8));
        out.writeBytes(shortBytes(1));
        writeEntry(out, 0x0112, 3, 1, shortBytes(orientation));
        out.writeBytes(intBytes(0));
        return out.toByteArray();
    }

    private static void writeEntry(ByteArrayOutputStream out, int tag, int type, int count, byte[] value) {
        out.writeBytes(shortBytes(tag));
        out.writeBytes(shortBytes(type));
        out.writeBytes(intBytes(count));
        byte[] padded = new byte[4];
        System.arraycopy(value, 0, padded, 0, Math.min(4, value.length));
        out.writeBytes(padded);
    }

    private static byte[] shortBytes(int value) {
        return new byte[] {(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    private static byte[] intBytes(int value) {
        return new byte[] {
            (byte) ((value >> 24) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        out.writeBytes(intBytes(data.length));
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(intBytes((int) crc.getValue()));
    }
}
