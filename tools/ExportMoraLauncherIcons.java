import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Deterministically exports or verifies Mora's generated launcher assets from
 * docs/design/mora-icon-v0.3.3.svg.
 *
 * Run from the repository root with:
 *   java tools/ExportMoraLauncherIcons.java
 *   java tools/ExportMoraLauncherIcons.java --verify
 */
public final class ExportMoraLauncherIcons {
    private static final Path SVG_SOURCE =
            Path.of("docs", "design", "mora-icon-v0.3.3.svg");
    private static final Path COLORS_SOURCE =
            Path.of("app", "src", "main", "res", "values", "colors.xml");
    private static final Path SOCIAL_PREVIEW_TEMPLATE =
            Path.of("docs", "design", "social-preview-template.png");
    private static final Path SOCIAL_PREVIEW =
            Path.of("docs", "assets", "social-preview.png");
    private static final Path RES =
            Path.of("app", "src", "main", "res");

    private static final int SOCIAL_PREVIEW_WIDTH = 1280;
    private static final int SOCIAL_PREVIEW_HEIGHT = 640;
    private static final double ADAPTIVE_SAFE_MIN = 199.0;
    private static final double ADAPTIVE_SAFE_MAX = 825.0;

    private static final byte[] PNG_SIGNATURE = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final List<String> FOREGROUND_PATH_IDS = List.of(
            "mark-shadow",
            "right-inner-facet",
            "left-inner-facet",
            "right-outer-wing",
            "left-outer-wing",
            "bookmark-shadow",
            "center-bookmark");

    private static final String MONOCHROME_PATH_ID = "monochrome-silhouette";

    private static final List<DensityTarget> DENSITIES = List.of(
            new DensityTarget("mdpi", 48),
            new DensityTarget("hdpi", 72),
            new DensityTarget("xhdpi", 96),
            new DensityTarget("xxhdpi", 144),
            new DensityTarget("xxxhdpi", 192));

    private static final List<String> FOREGROUND_DRAWABLES = List.of(
            "ic_launcher_foreground.xml",
            "ic_launcher_foreground_pine.xml",
            "ic_launcher_foreground_night.xml");

    private static final List<String> LEGACY_ICON_FILES = List.of(
            "ic_launcher.png",
            "ic_launcher_pine.png",
            "ic_launcher_night.png",
            "ic_launcher_round.png");

    private ExportMoraLauncherIcons() {}

    public static void main(String[] args) {
        try {
            Mode mode = parseMode(args);
            ensureRepositoryRoot();
            verifyColorsXml();

            Map<String, SvgPathSpec> svgPaths = readSvgPaths();
            validateSourceGeometry(svgPaths);

            Map<Path, byte[]> expectedVectors = generateExpectedVectorOutputs(svgPaths);

            if (mode == Mode.EXPORT) {
                int updated = writeOutputs(expectedVectors);
                VerificationResult result = verifyAllAssets(expectedVectors);
                if (!result.isClean()) {
                    throw new IllegalStateException(result.describe());
                }
                System.out.println(
                        "Exported "
                                + expectedVectors.size()
                                + " Mora vector drawables ("
                                + updated
                                + " updated) and verified 21 PNG launcher assets.");
                return;
            }

            VerificationResult result = verifyAllAssets(expectedVectors);
            if (!result.isClean()) {
                throw new IllegalStateException(result.describe());
            }
            System.out.println("Verified 25 generated Mora launcher assets.");
        } catch (Exception error) {
            String message = error.getMessage();
            System.err.println(message == null ? error.getClass().getSimpleName() : message);
            System.exit(1);
        }
    }

    private static Mode parseMode(String[] args) {
        if (args.length == 0) {
            return Mode.EXPORT;
        }
        if (args.length == 1 && "--verify".equals(args[0])) {
            return Mode.VERIFY;
        }
        throw new IllegalArgumentException(
                "Usage: java tools/ExportMoraLauncherIcons.java [--verify]");
    }

    private static void ensureRepositoryRoot() {
        for (Path source : List.of(
                SVG_SOURCE,
                COLORS_SOURCE,
                SOCIAL_PREVIEW_TEMPLATE)) {
            if (!Files.isRegularFile(source)) {
                throw new IllegalStateException(
                        "Run this exporter from the Mora repository root; missing " + source);
            }
        }
    }

