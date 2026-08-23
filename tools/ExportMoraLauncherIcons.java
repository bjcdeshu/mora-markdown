import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import javax.imageio.ImageIO;
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
    private static final int VIEWPORT = 1024;
    private static final int SUPERSAMPLE = 4;
    private static final int SOCIAL_PREVIEW_WIDTH = 1280;
    private static final int SOCIAL_PREVIEW_HEIGHT = 640;
    private static final int SOCIAL_MARK_SLOT_X = 68;
    private static final int SOCIAL_MARK_SLOT_Y = 60;
    private static final int SOCIAL_MARK_SLOT_WIDTH = 140;
    private static final int SOCIAL_MARK_SLOT_HEIGHT = 116;
    private static final double ADAPTIVE_SAFE_MIN = 199.0;
    private static final double ADAPTIVE_SAFE_MAX = 825.0;

    private static final List<DensityTarget> DENSITIES = List.of(
            new DensityTarget("mdpi", 48),
            new DensityTarget("hdpi", 72),
            new DensityTarget("xhdpi", 96),
            new DensityTarget("xxhdpi", 144),
            new DensityTarget("xxxhdpi", 192));

    private static final List<VectorTarget> VECTOR_TARGETS = List.of(
            new VectorTarget(
                    "ic_launcher_foreground.xml",
                    "@color/mora_icon_foreground"),
            new VectorTarget(
                    "ic_launcher_foreground_pine.xml",
                    "@color/mora_icon_pine_foreground"),
            new VectorTarget(
                    "ic_launcher_foreground_night.xml",
                    "@color/mora_icon_night_foreground"),
            new VectorTarget(
                    "ic_launcher_monochrome.xml",
                    "#FF000000"));

    private static final List<LegacyIconTarget> LEGACY_ICON_TARGETS = List.of(
            new LegacyIconTarget(
                    "ic_launcher.png",
                    "mora_icon_background",
                    "mora_icon_foreground",
                    false),
            new LegacyIconTarget(
                    "ic_launcher_pine.png",
                    "mora_icon_pine_background",
                    "mora_icon_pine_foreground",
                    false),
            new LegacyIconTarget(
                    "ic_launcher_night.png",
                    "mora_icon_night_background",
                    "mora_icon_night_foreground",
                    false),
            new LegacyIconTarget(
                    "ic_launcher_round.png",
                    "mora_icon_background",
                    "mora_icon_foreground",
                    true));

    private ExportMoraLauncherIcons() {}

    public static void main(String[] args) {
        try {
            Mode mode = parseMode(args);
            ensureRepositoryRoot();
            Map<Path, byte[]> expected = generateExpectedOutputs();

            if (mode == Mode.VERIFY) {
                VerificationResult result = compareOutputs(expected);
                if (!result.isClean()) {
                    throw new IllegalStateException(result.describe());
                }
                System.out.println(
                        "Verified " + expected.size() + " generated Mora launcher assets.");
                return;
            }

            int updated = writeOutputs(expected);
            VerificationResult result = compareOutputs(expected);
            if (!result.isClean()) {
                throw new IllegalStateException(result.describe());
            }
            System.out.println(
                    "Exported "
                            + expected.size()
                            + " Mora launcher assets ("
                            + updated
                            + " updated).");
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

    private static Map<Path, byte[]> generateExpectedOutputs() throws Exception {
        String pathData = readSvgPathData();
        Path2D.Double mark = parsePath(pathData);
        Map<String, Color> colors = readColors();
        validateSourceGeometry(mark);

        Map<Path, byte[]> outputs = new LinkedHashMap<>();
        addVectorDrawables(outputs, pathData);
        addLegacyPngs(outputs, mark, colors);
        outputs.put(SOCIAL_PREVIEW, renderSocialPreview(mark, colors));
        return outputs;
    }

    private static String readSvgPathData() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(SVG_SOURCE.toFile());
        NodeList paths = document.getElementsByTagName("path");
        for (int index = 0; index < paths.getLength(); index++) {
            Element path = (Element) paths.item(index);
            if ("folded-passage".equals(path.getAttribute("id"))) {
                return normalizePathData(path.getAttribute("d"));
            }
        }
        throw new IllegalStateException("SVG path #folded-passage was not found.");
    }

    private static String normalizePathData(String pathData) {
        return pathData.trim().replaceAll("\\s+", " ");
    }

    private static Map<String, Color> readColors() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(COLORS_SOURCE.toFile());
        Map<String, Color> colors = new LinkedHashMap<>();
        NodeList nodes = document.getElementsByTagName("color");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            colors.put(
                    element.getAttribute("name"),
                    Color.decode(element.getTextContent().trim()));
        }
        for (String required : List.of(
                "mora_icon_background",
                "mora_icon_foreground",
                "mora_icon_pine_background",
                "mora_icon_pine_foreground",
                "mora_icon_night_background",
                "mora_icon_night_foreground")) {
            if (!colors.containsKey(required)) {
                throw new IllegalStateException("Missing color resource: " + required);
            }
        }
        return colors;
    }

    private static void addVectorDrawables(
            Map<Path, byte[]> outputs,
            String pathData) {
        Path drawable = RES.resolve("drawable");
        for (VectorTarget target : VECTOR_TARGETS) {
            String xml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <vector xmlns:android="http://schemas.android.com/apk/res/android"
                        android:width="108dp"
                        android:height="108dp"
                        android:viewportWidth="1024"
                        android:viewportHeight="1024">
                        <path
                            android:fillColor="%s"
                            android:fillType="evenOdd"
                            android:pathData="%s" />
                    </vector>
                    """.formatted(target.fillColor(), pathData);
            outputs.put(
                    drawable.resolve(target.fileName()),
                    xml.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void validateSourceGeometry(Path2D.Double mark) {
        Rectangle2D bounds = mark.getBounds2D();
        if (bounds.getMinX() < ADAPTIVE_SAFE_MIN
                || bounds.getMinY() < ADAPTIVE_SAFE_MIN
                || bounds.getMaxX() > ADAPTIVE_SAFE_MAX
                || bounds.getMaxY() > ADAPTIVE_SAFE_MAX) {
            throw new IllegalStateException(
                    "Launcher mark exceeds the 66/108 adaptive safe zone: " + bounds);
        }
    }

    private static void addLegacyPngs(
            Map<Path, byte[]> outputs,
            Path2D.Double mark,
            Map<String, Color> colors) throws IOException {
        for (DensityTarget density : DENSITIES) {
            Path directory = RES.resolve("mipmap-" + density.name());
            for (LegacyIconTarget icon : LEGACY_ICON_TARGETS) {
                Path output = directory.resolve(icon.fileName());
                outputs.put(
                        output,
                        renderIcon(
                                output,
                                density.size(),
                                mark,
                                colors.get(icon.backgroundColor()),
                                colors.get(icon.foregroundColor()),
                                icon.round()));
            }
        }
    }

    private static byte[] renderSocialPreview(
            Path2D.Double mark,
            Map<String, Color> colors) throws IOException {
        BufferedImage template = ImageIO.read(SOCIAL_PREVIEW_TEMPLATE.toFile());
        if (template == null
                || template.getWidth() != SOCIAL_PREVIEW_WIDTH
                || template.getHeight() != SOCIAL_PREVIEW_HEIGHT) {
            throw new IllegalStateException(
                    "Unexpected social-preview template dimensions: "
                            + SOCIAL_PREVIEW_TEMPLATE);
        }

        BufferedImage image = new BufferedImage(
                SOCIAL_PREVIEW_WIDTH,
                SOCIAL_PREVIEW_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        image.setRGB(
                0,
                0,
                SOCIAL_PREVIEW_WIDTH,
                SOCIAL_PREVIEW_HEIGHT,
                template.getRGB(
                        0,
                        0,
                        SOCIAL_PREVIEW_WIDTH,
                        SOCIAL_PREVIEW_HEIGHT,
                        null,
                        0,
                        SOCIAL_PREVIEW_WIDTH),
                0,
                SOCIAL_PREVIEW_WIDTH);

        Graphics2D graphics = image.createGraphics();
        applyQualityHints(graphics);
        graphics.setColor(colors.get("mora_icon_background"));
        graphics.fillRect(
                SOCIAL_MARK_SLOT_X,
                SOCIAL_MARK_SLOT_Y,
                SOCIAL_MARK_SLOT_WIDTH,
                SOCIAL_MARK_SLOT_HEIGHT);

        Rectangle2D bounds = mark.getBounds2D();
        double scale = 100.0 / bounds.getWidth();
        AffineTransform transform = new AffineTransform();
        transform.translate(86.0 - bounds.getX() * scale, 84.0 - bounds.getY() * scale);
        transform.scale(scale, scale);

        graphics.setColor(colors.get("mora_icon_foreground"));
        graphics.fill(transform.createTransformedShape(mark));
        graphics.dispose();

        return encodePng(image, SOCIAL_PREVIEW);
    }

    private static byte[] renderIcon(
            Path output,
            int size,
            Path2D.Double mark,
            Color background,
            Color foreground,
            boolean round) throws IOException {
        int renderSize = size * SUPERSAMPLE;
        BufferedImage large =
                new BufferedImage(renderSize, renderSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = large.createGraphics();
        applyQualityHints(graphics);

        graphics.setColor(background);
        if (round) {
            graphics.fillOval(0, 0, renderSize, renderSize);
        } else {
            double arc = renderSize * 0.5;
            graphics.fill(new RoundRectangle2D.Double(
                    0,
                    0,
                    renderSize,
                    renderSize,
                    arc,
                    arc));
        }

        AffineTransform transform = AffineTransform.getScaleInstance(
                (double) renderSize / VIEWPORT,
                (double) renderSize / VIEWPORT);
        graphics.setColor(foreground);
        graphics.fill(transform.createTransformedShape(mark));
        graphics.dispose();

        BufferedImage outputImage =
                new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D outputGraphics = outputImage.createGraphics();
        applyQualityHints(outputGraphics);
        outputGraphics.drawImage(large, 0, 0, size, size, null);
        outputGraphics.dispose();

        return encodePng(outputImage, output);
    }

    private static byte[] encodePng(BufferedImage image, Path output) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("PNG writer unavailable for " + output);
        }
        return bytes.toByteArray();
    }

    private static void applyQualityHints(Graphics2D graphics) {
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(
                RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.setRenderingHint(
                RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    private static Path2D.Double parsePath(String pathData) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = Pattern.compile(
                        "[MLCQZmlcqz]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?")
                .matcher(pathData);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        int index = 0;
        char command = 0;
        double currentX = 0;
        double currentY = 0;
        double startX = 0;
        double startY = 0;

        while (index < tokens.size()) {
            String token = tokens.get(index);
            if (isCommand(token)) {
                command = token.charAt(0);
                index++;
            }
            if (command == 0) {
                throw new IllegalArgumentException("SVG path starts without a command.");
            }

            boolean relative = Character.isLowerCase(command);
            switch (Character.toUpperCase(command)) {
                case 'M' -> {
                    double x = number(tokens, index++);
                    double y = number(tokens, index++);
                    if (relative) {
                        x += currentX;
                        y += currentY;
                    }
                    path.moveTo(x, y);
                    currentX = startX = x;
                    currentY = startY = y;
                    command = relative ? 'l' : 'L';
                }
                case 'L' -> {
                    double x = number(tokens, index++);
                    double y = number(tokens, index++);
                    if (relative) {
                        x += currentX;
                        y += currentY;
                    }
                    path.lineTo(x, y);
                    currentX = x;
                    currentY = y;
                }
                case 'C' -> {
                    double x1 = number(tokens, index++);
                    double y1 = number(tokens, index++);
                    double x2 = number(tokens, index++);
                    double y2 = number(tokens, index++);
                    double x = number(tokens, index++);
                    double y = number(tokens, index++);
                    if (relative) {
                        x1 += currentX;
                        y1 += currentY;
                        x2 += currentX;
                        y2 += currentY;
                        x += currentX;
                        y += currentY;
                    }
                    path.curveTo(x1, y1, x2, y2, x, y);
                    currentX = x;
                    currentY = y;
                }
                case 'Q' -> {
                    double x1 = number(tokens, index++);
                    double y1 = number(tokens, index++);
                    double x = number(tokens, index++);
                    double y = number(tokens, index++);
                    if (relative) {
                        x1 += currentX;
                        y1 += currentY;
                        x += currentX;
                        y += currentY;
                    }
                    path.quadTo(x1, y1, x, y);
                    currentX = x;
                    currentY = y;
                }
                case 'Z' -> {
                    path.closePath();
                    currentX = startX;
                    currentY = startY;
                    command = 0;
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported SVG path command: " + command);
            }
        }
        return path;
    }

    private static boolean isCommand(String token) {
        return token.length() == 1 && Character.isLetter(token.charAt(0));
    }

    private static double number(List<String> tokens, int index) {
        if (index >= tokens.size() || isCommand(tokens.get(index))) {
            throw new IllegalArgumentException("Missing numeric SVG path argument.");
        }
        return Double.parseDouble(tokens.get(index));
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

    private static VerificationResult compareOutputs(Map<Path, byte[]> expected)
            throws IOException {
        List<Path> missing = new ArrayList<>();
        List<Path> stale = new ArrayList<>();

        for (Map.Entry<Path, byte[]> output : expected.entrySet()) {
            Path path = output.getKey();
            if (!Files.isRegularFile(path)) {
                missing.add(path);
            } else if (!Arrays.equals(Files.readAllBytes(path), output.getValue())) {
                stale.add(path);
            }
        }

        Set<Path> unexpected = findGeneratedCandidates();
        unexpected.removeAll(expected.keySet());
        return new VerificationResult(missing, stale, new ArrayList<>(unexpected));
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

    private record DensityTarget(String name, int size) {}

    private record VectorTarget(String fileName, String fillColor) {}

    private record LegacyIconTarget(
            String fileName,
            String backgroundColor,
            String foregroundColor,
            boolean round) {}

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
