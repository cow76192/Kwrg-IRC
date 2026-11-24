package me.cookie.kwrg.mixin.accessor;

import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DrawableGizmoPrimitives.class)
public interface DrawableGizmoPrimitivesAccessor {
    @Accessor("isEmpty") boolean kwrg$isEmpty();
    @Accessor("opaque") DrawableGizmoPrimitives.Group kwrg$opaque();
    @Accessor("translucent") DrawableGizmoPrimitives.Group kwrg$translucent();
}
