package me.cookie.kwrg.window.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector2d;
import org.joml.Vector4f;
import org.joml.Matrix4fc;
import org.joml.Matrix4f;

/** The same segmented surface is used for drawing and picking. */
public record WindowSurface(Vec3 center, Quaternionf rotation, double width, double height, boolean curved) {
    public int segments() { return curved ? 32 : 1; }

    private Vec3 localPoint(double u, double v) {
        double angle = (u - 0.5) * 1.2;
        double radius = width / 1.2;
        return new Vec3(curved ? radius * Math.sin(angle) : (u - 0.5) * width,
                (0.5 - v) * height, curved ? radius * (1 - Math.cos(angle)) : 0);
    }

    public Vec3 point(double u, double v) {
        Vec3 p = localPoint(u, v);
        Vector3f world = rotation.transform(new Vector3f((float) p.x, (float) p.y, (float) p.z));
        return center.add(world.x, world.y, world.z);
    }

    public record Hit(double u, double v, double distance) { }

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

    public Hit hit(Vec3 origin, Vec3 direction, double maxDistance) {
        Quaternionf inverse = new Quaternionf(rotation).conjugate();
        Vec3 relative = origin.subtract(center);
        Vector3f o = inverse.transform(new Vector3f((float) relative.x, (float) relative.y, (float) relative.z));
        Vector3f d = inverse.transform(new Vector3f((float) direction.x, (float) direction.y, (float) direction.z));

        Vec3 start = new Vec3(o.x, o.y, o.z), ray = new Vec3(d.x, d.y, d.z);
        Hit nearest = null;
        for (int i = 0; i < segments(); i++) {
            Vec3 a = localPoint((double) i / segments(), 0.5);
            Vec3 edge = localPoint((double) (i + 1) / segments(), 0.5).subtract(a);
            Vec3 normal = new Vec3(-edge.z, 0, edge.x);
            double denominator = ray.dot(normal);
            if (denominator >= -1e-7) continue;

            double distance = a.subtract(start).dot(normal) / denominator;
            if (distance < 0 || distance > maxDistance || nearest != null && distance >= nearest.distance) continue;

            Vec3 p = start.add(ray.scale(distance));
            double along = p.subtract(a).dot(edge) / edge.lengthSqr();
            double v = 0.5 - p.y / height;
            if (along >= -1e-6 && along <= 1.000001 && v >= 0 && v <= 1) {
                nearest = new Hit(Math.clamp((i + along) / segments(), 0, 1), v, distance);
            }
        }
        return nearest;
    }
}
