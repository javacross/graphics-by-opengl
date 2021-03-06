package com.nucleus.opengl.shader;

import java.nio.IntBuffer;

import com.nucleus.BackendException;
import com.nucleus.common.BufferUtils;
import com.nucleus.common.Environment;
import com.nucleus.common.Environment.Property;
import com.nucleus.environment.Lights;
import com.nucleus.light.Light;
import com.nucleus.opengl.GLException;
import com.nucleus.renderer.NucleusRenderer;
import com.nucleus.renderer.NucleusRenderer.Renderers;
import com.nucleus.renderer.Pass;
import com.nucleus.scene.gltf.Accessor;
import com.nucleus.scene.gltf.AccessorDictionary;
import com.nucleus.scene.gltf.GLTF;
import com.nucleus.scene.gltf.Material;
import com.nucleus.scene.gltf.Material.ShadingMaps;
import com.nucleus.scene.gltf.Material.ShadingMaps.Flags;
import com.nucleus.scene.gltf.PBRMetallicRoughness;
import com.nucleus.scene.gltf.Primitive;
import com.nucleus.scene.gltf.Primitive.Attributes;
import com.nucleus.scene.gltf.Scene;
import com.nucleus.scene.gltf.Texture.TextureInfo;
import com.nucleus.shader.GenericShaderProgram;
import com.nucleus.shader.ShaderSource;
import com.nucleus.vecmath.Matrix;

public class GLTFShaderProgram extends GenericShaderProgram {

    public class GLTFCategorizer extends Categorizer {

        public GLTFCategorizer(Pass pass, Shading shading, String category) {
            super(pass, shading, category);
        }

        @Override
        public String getShaderSourceName(ShaderType type) {
            switch (type) {
                case VERTEX:
                    return (function.getPath(type) + function.getPassString()) + "main";
                case FRAGMENT:
                    return (function.getPath(type) + function.getPassString()) + "main";
                case COMPUTE:
                    return "";
                case GEOMETRY:
                    return "";
                default:
                    throw new IllegalArgumentException("Not implemented for type: " + type);

            }
        }

    }

    transient protected String[][] commonSourceNames = new String[][] { { "common_structs.essl", "pbr" },
            { "common_structs.essl", "pbr" } };
    transient protected ShadingMaps pbrShading;

    transient protected NamedShaderVariable pbrDataUniform;
    transient protected NamedShaderVariable light0Uniform;
    transient protected NamedShaderVariable viewPosUniform;
    transient protected float[] pbrData;
    transient protected IntBuffer samplerUniformBuffer = BufferUtils.createIntBuffer(1);
    transient private boolean renderNormalMap = false;
    transient private boolean renderMRMap = false;

    /**
     * The dictionary created from linked program
     */
    protected AccessorDictionary<String> accessorDictionary = new AccessorDictionary<>();

    /**
     * Creates a new GLTF shaderprogram with the specified pbr shading parameters
     * 
     * @gles
     * @param pbrShading
     */
    public GLTFShaderProgram(ShadingMaps pbrShading) {
        init(new GLTFCategorizer(null, Shading.pbr, "gltf"), ProgramType.VERTEX_FRAGMENT);
        this.pbrShading = pbrShading;
        init();
    }

    private void init() {
        renderNormalMap = Environment.getInstance().isProperty(Property.RENDER_NORMALMAP, renderNormalMap);
        renderMRMap = Environment.getInstance().isProperty(Property.RENDER_MRMAP, renderMRMap);
    }

    /**
     * Returns the program accessor dictionary, this is created after linking the program and stores accessors
     * using shader variable name.
     * 
     * @return
     */
    public AccessorDictionary<String> getAccessorDictionary() {
        return accessorDictionary;
    }

