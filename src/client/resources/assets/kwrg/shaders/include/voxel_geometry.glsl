const float TAU = 6.2831853;
const vec2 FACE_CORNERS[6] = vec2[6](vec2(0, 0), vec2(1, 0), vec2(1, 1),
        vec2(0, 0), vec2(1, 1), vec2(0, 1));
const vec3 NORMALS[6] = vec3[6](vec3(1, 0, 0), vec3(-1, 0, 0), vec3(0, 1, 0),
        vec3(0, -1, 0), vec3(0, 0, 1), vec3(0, 0, -1));
const vec3 TANGENTS[6] = vec3[6](vec3(0, 0, -1), vec3(0, 0, 1), vec3(1, 0, 0),
        vec3(1, 0, 0), vec3(1, 0, 0), vec3(-1, 0, 0));

vec3 randomSeed(float index) {
    vec3 p = fract(vec3(index) * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}
