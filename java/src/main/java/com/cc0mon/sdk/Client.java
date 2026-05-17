package com.cc0mon.sdk;

import com.cc0mon.sdk.Errors.ApiException;
import com.cc0mon.sdk.Errors.ClientApiException;
import com.cc0mon.sdk.Errors.NetworkException;
import com.cc0mon.sdk.Errors.RateLimitException;
import com.cc0mon.sdk.Errors.ServerApiException;
import com.cc0mon.sdk.Errors.ValidationException;
import com.cc0mon.sdk.Models.Collector;
import com.cc0mon.sdk.Models.CollectorItem;
import com.cc0mon.sdk.Models.Contract;
import com.cc0mon.sdk.Models.Metadata;
import com.cc0mon.sdk.Models.OwnerInfo;
import com.cc0mon.sdk.Models.Species;
import com.cc0mon.sdk.Models.SpeciesImage;
import com.cc0mon.sdk.Models.Token;
import com.cc0mon.sdk.Models.Traits;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Synchronous HTTP client for the cc0mon API.
 *
 * <p>The client encapsulates request building, retry/backoff for {@code 429}
 * and {@code 5xx}, and conversion of JSON responses into typed records.
 *
 * <p>Default behavior: 30s timeout, 3 retries with exponential backoff
 * (base 1s, cap 30s, full jitter), {@code Retry-After} honored.
 */
public final class Client implements AutoCloseable {

    public static final String DEFAULT_BASE_URL = "https://api.cc0mon.com";
    public static final String VERSION = resolveVersion();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_RETRIES = 3;
    private static final double BACKOFF_BASE_SECONDS = 1.0;
    private static final double BACKOFF_CAP_SECONDS = 30.0;
    private static final Set<Integer> RETRY_STATUSES = Set.of(429, 500, 502, 503, 504);
    private static final Pattern ETH_ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final Logger LOG = Logger.getLogger("com.cc0mon.sdk");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final Duration timeout;
    private final int retries;
    private final String userAgent;
    private final HttpClient http;

    public Client() {
        this(DEFAULT_BASE_URL, DEFAULT_TIMEOUT, DEFAULT_RETRIES, null);
    }

