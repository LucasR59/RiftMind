import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

/**
 * Régénère les images de rang servies par le backend (backend/src/main/resources/assets/ranks/), à partir des emblèmes
 * officiels de Riot (« Ranked Emblems Latest », page Data Dragon du portail développeur).
 *
 * Usage (depuis la racine du dépôt, JDK 25 — celui du backend) :
 *   java scripts/RankImages.java                        # télécharge le zip de Riot (~64 Mo)
 *   java scripts/RankImages.java chemin/vers/le.zip     # zip déjà téléchargé
 *
 * Pour chaque palier : marges transparentes retirées, image carrée de 128 px (nette à 64 px sur un écran haute
 * densité). « Non classé » n'existe pas chez Riot : c'est l'emblème Fer en niveaux de gris, à moitié transparent.
 */
public class RankImages {

    static final String URL = "https://static.developer.riotgames.com/docs/lol/ranked-emblems-latest.zip";
    static final Path OUT = Path.of("backend/src/main/resources/assets/ranks");
    static final int SIZE = 128;
    static final List<String> TIERS = List.of("iron", "bronze", "silver", "gold", "platinum", "emerald", "diamond",
            "master", "grandmaster", "challenger");

    public static void main(String[] args) throws Exception {
        Path zip = args.length > 0 ? Path.of(args[0]) : download();
        Files.createDirectories(OUT);
        try (ZipFile file = new ZipFile(zip.toFile())) {
            BufferedImage iron = null;
            for (String tier : TIERS) {
                String name = "Ranked Emblems Latest/Rank=" + capitalize(tier) + ".png";
                ZipEntry entry = file.getEntry(name);
                if (entry == null) {
                    throw new IllegalStateException(name + " absent du zip");
                }
                BufferedImage image;
                try (InputStream in = file.getInputStream(entry)) {
                    image = square(crop(ImageIO.read(in)));
                }
                write(image, tier);
                if (tier.equals("iron")) {
                    iron = image;
                }
            }
            write(unranked(iron), "unranked");
        }
    }

    static Path download() throws IOException, InterruptedException {
        Path zip = Files.createTempFile("ranked-emblems-", ".zip");
        System.out.println("Téléchargement de " + URL);
        HttpResponse<Path> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(URL)).build(), HttpResponse.BodyHandlers.ofFile(zip));
        if (response.statusCode() != 200) {
            throw new IOException(URL + " a répondu " + response.statusCode());
        }
        return zip;
    }

    /** Retire les marges entièrement transparentes. */
    static BufferedImage crop(BufferedImage image) {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Réduit l'image dans un carré de {@link #SIZE} px, centrée, par étapes (meilleur rendu qu'en une fois). */
    static BufferedImage square(BufferedImage image) {
        BufferedImage current = image;
        double scale = (double) SIZE / Math.max(image.getWidth(), image.getHeight());
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        current = resize(current, width, height);
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(current, (SIZE - width) / 2, (SIZE - height) / 2, null);
        g.dispose();
        return out;
    }

    static BufferedImage resize(BufferedImage image, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    /** Emblème Fer en niveaux de gris, à moitié transparent. */
    static BufferedImage unranked(BufferedImage iron) {
        BufferedImage grey = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int argb = iron.getRGB(x, y);
                int r = (argb >> 16) & 0xff, gr = (argb >> 8) & 0xff, b = argb & 0xff;
                int l = (int) Math.round(0.299 * r + 0.587 * gr + 0.114 * b);
                grey.setRGB(x, y, (argb & 0xff000000) | (l << 16) | (l << 8) | l);
            }
        }
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        g.drawImage(grey, 0, 0, null);
        g.dispose();
        return out;
    }

    static void write(BufferedImage image, String tier) throws IOException {
        Path file = OUT.resolve(tier + ".png");
        ImageIO.write(image, "png", file.toFile());
        System.out.printf(Locale.ROOT, "écrit %s (%d octets)%n", file, Files.size(file));
    }

    static String capitalize(String tier) {
        return Character.toUpperCase(tier.charAt(0)) + tier.substring(1);
    }
}
