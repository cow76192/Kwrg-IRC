#version 330 core

out vec2 screenUv;

void main() {
    // One oversized triangle covers the viewport without a vertex buffer.
    screenUv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(screenUv * 2.0 - 1.0, 0.0, 1.0);
}
