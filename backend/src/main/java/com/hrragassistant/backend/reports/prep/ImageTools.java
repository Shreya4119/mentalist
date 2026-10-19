package com.hrragassistant.backend.reports.prep;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Plain-Java image handling (no extra libraries): decode PNG/JPG/TIFF/GIF/
 * BMP (multi-page TIFFs included), flatten transparency, scale down and
 * re-encode as JPEG until an image fits a provider's size limit.
 */
public final class ImageTools {

    private ImageTools() {
    }

    /** All pages of a multi-page TIFF; the first frame of anything else. */
    public static List<BufferedImage> readPages(byte[] data) throws IOException {
        List<BufferedImage> pages = new ArrayList<>();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (in == null) {
                throw new IOException("Unreadable image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("This image format can't be read");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, true);
                boolean multi = reader.getFormatName().toLowerCase().contains("tif");
                int count = multi ? reader.getNumImages(true) : 1;
                for (int i = 0; i < count; i++) {
                    pages.add(toRgb(reader.read(i)));
                }
            } finally {
                reader.dispose();
            }
        }
        if (pages.isEmpty()) {
            throw new IOException("The image has no pages");
        }
        return pages;
    }

    /** Transparent areas become white; JPEG has no alpha channel. */
    public static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    public static BufferedImage scaleToFit(BufferedImage src, int maxEdge) {
        int w = src.getWidth(), h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) {
            return src;
        }
        double f = (double) maxEdge / longest;
        int nw = Math.max(1, (int) Math.round(w * f)), nh = Math.max(1, (int) Math.round(h * f));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    public static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(toRgb(image), null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    public record Shrunk(byte[] jpeg, int width, int height) {
    }

    /**
     * Scales and compresses until the JPEG is at most {@code maxBytes}. Text in
     * financial tables stays legible down to roughly 1,200 px on the long edge.
     */
    public static Shrunk shrinkToFit(BufferedImage image, int maxEdge, long maxBytes) throws IOException {
        int edge = maxEdge;
        float[] qualities = {0.85f, 0.75f, 0.6f};
        while (true) {
            BufferedImage scaled = scaleToFit(image, edge);
            for (float q : qualities) {
                byte[] out = jpeg(scaled, q);
                if (out.length <= maxBytes) {
                    return new Shrunk(out, scaled.getWidth(), scaled.getHeight());
                }
            }
            if (edge <= 800) {
                byte[] out = jpeg(scaled, 0.5f);
                return new Shrunk(out, scaled.getWidth(), scaled.getHeight());
            }
            edge = (int) (edge * 0.8);
        }
    }

    /** Width and height without decoding the whole image, or null when unreadable. */
    public static int[] dimensions(byte[] data) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                return null;
            }
            ImageReader r = readers.next();
            try {
                r.setInput(in, true, true);
                return new int[] {r.getWidth(0), r.getHeight(0)};
            } finally {
                r.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
