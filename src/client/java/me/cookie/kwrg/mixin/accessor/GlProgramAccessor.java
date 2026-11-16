package me.cookie.kwrg.mixin.accessor;

import com.mojang.blaze3d.opengl.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlProgram.class)
public interface GlProgramAccessor {
    @Invoker("<init>")
    static GlProgram kwrg$create(int id, String label) {
        throw new AssertionError();
    }
}
