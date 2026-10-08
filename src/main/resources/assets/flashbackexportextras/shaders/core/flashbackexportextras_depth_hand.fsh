#version 330
#ifdef FBEE_SPIRV
// Minecraft 26.3 compiles GLSL to SPIR-V: every interface variable needs an
// explicit location.
#extension GL_ARB_separate_shader_objects : require
#define FBEE_LOCATION(n) layout(location = n)
#else
#define FBEE_LOCATION(n)
#endif

// The depth attachment the first-person hand was drawn into: cleared to 0.0
// (reversed-Z far) just before the hand, so any other value is a hand pixel.
uniform sampler2D InDepth;

layout(std140) uniform HandParameters {
    float HandDepth;
    float Unused0;
    float Unused1;
    float Unused2;
};
FBEE_LOCATION(0) in vec2 texCoord;
FBEE_LOCATION(0) out float fragDepth;

void main() {
    if (texture(InDepth, texCoord).r <= 0.0) {
        discard;
    }
    // The hand is the nearest thing in the frame: the near plane, so nothing
    // composited by depth can pass in front of it.
    fragDepth = HandDepth;
}
