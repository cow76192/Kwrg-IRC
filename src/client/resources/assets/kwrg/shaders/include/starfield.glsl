float starHash(vec2 cell) {
    vec3 p = fract(vec3(cell.xyx) * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

vec3 starLayer(vec2 p, float scale, float time, float viewportHeight) {
    vec2 cell = floor(p * scale);
    float seed = starHash(cell);
    if (seed < 0.73) {
        return vec3(0.0);
    }
    vec2 offset = vec2(starHash(cell + 17.0), starHash(cell + 49.0));
    float distanceToStar = length(fract(p * scale) - mix(vec2(0.2), vec2(0.8), offset));
    float radius = mix(0.018, 0.063, seed * seed);
    float pixel = scale * 2.0 / viewportHeight;
    float star = 1.0 - smoothstep(radius, radius + pixel, distanceToStar);
    float halo = exp(-distanceToStar * 28.0) * seed * seed;
    float twinkle = 0.72 + 0.28 * sin(time * (0.7 + seed * 1.8) + seed * 56.0);
    vec3 tint = mix(vec3(0.36, 0.48, 0.82), vec3(0.85, 0.96, 1.0), offset.x);
    return tint * (star + halo) * twinkle * smoothstep(0.73, 1.0, seed);
}