    @Override
    protected String[] getLibName(Renderers version, ShaderType type) {
        switch (type) {
            case VERTEX:
                if (commonSourceNames[ShaderType.VERTEX.index] != null) {
                    String[] result = new String[commonSourceNames[ShaderType.VERTEX.index].length];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = function.getPath(type) + commonSourceNames[ShaderType.VERTEX.index][i];
                    }
                    return result;
                }
                break;
            case FRAGMENT:
                if (commonSourceNames[ShaderType.FRAGMENT.index] != null) {
                    String[] result = new String[commonSourceNames[ShaderType.FRAGMENT.index].length];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = function.getPath(type) + commonSourceNames[ShaderType.FRAGMENT.index][i];
                    }
                    return result;
                }
                break;
            default:
                return null;
        }
        return null;
    }

    @Override
    public void initUniformData() {
        // Init may be called several times
        if (pbrDataUniform == null) {
            pbrDataUniform = getUniformByName(Attributes._PBRDATA.name());
            if (pbrDataUniform != null) {
                // Will be null in vector debug shader
                pbrData = new float[pbrDataUniform.getSizeInFloats()];
            }
            light0Uniform = getUniformByName(Attributes._LIGHT_0.name());
            viewPosUniform = getUniformByName(Attributes._VIEWPOS.name());
        }
    }

    @Override
    public void updateUniformData() {

    }

    /**
     * Update the global environment variables, camera and directional light
     * 
     * @param renderer
     * @param scene
     */
    public void updateEnvironmentUniforms(NucleusRenderer renderer, Scene scene) {
        if (light0Uniform != null) {
            Light l = Lights.getInstance().getLight();
            setUniformData(light0Uniform, l.getLight(), 0);
        }
        if (viewPosUniform != null) {
            float[] viewPos = new float[viewPosUniform.getSizeInFloats()];
            float[] cameraMatrix = scene.getCameraInstance().updateMatrix();
            Matrix.getTranslate(cameraMatrix, viewPos, 0);
            setUniformData(viewPosUniform, viewPos, 0);
        }
    }

    /**
     * Read uniforms from material for the primitive and upload.
     * 
     * @param primitive
     * @throws GLException
     */
    public void updatePBRUniforms(Primitive primitive) throws GLException {
        Material material = primitive.getMaterial();
        if (material != null) {
            PBRMetallicRoughness pbr = material.getPbrMetallicRoughness();
            pbr.calculatePBRData();
            pbr.getPBR(pbrData, 0);
        }
        setUniformData(pbrDataUniform, pbrData, 0);
    }

    /**
     * Prepares a texture used before rendering starts.
     * This shall set texture parameters to used textures, ie activate texture, bind texture then set parameters.
     * 
     * @param renderer
     * @param gltf
     * @param primitive
     * @param attribute
     * @param texUniform
     * @param texInfo
     * @throws BackendException
     */
    public void prepareTexture(NucleusRenderer renderer, GLTF gltf, Primitive primitive, NamedShaderVariable attribute,
            NamedShaderVariable texUniform, TextureInfo texInfo) throws BackendException {
        if (texInfo == null || attribute == null || texUniform == null) {
            return;
        }
        samplerUniformBuffer.position(0);
        samplerUniformBuffer.put(texInfo.getIndex());
        samplerUniformBuffer.rewind();
        Accessor accessor = primitive.getAccessor(Attributes.getTextureCoord(texInfo.getTexCoord()));
        renderer.prepareTexture(gltf.getTexture(texInfo), texUniform.getOffset(), accessor, attribute, texUniform,
                samplerUniformBuffer);

    }

    /**
     * Prepares the textures needed for this primitive
     * 
     * @param renderer
     * @param gltf
     * @param material
     * @throws BackendException
     */
    public void prepareTextures(NucleusRenderer renderer, GLTF gltf, Primitive primitive, Material material)
            throws BackendException {
        if (material == null) {
            return;
        }
        if (renderNormalMap && material.getNormalTexture() != null
                && material.getPbrMetallicRoughness().getBaseColorTexture() != null) {
            prepareTexture(renderer, gltf, primitive, getAttributeByName(Attributes._TEXCOORDNORMAL.name()),
                    getUniformByName("uTexture0"),
                    material.getNormalTexture());
        } else if (renderMRMap && material.getPbrMetallicRoughness().getMetallicRoughnessTexture() != null
                && material.getPbrMetallicRoughness().getBaseColorTexture() != null) {
            prepareTexture(renderer, gltf, primitive, getAttributeByName(Attributes._TEXCOORDMR.name()),
                    getUniformByName("uTexture0"),
                    material.getPbrMetallicRoughness().getMetallicRoughnessTexture());
        } else {
            prepareTexture(renderer, gltf, primitive, getAttributeByName(Attributes.TEXCOORD_0.name()),
                    getUniformByName("uTexture0"),
                    material.getPbrMetallicRoughness().getBaseColorTexture());
        }
        prepareTexture(renderer, gltf, primitive, getAttributeByName(Attributes._TEXCOORDNORMAL.name()),
                getUniformByName("uTextureNormal"), material.getNormalTexture());
        prepareTexture(renderer, gltf, primitive, getAttributeByName(Attributes._TEXCOORDMR.name()),
                getUniformByName("uTextureMR"), material.getPbrMetallicRoughness().getMetallicRoughnessTexture());
        prepareTexture(renderer, gltf, primitive, getAttributeByName(Attributes._TEXCOORDOCCLUSION.name()),
                getUniformByName("uTextureOcclusion"), material.getOcclusionTexture());
    }

    @Override
    public String getKey() {
        return getClass().getSimpleName() + pbrShading.getFlags();
    }

    /**
     * Returns the defines to turn on PBR functions for this material
     * 
     * @return
     */
    public String getDefines(ShaderType type) {
        StringBuffer sb = new StringBuffer();
        for (Flags f : Flags.values()) {
            if (pbrShading.isFlag(f)) {
                sb.append(ShaderSource.DEFINE + " " + f.define + " 1\n");
            } else {
                sb.append(ShaderSource.UNDEF + " " + f.define + "\n");
            }
        }
        return sb.toString();
    }

}
