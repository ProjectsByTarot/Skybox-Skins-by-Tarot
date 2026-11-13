#version 120

varying vec2 texCoord;
varying vec3 worldDir;

uniform sampler2D maskTex;
uniform sampler2D skyboxTex;
uniform sampler2D skinTex;

const float PI = 3.14159265359;

void main() {
    if (texture2D(maskTex, texCoord).r < 0.5) {
        gl_FragColor = texture2D(skinTex, texCoord);
    } else {
        vec3 direction = normalize(worldDir);
        float u = 0.5 + atan(direction.z, direction.x) / (2.0 * PI);
        float v = 0.5 - asin(clamp(direction.y, -1.0, 1.0)) / PI;
        gl_FragColor = texture2D(skyboxTex, vec2(u, v));
    }
}
