package me.cookie.kwrg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cookie.kwrg.render.utils.ParallelShaderLinks;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class GlDeviceMixin {
    @WrapOperation(method = "compileProgram", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/opengl/GlProgram;link(Lcom/mojang/blaze3d/opengl/GlShaderModule;Lcom/mojang/blaze3d/opengl/GlShaderModule;Lcom/mojang/blaze3d/vertex/VertexFormat;Ljava/lang/String;)Lcom/mojang/blaze3d/opengl/GlProgram;"))
    private GlProgram kwrg$linkStagedProgram(GlShaderModule vertex, GlShaderModule fragment, VertexFormat format,
                                             String label, Operation<GlProgram> original) throws ShaderManager.CompilationException {
        var staged = ParallelShaderLinks.current();
        return staged == null ? original.call(vertex, fragment, format, label) : staged.link(vertex, fragment, format, label);
    }
}
