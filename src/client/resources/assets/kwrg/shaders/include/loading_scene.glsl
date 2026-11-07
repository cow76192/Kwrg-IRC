layout(std140) uniform LoadingFrame {
    float Time;
    float Progress;
    float Opacity;
    float Burst;
    vec4 Viewport; // Framebuffer size, followed by normalized pointer coordinates.
    float Charge;
    float PointerMotion;
    float LayerPhase;
    float Padding;
    ivec4 Groups; // Finished rows, active layer, inflow, and hologram lines.
};

const float TAU = 6.2831853;
const float GRID_STEP = 0.16;
const float HALF_EDGE = float(GRID_SIZE) * GRID_STEP * 0.5;
const float EYE_DISTANCE = 6.5;
const vec2 FACE_CORNERS[6] = vec2[6](vec2(0, 0), vec2(1, 0), vec2(1, 1),
        vec2(0, 0), vec2(1, 1), vec2(0, 1));
const vec3 NORMALS[6] = vec3[6](vec3(1, 0, 0), vec3(-1, 0, 0), vec3(0, 1, 0),
        vec3(0, -1, 0), vec3(0, 0, 1), vec3(0, 0, -1));
const vec3 TANGENTS[6] = vec3[6](vec3(0, 0, -1), vec3(0, 0, 1), vec3(1, 0, 0),
        vec3(1, 0, 0), vec3(1, 0, 0), vec3(-1, 0, 0));

struct Camera {
    vec3 back;
    vec3 right;
    vec3 up;
    vec2 shift;
    vec2 focal;
};

float releasePulse() {
    return smoothstep(0.0, 0.028, Burst) * exp2(-9.0 * Burst);
}

vec2 screenShift() {
    vec2 shake = vec2(sin(Time * 113.0), sin(Time * 151.0 + 0.6));
    return vec2(0.0, 0.11) - Viewport.zw * vec2(0.033, 0.025) + shake * (0.016 * releasePulse());
}

float chargeStrength() {
    return smoothstep(0.0, 1.0, Charge) * (1.0 - smoothstep(0.0, 0.17, Burst));
}

float blockScale() {
    return 1.0 - 0.15 * chargeStrength();
}

Camera sceneCamera() {
    float orbit = 0.64 + Time * 0.22 + Viewport.z * 0.15;
    vec3 back = normalize(vec3(sin(orbit), 0.43 + 0.045 * sin(Time * 0.37) + Viewport.w * 0.12, cos(orbit)));
    vec3 right = normalize(vec3(back.z, 0.0, -back.x));
    float aspect = Viewport.x / Viewport.y;
    float lens = 2.13 * min(1.0, aspect / 0.83) * (1.0 + 0.11 * chargeStrength() - 0.24 * releasePulse());
    return Camera(back, right, cross(back, right), screenShift(), vec2(lens / aspect, lens));
}

vec4 project(Camera camera, vec3 position) {
    float depth = EYE_DISTANCE - dot(position, camera.back);
    vec2 view = vec2(dot(position, camera.right), dot(position, camera.up));
    return vec4(view * camera.focal + camera.shift * depth, depth - 0.20, depth);
}

vec3 randomSeed(float index) {
    vec3 p = fract(vec3(index) * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

vec3 cellSeed(ivec3 cell) {
    return randomSeed(float(cell.x + GRID_SIZE * (cell.z + GRID_SIZE * cell.y)) + 1.0);
}

vec3 terrainColor(ivec3 cell, vec3 normal, vec3 seed) {
    float fringe = float(GRID_SIZE - 1 - cell.y);
    vec3 grass = mix(vec3(0.15, 0.39, 0.12), vec3(0.48, 0.72, 0.23), seed.x);
    vec3 soil = mix(vec3(0.18, 0.102, 0.088), vec3(0.48, 0.295, 0.18), seed.y);
    float top = step(0.5, normal.y) * step(fringe, 0.0);
    float edge = (1.0 - step(0.5, abs(normal.y))) * step(fringe, floor(seed.z * 3.2));
    return mix(soil, grass, top + edge * 0.83);
}

vec3 terrainLight(vec3 normal) {
    float key = max(0.0, dot(normal, vec3(-0.43, 0.79, 0.44)));
    float fill = max(0.0, dot(normal, vec3(0.70, 0.48, -0.52)));
    return vec3(0.50, 0.53, 0.61) + key * vec3(0.64, 0.57, 0.43) + fill * vec3(0.12, 0.21, 0.31);
}

vec3 starColor(float seed) {
    return mix(vec3(0.26, 1.16, 1.42), vec3(1.72, 0.81, 0.28), smoothstep(0.31, 0.82, seed));
}

float fresnel(vec3 normal, vec3 toEye) {
    float grazing = 1.0 - clamp(dot(normal, normalize(toEye)), 0.0, 1.0);
    float square = grazing * grazing;
    return square * square;
}

vec3 expose(vec3 color) {
    return color / (1.0 + 0.47 * color);
}
