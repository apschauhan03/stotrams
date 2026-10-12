package com.stotram.service.generation.provider;

import com.stotram.service.generation.GeneratedImage;
import com.stotram.service.generation.ImageEditRequest;
import com.stotram.service.generation.ImageGenerationException;
import com.stotram.service.generation.ImageGenerator;
import com.stotram.service.generation.ImageRequest;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Draws a placeholder image locally instead of calling a vendor. Exists so the
 * whole pipeline — fan-out, storage, batch bookkeeping, review queue — can be
 * exercised end-to-end with no API key and no spend.
 *
 * <p>It behaves like a real provider in the ways that matter for testing: it takes
 * time, and it fails occasionally (see {@code app.generation.fake.failure-rate}),
 * so the per-image error handling is actually exercised rather than assumed.
 */
@Component
public class FakeImageGenerator implements ImageGenerator {

    private final long latencyMs;
    private final double failureRate;

    public FakeImageGenerator(
            @Value("${app.generation.fake.latency-ms:1500}") long latencyMs,
            @Value("${app.generation.fake.failure-rate:0.0}") double failureRate) {
        this.latencyMs = latencyMs;
        this.failureRate = failureRate;
    }

    @Override
    public String providerId() {
        return "fake";
    }

    @Override
    public String displayName() {
        return "Fake placeholder (free, for testing)";
    }

    @Override
    public boolean paid() {
        return false;
    }

    @Override
    public GeneratedImage generate(ImageRequest request) {
        sleep();
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new ImageGenerationException("fake provider: simulated failure");
        }

        BufferedImage image = draw(request);
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new ImageGenerationException("fake provider: could not encode PNG", e);
        }

        return new GeneratedImage(
                out.toByteArray(), "image/png",
                request.width(), request.height(), BigDecimal.ZERO);
    }

    @Override
    public boolean supportsEdit() {
        return true;
    }

    /** Free stand-in for an edit: the source image with an "EDITED" banner on top. */
    @Override
    public GeneratedImage edit(ImageEditRequest request) {
        sleep();
        BufferedImage source;
        try {
            source = ImageIO.read(new java.io.ByteArrayInputStream(request.image()));
        } catch (IOException e) {
            throw new ImageGenerationException("fake provider: could not read source image", e);
        }
        if (source == null) {
            throw new ImageGenerationException("fake provider: unreadable source image");
        }
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, null);
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(0, 0, w, h / 9);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, w / 22));
            g.drawString("EDITED (placeholder)", w / 24, h / 22);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, w / 36));
            g.drawString(truncate(request.instruction()), w / 24, h / 12);
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new ImageGenerationException("fake provider: could not encode PNG", e);
        }
        return new GeneratedImage(out.toByteArray(), "image/png", w, h, BigDecimal.ZERO);
    }

    /** Gradient keyed off the prompt hash, so different prompts look different. */
    private BufferedImage draw(ImageRequest request) {
        int w = request.width();
        int h = request.height();
        float hue = Math.abs(request.prompt().hashCode() % 360) / 360f;

        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(
                    0, 0, Color.getHSBColor(hue, 0.55f, 0.95f),
                    0, h, Color.getHSBColor((hue + 0.12f) % 1f, 0.75f, 0.45f)));
            g.fillRect(0, 0, w, h);

            g.setColor(new Color(255, 255, 255, 220));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, w / 18));
            g.drawString("PLACEHOLDER", w / 12, h / 2);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, w / 34));
            g.drawString(truncate(request.prompt()), w / 12, h / 2 + h / 16);
        } finally {
            g.dispose();
        }
        return image;
    }

    private String truncate(String prompt) {
        return prompt.length() <= 42 ? prompt : prompt.substring(0, 42) + "...";
    }

    private void sleep() {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ImageGenerationException("fake provider: interrupted", e);
        }
    }
}
