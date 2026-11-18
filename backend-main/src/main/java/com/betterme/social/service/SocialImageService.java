package com.betterme.social.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import java.util.Locale;

@Service
public class SocialImageService {
    public static final long MAX_BYTES = 2 * 1024 * 1024;

    // Decode actual content, discard metadata, resize, and store only a freshly encoded JPEG.
    public byte[] prepare(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) return null;
        if (photo.getSize() > MAX_BYTES) throw invalid("Choose a photo smaller than 2 MB.");
        try (InputStream stream = photo.getInputStream();
             ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            if (input == null) throw invalid("Choose a valid JPEG or PNG photo.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Only JPEG and PNG photos are supported.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png"))
                    throw invalid("Only JPEG and PNG photos are supported.");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 20_000_000)
                    throw invalid("Choose a photo under 20 megapixels.");
                BufferedImage original = reader.read(0);
                double scale = Math.min(1.0, 1600.0 / Math.max(width, height));
                BufferedImage resized = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = resized.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, resized.getWidth(), resized.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(original, 0, 0, resized.getWidth(), resized.getHeight(), null);
                } finally { graphics.dispose(); }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(resized, "jpeg", output)) throw invalid("Could not process this photo.");
                if (output.size() > MAX_BYTES) throw invalid("Photo is too detailed. Choose a smaller photo.");
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid("Could not read this photo. Choose another JPEG or PNG.");
        }
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
