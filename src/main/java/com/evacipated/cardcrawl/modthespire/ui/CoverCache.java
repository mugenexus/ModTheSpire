package com.evacipated.cardcrawl.modthespire.ui;

import com.evacipated.cardcrawl.modthespire.lib.ConfigUtils;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

final class CoverCache
{
    private final Path directory = Paths.get(ConfigUtils.CONFIG_DIR, "cover-thumbnails");
    private long generation;

    synchronized long generation()
    {
        return generation;
    }

    private Path entry(String key)
    {
        return directory.resolve(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)) + ".png");
    }

    synchronized ImageIcon read(String key)
    {
        Path path = entry(key);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null;
            BufferedImage image = ImageIO.read(path.toFile());
            return image == null ? null : new ImageIcon(image);
        } catch (IOException ignored) {
            return null;
        }
    }

    synchronized void write(String key, ImageIcon icon, long requestedGeneration) throws IOException
    {
        if (generation != requestedGeneration) return;
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, ".cover-", ".tmp");
        try {
            BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.drawImage(icon.getImage(), 0, 0, null);
            graphics.dispose();
            ImageIO.write(image, "png", temp.toFile());
            Files.move(temp, entry(key), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    synchronized long size() throws IOException
    {
        if (!Files.exists(directory)) return 0;
        long bytes = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.png")) {
            for (Path file : files) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) bytes += Files.size(file);
            }
        }
        return bytes;
    }

    synchronized void clear() throws IOException
    {
        generation++;
        if (!Files.exists(directory)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.png")) {
            for (Path file : files) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(file);
            }
        }
    }
}
