package me.cookie.kwrg.render;

import me.cookie.kwrg.mixin.accessor.DrawableGizmoPrimitivesAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives.Group;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.AbstractList;
import java.util.List;

public final class OverlayGizmos {
    private OverlayGizmos() {}

    public static void submit(LevelRenderContext context, DrawableGizmoPrimitives mesh, RenderType type, float opacity) {
        var access = (DrawableGizmoPrimitivesAccessor) mesh;
        if (access.kwrg$isEmpty()) return;

        for (Group group : List.of(access.kwrg$opaque(), access.kwrg$translucent())) {
            if (group.lines().isEmpty() && group.quads().isEmpty()) continue;
            var overlay = new Group(group.opaque(), new Batch<>(group.lines(), type, opacity),
                    new Batch<>(group.quads(), type, opacity), group.triangleFans(), group.texts(), group.points());
            context.submitNodeCollector().submitGizmoPrimitives(overlay, context.levelState().cameraRenderState, false);
        }
    }

    // Carry the render type through vanilla's deferred submission without copying the mesh.
    public static final class Batch<T> extends AbstractList<T> {
        private final List<T> primitives;
        public final RenderType type;
        public final float opacity;

        Batch(List<T> primitives, RenderType type, float opacity) {
            this.primitives = primitives;
            this.type = type;
            this.opacity = opacity;
        }

        @Override public T get(int index) { return primitives.get(index); }
        @Override public int size() { return primitives.size(); }
    }
}
