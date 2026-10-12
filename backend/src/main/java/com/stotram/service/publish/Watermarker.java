package com.stotram.service.publish;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Stamps the Stotrams watermark — the gold lotus and "स्तोत्रम्" — onto an image, and
 * re-encodes it as a JPEG for the feed. Done in code at publish time so every published
 * image carries it, identically, whatever the image model drew.
 *
 * <p>Placement: top-right, on a translucent dark pill so it reads on light and dark
 * images alike; sized relative to the image so it looks the same at any resolution.
 *
 * <p>The Hindi text is laid out with {@link TextLayout}, which applies the font's shaping
 * rules (conjuncts, matras, halant) — drawing the string glyph by glyph would break them.
 * Font: Tiro Devanagari Hindi (SIL OFL, bundled in resources/fonts with its license), the
 * same face the website uses for the Hindi name.
 */
@Component
public class Watermarker {

    public record Result(byte[] jpeg, int width, int height) {
    }

    /**
     * Bump whenever the look or placement changes: on the next startup every live image is
     * re-rendered from its clean original (WatermarkBackfill), so old and new never mix.
     * It's also a folder in the published key, so a new design gets new URLs and no browser
     * or CDN keeps serving a cached copy of the old one.
     */
    public static final String VERSION = "v2-top-right";

    private static final String TEXT = "स्तोत्रम्";
    private static final String FONT = "/fonts/TiroDevanagariHindi-Regular.ttf";
    private static final float JPEG_QUALITY = 0.88f;

    private static final Color PILL = new Color(30, 12, 5, 155);
    private static final Color TEXT_COLOR = new Color(0xFF, 0xF8, 0xEA);
    private static final Color TEXT_SHADOW = new Color(0, 0, 0, 100);
    private static final Color GOLD_TOP = new Color(0xF6, 0xCD, 0x66);
    private static final Color GOLD_BOTTOM = new Color(0xDC, 0x9F, 0x38);
    private static final Color RAY = new Color(0xE3, 0xAE, 0x48);

    private final Font font;

    public Watermarker() {
        // Fail at startup, not at the first publish, if the font is missing or broken.
        try (InputStream in = Watermarker.class.getResourceAsStream(FONT)) {
            if (in == null) {
                throw new IllegalStateException("Watermark font missing from classpath: " + FONT);
            }
            this.font = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Could not load watermark font " + FONT, e);
        }
        if (font.canDisplayUpTo(TEXT) != -1) {
            throw new IllegalStateException("Watermark font cannot render: " + TEXT);
        }
    }

    public Result apply(byte[] original) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(original));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read image", e);
        }
        if (source == null) {
            throw new IllegalArgumentException("Not a readable image format");
        }

        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(Color.WHITE);             // JPEG has no transparency
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, null);
            drawMark(g, w, h);
        } finally {
            g.dispose();
        }
        return new Result(encodeJpeg(out), w, h);
    }

    private void drawMark(Graphics2D g, int w, int h) {
        float unit = Math.min(w, h);
        float fontSize = unit * 0.05f;
        TextLayout layout = new TextLayout(TEXT, font.deriveFont(fontSize), g.getFontRenderContext());
        Rectangle2D text = layout.getBounds();      // relative to the baseline origin

        float markSize = fontSize * 1.35f;
        float gap = fontSize * 0.2f;
        float padX = fontSize * 0.4f;
        float padY = fontSize * 0.22f;
        float pillW = padX + markSize + gap + (float) text.getWidth() + padX * 1.2f;
        float pillH = Math.max(markSize, (float) text.getHeight()) + padY * 2;
        float margin = unit * 0.035f;
        float x = w - margin - pillW;           // top-right corner
        float y = margin;

        g.setColor(PILL);
        g.fill(new RoundRectangle2D.Float(x, y, pillW, pillH, pillH, pillH));

        float markX = x + padX;
        float markY = y + (pillH - markSize) / 2;
        drawLotus(g, markX, markY, markSize);

        float textX = markX + markSize + gap - (float) text.getX();
        float textY = y + pillH / 2 - (float) (text.getY() + text.getHeight() / 2);
        g.setColor(TEXT_SHADOW);
        layout.draw(g, textX + fontSize * 0.03f, textY + fontSize * 0.04f);
        g.setColor(TEXT_COLOR);
        layout.draw(g, textX, textY);
    }

    /** The logo's lotus (same geometry as the website's LotusMark, 200×200 units). */
    private static void drawLotus(Graphics2D g, float x, float y, float size) {
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        g.scale(size / 200.0, size / 200.0);

        g.setColor(RAY);
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 - Math.PI / 2;
            g.draw(new java.awt.geom.Line2D.Double(
                    100 + 80 * Math.cos(a), 100 + 80 * Math.sin(a),
                    100 + 94 * Math.cos(a), 100 + 94 * Math.sin(a)));
        }

        Path2D[] petals = {
                petal(97, 154, 62, 160, 36, 144, 24, 112, 50, 104, 84, 120),   // outer left
                mirror(petal(97, 154, 62, 160, 36, 144, 24, 112, 50, 104, 84, 120)),
                petal(97, 152, 64, 142, 50, 110, 55, 74, 80, 84, 95, 110),     // inner left
                mirror(petal(97, 152, 64, 142, 50, 110, 55, 74, 80, 84, 95, 110)),
                petal(100, 42, 128, 70, 131, 114, 100, 154, 69, 114, 72, 70),  // centre
        };
        g.setPaint(new GradientPaint(0, 40, GOLD_TOP, 0, 160, GOLD_BOTTOM));
        for (Path2D p : petals) {
            g.fill(p);
        }
        // Thin dark outlines separate the petals, like the logo's gaps.
        g.setColor(new Color(40, 16, 6, 170));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Path2D p : petals) {
            g.draw(p);
        }
        g.setTransform(saved);
    }

    /** M x0 y0 C a b c d x1 y1 C e f g h x0 y0 Z — a petal from two cubic curves. */
    private static Path2D petal(float x0, float y0, float c1x, float c1y, float c2x, float c2y,
                                float x1, float y1, float c3x, float c3y, float c4x, float c4y) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(x0, y0);
        p.curveTo(c1x, c1y, c2x, c2y, x1, y1);
        p.curveTo(c3x, c3y, c4x, c4y, x0, y0);
        p.closePath();
        return p;
    }

    private static Path2D mirror(Path2D p) {
        AffineTransform flip = new AffineTransform(-1, 0, 0, 1, 200, 0);
        return new Path2D.Float(p, flip);
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        var bytes = new ByteArrayOutputStream();
        try (var out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
