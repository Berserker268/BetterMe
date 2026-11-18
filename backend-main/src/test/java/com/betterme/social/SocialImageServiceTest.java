package com.betterme.social;

import com.betterme.social.service.SocialImageService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class SocialImageServiceTest {
    final SocialImageService images = new SocialImageService();

    @Test
    void acceptsActualImageResizesAndReencodesIt() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_ARGB), "png", output);
        byte[] image = images.prepare(new MockMultipartFile("photo", "untrusted-name.svg", "text/plain", output.toByteArray()));
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(image));
        assertEquals(1600, result.getWidth()); assertEquals(800, result.getHeight());
        assertEquals(0xff, image[0] & 0xff); assertEquals(0xd8, image[1] & 0xff);
    }

    @Test
    void rejectsSpoofedUnsupportedAndOversizedFiles() throws Exception {
        assertThrows(ResponseStatusException.class, () -> images.prepare(new MockMultipartFile("photo", "fake.jpg", "image/jpeg", "<svg/>".getBytes())));
        assertThrows(ResponseStatusException.class, () -> images.prepare(new MockMultipartFile("photo", "large.jpg", "image/jpeg", new byte[2 * 1024 * 1024 + 1])));
        ByteArrayOutputStream gif = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB), "gif", gif);
        assertThrows(ResponseStatusException.class, () -> images.prepare(new MockMultipartFile("photo", "fake.png", "image/png", gif.toByteArray())));
        assertNull(images.prepare(null));
    }
}
