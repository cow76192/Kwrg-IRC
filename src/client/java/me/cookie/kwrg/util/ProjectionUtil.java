package me.cookie.kwrg.util;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class ProjectionUtil {
    private ProjectionUtil() { }

    public record Ray(Vec3 origin, Vec3 direction) { }

    public static Ray screenRay(Vec3 camera, Matrix4fc viewProjection, double u, double v) {
        if (!Double.isFinite(u) || !Double.isFinite(v) || u < 0 || u > 1 || v < 0 || v > 1) return null;

        Matrix4f inverse = new Matrix4f(viewProjection).invert();
        Vector3f near = inverse.transformProject(new Vector3f((float)(u * 2 - 1), (float)(1 - v * 2), 0));
        Vector3f far = inverse.transformProject(new Vector3f((float)(u * 2 - 1), (float)(1 - v * 2), .5f));
        if (!near.isFinite() || !far.isFinite()) return null;

        Vec3 direction = new Vec3(far).subtract(new Vec3(near));
        if (direction.lengthSqr() < 1e-12) return null;
        return new Ray(camera.add(new Vec3(near)), direction.normalize());
    }

    public static Vector2d screenPoint(Vec3 point, Vec3 camera, Matrix4fc viewProjection) {
        Vec3 relative=point.subtract(camera);
        Vector4f clip=viewProjection.transform(new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1));
        if(clip.w<=0)return null;
        return new Vector2d((clip.x/clip.w+1)*.5,(1-clip.y/clip.w)*.5);
    }

}
