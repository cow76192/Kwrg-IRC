package me.cookie.kwrg.gui.util;

import com.mojang.blaze3d.font.GlyphProvider;
import me.cookie.kwrg.irc.IrcConfig;
import me.cookie.kwrg.mixin.accessor.FontManagerAccessor;
import me.cookie.kwrg.mixin.accessor.FontSetAccessor;
import me.cookie.kwrg.mixin.accessor.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FontManagerHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger("kwrg-font");
    public static final String DEFAULT_FONT = "default";

    public static final Identifier GUI_FONT_ID = Identifier.fromNamespaceAndPath("kwrg", "gui_font");
    private static final FontDescription GUI_FONT_DESC = new FontDescription.Resource(GUI_FONT_ID);
    private static GlyphProvider currentActiveProvider = null;
    private static String currentLoadedFontName = null;
    private static Font guiInputFont;

    /** Native inputs measure plain strings, so their font must also use the GUI glyphs by default. */
    public static Font getGuiInputFont() {
        if (guiInputFont == null) {
            guiInputFont = new Font(new Font.Provider() {
                private FontSet fontSet() {
                    FontManager manager = ((MinecraftAccessor) Minecraft.getInstance()).kwrg$getFontManager();
                    var sets = ((FontManagerAccessor) manager).kwrg$getFontSets();
                    return getGuiStyle().isEmpty() ? sets.get(Minecraft.DEFAULT_FONT)
                            : sets.getOrDefault(GUI_FONT_ID, sets.get(Minecraft.DEFAULT_FONT));
                }

                @Override public GlyphSource glyphs(FontDescription description) { return fontSet().source(false); }
                @Override public EffectGlyph effect() { return fontSet().whiteGlyph(); }
            });
        }
        return guiInputFont;
    }

    public static Path ensureFontDir() {
        Path fontDir = IrcConfig.getFontDir();
        try {
            if (!Files.exists(fontDir)) {
                Files.createDirectories(fontDir);
            }
        } catch (IOException ignored) {}
        return fontDir;
    }

    public static List<String> getAvailableFonts() {
        List<String> list = new ArrayList<>();
        list.add(DEFAULT_FONT);

        Path fontDir = ensureFontDir();
        if (Files.exists(fontDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(fontDir, "*.{ttf,otf,TTF,OTF}")) {
                for (Path p : stream) {
                    list.add(p.getFileName().toString());
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    public static Style getGuiStyle() {
        String selected = IrcConfig.get().guiFont;
        if (selected == null || selected.isBlank() || DEFAULT_FONT.equalsIgnoreCase(selected)) {
            return Style.EMPTY;
        }
        return Style.EMPTY.withFont(GUI_FONT_DESC);
    }

    public static Component apply(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return Component.literal(text).setStyle(getGuiStyle());
    }

    public static Component apply(Component component) {
        if (component == null) return Component.empty();
        Style guiStyle = getGuiStyle();
        if (guiStyle.isEmpty()) return component;
        return component.copy().setStyle(component.getStyle().applyTo(guiStyle));
    }

    public static String getCurrentLoadedFontName() {
        return currentLoadedFontName != null ? currentLoadedFontName : DEFAULT_FONT;
    }

    public static void onResourceReload() {
        LOGGER.info("[Kwrg] 检测到客户端资源重载，正在恢复自定义 GUI 字体...");
        currentActiveProvider = null;
        currentLoadedFontName = null;

        String configuredFont = IrcConfig.get().guiFont;
        if (configuredFont != null && !configuredFont.isBlank() && !DEFAULT_FONT.equalsIgnoreCase(configuredFont)) {
            applyFontInternal(configuredFont, true);
        }
    }

    public static boolean applyFont(String fontFileName) {
        return applyFontInternal(fontFileName, false);
    }

    public static boolean reloadCurrentFont() {
        return applyFontInternal(IrcConfig.get().guiFont, true);
    }

    private static boolean applyFontInternal(String fontFileName, boolean forceReload) {
        if (fontFileName == null || fontFileName.isBlank()) {
            fontFileName = DEFAULT_FONT;
        }

        if (!forceReload && fontFileName.equalsIgnoreCase(currentLoadedFontName)) {
            return true;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;

        FontManager fontManager = ((MinecraftAccessor) mc).kwrg$getFontManager();
        if (fontManager == null) return false;

        FontManagerAccessor managerAccessor = (FontManagerAccessor) fontManager;
        Map<Identifier, FontSet> fontSets = managerAccessor.kwrg$getFontSets();
        if (fontSets == null) return false;

        FontSet defaultFontSet = fontSets.get(Minecraft.DEFAULT_FONT);
        List<GlyphProvider.Conditional> fallbackProviders = defaultFontSet != null
                ? ((FontSetAccessor) defaultFontSet).kwrg$getAllProviders()
                : List.of();

        if (DEFAULT_FONT.equalsIgnoreCase(fontFileName)) {
            closeCurrentProvider();
            FontSet guiFontSet = fontSets.get(GUI_FONT_ID);
            if (guiFontSet != null) {
                guiFontSet.reload(fallbackProviders, Set.of());
            }
            currentLoadedFontName = DEFAULT_FONT;
            LOGGER.info("[Kwrg] 已重置为原版默认字体");
            return true;
        }

        Path fontFile = ensureFontDir().resolve(fontFileName);
        if (!Files.exists(fontFile)) {
            LOGGER.warn("[Kwrg] 目标字体文件不存在: {}", fontFile);
            return false;
        }

        try {
            TrueTypeGlyphProviderDefinition def = new TrueTypeGlyphProviderDefinition(
                    Identifier.fromNamespaceAndPath("kwrg", "dynamic"),
                    10.5f,
                    4.0f,
                    TrueTypeGlyphProviderDefinition.Shift.NONE,
                    ""
            );

            ResourceManager singleFileManager = new ResourceManager() {
                @Override
                public InputStream open(Identifier location) throws IOException {
                    return Files.newInputStream(fontFile);
                }

                @Override public Set<String> getNamespaces() { return Set.of(); }
                @Override public List<Resource> getResourceStack(Identifier location) { return List.of(); }
                @Override public Map<Identifier, Resource> listResources(String path, Predicate<Identifier> filter) { return Map.of(); }
                @Override public Map<Identifier, List<Resource>> listResourceStacks(String path, Predicate<Identifier> filter) { return Map.of(); }
                @Override public Stream<net.minecraft.server.packs.PackResources> listPacks() { return Stream.empty(); }
                @Override public Optional<Resource> getResource(Identifier location) { return Optional.empty(); }
            };

            GlyphProviderDefinition.Loader loader = def.unpack().left().orElseThrow();
            GlyphProvider newProvider = loader.load(singleFileManager);

            // 成功解析出新 provider 后再关闭旧 provider，保证平滑过渡
            closeCurrentProvider();
            currentActiveProvider = newProvider;

            List<GlyphProvider.Conditional> combinedProviders = new ArrayList<>();
            combinedProviders.add(new GlyphProvider.Conditional(newProvider, FontOption.Filter.ALWAYS_PASS));
            combinedProviders.addAll(fallbackProviders);

            FontSet guiFontSet = fontSets.get(GUI_FONT_ID);
            if (guiFontSet == null) {
                guiFontSet = managerAccessor.kwrg$createFontSet(GUI_FONT_ID, combinedProviders, Set.of());
                fontSets.put(GUI_FONT_ID, guiFontSet);
            } else {
                guiFontSet.reload(combinedProviders, Set.of());
            }

            currentLoadedFontName = fontFileName;
            LOGGER.info("[Kwrg] 成功无缝装载外部字体: {}", fontFileName);
            return true;
        } catch (Exception e) {
            LOGGER.error("[Kwrg] 加载外部字体失败: " + fontFileName, e);
            return false;
        }
    }

    private static void closeCurrentProvider() {
        if (currentActiveProvider != null) {
            try {
                currentActiveProvider.close();
            } catch (Exception ignored) {}
            currentActiveProvider = null;
        }
    }

    public static String calculateFileHash(Path file) {
        if (!Files.exists(file) || Files.isDirectory(file)) return "";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String findFontByHash(String targetHash) {
        if (targetHash == null || targetHash.isBlank()) return null;
        Path fontDir = ensureFontDir();
        if (!Files.exists(fontDir)) return null;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(fontDir, "*.{ttf,otf,TTF,OTF}")) {
            for (Path p : stream) {
                String hash = calculateFileHash(p);
                if (targetHash.equalsIgnoreCase(hash)) {
                    return p.getFileName().toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
