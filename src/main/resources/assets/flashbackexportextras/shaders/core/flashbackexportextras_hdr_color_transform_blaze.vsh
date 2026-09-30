#version 330
#ifdef FBEE_SPIRV
// Minecraft 26.3 compiles GLSL to SPIR-V: every interface variable needs an
// explicit location and the vertex index built-in is gl_VertexIndex.
#extension GL_ARB_separate_shader_objects : require
#define FBEE_VERTEX_ID gl_VertexIndex
#define FBEE_LOCATION(n) layout(location = n)
#else
#define FBEE_VERTEX_ID gl_VertexID
#define FBEE_LOCATION(n)
#endif

FBEE_LOCATION(0) out vec2 texCoord;

void main() {
    // The two vertices at x/y = 3 lie outside the viewport. Together with
    // (-1, -1) they form a single triangle that covers the entire screen.
    vec2 triangle = vec2((FBEE_VERTEX_ID << 1) & 2, FBEE_VERTEX_ID & 2);
    gl_Position = vec4(triangle * 2.0 - 1.0, 0.0, 1.0);
    // Interpolation across the visible part of the oversized triangle maps
    // this 0..2 range to the source texture's complete 0..1 range.
    texCoord = vec2(triangle.x, 1.0 - triangle.y);
}