    private static void verifyColorsXml() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(COLORS_SOURCE.toFile());
        Map<String, String> colors = new LinkedHashMap<>();
        NodeList nodes = document.getElementsByTagName("color");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            colors.put(
                    element.getAttribute("name"),
                    element.getTextContent().trim());
        }
        for (String required : List.of(
                "mora_icon_background",
                "mora_icon_foreground",
                "mora_icon_pine_background",
                "mora_icon_pine_foreground",
                "mora_icon_night_background",
                "mora_icon_night_foreground")) {
            String hex = colors.get(required);
            if (hex == null || !hex.matches("^#[0-9A-Fa-f]{6,8}$")) {
                throw new IllegalStateException("Missing or invalid color resource: " + required);
            }
        }
    }

    private static Map<String, SvgPathSpec> readSvgPaths() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(SVG_SOURCE.toFile());
        Map<String, SvgPathSpec> specs = new LinkedHashMap<>();
        NodeList paths = document.getElementsByTagName("path");
        for (int index = 0; index < paths.getLength(); index++) {
            Element path = (Element) paths.item(index);
            String id = path.getAttribute("id");
            if (!id.isBlank()) {
                specs.put(
                        id,
                        new SvgPathSpec(
                                id,
                                path.getAttribute("fill").trim(),
                                normalizePathData(path.getAttribute("d"))));
            }
        }
        for (String requiredId : FOREGROUND_PATH_IDS) {
            if (!specs.containsKey(requiredId)) {
                throw new IllegalStateException("SVG path #" + requiredId + " was not found.");
            }
        }
        if (!specs.containsKey(MONOCHROME_PATH_ID)) {
            throw new IllegalStateException("SVG path #" + MONOCHROME_PATH_ID + " was not found.");
        }
        return specs;
    }

    private static String normalizePathData(String pathData) {
        return pathData.trim().replaceAll("\\s+", " ");
    }

    private static void validateSourceGeometry(Map<String, SvgPathSpec> specs) {
        Pattern numberPattern = Pattern.compile("[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?");
        for (SvgPathSpec spec : specs.values()) {
            Matcher matcher = numberPattern.matcher(spec.pathData());
            int count = 0;
            while (matcher.find()) {
                double coordinate = Double.parseDouble(matcher.group());
                count++;
                if (coordinate < ADAPTIVE_SAFE_MIN || coordinate > ADAPTIVE_SAFE_MAX) {
                    throw new IllegalStateException(
                            "SVG path #"
                                    + spec.id()
                                    + " coordinate "
                                    + coordinate
                                    + " exceeds the 66/108 adaptive safe zone ["
                                    + ADAPTIVE_SAFE_MIN
                                    + ", "
                                    + ADAPTIVE_SAFE_MAX
                                    + "].");
                }
            }
            if (count < 6) {
                throw new IllegalStateException("SVG path #" + spec.id() + " has insufficient coordinates.");
            }
        }
    }

    private static Map<Path, byte[]> generateExpectedVectorOutputs(
            Map<String, SvgPathSpec> svgPaths) {
        Map<Path, byte[]> outputs = new LinkedHashMap<>();
        Path drawable = RES.resolve("drawable");

        StringBuilder foregroundBuilder = new StringBuilder();
        foregroundBuilder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        foregroundBuilder.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
        foregroundBuilder.append("    android:width=\"108dp\"\n");
        foregroundBuilder.append("    android:height=\"108dp\"\n");
        foregroundBuilder.append("    android:viewportWidth=\"1024\"\n");
        foregroundBuilder.append("    android:viewportHeight=\"1024\">\n");
        for (String id : FOREGROUND_PATH_IDS) {
            SvgPathSpec spec = svgPaths.get(id);
            String fillColor = "left-outer-wing".equals(id)
                    ? "@color/mora_icon_foreground"
                    : spec.fill();
            foregroundBuilder.append("    <path\n");
            foregroundBuilder.append("        android:fillColor=\"").append(fillColor).append("\"\n");
            foregroundBuilder.append("        android:pathData=\"").append(spec.pathData()).append("\" />\n");
        }
        foregroundBuilder.append("</vector>\n");
        byte[] foregroundBytes = foregroundBuilder.toString().getBytes(StandardCharsets.UTF_8);

        for (String drawableName : FOREGROUND_DRAWABLES) {
            String variantXml = foregroundBuilder.toString();
            if ("ic_launcher_foreground_pine.xml".equals(drawableName)) {
                variantXml = variantXml.replace(
                        "@color/mora_icon_foreground",
                        "@color/mora_icon_pine_foreground");
            } else if ("ic_launcher_foreground_night.xml".equals(drawableName)) {
                variantXml = variantXml.replace(
                        "@color/mora_icon_foreground",
                        "@color/mora_icon_night_foreground");
            }
            outputs.put(
                    drawable.resolve(drawableName),
                    variantXml.getBytes(StandardCharsets.UTF_8));
        }

        SvgPathSpec monoSpec = svgPaths.get(MONOCHROME_PATH_ID);
        String monoXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="1024"
                    android:viewportHeight="1024">
                    <path
                        android:fillColor="#FF000000"
                        android:pathData="%s" />
                </vector>
                """.formatted(monoSpec.pathData());
        outputs.put(
                drawable.resolve("ic_launcher_monochrome.xml"),
                monoXml.getBytes(StandardCharsets.UTF_8));
        return outputs;
    }

    private static int writeOutputs(Map<Path, byte[]> expected) throws IOException {
        int updated = 0;
        for (Map.Entry<Path, byte[]> output : expected.entrySet()) {
            Path path = output.getKey();
            byte[] bytes = output.getValue();
            if (Files.isRegularFile(path)
                    && Arrays.equals(Files.readAllBytes(path), bytes)) {
                continue;
            }
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
            updated++;
        }
        return updated;
    }

    private static VerificationResult verifyAllAssets(Map<Path, byte[]> expectedVectors)
            throws IOException {
        List<Path> missing = new ArrayList<>();
        List<Path> stale = new ArrayList<>();
        Set<Path> expectedAll = new TreeSet<>(expectedVectors.keySet());

        for (Map.Entry<Path, byte[]> output : expectedVectors.entrySet()) {
            Path path = output.getKey();
            if (!Files.isRegularFile(path)) {
                missing.add(path);
            } else if (!Arrays.equals(Files.readAllBytes(path), output.getValue())) {
                stale.add(path);
            }
        }

        for (DensityTarget density : DENSITIES) {
            Path directory = RES.resolve("mipmap-" + density.name());
            for (String fileName : LEGACY_ICON_FILES) {
                Path pngPath = directory.resolve(fileName);
                expectedAll.add(pngPath);
                verifyPngDimensions(pngPath, density.size(), density.size(), missing, stale);
            }
        }

        expectedAll.add(SOCIAL_PREVIEW);
        verifyPngDimensions(
                SOCIAL_PREVIEW,
                SOCIAL_PREVIEW_WIDTH,
                SOCIAL_PREVIEW_HEIGHT,
                missing,
                stale);

        Set<Path> unexpected = findGeneratedCandidates();
        unexpected.removeAll(expectedAll);
        return new VerificationResult(missing, stale, new ArrayList<>(unexpected));
    }

    private static void verifyPngDimensions(
            Path path,
            int expectedWidth,
            int expectedHeight,
            List<Path> missing,
            List<Path> stale) throws IOException {
        if (!Files.isRegularFile(path)) {
            missing.add(path);
            return;
        }
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < 33) {
            stale.add(path);
            return;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                stale.add(path);
                return;
            }
        }
        int width = ByteBuffer.wrap(bytes, 16, 4).getInt();
        int height = ByteBuffer.wrap(bytes, 20, 4).getInt();
        if (width != expectedWidth || height != expectedHeight) {
            stale.add(path);
        }
    }

    private static Set<Path> findGeneratedCandidates() throws IOException {
        Set<Path> candidates = new TreeSet<>();
        Path drawable = RES.resolve("drawable");
        if (Files.isDirectory(drawable)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(drawable)) {
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    if (Files.isRegularFile(entry)
                            && name.endsWith(".xml")
                            && (name.startsWith("ic_launcher_foreground")
                                    || name.startsWith("ic_launcher_monochrome"))) {
                        candidates.add(entry);
                    }
                }
            }
        }

        if (Files.isDirectory(RES)) {
            try (DirectoryStream<Path> directories =
                    Files.newDirectoryStream(RES, "mipmap-*")) {
                for (Path directory : directories) {
                    if (!Files.isDirectory(directory)) {
                        continue;
                    }
                    try (DirectoryStream<Path> entries =
                            Files.newDirectoryStream(directory, "ic_launcher*.png")) {
                        for (Path entry : entries) {
                            if (Files.isRegularFile(entry)) {
                                candidates.add(entry);
                            }
                        }
                    }
                }
            }
        }

        if (Files.exists(SOCIAL_PREVIEW)) {
            candidates.add(SOCIAL_PREVIEW);
        }
        return candidates;
    }

    private enum Mode {
        EXPORT,
        VERIFY
    }

    private record SvgPathSpec(String id, String fill, String pathData) {}

    private record DensityTarget(String name, int size) {}

    private record VerificationResult(
            List<Path> missing,
            List<Path> stale,
            List<Path> unexpected) {
        boolean isClean() {
            return missing.isEmpty() && stale.isEmpty() && unexpected.isEmpty();
        }

        String describe() {
            StringBuilder message = new StringBuilder(
                    "Generated Mora launcher assets are not current:");
            append(message, "missing", missing);
            append(message, "stale", stale);
            append(message, "unexpected", unexpected);
            message.append(System.lineSeparator())
                    .append("Run `java tools/ExportMoraLauncherIcons.java` to regenerate.");
            return message.toString();
        }

        private static void append(
                StringBuilder message,
                String label,
                List<Path> paths) {
            paths.stream()
                    .sorted()
                    .forEach(path -> message.append(System.lineSeparator())
                            .append("  ")
                            .append(label)
                            .append(": ")
                            .append(path));
        }
    }
}
