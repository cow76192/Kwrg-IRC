package me.cookie.kwrg.svg.render;

import me.cookie.kwrg.svg.SvgNode;
import me.cookie.kwrg.svg.SvgParser;
import me.cookie.kwrg.svg.effect.SvgAnimation;
import me.cookie.kwrg.svg.effect.SvgFilter;
import me.cookie.kwrg.svg.shape.SvgPath;
import me.cookie.kwrg.svg.style.SvgPaint;
import me.cookie.kwrg.svg.style.SvgStyle;
import me.cookie.kwrg.svg.style.SvgTransform;
import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SvgRenderer {

    private static final Set<String> SKIP = Set.of(
            "defs", "style", "title", "desc", "metadata", "clippath", "mask", "filter",
            "lineargradient", "radialgradient", "pattern", "marker", "symbol", "script", "foreignobject"
    );

    private static final Set<String> CONTAINERS = Set.of("g", "a", "switch", "svg");
    private static final float MAX_BOX = 8192.0f;

    private static final ThreadLocal<Boolean> SKIP_ANIMATION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SvgRenderer() {
    }

    public static BufferedImage render(SvgParser.Document document, int width, int height, long timeMs) {
        return render(document, width, height, timeMs, false);
    }

    public static BufferedImage render(SvgParser.Document document, int width, int height, long timeMs, boolean cropWidth) {
        return render(document, width, height, timeMs, cropWidth, false);
    }

    public static BufferedImage render(SvgParser.Document document, int width, int height, long timeMs, boolean cropWidth, boolean skipAnimation) {
        SKIP_ANIMATION.set(skipAnimation);
        int safeWidth = Math.max(1, Math.min(width, 1024));
        int safeHeight = Math.max(1, Math.min(height, 1024));
        BufferedImage image = new BufferedImage(safeWidth, safeHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(new Color(0, 0, 0, 0));
            g.setComposite(AlphaComposite.Src);
            g.fillRect(0, 0, safeWidth, safeHeight);
            g.setComposite(AlphaComposite.SrcOver);

            SvgNode root = document.root();
            double[] box = intrinsicBox(document);
            double scale = cropWidth ? safeWidth / box[2] : Math.min(safeWidth / box[2], safeHeight / box[3]);
            if (!(scale > 0.0) || Double.isNaN(scale)) {
                scale = 1.0;
            }
            double offsetX = cropWidth ? 0.0 : (safeWidth - box[2] * scale) / 2.0;
            double offsetY = cropWidth ? 0.0 : (safeHeight - box[3] * scale) / 2.0;
            AffineTransform base = new AffineTransform();
            base.translate(offsetX, offsetY);
            base.scale(scale, scale);
            base.translate(-box[0], -box[1]);
            g.transform(base);
            g.clip(new Rectangle2D.Double(box[0], box[1], box[2], box[3]));

            SvgStyle style = SvgStyle.of(SvgStyle.root(), root, document.rules(), java.util.Collections.emptyMap());
            float[] deviceScale = {(float) scale};
            for (SvgNode child : root.children()) {
                draw(g, document, child, style, timeMs, 0, deviceScale);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    public static double[] intrinsicBox(SvgParser.Document document) {
        SvgNode root = document.root();
        String raw = root.attribute("viewBox");
        if (raw != null && !raw.isBlank()) {
            float[] values = SvgTransform.numbers(raw);
            if (values.length >= 4 && values[2] > 0.0f && values[3] > 0.0f) {
                return new double[]{values[0], values[1], Math.min(values[2], MAX_BOX), Math.min(values[3], MAX_BOX)};
            }
        }
        double w = absoluteLength(root.attribute("width"));
        double h = absoluteLength(root.attribute("height"));
        if (w > 0.0 && h > 0.0) {
            return new double[]{0.0, 0.0, w, h};
        }
        Rectangle2D measured = measure(document);
        if (measured != null && measured.getWidth() > 0.5 && measured.getHeight() > 0.5) {
            return new double[]{measured.getX(), measured.getY(), measured.getWidth(), measured.getHeight()};
        }
        return new double[]{0.0, 0.0, w > 0.0 ? w : 128.0, h > 0.0 ? h : 128.0};
    }

    private static double absoluteLength(String raw) {
        if (raw == null) {
            return -1.0;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.endsWith("%")) {
            return -1.0;
        }
        return SvgStyle.parseNumber(value, -1.0f);
    }

    public static Rectangle2D measure(SvgParser.Document document) {
        Rectangle2D[] accumulator = new Rectangle2D[]{null};
        AffineTransform identity = new AffineTransform();
        for (SvgNode child : document.root().children()) {
            measureNode(child, identity, accumulator, 0);
        }
        return accumulator[0];
    }

    private static void measureNode(SvgNode node, AffineTransform parent, Rectangle2D[] accumulator, int depth) {
        if (node == null || depth > 40) {
            return;
        }
        String tag = node.tag().toLowerCase(Locale.ROOT);
        if (SKIP.contains(tag)) {
            return;
        }
        AffineTransform current = parent;
        AffineTransform local = SvgTransform.parse(node.attribute("transform"));
        if (local != null) {
            current = new AffineTransform(parent);
            current.concatenate(local);
        }
        Shape shape = shapeOf(node, tag);
        if (shape != null) {
            Rectangle2D bounds = current.createTransformedShape(shape).getBounds2D();
            if (accumulator[0] == null) {
                accumulator[0] = bounds;
            } else {
                accumulator[0].add(bounds);
            }
            return;
        }
        for (SvgNode child : node.children()) {
            measureNode(child, current, accumulator, depth + 1);
        }
    }

    private static void draw(Graphics2D g, SvgParser.Document document, SvgNode node, SvgStyle parentStyle,
                             long timeMs, int depth, float[] deviceScale) {
        if (node == null || depth > 40) {
            return;
        }
        String tag = node.tag().toLowerCase(Locale.ROOT);
        if (SKIP.contains(tag)) {
            return;
        }
        Map<String, String> overrides = SKIP_ANIMATION.get() ? java.util.Collections.emptyMap() : SvgAnimation.overrides(node, timeMs);
        SvgStyle style = SvgStyle.of(parentStyle, node, document.rules(), overrides);
        if (!style.visible()) {
            return;
        }

        AffineTransform savedTransform = g.getTransform();
        Composite savedComposite = g.getComposite();
        Shape savedClip = g.getClip();
        String transformRaw = overrides.containsKey("transform") ? overrides.get("transform") : node.attribute("transform");
        AffineTransform local = SvgTransform.parse(transformRaw);
        if (local != null) {
            g.transform(local);
        }

        Shape clip = clipShape(document, node, style);
        if (clip != null) {
            g.clip(clip);
        }

        float opacity = style.opacity("opacity", 1.0f);
        String filterRef = style.get("filter");
        String maskRef = style.get("mask");
        boolean needsLayer = filterRef != null || maskRef != null || (CONTAINERS.contains(tag) && opacity < 0.999f);

        if (needsLayer) {
            Rectangle2D canvas = g.getDeviceConfiguration().getBounds();
            Shape clipNow = g.getClip();
            Shape deviceClip = clipNow == null ? canvas : g.getTransform().createTransformedShape(clipNow);
            Rectangle2D area = deviceClip.getBounds2D().createIntersection(canvas);
            if (area.getWidth() < 1.0 || area.getHeight() < 1.0) {
                g.setTransform(savedTransform);
                g.setComposite(savedComposite);
                g.setClip(savedClip);
                return;
            }
            int layerWidth = Math.max(1, (int) Math.ceil(area.getWidth()));
            int layerHeight = Math.max(1, (int) Math.ceil(area.getHeight()));
            BufferedImage layer = new BufferedImage(layerWidth, layerHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D lg = layer.createGraphics();
            lg.setRenderingHints(g.getRenderingHints());
            try {
                lg.translate(-area.getX(), -area.getY());
                lg.transform(g.getTransform());
                lg.setComposite(AlphaComposite.SrcOver);
                paintNode(lg, document, node, tag, style, overrides, timeMs, depth, deviceScale);
            } finally {
                lg.dispose();
            }
            BufferedImage effected = layer;
            if (maskRef != null) {
                effected = applyMask(document, maskRef, effected, area, deviceScale[0]);
            }
            if (filterRef != null) {
                SvgNode filterNode = resolve(document, filterRef);
                if (filterNode != null) {
                    effected = SvgFilter.apply(filterNode, effected, new Rectangle2D.Double(0, 0, area.getWidth(), area.getHeight()), deviceScale[0]);
                }
            }
            if (opacity < 0.999f) {
                effected = applyOpacity(effected, opacity);
            }
            g.setTransform(new AffineTransform());
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(effected, (int) Math.floor(area.getX()), (int) Math.floor(area.getY()), null);
            g.setTransform(savedTransform);
        } else {
            paintNode(g, document, node, tag, style, overrides, timeMs, depth, deviceScale);
        }

        g.setTransform(savedTransform);
        g.setComposite(savedComposite);
        g.setClip(savedClip);
    }

    private static void paintNode(Graphics2D g, SvgParser.Document document, SvgNode node, String tag, SvgStyle style,
                                  Map<String, String> overrides, long timeMs, int depth, float[] deviceScale) {
        if (CONTAINERS.contains(tag)) {
            for (SvgNode child : node.children()) {
                draw(g, document, child, style, timeMs, depth + 1, deviceScale);
            }
            return;
        }
        if ("use".equals(tag)) {
            SvgNode target = resolve(document, href(node));
            if (target == null) {
                return;
            }
            AffineTransform saved = g.getTransform();
            float x = SvgStyle.parseNumber(node.attribute("x"), 0.0f);
            float y = SvgStyle.parseNumber(node.attribute("y"), 0.0f);
            if (x != 0.0f || y != 0.0f) {
                g.translate(x, y);
            }
            draw(g, document, target, style, timeMs, depth + 1, deviceScale);
            g.setTransform(saved);
            return;
        }
        if ("text".equals(tag)) {
            drawText(g, node, style);
            return;
        }
        if ("image".equals(tag)) {
            drawImage(g, node);
            return;
        }
        Shape shape = shapeOf(node, tag);
        if (shape == null) {
            for (SvgNode child : node.children()) {
                draw(g, document, child, style, timeMs, depth + 1, deviceScale);
            }
            return;
        }
        paintShape(g, document.root(), style, shape);
    }

    private static void paintShape(Graphics2D g, SvgNode root, SvgStyle style, Shape shape) {
        Rectangle2D bounds = shape.getBounds2D();
        String fillRaw = style.get("fill", "black");
        if (!"none".equals(fillRaw)) {
            Paint paint = SvgPaint.of(root, style, fillRaw, bounds);
            if (paint != null) {
                Composite saved = g.getComposite();
                float alpha = style.opacity("fill-opacity", 1.0f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setPaint(paint);
                g.fill(shape);
                g.setComposite(saved);
            }
        }
        String strokeRaw = style.get("stroke", "none");
        if (!"none".equals(strokeRaw)) {
            Paint paint = SvgPaint.of(root, style, strokeRaw, bounds);
            if (paint != null) {
                Composite saved = g.getComposite();
                float alpha = style.opacity("stroke-opacity", 1.0f);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setPaint(paint);
                g.setStroke(strokeOf(style));
                g.draw(shape);
                g.setComposite(saved);
            }
        }
    }

    private static BasicStroke strokeOf(SvgStyle style) {
        float width = style.number("stroke-width", 1.0f);
        if (width <= 0.0f) {
            width = 1.0f;
        }
        int cap = switch (style.get("stroke-linecap", "butt")) {
            case "round" -> BasicStroke.CAP_ROUND;
            case "square" -> BasicStroke.CAP_SQUARE;
            default -> BasicStroke.CAP_BUTT;
        };
        int join = switch (style.get("stroke-linejoin", "miter")) {
            case "round" -> BasicStroke.JOIN_ROUND;
            case "bevel" -> BasicStroke.JOIN_BEVEL;
            default -> BasicStroke.JOIN_MITER;
        };
        String dashRaw = style.get("stroke-dasharray");
        if (dashRaw != null && !dashRaw.isBlank() && !"none".equals(dashRaw)) {
            float[] values = SvgTransform.numbers(dashRaw);
            if (values.length > 0) {
                for (int i = 0; i < values.length; i++) {
                    if (values[i] <= 0.0f) {
                        values[i] = 1.0f;
                    }
                }
                return new BasicStroke(width, cap, join, style.number("stroke-miterlimit", 4.0f), values, 0.0f);
            }
        }
        return new BasicStroke(width, cap, join, style.number("stroke-miterlimit", 4.0f));
    }

    private static Shape shapeOf(SvgNode node, String tag) {
        switch (tag) {
            case "rect": {
                float x = SvgStyle.parseNumber(node.attribute("x"), 0.0f);
                float y = SvgStyle.parseNumber(node.attribute("y"), 0.0f);
                float w = SvgStyle.parseNumber(node.attribute("width"), 0.0f);
                float h = SvgStyle.parseNumber(node.attribute("height"), 0.0f);
                if (w <= 0.0f || h <= 0.0f) {
                    return null;
                }
                float rx = SvgStyle.parseNumber(node.attribute("rx"), 0.0f);
                float ry = SvgStyle.parseNumber(node.attribute("ry"), rx);
                if (rx <= 0.0f && ry <= 0.0f) {
                    return new Rectangle2D.Float(x, y, w, h);
                }
                rx = Math.min(rx <= 0.0f ? ry : rx, w / 2.0f);
                ry = Math.min(ry <= 0.0f ? rx : ry, h / 2.0f);
                return new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, rx * 2.0f, ry * 2.0f);
            }
            case "circle": {
                float cx = SvgStyle.parseNumber(node.attribute("cx"), 0.0f);
                float cy = SvgStyle.parseNumber(node.attribute("cy"), 0.0f);
                float r = SvgStyle.parseNumber(node.attribute("r"), 0.0f);
                if (r <= 0.0f) {
                    return null;
                }
                return new Ellipse2D.Float(cx - r, cy - r, r * 2.0f, r * 2.0f);
            }
            case "ellipse": {
                float cx = SvgStyle.parseNumber(node.attribute("cx"), 0.0f);
                float cy = SvgStyle.parseNumber(node.attribute("cy"), 0.0f);
                float rx = SvgStyle.parseNumber(node.attribute("rx"), 0.0f);
                float ry = SvgStyle.parseNumber(node.attribute("ry"), 0.0f);
                if (rx <= 0.0f || ry <= 0.0f) {
                    return null;
                }
                return new Ellipse2D.Float(cx - rx, cy - ry, rx * 2.0f, ry * 2.0f);
            }
            case "line": {
                return new Line2D.Float(
                        SvgStyle.parseNumber(node.attribute("x1"), 0.0f),
                        SvgStyle.parseNumber(node.attribute("y1"), 0.0f),
                        SvgStyle.parseNumber(node.attribute("x2"), 0.0f),
                        SvgStyle.parseNumber(node.attribute("y2"), 0.0f));
            }
            case "polyline":
            case "polygon": {
                float[] values = SvgTransform.numbers(node.attribute("points"));
                if (values.length < 4) {
                    return null;
                }
                Path2D.Float path = new Path2D.Float();
                path.moveTo(values[0], values[1]);
                for (int i = 2; i + 1 < values.length; i += 2) {
                    path.lineTo(values[i], values[i + 1]);
                }
                if ("polygon".equals(tag)) {
                    path.closePath();
                }
                return path;
            }
            case "path": {
                String d = node.attribute("d");
                if (d == null || d.isBlank()) {
                    return null;
                }
                Path2D.Double path = SvgPath.parse(d);
                return path.getCurrentPoint() == null ? null : path;
            }
            default:
                return null;
        }
    }

    private static void drawText(Graphics2D g, SvgNode node, SvgStyle style) {
        String content = node.text();
        if (content == null || content.isEmpty()) {
            return;
        }
        float size = style.number("font-size", 12.0f);
        if (size <= 0.0f) {
            return;
        }
        int fontStyle = Font.PLAIN;
        if ("bold".equals(style.get("font-weight"))) {
            fontStyle |= Font.BOLD;
        }
        if ("italic".equals(style.get("font-style"))) {
            fontStyle |= Font.ITALIC;
        }
        Font font = new Font(style.get("font-family", "SansSerif"), fontStyle, Math.max(1, Math.round(size)));
        g.setFont(font);
        float x = SvgStyle.parseNumber(node.attribute("x"), 0.0f);
        float y = SvgStyle.parseNumber(node.attribute("y"), 0.0f);
        FontMetrics metrics = g.getFontMetrics(font);
        String anchor = style.get("text-anchor", "start");
        if ("middle".equals(anchor)) {
            x -= metrics.stringWidth(content) / 2.0f;
        } else if ("end".equals(anchor)) {
            x -= metrics.stringWidth(content);
        }
        Paint paint = SvgPaint.of(node, style, style.get("fill", "black"), new Rectangle2D.Float(x, y - size, metrics.stringWidth(content), size));
        if (paint != null) {
            g.setPaint(paint);
            g.drawString(content, x, y);
        }
    }

    private static void drawImage(Graphics2D g, SvgNode node) {
        String href = href(node);
        if (href == null || !href.startsWith("data:")) {
            return;
        }
        int comma = href.indexOf(',');
        if (comma < 0) {
            return;
        }
        try {
            String meta = href.substring(5, comma);
            String payload = href.substring(comma + 1);
            byte[] bytes = meta.contains("base64")
                    ? Base64.getDecoder().decode(payload)
                    : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length == 0 || bytes.length > 1024 * 1024) {
                return;
            }
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) {
                return;
            }
            float x = SvgStyle.parseNumber(node.attribute("x"), 0.0f);
            float y = SvgStyle.parseNumber(node.attribute("y"), 0.0f);
            float w = SvgStyle.parseNumber(node.attribute("width"), source.getWidth());
            float h = SvgStyle.parseNumber(node.attribute("height"), source.getHeight());
            g.drawImage(source, (int) x, (int) y, (int) w, (int) h, null);
        } catch (Exception ignored) {
        }
    }

    private static Shape clipShape(SvgParser.Document document, SvgNode node, SvgStyle style) {
        String raw = style.get("clip-path");
        if (raw == null || !raw.startsWith("url(")) {
            return null;
        }
        SvgNode clipNode = resolve(document, raw);
        if (clipNode == null) {
            return null;
        }
        Area area = new Area();
        collectClip(document, clipNode, area, style);
        return area.isEmpty() ? null : area;
    }

    private static void collectClip(SvgParser.Document document, SvgNode node, Area area, SvgStyle style) {
        for (SvgNode child : node.children()) {
            String tag = child.tag().toLowerCase(Locale.ROOT);
            SvgStyle childStyle = SvgStyle.of(style, child, document.rules(), Map.of());
            String transformRaw = child.attribute("transform");
            AffineTransform transform = SvgTransform.parse(transformRaw);
            Shape shape = shapeOf(child, tag);
            if (shape != null) {
                if (transform != null) {
                    shape = transform.createTransformedShape(shape);
                }
                area.add(new Area(shape));
            }
            if (!child.children().isEmpty()) {
                collectClip(document, child, area, childStyle);
            }
        }
    }

    private static BufferedImage applyMask(SvgParser.Document document, String reference, BufferedImage content,
                                          Rectangle2D clipBounds, float scale) {
        SvgNode maskNode = resolve(document, reference);
        if (maskNode == null) {
            return content;
        }
        BufferedImage mask = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D mg = mask.createGraphics();
        try {
            mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            mg.setComposite(AlphaComposite.Clear);
            mg.fillRect(0, 0, content.getWidth(), content.getHeight());
            mg.setComposite(AlphaComposite.SrcOver);
            AffineTransform base = new AffineTransform();
            base.scale(scale, scale);
            base.translate(-clipBounds.getX(), -clipBounds.getY());
            mg.transform(base);
            SvgStyle style = SvgStyle.root();
            float[] deviceScale = {scale};
            for (SvgNode child : maskNode.children()) {
                draw(mg, document, child, style, 0L, 0, deviceScale);
            }
        } finally {
            mg.dispose();
        }
        BufferedImage result = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < content.getHeight(); y++) {
            for (int x = 0; x < content.getWidth(); x++) {
                int src = content.getRGB(x, y);
                int msk = mask.getRGB(x, y);
                int luminance = Math.max(Math.max((msk >> 16) & 0xFF, (msk >> 8) & 0xFF), msk & 0xFF);
                int alpha = ((msk >>> 24) & 0xFF) * luminance / 255;
                int outAlpha = ((src >>> 24) & 0xFF) * alpha / 255;
                result.setRGB(x, y, (outAlpha << 24) | (src & 0x00FFFFFF));
            }
        }
        return result;
    }

    private static BufferedImage applyOpacity(BufferedImage source, float opacity) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    private static SvgNode resolve(SvgParser.Document document, String reference) {
        if (reference == null) {
            return null;
        }
        String id = SvgPaint.innerId(reference);
        if (id == null && reference.startsWith("#")) {
            id = reference.substring(1).trim().toLowerCase(Locale.ROOT);
        }
        if (id == null) {
            return null;
        }
        return document.root().index().get(id);
    }

    private static String href(SvgNode node) {
        String href = node.attribute("href");
        if (href == null) {
            href = node.attribute("xlink:href");
        }
        return href;
    }
}
