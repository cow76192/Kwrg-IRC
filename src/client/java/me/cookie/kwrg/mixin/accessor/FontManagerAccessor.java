package me.cookie.kwrg.mixin.accessor;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(FontManager.class)
public interface FontManagerAccessor {
    @Accessor("fontSets")
    Map<Identifier, FontSet> kwrg$getFontSets();

    @Accessor("textureManager")
    TextureManager kwrg$getTextureManager();

    @Invoker("createFontSet")
    FontSet kwrg$createFontSet(Identifier id, List<GlyphProvider.Conditional> providers, Set<FontOption> options);
}