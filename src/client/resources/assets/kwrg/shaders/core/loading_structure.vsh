#version 330 core

#moj_import <kwrg:loading_scene.glsl>

out vec2 faceUv;
#ifdef HOLOGRAM
out float depth;
flat out int kind;
flat out vec4 tint;
#else
out vec3 localPosition;
out vec3 toEye;
flat out int face;
#endif

vec3 facePoint(int faceIndex, vec2 corner) {
    vec3 normal = NORMALS[faceIndex];
    vec3 tangent = TANGENTS[faceIndex];
    return normal + tangent * corner.x + cross(normal, tangent) * corner.y;
}

#ifdef HOLOGRAM
const int EDGE_COUNT = 12;
const int LINES_PER_AXIS = GRID_SIZE - 1;

vec4 projectLine(Camera camera, vec3 start, vec3 end, float width) {
    vec4 a = project(camera, start);
    vec4 b = project(camera, end);
    vec2 pixels = (b.xy / b.w - a.xy / a.w) * Viewport.xy;
    vec2 perpendicular = vec2(-pixels.y, pixels.x) / max(length(pixels), 0.001);
    vec4 clip = mix(a, b, faceUv.x);
    clip.xy += perpendicular / Viewport.xy * (faceUv.y * 2.0 - 1.0) * (width * clip.w);
    return clip;
}

vec4 linePosition(Camera camera, vec3 start, vec3 end) {
    float scale = HALF_EDGE * 1.075 * blockScale();
    return projectLine(camera, start * scale, end * scale, 1.75);
}

vec4 gridLine(Camera camera, int index) {
    vec3 start;
    vec3 end;
    float alpha = 0.08;
    if (index < EDGE_COUNT) {
        int axis = index / 4;
        start[axis] = -1.0;
        start[(axis + 1) % 3] = (index & 1) == 0 ? -1.0 : 1.0;
        start[(axis + 2) % 3] = (index & 2) == 0 ? -1.0 : 1.0;
        end = start;
        end[axis] = 1.0;
        if (dot(camera.back, (start + end) * 0.5) < -0.1) {
            alpha *= 0.44;
        }
    } else {
        int line = index - EDGE_COUNT;
        int faceIndex = line / (2 * LINES_PER_AXIS);
        faceIndex += faceIndex >= 3 ? 1 : 0; // Skip the underside.
        bool vertical = line % (2 * LINES_PER_AXIS) < LINES_PER_AXIS;
        float across = 2.0 * float(line % LINES_PER_AXIS + 1) / float(GRID_SIZE) - 1.0;
        float bottom = (float(Groups.x) + clamp(LayerPhase, 0.0, 1.0)) * 2.0 / float(GRID_SIZE) - 1.0;
        if (faceIndex == 2) {
            bottom = -1.0;
        }
        float facing = smoothstep(0.05, 0.25, dot(NORMALS[faceIndex], camera.back));
        if (facing <= 0.0 || (!vertical && across <= bottom)) {
            tint = vec4(0.0);
            return vec4(2.0, 2.0, 2.0, 1.0);
        }
        start = facePoint(faceIndex, vertical ? vec2(across, bottom) : vec2(-1.0, across));
        end = facePoint(faceIndex, vertical ? vec2(across, 1.0) : vec2(1.0, across));
        alpha = 0.13 * facing * (vertical ? 1.0 : smoothstep(0.0, 0.08, across - bottom));
    }
    tint = vec4(0.38, 0.67, 0.81, alpha * smoothstep(0.17, 0.31, Progress));
    return linePosition(camera, start, end);
}
#endif

void main() {
    Camera camera = sceneCamera();
    faceUv = FACE_CORNERS[gl_VertexID % 6];
    vec2 corner = faceUv * 2.0 - 1.0;
#ifdef HOLOGRAM
    int index = gl_InstanceID - Groups.w;
    if (index < 0) {
        kind = 0;
        gl_Position = gridLine(camera, gl_InstanceID);
        depth = gl_Position.w;
        return;
    }
    if (index < int(RingLines)) {
        kind = 0;
        const float SEGMENTS = 64.0;
        bool tick = index >= int(SEGMENTS);
        float slot = tick ? float(index - int(SEGMENTS)) * 2.0 : float(index);
        float a = slot * TAU / SEGMENTS + Time * 0.23;
        float b = a + (tick ? 0.0 : TAU / SEGMENTS);
        vec3 start = vec3(cos(a), cos(a) * 0.24, sin(a)) * (tick ? 1.48 : 1.53);
        vec3 end = vec3(cos(b), cos(b) * 0.24, sin(b)) * (tick ? 1.67 : 1.53);
        tint = vec4(1.0, 0.52, 0.20, Theme.z * (tick ? 0.66 : 0.37));
        gl_Position = linePosition(camera, start, end);
        depth = gl_Position.w;
        return;
    }
    index -= int(RingLines);
    if (index < int(Flight.w)) {
        kind = 0;
        float height = boardDeck() + 0.34 * Browser.x;
        float extent = 0.64 * blockScale();
        float slot = (float(index % 9) / 4.0 - 1.0) * extent;
        vec3 start = index < 9 ? vec3(slot, height, -extent) : vec3(-extent, height, slot);
        vec3 end = index < 9 ? vec3(slot, height, extent) : vec3(extent, height, slot);
        tint = vec4(0.18, 0.84, 1.0, Browser.x * 0.33);
        gl_Position = projectLine(camera, start, end, 1.15);
        depth = gl_Position.w;
        return;
    }
    index -= int(Flight.w);
    kind = 1;
    vec3 seed = randomSeed(float(index) + 7231.0);
    bool nearEye = index % 3 == 0;
    float layer = nearEye ? mix(1.10, 2.72, seed.z) : mix(12.0, 22.0, seed.z);
    vec2 base = seed.xy * 2.0 - 1.0;
    vec2 drift = sin(vec2(Time * 0.30, Time * 0.24) + seed.yz * TAU) * 0.10;
    vec2 offset = (base * vec2(1.25, 1.09) + drift - Viewport.zw * (nearEye ? 0.15 : 0.035))
            * layer / camera.focal;
    float size = (nearEye ? 0.05 : 0.13) * (0.62 + seed.y);
    vec3 position = camera.eye - camera.back * layer + camera.right * (offset.x + corner.x * size)
            + camera.up * (offset.y + corner.y * size);
    vec3 color = mix(vec3(0.24, 0.40, 0.74), vec3(0.76, 0.58, 0.37), seed.x);
    tint = vec4(color, (nearEye ? 0.20 : 0.28) * (0.56 + 0.44 * seed.z));
    gl_Position = project(camera, position);
    depth = gl_Position.w;
#else
    face = gl_VertexID / 6;
    localPosition = facePoint(face, corner) * HALF_EDGE;
    localPosition.y = -HALF_EDGE + (localPosition.y + HALF_EDGE) * (float(Groups.x) / float(GRID_SIZE));
    vec3 position = localPosition * blockScale();
    position.y += Menu.y * sin(Time * 0.94) * 0.065;
    gl_Position = project(camera, position);
    toEye = camera.eye - position;
#endif
}
