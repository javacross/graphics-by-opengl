#version 300 es
#line 3
#define NORMAL_MAP 1
/* 
 *
 * Fragment shader for glTF asset
 * @author Richard Sahlin
 */
 // Put all defines before precision - common source is inserted here
precision highp float;

void main() {
    BRDF brdf = getPerPixelBRDF(normalize(vec3(texture(uTextureNormal, vTexNormal) * 2.0 - 1.0) * mTangentLight));
    vec3[2] diffuseSpecular = calculateFresnelDiffuse(brdf);
    outputPixel(vec4(diffuseSpecular[0] + diffuseSpecular[1], 1.0));
}