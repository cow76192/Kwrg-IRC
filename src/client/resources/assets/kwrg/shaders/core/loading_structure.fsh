#version 330 core

#moj_import <kwrg:loading_scene.glsl>

in vec2 faceUv;
#ifdef HOLOGRAM
in float depth;
flat in int kind;
flat in vec4 tint;
#else
in vec3 localPosition;
in vec3 toEye;
flat in int face;
#endif
out vec4 fragColor;

void main() {
#ifdef HOLOGRAM
    float fade = Opacity * (1.0 - smoothstep(0.01, 0.46, Burst));
    if (kind == 1) {
        float defocus = max(1.0 - smoothstep(0.9, 4.2, depth), smoothstep(6.8, 19.0, depth));
        float radius = length(faceUv - 0.5) * 2.0;
        float feather = 1.0 - smoothstep(mix(0.60, 0.10, defocus), 1.0, radius);
        fragColor = vec4(tint.rgb, feather * feather * tint.a * fade * (1.0 - 0.40 * defocus));
        return;
    }

    float line = 1.0 - smoothstep(0.24, 1.0, abs(faceUv.y * 2.0 - 1.0));
    fragColor = vec4(tint.rgb, tint.a * line * fade);
#else
    vec3 normal = NORMALS[face];
    vec3 tangent = TANGENTS[face];
    vec3 grid = (localPosition + HALF_EDGE - normal * 0.001) / GRID_STEP;
    ivec3 cell = ivec3(clamp(floor(grid), 0.0, float(GRID_SIZE - 1)));
    vec3 seed = cellSeed(cell);
    vec2 tile = vec2(dot(grid, abs(tangent)), dot(grid, abs(cross(normal, tangent))));
    vec2 cellUv = fract(tile);
    vec2 edges = min(cellUv, 1.0 - cellUv);
    float edge = min(edges.x, edges.y);
    float aa = clamp(max(fwidth(tile.x), fwidth(tile.y)), 0.007, 0.10);
    float seam = 1.0 - smoothstep(0.014, 0.014 + aa * 0.68, edge);
    float bevel = clamp((1.0 - smoothstep(0.024, 0.13, edge)) * (1.0 - seam), 0.0, 1.0);
    float rim = fresnel(normal, toEye);
    vec3 terrain = terrainColor(cell, normal, seed) * terrainLight(normal);
    vec3 color = terrain * (1.0 - 0.56 * seam) + vec3(0.10, 0.21, 0.26) * (0.22 * bevel + 0.28 * rim);

    float distanceToCore = dot(localPosition, localPosition);
    float core = exp2(-0.77 * distanceToCore) * (0.85 + 0.15 * sin(Time * 4.7));
    float open = 1.0 - smoothstep(0.61, BUILD_END, Progress);
    float charge = chargeStrength();
    vec3 heat = mix(vec3(0.21, 0.87, 1.32), vec3(1.58, 0.69, 0.25), core);
    color += heat * core * (open * 0.84 + charge * 1.55) * (seam + 0.16 * bevel);
    color += charge * vec3(0.29, 0.34, 0.22) * (0.25 + 0.55 * rim);
    fragColor = vec4(expose(color), Opacity * (1.0 - 0.18 * seam * open));
#endif
}
