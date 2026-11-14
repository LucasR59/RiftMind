package com.riftmind.backend.assets;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Faux Data Dragon, sur un vrai serveur HTTP local : les tests passent par le vrai client
 * HTTP (délais, redirections, taille) sans jamais appeler Riot.
 *
 * <p>Il sert les fichiers de {@code src/test/resources/assets/} (les mêmes que la fausse API des tests de bout en
 * bout, voir son README) :
 * <ul>
 *   <li>{@code /ddragon/api/versions.json}, et les 4 fichiers de données de 16.21.1, pour toute version demandée ;</li>
 *   <li>{@code icon.png} pour toute image PNG ;</li>
 *   <li>404 pour le reste.</li>
 * </ul>
 * {@link #respond} remplace la réponse d'un chemin (erreur, autre contenu). Chaque requête est enregistrée.
 */
public final class FakeAssetServer implements AutoCloseable {

    public static final Path FILES = Path.of("src/test/resources/assets");
    public static final String VERSION = "16.21.1";
    /** Images citées par les fichiers de données : 10 champions, 42 objets, 7 sorts, 5 arbres et 34 runes. */
    public static final int DATA_DRAGON_IMAGES = 10 + 42 + 7 + 5 + 34;

    private static final Pattern DATA = Pattern.compile("/ddragon/cdn/[^/]+/data/(.+)");

    /** Réponse imposée. */
    public record Response(int status, byte[] body, Map<String, String> headers) {
    }

    /** Requête reçue. */
    public record Request(String path, Headers headers) {
    }

    private final HttpServer server;
    private final Map<String, Response> overrides = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Duration delay = Duration.ZERO;

    private FakeAssetServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }

    public static FakeAssetServer start() {
        try {
            return new FakeAssetServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    /** Configuration pointant vers ce serveur, avec la copie locale dans {@code directory}. */
    public AssetProperties properties(Path directory) {
        return AssetProperties.of(directory, baseUrl() + "/ddragon");
    }

    /** Le vrai client HTTP de l'application ({@code AssetConfiguration}). */
    public static AssetDownloader downloader(AssetProperties properties) {
        return AssetConfiguration.downloader(RestClient.builder(), properties, properties.readTimeout());
    }

    // --- Réponses

    public void respond(String path, int status) {
        respond(path, status, new byte[0], Map.of());
    }

    public void respond(String path, int status, String body) {
        respond(path, status, body.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    public void respond(String path, int status, byte[] body, Map<String, String> headers) {
        overrides.put(path, new Response(status, body, headers));
    }

    /** {@code versions.json} annonce cette version ; ses données sont celles de 16.21.1. */
    public void latestVersion(String version) {
        respond("/ddragon/api/versions.json", 200, "[\"" + version + "\",\"" + VERSION + "\"]");
    }

    /** Fait attendre chaque réponse (requêtes simultanées). */
    public void delay(Duration delay) {
        this.delay = delay;
    }

    public void reset(String path) {
        overrides.remove(path);
    }

    // --- Requêtes reçues

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public long count(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).count();
    }

    public int total() {
        return requests.size();
    }

    public void clearRequests() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        requests.add(new Request(path, exchange.getRequestHeaders()));
        try (exchange) {
            if (!delay.isZero()) {
                Thread.sleep(delay);
            }
            Response response = overrides.getOrDefault(path, defaultResponse(path));
            response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            exchange.sendResponseHeaders(response.status(), response.body().length == 0 ? -1 : response.body().length);
            if (response.body().length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(response.body());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Response defaultResponse(String path) throws IOException {
        Path file;
        Matcher data = DATA.matcher(path);
        if (path.endsWith(".png")) {
            file = FILES.resolve("icon.png");
        } else if (data.matches()) {
            file = FILES.resolve("ddragon/cdn/" + VERSION + "/data/" + data.group(1));
        } else {
            file = FILES.resolve(path.substring(1));
        }
        if (!file.normalize().startsWith(FILES) || !Files.isRegularFile(file)) {
            return new Response(404, new byte[0], Map.of());
        }
        return new Response(200, Files.readAllBytes(file), Map.of());
    }

    /** Contenu d'un fichier de {@link #FILES}. */
    public static byte[] read(String path) {
        try {
            return Files.readAllBytes(FILES.resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