    public Client(String baseUrl, Duration timeout, int retries, String userAgent) {
        if (baseUrl == null) throw new IllegalArgumentException("baseUrl must not be null");
        if (timeout == null) throw new IllegalArgumentException("timeout must not be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = timeout;
        this.retries = Math.max(0, retries);
        this.userAgent = userAgent != null ? userAgent : "cc0mon-sdk-java/" + VERSION;
        this.http = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    private static String resolveVersion() {
        String v = Client.class.getPackage().getImplementationVersion();
        return v != null ? v : "0.0.0-local";
    }

    @Override
    public void close() {
        // HttpClient is auto-closed by JDK 21; nothing to do explicitly on JDK 17.
    }

    // ---------- HTTP plumbing ----------

    private HttpResponse<byte[]> request(String path, String accept) {
        URI uri = URI.create(baseUrl + path);
        int attempt = 0;
        while (true) {
            HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Accept", accept)
                .header("User-Agent", userAgent)
                .timeout(timeout)
                .GET()
                .build();
            long start = System.nanoTime();
            HttpResponse<byte[]> resp;
            try {
                resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException e) {
                long latencyMs = (System.nanoTime() - start) / 1_000_000;
                logEvent("GET", uri.toString(), null, latencyMs, 0, "network: " + e.getMessage());
                if (attempt >= retries) {
                    throw new NetworkException("network error after " + (retries + 1) + " attempts", e);
                }
                sleepForRetry(attempt, null);
                attempt++;
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NetworkException("interrupted", e);
            }

            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            int status = resp.statusCode();
            int bytes = resp.body() == null ? 0 : resp.body().length;
            logEvent("GET", uri.toString(), status, latencyMs, bytes, status < 400 ? "ok" : "http error");

            if (status < 400) return resp;

            if (RETRY_STATUSES.contains(status) && attempt < retries) {
                sleepForRetry(attempt, parseRetryAfter(resp));
                attempt++;
                continue;
            }
            throw mapStatusToException(resp);
        }
    }

    private static void logEvent(String method, String url, Integer status, long latencyMs, int bytes, String message) {
        LOG.info(method + " " + url
            + " | status=" + (status == null ? "-" : status)
            + " | latency_ms=" + latencyMs
            + " | bytes=" + bytes
            + " | " + message);
    }

    private void sleepForRetry(int attempt, Double retryAfterSeconds) {
        double sleepSeconds;
        if (retryAfterSeconds != null) {
            sleepSeconds = retryAfterSeconds;
        } else {
            double backoff = Math.min(BACKOFF_CAP_SECONDS, BACKOFF_BASE_SECONDS * Math.pow(2, attempt));
            sleepSeconds = ThreadLocalRandom.current().nextDouble(0, backoff);
        }
        LOG.info(String.format("retry attempt=%d sleeping=%.2fs", attempt + 1, sleepSeconds));
        try {
            Thread.sleep((long) (sleepSeconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("interrupted during retry backoff", e);
        }
    }

    private static Double parseRetryAfter(HttpResponse<?> resp) {
        Optional<String> hdr = resp.headers().firstValue("Retry-After");
        if (hdr.isEmpty()) return null;
        try {
            return Math.max(0.0, Double.parseDouble(hdr.get()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ApiException mapStatusToException(HttpResponse<byte[]> resp) {
        int status = resp.statusCode();
        String body = resp.body() == null ? "" : new String(resp.body(), StandardCharsets.UTF_8);
        if (status == 429) return new RateLimitException(status, body, parseRetryAfter(resp));
        if (status >= 400 && status < 500) return new ClientApiException(status, body);
        if (status >= 500 && status < 600) return new ServerApiException(status, body);
        return new ApiException(status, body);
    }

    private JsonNode getJson(String path) {
        HttpResponse<byte[]> r = request(path, "application/json");
        try {
            return JSON.readTree(r.body());
        } catch (IOException e) {
            // Status was a success but the body is unparseable: closest semantic match is NetworkException.
            throw new NetworkException("invalid JSON response from " + path + ": " + e.getMessage(), e);
        }
    }

    private byte[] getBytes(String path, String accept) {
        return request(path, accept).body();
    }

    // ---------- Validation helpers ----------

    private static int validateTokenId(int id) {
        if (id < 1 || id > 10000) {
            throw new ValidationException("token id must be in 1..10000, got " + id);
        }
        return id;
    }

    private static String normalizeAddress(String address) {
        if (address == null || !ETH_ADDRESS.matcher(address).matches()) {
            throw new ValidationException("address must match 0x[0-9a-fA-F]{40}, got " + address);
        }
        return address.toLowerCase();
    }

    // ---------- Public methods (one per endpoint) ----------

    public Token getToken(int id) {
        return Token.fromJson(getJson("/cc0mon/" + validateTokenId(id)));
    }

    public Metadata getMetadata(int id) {
        return Metadata.fromJson(getJson("/cc0mon/" + validateTokenId(id) + "/metadata"));
    }

    public Traits getTraits(int id) {
        return Traits.fromJson(getJson("/cc0mon/" + validateTokenId(id) + "/traits"));
    }

    public byte[] getImageSvg(int id) {
        return getBytes("/cc0mon/" + validateTokenId(id) + "/image.svg", "image/svg+xml");
    }

    public byte[] getImagePng(int id) {
        return getBytes("/cc0mon/" + validateTokenId(id) + "/image.png", "image/png");
    }

    public OwnerInfo getOwner(int id) {
        return OwnerInfo.fromJson(getJson("/cc0mon/" + validateTokenId(id) + "/owner"));
    }

    public Contract getContract() {
        return Contract.fromJson(getJson("/contract"));
    }

    public List<Species> getRegistry() {
        JsonNode node = getJson("/registry");
        List<Species> out = new ArrayList<>();
        JsonNode arr;
        if (node.isArray()) {
            arr = node;
        } else if (node.has("cc0mon")) {
            arr = node.get("cc0mon");
        } else if (node.has("species")) {
            arr = node.get("species");
        } else {
            arr = node.get("items");
        }
        if (arr != null && arr.isArray()) for (JsonNode n : arr) out.add(Species.fromJson(n));
        return out;
    }

    public List<SpeciesImage> getRegistryImages() {
        JsonNode node = getJson("/registry/images");
        List<SpeciesImage> out = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode n : node) out.add(SpeciesImage.fromJson(n));
            return out;
        }
        JsonNode images = node.get("images");
        if (images != null && images.isObject()) {
            Iterator<JsonNode> it = images.elements();
            while (it.hasNext()) out.add(SpeciesImage.fromJson(it.next()));
            return out;
        }
        if (images != null && images.isArray()) {
            for (JsonNode n : images) out.add(SpeciesImage.fromJson(n));
            return out;
        }
        JsonNode arr = node.has("species") ? node.get("species") : node.get("items");
        if (arr != null && arr.isArray()) for (JsonNode n : arr) out.add(SpeciesImage.fromJson(n));
        return out;
    }

    public Collector getCollector(String address) {
        return Collector.fromJson(getJson("/collector/" + normalizeAddress(address)));
    }

    // ---------- Convenience search methods (client-side filters) ----------

    /**
     * Return species filtered by energy/rarity/name substring. All filters are
     * AND-combined; energy and rarity are validated against canonical sets.
     * Pass {@code null} for any filter you want to skip.
     */
    public List<Species> findSpecies(String energy, String rarity, String nameContains) {
        String e = energy == null ? null : Validators.energy(energy);
        String r = rarity == null ? null : Validators.rarity(rarity);
        String needle = nameContains == null ? null : nameContains.toLowerCase(Locale.ROOT);
        List<Species> out = new ArrayList<>();
        for (Species s : getRegistry()) {
            if (e != null && !e.equals(s.energy())) continue;
            if (r != null && !r.equals(s.rarity())) continue;
            if (needle != null) {
                String name = s.name() == null ? "" : s.name().toLowerCase(Locale.ROOT);
                if (!name.contains(needle)) continue;
            }
            out.add(s);
        }
        return out;
    }

    /**
     * Return a wallet's checklist items, filtered by ownership/energy/rarity.
     */
    public List<CollectorItem> findCollectorItems(String address, boolean ownedOnly, String energy, String rarity) {
        String e = energy == null ? null : Validators.energy(energy);
        String r = rarity == null ? null : Validators.rarity(rarity);
        Collector collector = getCollector(address);
        List<CollectorItem> out = new ArrayList<>();
        for (CollectorItem i : collector.checklist()) {
            if (ownedOnly && !i.collected()) continue;
            if (e != null && !e.equals(i.energy())) continue;
            if (r != null && !r.equals(i.rarity())) continue;
            out.add(i);
        }
        return out;
    }
}
