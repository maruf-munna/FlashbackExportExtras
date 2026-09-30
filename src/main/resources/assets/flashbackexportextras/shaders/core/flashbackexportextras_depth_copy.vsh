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
    vec2 p = vec2((FBEE_VERTEX_ID << 1) & 2, FBEE_VERTEX_ID & 2);
    texCoord = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
