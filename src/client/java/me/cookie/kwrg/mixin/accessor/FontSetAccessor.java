package me.cookie.kwrg.mixin.accessor;

import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Set;

@Mixin(FontSet.class)
public interface FontSetAccessor {
    @Accessor("allProviders")
    List<GlyphProvider.Conditional> kwrg$getAllProviders();

    @Accessor("allProviders")
    void kwrg$setAllProviders(List<GlyphProvider.Conditional> providers);

    @Accessor("activeProviders")
    void kwrg$setActiveProviders(List<GlyphProvider> providers);

    @Invoker("resetTextures")
    void kwrg$resetTextures();

    @Invoker("selectProviders")
    List<GlyphProvider> kwrg$selectProviders(List<GlyphProvider.Conditional> providers, Set<FontOption> options);
}
