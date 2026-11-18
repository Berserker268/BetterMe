package com.betterme.social;

import com.betterme.social.service.SocialService;

import com.betterme.BackendMainApplication;
import com.betterme.user.*;
import org.springframework.boot.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** Optional browser fixture. Uses only an isolated, disposable H2 database on port 18081. */
public class SocialBrowserFixture {
    public static void main(String[] args) {
        new SpringApplication(BackendMainApplication.class, Fixture.class).run(
                "--server.port=18081", "--spring.config.import=",
                "--spring.datasource.url=jdbc:h2:mem:social-browser;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--app.remember-me-key=browser-test-only-key", "--logging.level.org.hibernate.SQL=WARN",
                "--spring.jpa.show-sql=false");
    }

    @TestConfiguration
    static class Fixture {
        @Bean
        CommandLineRunner seed(UserRepository users, PasswordEncoder passwords, SocialService social) {
            return args -> {
                User mahdi = account(users, passwords, "Mahdi Rahman", "mahdi@example.test");
                User aisha = account(users, passwords, "Aisha Rahman", "aisha@example.test");
                User marcus = account(users, passwords, "Marcus Lee", "marcus@example.test");
                User emily = account(users, passwords, "Emily Chen", "emily@example.test");
                account(users, passwords, "Daniel Ortiz", "daniel@example.test");
                social.requestFriend(mahdi.getEmail(), aisha.getId());
                social.respond(aisha.getEmail(), social.requests(aisha.getEmail(), 0).items().getFirst().id(), true);
                social.requestFriend(mahdi.getEmail(), marcus.getId());
                social.respond(marcus.getEmail(), social.requests(marcus.getEmail(), 0).items().getFirst().id(), true);
                social.requestFriend(emily.getEmail(), mahdi.getId());
                social.publish(marcus.getEmail(), "Hit my reading goal this week — finished 2 books! On to the next one.", "Study", null);
                BufferedImage image = new BufferedImage(1200, 500, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                graphics.setPaint(new GradientPaint(0, 0, new Color(71, 104, 88), 1200, 500, new Color(204, 164, 99)));
                graphics.fillRect(0, 0, 1200, 500);
                graphics.setColor(new Color(28, 54, 42));
                graphics.fillOval(-150, 220, 1500, 500);
                graphics.dispose();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "jpeg", output);
                social.publish(aisha.getEmail(), "Morning run ✅ 5 km! Feeling strong and energized for the day.", "Fitness", output.toByteArray());
            };
        }
        User account(UserRepository users, PasswordEncoder passwords, String name, String email) {
            User user = new User(); user.setFullName(name); user.setEmail(email);
            user.setPasswordHash(passwords.encode("SocialBrowserTest123!"));
            return users.save(user);
        }
    }
}
