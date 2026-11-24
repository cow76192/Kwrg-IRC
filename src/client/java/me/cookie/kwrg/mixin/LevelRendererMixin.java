package me.cookie.kwrg.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cookie.kwrg.render.WorldOverlayRender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private RenderBuffers renderBuffers;

    // Iris composites before the model-view pop. The camera state is still live here.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/level/LevelRenderState;reset()V"))
    private void kwrg$renderAfterIrisComposite(GraphicsResourceAllocator allocator, DeltaTracker delta,
                                               boolean renderOutline, CameraRenderState camera, Matrix4fc modelView,
                                               GpuBufferSlice terrainFog, Vector4f fogColor, boolean renderSky,
                                               ChunkSectionsToRender chunks, CallbackInfo ci) {
        if (!camera.initialized || !WorldOverlayRender.isShaderPackInUse()) return;

        var matrices = RenderSystem.getModelViewStack();
        var previousFog = RenderSystem.getShaderFog();
        matrices.pushMatrix().mul(modelView);
        try {
            RenderSystem.setShaderFog(terrainFog);
            WorldOverlayRender.renderAfterComposite(
                    new WorldOverlayRender.Context(camera, new PoseStack(), renderBuffers.bufferSource()));
        } finally {
            RenderSystem.setShaderFog(previousFog);
            matrices.popMatrix();
        }
    }
}
