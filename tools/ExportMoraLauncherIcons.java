import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Deterministically exports Mora's launcher VectorDrawables and legacy PNGs
 * from docs/design/mora-icon-v0.3.3.svg.
 *
 * Run from the repository root with:
 *   java tools/ExportMoraLauncherIcons.java
 */
public final class ExportMoraLauncherIcons {
    private static final Path SVG_SOURCE =
            Path.of("docs", "design", "mora-icon-v0.3.3.svg");
    private static final Path COLORS_SOURCE =
            Path.of("app", "src", "main", "res", "values", "colors.xml");
    private static final Path SOCIAL_PREVIEW =
            Path.of("docs", "assets", "social-preview.png");
    private static final Path RES =
            Path.of("app", "src", "main", "res");
    private static final int VIEWPORT = 1024;
    private static final int SUPERSAMPLE = 4;
    private static final double ADAPTIVE_SAFE_MIN = 199.0;
    private static final double ADAPTIVE_SAFE_MAX = 825.0;

    private static final Map<String, Integer> DENSITIES = Map.of(
            "mdpi", 48,
            "hdpi", 72,
            "xhdpi", 96,
            "xxhdpi", 144,
            "xxxhdpi", 192);

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

    private ExportMoraLauncherIcons() {}

    public static void main(String[] args) throws Exception {
        ensureRepositoryRoot();
        String pathData = readSvgPathData();
        Path2D.Double mark = parsePath(pathData);
        Map<String, Color> colors = readColors();

        validateSourceGeometry(mark);
        exportVectorDrawables(pathData);
        exportLegacyPngs(mark, colors);
        exportSocialPreview(mark, colors);
        validateOutputs(pathData);

        System.out.println("Exported Mora launcher resources from " + SVG_SOURCE);
    }

    private static void ensureRepositoryRoot() {
        if (!Files.isRegularFile(SVG_SOURCE) || !Files.isRegularFile(COLORS_SOURCE)) {
            throw new IllegalStateException(
                    "Run this exporter from the Mora repository root.");
        }
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

    private static void exportVectorDrawables(String pathData) throws IOException {
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
            Files.writeString(
                    drawable.resolve(target.fileName()),
                    xml,
                    StandardCharsets.UTF_8);
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

    private static void exportLegacyPngs(
            Path2D.Double mark,
            Map<String, Color> colors) throws IOException {
        for (Map.Entry<String, Integer> density : DENSITIES.entrySet()) {
            Path directory = RES.resolve("mipmap-" + density.getKey());
            Files.createDirectories(directory);
            writeIcon(
                    directory.resolve("ic_launcher.png"),
                    density.getValue(),
                    mark,
                    colors.get("mora_icon_background"),
                    colors.get("mora_icon_foreground"),
                    false);
            writeIcon(
                    directory.resolve("ic_launcher_pine.png"),
                    density.getValue(),
                    mark,
                    colors.get("mora_icon_pine_background"),
                    colors.get("mora_icon_pine_foreground"),
                    false);
            writeIcon(
                    directory.resolve("ic_launcher_night.png"),
                    density.getValue(),
                    mark,
                    colors.get("mora_icon_night_background"),
                    colors.get("mora_icon_night_foreground"),
                    false);
            writeIcon(
                    directory.resolve("ic_launcher_round.png"),
                    density.getValue(),
                    mark,
                    colors.get("mora_icon_background"),
                    colors.get("mora_icon_foreground"),
                    true);
        }
    }

    private static void exportSocialPreview(
            Path2D.Double mark,
            Map<String, Color> colors) throws IOException {
        BufferedImage image = ImageIO.read(SOCIAL_PREVIEW.toFile());
        if (image == null || image.getWidth() != 1280 || image.getHeight() != 640) {
            throw new IllegalStateException(
                    "Unexpected social-preview dimensions: " + SOCIAL_PREVIEW);
        }

        Graphics2D graphics = image.createGraphics();
        applyQualityHints(graphics);
        graphics.setColor(colors.get("mora_icon_background"));
        graphics.fillRect(68, 60, 140, 116);

        Rectangle2D bounds = mark.getBounds2D();
        double scale = 100.0 / bounds.getWidth();
        AffineTransform transform = new AffineTransform();
        transform.translate(86.0 - bounds.getX() * scale, 84.0 - bounds.getY() * scale);
        transform.scale(scale, scale);

        graphics.setColor(colors.get("mora_icon_foreground"));
        graphics.fill(transform.createTransformedShape(mark));
        graphics.dispose();

        if (!ImageIO.write(image, "png", SOCIAL_PREVIEW.toFile())) {
            throw new IOException("PNG writer unavailable for " + SOCIAL_PREVIEW);
        }
    }

    private static void writeIcon(
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

        if (!ImageIO.write(outputImage, "png", output.toFile())) {
            throw new IOException("PNG writer unavailable for " + output);
        }
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

    private static void validateOutputs(String pathData) throws Exception {
        String canonical = normalizePathData(pathData);
        for (VectorTarget target : VECTOR_TARGETS) {
            Path output = RES.resolve("drawable").resolve(target.fileName());
            String xml = Files.readString(output, StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("android:pathData=\"([^\"]+)\"")
                    .matcher(xml);
            if (!matcher.find() || !canonical.equals(normalizePathData(matcher.group(1)))) {
                throw new IllegalStateException(
                        "Vector path mismatch: " + output);
            }
        }

        for (Map.Entry<String, Integer> density : DENSITIES.entrySet()) {
            Path directory = RES.resolve("mipmap-" + density.getKey());
            for (String name : List.of(
                    "ic_launcher.png",
                    "ic_launcher_pine.png",
                    "ic_launcher_night.png",
                    "ic_launcher_round.png")) {
                Path output = directory.resolve(name);
                BufferedImage image = ImageIO.read(output.toFile());
                if (image == null
                        || image.getWidth() != density.getValue()
                        || image.getHeight() != density.getValue()
                        || image.getColorModel().getNumComponents() != 4) {
                    throw new IllegalStateException(
                            "Invalid generated PNG: " + output);
                }
            }
        }
    }

    private record VectorTarget(String fileName, String fillColor) {}
}
