const vec3 PORTAL_STAGE_EYE = vec3(1.48, 1.88, 4.32);
const vec3 PORTAL_STAGE_FOCUS = vec3(0.0, 0.63, 0.0);

vec3 portalGrass(vec3 grass, vec3 position, vec3 normal, vec3 seed, float reveal) {
    float stage = step(0.5, normal.y) * (1.0 - step(0.72, abs(position.x)))
            * (1.0 - step(0.48, abs(position.z)));
    vec3 path = mix(vec3(0.29, 0.21, 0.10), vec3(0.52, 0.40, 0.20), seed.y);
    return mix(grass, path, stage * reveal);
}
