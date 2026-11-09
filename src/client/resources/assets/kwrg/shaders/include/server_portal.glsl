layout(std140) uniform PortalFrame {
    vec4 Viewport; // Width, height, elapsed construction, and scene time.
    vec4 State; // Opacity, time since ignition, joining flag, and time since failure (-1 while live).
    vec4 Arrival; // World arrival progress, lens, portal dive, and whether a retry has lit the portal.
    vec4 Journey; // Seconds since voluntary exit, return to the list, and retry (-1 when inactive).
    ivec4 Palette; // Random seed and crying-obsidian bit mask for this portal.
    mat4 LandingView; // Departure pose expressed in the current world camera.
};

const int FRAME_BLOCKS = 14;
const int ANCHOR_BLOCKS = 4;
const int DEBRIS_PER_BLOCK = 4;
const float CONTACT_MAX_RADIUS = 0.40;
const float BREAK_START = 0.25;
const float ORBIT_CAPTURE = 0.90;
const vec3 PORTAL_CORE = vec3(0.0, 0.73, 0.0);

float pressureGlow() {
    return step(0.0, State.w) * smoothstep(0.0, 0.065, State.w)
            * (1.0 - smoothstep(BREAK_START, BREAK_START + 0.13, State.w));
}

float ruptureFlash() {
    float elapsed = State.w - BREAK_START;
    return step(0.0, elapsed) * (1.0 - smoothstep(0.0, 0.13, elapsed));
}

float ignitionFlash() {
    return (1.0 - smoothstep(0.0, 0.13, abs(State.y - 0.37))) * max(step(State.w, -0.001), step(0.0, Journey.z));
}

float portalOpen() {
    float draining = 1.0 - smoothstep(0.34, 0.51, Journey.x);
    float ready = max(max(State.z, Arrival.w) * step(State.w, -0.001), step(0.0, Journey.z));
    return ready * smoothstep(0.33, 0.47, State.y) * draining;
}
