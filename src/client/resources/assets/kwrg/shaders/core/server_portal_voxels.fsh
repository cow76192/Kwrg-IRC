#version 330 core

#define GRID_SIZE 16
#moj_import <kwrg:server_portal.glsl>
#moj_import <kwrg:voxel_geometry.glsl>
#moj_import <kwrg:grass_palette.glsl>
#moj_import <kwrg:portal_stage.glsl>

in vec2 faceUv;
in vec3 localPosition;
flat in vec4 tint;
flat in int material;
flat in int faceIndex;
flat in float facet;
flat in uvec4 contactFootprints;
out vec4 fragColor;

#ifdef PORTAL_CLOUDS
void main() {
    fragColor = vec4(tint.rgb * mix(facet, 1.0, 0.44), tint.a);
}
#else
uniform sampler2D ObsidianSampler;
uniform sampler2D CryingObsidianSampler;

float contactShadow(ivec2 cell) {
    vec2 ground = (vec2(cell) + 0.5) * 0.16 - 1.28;
    float shadow = 0.0;
    for (int index = 0; index < ANCHOR_BLOCKS; index++) {
        uint footprint = contactFootprints[index];
        float contact = float(footprint >> 24u) / 255.0;
        if (contact <= 0.0) continue;
        vec2 center = vec2(footprint & 255u, (footprint >> 8u) & 255u) * (2.56 / 255.0) - 1.28;
        float radius = float((footprint >> 16u) & 255u) * (CONTACT_MAX_RADIUS / 255.0);
        vec2 delta = abs(ground - center);
        float coverage = 1.0 - smoothstep(radius, radius + 0.075, max(delta.x, delta.y));
        shadow = max(shadow, contact * coverage);
    }
    return 1.0 - 0.40 * shadow;
}

vec3 groundLight(ivec3 cell, vec3 normal) {
    vec3 ground = (vec3(cell) + 0.5) * 0.16 - vec3(1.28, 2.56, 1.28);
    vec3 offset = vec3(0.0, 0.31, 0.13) - ground;
    float distanceSquared = dot(offset, offset);
    float incidence = max(0.0, dot(normal, normalize(offset)));
    float falloff = incidence / (1.0 + 2.7 * distanceSquared);
    float pulse = 0.74 + 0.26 * sin(Viewport.w * 6.4);
    float frontGrass = float(cell.y == 15 && (cell.x == 7 || cell.x == 8) && cell.z == 11);
    float light = portalOpen() * (falloff * (0.72 + 0.39 * pulse) + frontGrass * (0.12 + 0.22 * pulse));
    vec3 pressure = vec3(0.73, 0.22, 1.0) * (pressureGlow() * 0.37 + ruptureFlash() * 0.64);
    return vec3(1.00, 0.17, 0.64) * light + (vec3(1.0, 0.58, 0.23) * ignitionFlash() * 0.58 + pressure) * falloff;
}

void main() {
    if (tint.a < 0.005) discard;
    vec2 edge = min(faceUv, 1.0 - faceUv);
    float rim = 1.0 - smoothstep(0.02, 0.13, min(edge.x, edge.y));
    vec3 color = tint.rgb * (facet + 0.22 * rim);
    if (material == 0) {
        vec3 normal = NORMALS[faceIndex];
        vec3 grid = (localPosition + 1.28 - normal * 0.001) / 0.16;
        ivec3 cell = ivec3(clamp(floor(grid), 0.0, 15.0));
        vec3 seed = cellSeed(cell);
        float returnTime = max(Journey.x, Journey.y);
        float path = 1.0 - smoothstep(0.43 + seed.x * 0.19, 0.51 + seed.x * 0.19, returnTime);
        vec3 terrain = terrainColor(cell, normal, seed) * terrainLight(normal) * grassSurfaceShade(cell, normal);
        color = portalGrass(terrain, localPosition, normal, seed, path);
        vec2 pixels = fract(faceUv * 16.0);
        float seam = 1.0 - smoothstep(0.022, 0.065, min(min(pixels.x, 1.0 - pixels.x), min(pixels.y, 1.0 - pixels.y)));
        color *= 1.0 - 0.40 * seam;
        color += groundLight(cell, normal);
        color /= 1.0 + 0.47 * color;
        if (State.w >= 0.0 && faceIndex == 2) color *= contactShadow(cell.xz);
    } else if (material == 1 || material == 5) {
        vec3 texel = material == 5 ? texture(CryingObsidianSampler, faceUv).rgb : texture(ObsidianSampler, faceUv).rgb;
        color = texel * (0.72 + 0.52 * facet);
        color += ignitionFlash() * texel * vec3(0.69, 0.41, 0.25);
        if (material == 5) {
            float crack = smoothstep(0.065, 0.52, texel.b - texel.g);
            color *= 1.0 + 0.50 * pressureGlow();
            color += vec3(0.78, 0.16, 1.0) * crack * pressureGlow() * 1.50;
        }
    }
    fragColor = vec4(color, tint.a);
}
#endif
