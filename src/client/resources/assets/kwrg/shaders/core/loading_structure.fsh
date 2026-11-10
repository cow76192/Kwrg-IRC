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
    float fade = Opacity * (Menu.x > 0.5 ? 1.0 : 1.0 - smoothstep(0.01, 0.46, Burst));
    if (Switch.y >= 0.0) fade *= smoothstep(0.01, 0.55, Switch.y);
    else if (Warp.z > 0.0) fade *= 1.0 - smoothstep(0.0, 0.70, Burst);
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
    vec3 terrain = terrainColor(cell, normal, seed) * terrainLight(normal) * grassSurfaceShade(cell, normal);
    terrain = portalGrass(terrain, localPosition, normal, seed, smoothstep(0.16, 0.75, ServerFlight.x));
    vec3 color = terrain * (1.0 - 0.56 * seam) + vec3(0.10, 0.21, 0.26) * (0.22 * bevel + 0.28 * rim);

    float distanceToCore = dot(localPosition, localPosition);
    float core = exp2(-0.77 * distanceToCore) * (0.85 + 0.15 * sin(Time * 4.7));
    float open = 1.0 - smoothstep(0.61, BUILD_END, Progress);
    float charge = chargeStrength();
    vec3 heat = mix(vec3(0.21, 0.87, 1.32), vec3(1.58, 0.69, 0.25), core);
    color += heat * core * (open * 0.84 + charge * 1.55) * (seam + 0.16 * bevel);
    color += charge * vec3(0.29, 0.34, 0.22) * (0.25 + 0.55 * rim);
    if (Menu.x > 0.5) {
        float scan = pow(0.5 + 0.5 * sin(localPosition.y * 7.5 - Time * 2.8), 7.0);
        vec3 circuit = mix(vec3(0.19, 0.73, 1.0), vec3(0.67, 0.26, 0.96), seed.z);
        color = mix(color, color * 0.29 + circuit * (0.22 + 0.64 * seam + 0.25 * scan), Theme.y);
        color = mix(color, color * 0.46 + vec3(0.86, 0.29, 0.09) * (0.17 + 0.63 * bevel + 0.29 * scan), Theme.z);
        color += Theme.x * menuPalette(seed.z) * (0.08 + rim * 0.18);
        color *= 1.0 - 0.60 * Theme.w;
    }
    if (face == 2 && Browser.x > 0.0) {
        float circuit = smoothstep(0.0, 0.60, Browser.x) * Browser.y * (1.0 - smoothstep(0.04, 0.76, ServerFlight.x));
        color = mix(color, vec3(0.044, 0.035, 0.11) + seam * vec3(0.07, 0.55, 0.80), circuit);
        float hardcore = step(2.5, Browser.z) * Browser.w * Browser.x;
        color = mix(color, vec3(0.14, 0.035, 0.06) + seam * vec3(0.48, 0.04, 0.08), hardcore);
    }
    fragColor = vec4(expose(color), Opacity * (1.0 - 0.18 * seam * open));
#endif
}
