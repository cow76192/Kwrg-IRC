#version 330 core

#moj_import <kwrg:loading_scene.glsl>

out vec2 faceUv;
out vec3 toEye;
flat out vec3 faceNormal;
flat out vec4 voxelColor;
flat out float emission;

// Curl of a smooth 3D trigonometric vector potential. Its divergence is zero.
vec3 curlField(vec3 p) {
    return sin(p.yzx) - cos(p.zxy);
}

vec3 flowPosition(vec3 seed, float cycle) {
    float lane = seed.x * 4.0;
    float angle = floor(lane) * (TAU / 4.0) + (fract(lane) - 0.5) * 1.9
            + cycle * (5.1 + seed.z * 1.2) + Time * 0.54;
    float tilt = (seed.y * 2.0 - 1.0) * 1.33 + 0.20 * sin(angle * 1.3);
    float horizontal = cos(tilt);
    vec3 direction = vec3(cos(angle) * horizontal, sin(tilt), sin(angle) * horizontal);
    vec3 axes = abs(direction);
    float fieldRadius = (8.0 + seed.z * 8.0) / max(axes.x, max(axes.y, axes.z));
    float inward = 1.0 - cycle;
    float radius = mix(0.24 + 0.45 * seed.y, fieldRadius, inward * inward * inward);
    vec3 position = direction * radius;
    vec3 field = position * 0.84 + vec3(seed.y * 2.0, Time * 0.72, -Time * 0.38);
    return position + curlField(field) * (0.13 + 0.72 * smoothstep(0.32, 4.0, radius));
}

vec3 repel(vec3 position, Camera camera) {
    if (PointerMotion < 0.005) {
        return position;
    }
    vec2 pointer = (Viewport.zw - camera.shift) / camera.focal;
    vec3 ray = normalize(camera.right * pointer.x + camera.up * pointer.y - camera.back);
    vec3 offset = position - camera.back * EYE_DISTANCE;
    vec3 away = offset - ray * max(0.0, dot(ray, offset));
    float distance = length(away);
    return position + away / max(distance, 0.07) * (1.0 - smoothstep(0.06, 0.56, distance))
            * (0.46 * PointerMotion);
}

float easeOutBack(float t) {
    float u = t - 1.0;
    return 1.0 + u * u * (2.70 * u + 1.70);
}

mat3 tumbling(vec3 seed, float strength) {
    // Bounded angles keep the final alignment continuous when progress stalls mid-turn.
    vec2 angle = sin(Time * (2.4 + seed.xy * 4.7) + seed.yz * TAU) * (3.45 * strength);
    vec2 c = cos(angle);
    vec2 s = sin(angle);
    return mat3(c.y, s.y, 0.0, -s.y * c.x, c.y * c.x, s.x, s.y * s.x, -c.y * s.x, c.x);
}

const int LAYER_CELLS = GRID_SIZE * GRID_SIZE;
const int PERIMETER = 4 * GRID_SIZE - 4;
const int SHELL_CELLS = 2 * LAYER_CELLS + (GRID_SIZE - 2) * PERIMETER;

ivec3 shellCell(int index) {
    if (index < 2 * LAYER_CELLS) {
        return ivec3(index % GRID_SIZE, index < LAYER_CELLS ? 0 : GRID_SIZE - 1,
                (index / GRID_SIZE) % GRID_SIZE);
    }
    int side = index - 2 * LAYER_CELLS;
    int edge = side % PERIMETER;
    int row = 1 + side / PERIMETER;
    if (edge < 2 * GRID_SIZE) {
        return ivec3(edge % GRID_SIZE, row, edge < GRID_SIZE ? 0 : GRID_SIZE - 1);
    }
    int inner = edge - 2 * GRID_SIZE;
    return ivec3(inner < GRID_SIZE - 2 ? 0 : GRID_SIZE - 1, row, 1 + inner % (GRID_SIZE - 2));
}

