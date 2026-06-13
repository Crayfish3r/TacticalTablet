package com.makar.tacticaltablet.integration.discord;

import com.makar.tacticaltablet.core.TacticalTabletMod;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiscordWebhookClient {

    public static final int DISCORD_CONTENT_LIMIT = 2000;

    private static final Gson GSON = new Gson();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static ExecutorService executor = createExecutor();
    private static HttpClient client = createClient();

    private DiscordWebhookClient() {
    }

    public static CompletableFuture<Boolean> sendMessageAsync(String webhookUrl, String message) {
        String url = webhookUrl == null ? "" : webhookUrl.trim();

        if (url.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        URI uri = parseUri(url, "Discord webhook URL");

        if (uri == null) {
            return CompletableFuture.completedFuture(false);
        }

        HttpRequest request = requestBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(message)))
                .build();

        ensureClient();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status >= 200 && status < 300) {
                        return true;
                    }

                    TacticalTabletMod.LOGGER.warn("Discord webhook returned HTTP {}", status);
                    return false;
                })
                .exceptionally(throwable -> {
                    TacticalTabletMod.LOGGER.error("Discord webhook request failed", throwable);
                    return false;
                });
    }

    public static CompletableFuture<Boolean> sendEmbedAsync(String webhookUrl, DiscordEmbed embed) {
        return sendEmbedsAsync(webhookUrl, List.of(embed));
    }

    public static CompletableFuture<Boolean> sendEmbedsAsync(String webhookUrl, List<DiscordEmbed> embeds) {
        String url = webhookUrl == null ? "" : webhookUrl.trim();

        if (url.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        URI uri = parseUri(url, "Discord webhook URL");

        if (uri == null) {
            return CompletableFuture.completedFuture(false);
        }

        HttpRequest request = requestBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(buildEmbedsPayload(embeds)))
                .build();

        ensureClient();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status >= 200 && status < 300) {
                        return true;
                    }

                    TacticalTabletMod.LOGGER.warn("Discord webhook returned HTTP {}", status);
                    return false;
                })
                .exceptionally(throwable -> {
                    TacticalTabletMod.LOGGER.error("Discord webhook request failed", throwable);
                    return false;
                });
    }

    public static CompletableFuture<String> sendMessageAndGetIdAsync(String webhookUrl, String message) {
        String url = appendQuery(webhookUrl, "wait=true");

        if (url.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        URI uri = parseUri(url, "Discord webhook URL");

        if (uri == null) {
            return CompletableFuture.completedFuture("");
        }

        HttpRequest request = requestBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(message)))
                .build();

        ensureClient();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int status = response.statusCode();

                    if (status < 200 || status >= 300) {
                        TacticalTabletMod.LOGGER.warn("Discord webhook create message returned HTTP {}", status);
                        return "";
                    }

                    try {
                        JsonObject object = GSON.fromJson(response.body(), JsonObject.class);

                        if (object != null && object.has("id")) {
                            return object.get("id").getAsString();
                        }
                    } catch (JsonSyntaxException | IllegalStateException exception) {
                        TacticalTabletMod.LOGGER.warn("Discord webhook response did not contain a valid message id", exception);
                    }

                    return "";
                })
                .exceptionally(throwable -> {
                    TacticalTabletMod.LOGGER.error("Discord webhook create message request failed", throwable);
                    return "";
                });
    }

    public static CompletableFuture<String> sendEmbedAndGetIdAsync(String webhookUrl, DiscordEmbed embed) {
        String url = appendQuery(webhookUrl, "wait=true");

        if (url.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        URI uri = parseUri(url, "Discord webhook URL");

        if (uri == null) {
            return CompletableFuture.completedFuture("");
        }

        HttpRequest request = requestBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(buildEmbedPayload(embed)))
                .build();

        ensureClient();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int status = response.statusCode();

                    if (status < 200 || status >= 300) {
                        TacticalTabletMod.LOGGER.warn("Discord webhook create message returned HTTP {}", status);
                        return "";
                    }

                    try {
                        JsonObject object = GSON.fromJson(response.body(), JsonObject.class);

                        if (object != null && object.has("id")) {
                            return object.get("id").getAsString();
                        }
                    } catch (JsonSyntaxException | IllegalStateException exception) {
                        TacticalTabletMod.LOGGER.warn("Discord webhook response did not contain a valid message id", exception);
                    }

                    return "";
                })
                .exceptionally(throwable -> {
                    TacticalTabletMod.LOGGER.error("Discord webhook create message request failed", throwable);
                    return "";
                });
    }

    public static CompletableFuture<WebhookResponse> editMessageAsync(String webhookUrl, String messageId, String message) {
        String url = buildEditUrl(webhookUrl, messageId);

        if (url.isBlank()) {
            return CompletableFuture.completedFuture(WebhookResponse.invalid());
        }

        URI uri = parseUri(url, "Discord webhook edit URL");

        if (uri == null) {
            return CompletableFuture.completedFuture(WebhookResponse.invalid());
        }

        HttpRequest request = requestBuilder(uri)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(buildPayload(message)))
                .build();

        ensureClient();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> new WebhookResponse(
                        response.statusCode() >= 200 && response.statusCode() < 300,
                        response.statusCode(),
                        response.body() == null ? "" : response.body()
                ))
                .exceptionally(throwable -> {
                    TacticalTabletMod.LOGGER.error("Discord webhook edit message request failed", throwable);
                    return WebhookResponse.failed();
                });
    }

    public static CompletableFuture<WebhookResponse> editEmbedAsync(String webhookUrl, String messageId, DiscordEmbed embed) {
        String url = buildEditUrl(webhookUrl, messageId);

        if (url.isBlank()) {
            return CompletableFuture.completedFuture(WebhookResponse.invalid());
        }

        URI uri = parseUri(url, "Discord webhook edit URL");

        if (uri == null) {
            return CompletableFuture.completedFuture(WebhookResponse.invalid());
        }

        HttpRequest request = requestBuilder(uri)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(buildEmbedPayload(embed)))
                .build();

        ensureClient();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> new WebhookResponse(
                        response.statusCode() >= 200 && response.statusCode() < 300,
                        response.statusCode(),
                        response.body() == null ? "" : response.body()
                ))
                .exceptionally(throwable -> {
                    TacticalTabletMod.LOGGER.error("Discord webhook edit message request failed", throwable);
                    return WebhookResponse.failed();
                });
    }

    public static String trimContent(String message) {
        String value = Objects.toString(message, "");

        if (value.length() <= DISCORD_CONTENT_LIMIT) {
            return value;
        }

        String marker = "\n… сообщение сокращено";
        int limit = Math.max(0, DISCORD_CONTENT_LIMIT - marker.length());
        return value.substring(0, limit) + marker;
    }

    public static synchronized void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json; charset=utf-8");
    }

    private static String buildPayload(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", trimContent(message));
        return GSON.toJson(payload);
    }

    private static String buildEmbedPayload(DiscordEmbed embed) {
        return buildEmbedsPayload(List.of(embed));
    }

    private static String buildEmbedsPayload(List<DiscordEmbed> embeds) {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", "");

        JsonArray embedsJson = new JsonArray();
        List<DiscordEmbed> safeEmbeds = embeds == null || embeds.isEmpty() ? List.of(DiscordEmbed.empty()) : embeds;

        for (DiscordEmbed embed : safeEmbeds) {
            DiscordEmbed safe = embed == null ? DiscordEmbed.empty() : embed;
            JsonObject embedJson = new JsonObject();
            embedJson.addProperty("title", trimEmbedText(safe.title(), 256));
            embedJson.addProperty("description", trimEmbedText(safe.description(), 4096));
            embedJson.addProperty("color", Math.max(0, Math.min(0xFFFFFF, safe.color())));

            if (!safe.footer().isBlank()) {
                JsonObject footer = new JsonObject();
                footer.addProperty("text", trimEmbedText(safe.footer(), 2048));
                embedJson.add("footer", footer);
            }

            embedsJson.add(embedJson);
        }

        payload.add("embeds", embedsJson);
        return GSON.toJson(payload);
    }

    private static String trimEmbedText(String text, int limit) {
        String value = Objects.toString(text, "");
        if (value.length() <= limit) {
            return value;
        }

        return value.substring(0, Math.max(0, limit - 1)) + "…";
    }

    private static URI parseUri(String url, String description) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException exception) {
            TacticalTabletMod.LOGGER.error("Invalid {}", description, exception);
            return null;
        }
    }

    private static String appendQuery(String webhookUrl, String query) {
        String url = webhookUrl == null ? "" : webhookUrl.trim();

        if (url.isBlank()) {
            return "";
        }

        if (url.contains("?")) {
            return url + "&" + query;
        }

        return url + "?" + query;
    }

    private static String buildEditUrl(String webhookUrl, String messageId) {
        String base = webhookUrl == null ? "" : webhookUrl.trim();
        String id = messageId == null ? "" : messageId.trim();

        if (base.isBlank() || id.isBlank()) {
            return "";
        }

        int queryStart = base.indexOf('?');

        if (queryStart >= 0) {
            base = base.substring(0, queryStart);
        }

        String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
        return base + "/messages/" + encodedId;
    }

    private static synchronized void ensureClient() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = createExecutor();
            client = createClient();
        }
    }

    private static HttpClient createClient() {
        return HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .executor(executor)
                .build();
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TacticalTablet-DiscordWebhook");
            thread.setDaemon(true);
            return thread;
        });
    }

    public record WebhookResponse(boolean success, int statusCode, String body) {

        public static WebhookResponse invalid() {
            return new WebhookResponse(false, 0, "");
        }

        public static WebhookResponse failed() {
            return new WebhookResponse(false, -1, "");
        }

        public boolean isNotFound() {
            return statusCode == 404;
        }

        public boolean shouldResetMessageId() {
            return statusCode == 404 || statusCode == 401 || statusCode == 403;
        }
    }

    public record DiscordEmbed(String title, String description, int color, String footer) {
        public static DiscordEmbed empty() {
            return new DiscordEmbed("", "", 0x2B2D31, "");
        }
    }
}

