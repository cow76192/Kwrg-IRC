package me.cookie.kwrg.mixin.accessor;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$ShaderSourceKey")
public interface ShaderSourceKeyAccessor {
    @Accessor("id")
    Identifier kwrg$getId();

    @Accessor("type")
    ShaderType kwrg$getType();
}