void main() {
    int id = gl_InstanceID;
    int face = gl_VertexID / 6;
    vec3 normal = NORMALS[face];
    Camera camera = sceneCamera();
    vec3 seed = randomSeed(float(id) + 4127.0);
    vec3 center;
    vec3 color;
    float spin;
    float opacity;
    float size;

    if (Burst > 0.0) {
        bool shell = id < SHELL_CELLS;
        if (shell) {
            ivec3 cell = shellCell(id);
            seed = cellSeed(cell);
            center = (vec3(cell) + 0.5) * GRID_STEP - HALF_EDGE;
            color = terrainColor(cell, normal, seed) * terrainLight(normal);
        } else {
            center = (seed * 2.0 - 1.0) * HALF_EDGE * 0.84;
            color = starColor(seed.y);
        }
        float heat = 1.0 - smoothstep(0.15, 1.84, length(center));
        vec3 burstColor = mix(vec3(0.16, 0.64, 0.82), starColor(seed.z), heat);
        color = mix(color, burstColor, smoothstep(0.0, 0.16, Burst) * 0.88);
        float travel = Burst * (5.3 + 2.6 * Burst);
        vec3 scatter = normalize(center + (seed - 0.49) * 0.46);
        center = center * blockScale() + travel * (scatter * (0.48 + seed.y) + camera.back * (0.94 + seed.x * 1.61));
        size = mix(shell ? GRID_STEP * 0.484 * blockScale() : 0.042,
                0.018 + seed.z * 0.048, smoothstep(0.0, 0.31, Burst));
        opacity = (1.0 - smoothstep(0.45, 0.98, Burst)) * (shell ? 1.0 : smoothstep(0.01, 0.10, Burst));
        spin = smoothstep(0.0, 0.20, Burst);
        emission = 0.18 + heat * 0.45 + releasePulse() * 0.72 + chargeStrength() * 0.52;
    } else if (id < Groups.y) {
        ivec3 cell = ivec3(id % GRID_SIZE, Groups.x, id / GRID_SIZE);
        seed = cellSeed(cell);
        float phase = clamp((LayerPhase - seed.y * 0.23) / 0.77, 0.0, 1.0);
        if (phase <= 0.0) {
            gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
            return;
        }
        vec3 slot = (vec3(cell) + 0.5) * GRID_STEP - HALF_EDGE;
        float align = smoothstep(0.77, 0.99, phase);
        center = slot;
        if (phase < 0.99) {
            float cycle = fract(seed.z + Time * (0.18 + seed.y * 0.14));
            vec3 start = repel(flowPosition(seed, cycle) * 0.53, camera);
            vec3 offset = start - slot;
            float recover = easeOutBack(clamp((phase - 0.69) / 0.31, 0.0, 1.0));
            vec3 spring = offset * ((1.0 - recover) * min(1.0, 0.28 / max(length(offset), 0.001)));
            center += mix(offset * (1.0 - smoothstep(0.04, 0.80, phase)), spring, smoothstep(0.63, 0.79, phase));
        }
        float heat = 1.0 - smoothstep(0.24, 2.85, length(center));
        vec3 flightColor = mix(vec3(0.19, 0.31, 0.61), starColor(seed.z), heat);
        color = mix(flightColor, terrainColor(cell, normal, seed) * terrainLight(normal), align);
        size = mix(0.015 + seed.x * 0.020, GRID_STEP * 0.484, smoothstep(0.25, 0.93, phase));
        opacity = smoothstep(0.0, 0.13, phase);
        float impact = smoothstep(0.76, 0.86, phase) * (1.0 - smoothstep(0.86, 0.99, phase));
        emission = (1.0 - align) * heat * 0.54 + impact * 0.88;
        spin = 1.0 - align;
    } else if (id < Groups.y + Groups.z) {
        int flowId = id - Groups.y;
        seed = randomSeed(float(flowId) + 4127.0);
        float cycle = fract(seed.z + Time * (0.18 + seed.y * 0.19));
        center = repel(flowPosition(seed, cycle), camera);
        float heat = 1.0 - smoothstep(0.20, 2.75, length(center));
        color = mix(vec3(0.16, 0.27, 0.56), starColor(seed.y), heat);
        emission = heat * 0.79 + 0.12;
        float density = flowId < ASSEMBLY_INFLOW ? 1.0 : 1.0 - smoothstep(0.22, BUILD_START, Progress);
        opacity = smoothstep(0.0, 0.09, cycle) * (1.0 - smoothstep(0.89, 1.0, cycle))
                * (1.0 - smoothstep(0.62, BUILD_END, Progress)) * density * 0.87;
        size = 0.014 + seed.x * seed.x * 0.021;
        spin = 1.0;
    } else {
        seed = randomSeed(float(id - Groups.y - Groups.z) + 5309.0);
        float age = fract(seed.x + Time * (0.24 + seed.z * 0.35));
        vec2 drift = vec2(sin(Time * 1.3 + seed.y * TAU), cos(Time * 1.6 + seed.z * TAU));
        vec2 footprint = (seed.yz * 2.0 - 1.0) * HALF_EDGE + age * drift * 0.24;
        center = vec3(footprint.x, HALF_EDGE + age * (0.42 + seed.y), footprint.y) * blockScale();
        color = mix(vec3(0.30, 0.84, 0.67), vec3(0.65, 0.91, 0.44), seed.z);
        opacity = smoothstep(0.79, 0.85, Progress) * smoothstep(0.0, 0.14, age)
                * (1.0 - smoothstep(0.34, 1.0, age));
        size = 0.012 + seed.x * 0.013;
        emission = 0.44 + 0.56 * chargeStrength();
        spin = 0.38;
    }

    float depth = EYE_DISTANCE - dot(center, camera.back);
    opacity *= smoothstep(0.27, 0.77, depth) * Opacity;
    if (opacity < 0.004) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }

    faceUv = FACE_CORNERS[gl_VertexID % 6];
    vec2 corner = faceUv * 2.0 - 1.0;
    vec3 tangent = TANGENTS[face];
    vec3 vertex = normal + tangent * corner.x + cross(normal, tangent) * corner.y;
    if (spin > 0.0) {
        mat3 rotation = tumbling(seed, spin);
        normal = rotation * normal;
        vertex = rotation * vertex;
    }
    vec3 position = center + vertex * size;
    gl_Position = project(camera, position);
    toEye = camera.back * EYE_DISTANCE - position;
    faceNormal = normal;
    voxelColor = vec4(color, opacity);
}
