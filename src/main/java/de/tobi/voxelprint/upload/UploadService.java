package de.tobi.voxelprint.upload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tobi.voxelprint.VoxelPrint;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends a finished export to the web platform.
 *
 * <p>So that the way from the game to a printable file is one click rather than
 * a file manager, a browser and a drag. The platform already takes an upload and
 * answers with a project id, and its page opens that project from the query
 * string, so the mod can hand the player a link that is already their build.
 *
 * <p>Never on the client thread. An upload crosses a network and the game must
 * not wait on it; the result comes back on a future the caller hands to the
 * client to act on.
 *
 * <p>One at a time, per the same reasoning as the export itself: a second click
 * while the first is still in the air is an accident, not an instruction.
 */
public final class UploadService {

    /** Long enough for a slow line, short enough that a dead host gives up. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    /**
     * The largest file this will send.
     *
     * <p>The platform refuses anything larger anyway; refusing it here saves
     * pushing a pointless few megabytes across the network first.
     */
    private static final long MAX_BYTES = 64L * 1024L * 1024L;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final AtomicBoolean running = new AtomicBoolean(false);

    /** What came back, either a project to open or a reason it did not work. */
    public record Uploaded(boolean success, String projectUrl, String error) {

        static Uploaded ok(String projectUrl) {
            return new Uploaded(true, projectUrl, "");
        }

        static Uploaded failed(String error) {
            return new Uploaded(false, "", error);
        }
    }

    /** Whether an upload is in the air right now. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Uploads one export.
     *
     * @param file    the {@code .mcprint} to send
     * @param baseUrl the platform, without a trailing slash
     * @return the project's address on the platform, or why there is none
     */
    public CompletableFuture<Uploaded> upload(Path file, String baseUrl) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(baseUrl, "baseUrl");

        if (baseUrl.isEmpty()) {
            return CompletableFuture.completedFuture(Uploaded.failed("no website configured"));
        }
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(Uploaded.failed("an upload is already running"));
        }

        return CompletableFuture
                .supplyAsync(() -> send(file, baseUrl))
                .whenComplete((result, error) -> running.set(false));
    }

    private Uploaded send(Path file, String baseUrl) {
        try {
            long size = Files.size(file);
            if (size > MAX_BYTES) {
                return Uploaded.failed("the file is larger than " + (MAX_BYTES / 1024 / 1024) + " MB");
            }

            String boundary = "VoxelPrint" + Long.toHexString(System.nanoTime());
            HttpRequest request = HttpRequest.newBuilder(new URI(baseUrl + "/api/projects"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(
                            multipart(boundary, file.getFileName().toString(), Files.readAllBytes(file))))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return Uploaded.failed(reasonFrom(response));
            }

            String id = idFrom(response.body());
            if (id == null) {
                return Uploaded.failed("the website answered without a project id");
            }
            return Uploaded.ok(baseUrl + "/?project=" + id);
        } catch (IOException | URISyntaxException e) {
            VoxelPrint.LOGGER.warn("Upload of {} failed", file, e);
            return Uploaded.failed(String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Uploaded.failed("interrupted");
        }
    }

    /**
     * The three pieces of a one field multipart body.
     *
     * <p>Written out rather than pulled from a library: it is a header, the
     * bytes, and a closing line, and the platform accepts exactly one field.
     */
    private static List<byte[]> multipart(String boundary, String fileName, byte[] content) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + sanitise(fileName) + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        return List.of(
                header.getBytes(StandardCharsets.UTF_8),
                content,
                footer.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Keeps a file name from breaking out of the header it sits in.
     *
     * <p>Export names are already checked against a positive list before a file
     * is written, so this should never have anything to do. It is here because
     * "should never" and "cannot" are different things, and a quote or a newline
     * in a header field is how one field becomes two.
     */
    private static String sanitise(String fileName) {
        return fileName.replaceAll("[\"\\r\\n\\\\]", "_");
    }

    /** The project id, or null when the answer was not what was expected. */
    private static String idFrom(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject object = parsed.getAsJsonObject();
            JsonElement id = object.get("id");
            return id != null && id.isJsonPrimitive() ? id.getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * What to tell the player when the platform says no.
     *
     * <p>The platform explains itself in an {@code error} field; that is worth
     * repeating, because "the file is larger than the allowed 8388608 bytes" is
     * something somebody can act on and "HTTP 413" is not.
     */
    private static String reasonFrom(HttpResponse<String> response) {
        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            if (parsed.isJsonObject()) {
                JsonElement error = parsed.getAsJsonObject().get("error");
                if (error != null && error.isJsonPrimitive()) {
                    return error.getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // Not JSON, so the status code is all there is to report.
        }
        return "the website answered with " + response.statusCode();
    }
}
