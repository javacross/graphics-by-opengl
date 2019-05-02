#version 300 es
#line 3
#define METALLIC_ROUGH_MAP 1
/* 
 *
 * Fragment shader for glTF asset - using 1 texture, normal map and metallic roughness map
 */
 // Put all defines before precision - common source is inserted here
precision highp float;

void main() {
    BRDF brdf = getPerVertexBRDF();
    vec3[2] diffuseSpecular = calculateFresnelDiffuse(brdf, vec3(texture(uTextureMR, vTexMR)));
    outputPixel(vec4(diffuseSpecular[0] * texture(uTexture0, vTexCoord0).rgb + diffuseSpecular[1], 1.0));
}