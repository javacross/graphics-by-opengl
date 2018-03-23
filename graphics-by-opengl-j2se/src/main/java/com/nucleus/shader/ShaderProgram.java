package com.nucleus.shader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.nucleus.SimpleLogger;
import com.nucleus.assets.AssetManager;
import com.nucleus.common.Constants;
import com.nucleus.common.StringUtils;
import com.nucleus.geometry.AttributeBuffer;
import com.nucleus.geometry.AttributeUpdater.Property;
import com.nucleus.geometry.Mesh;
import com.nucleus.geometry.Mesh.BufferIndex;
import com.nucleus.light.GlobalLight;
import com.nucleus.opengl.GLES20Wrapper;
import com.nucleus.opengl.GLES30Wrapper;
import com.nucleus.opengl.GLESWrapper;
import com.nucleus.opengl.GLESWrapper.GLES20;
import com.nucleus.opengl.GLESWrapper.GLES30;
import com.nucleus.opengl.GLESWrapper.GLES31;
import com.nucleus.opengl.GLESWrapper.GLES_EXTENSIONS;
import com.nucleus.opengl.GLESWrapper.ProgramInfo;
import com.nucleus.opengl.GLESWrapper.Renderers;
import com.nucleus.opengl.GLException;
import com.nucleus.opengl.GLUtils;
import com.nucleus.renderer.Pass;
import com.nucleus.renderer.Window;
import com.nucleus.shader.ShaderVariable.InterfaceBlock;
import com.nucleus.shader.ShaderVariable.VariableType;
import com.nucleus.texturing.Texture2D;
import com.nucleus.texturing.Texture2D.Shading;
import com.nucleus.texturing.TextureType;
import com.nucleus.texturing.TextureUtils;
import com.nucleus.texturing.TiledTexture2D;
import com.nucleus.vecmath.Matrix;

/**
 * This class handles loading, compiling and linking of OpenGL ES shader programs.
 * Implement for each program to make a mapping between shader variable names and GLES attribute/uniform locations.
 * A ShaderProgram object shall contain information that is specific to the shader program compilation and linking.
 * Uniform and attribute mapping shall be included but not the uniform and attribute data - this is so that the same
 * ShaderProgram instance can be used to render multiple objects.
 * 
 * @author Richard Sahlin
 *
 */
public abstract class ShaderProgram {

    public enum Shaders {
        VERTEX_FRAGMENT(),
        COMPUTE();
    }

    public static final String PROGRAM_DIRECTORY = "assets/";
    public static final String SHADER_SOURCE_SUFFIX = ".essl";
    /**
     * Shader suffix as added after checking for which version to use
     */
    public static final String COMMON_VERTEX_SHADER = "commonvertex";
    public static final String FRAGMENT_TYPE = "fragment";
    public static final String VERTEX_TYPE = "vertex";
    protected final static String MUST_SET_FIELDS = "Must set attributesPerVertex,vertexShaderName and fragmentShaderName";

    /**
     * Set to true to force appending common shader to shader source
     * TODO Move setting to environment
     */
    protected static boolean appendCommonShaders = false;

    protected final Shaders shaders;

    /**
     * Number of vertices per sprite - this is for a quad that is created using element buffer.
     */
    public final static int VERTICES_PER_SPRITE = 4;
    /**
     * Draw using an index list each quad is made up of 6 indices (2 triangles)
     */
    public final static int INDICES_PER_SPRITE = 6;
    /**
     * Default number of components 1
     */
    public final static int DEFAULT_COMPONENTS = 3;

    /**
     * Enum used when the attribute locations shall be automatically bound.
     * The mode can be either APPEND or PREFIX.
     * APPEND means that the location to bind the attribute to is appended after the name - must contain 2 numbers,
     * eg 00 for first location.
     * PREFIX means that the name is prefixed with the attribute location, must contain 2 numbers, eg 00 for first
     * location.
     * 
     * @author Richard Sahlin
     *
     */
    public enum AttribNameMapping {
        APPEND(),
        PREFIX();
    }

    public final static String SHADER_SOURCE_ERROR = "Error setting shader source: ";
    public final static String COMPILE_SHADER_ERROR = "Error compiling shader: ";
    public final static String COMPILE_STATUS_ERROR = "Failed compile status: ";
    public final static String CREATE_SHADER_ERROR = "Can not create shader object, context not active?";
    public final static String LINK_PROGRAM_ERROR = "Error linking program: ";
    public final static String BIND_ATTRIBUTE_ERROR = "Error binding attribute: ";
    public final static String VARIABLE_LOCATION_ERROR = "Could not get shader variable location: ";
    public final static String NULL_VARIABLES_ERROR = "ShaderVariables are null, program not created? Must call fetchProgramInfo()";
    public final static String GET_PROGRAM_INFO_ERROR = "Error fetching program info.";

    /**
     * The function of the shader program
     * 
     * How to get shader name from program:
     * <optional pass> shading / category / type
     * Eg:
     * textureduvspritevertex.essl
     * flatspritefragment.essl
     * shadow2textureduvspritevertex.essl
     * TODO maybe put the fields below in an inner class
     */
    public class Function {
        protected Pass pass;
        protected Texture2D.Shading shading;
        protected String category;

        public Function(Pass pass, Texture2D.Shading shading, String category) {
            this.pass = pass;
            this.shading = shading;
            this.category = category;
        }

        /**
         * Returns the shading used, or null if not relevant
         * 
         * @return
         */
        public Texture2D.Shading getShading() {
            return shading;
        }

        /**
         * Returns the name of the category of this shader function, for instance sprite, charmap
         * 
         * @return The category name of null if not relevant
         */
        public String getCategory() {
            return category;
        }

        /**
         * If this shader can only be used in a specific pass, normally only set for passes other than {@link Pass#MAIN}
         * 
         * @return Pass that the shader belongs to, or null if not relevant.
         */
        public Pass getPass() {
            return pass;
        }

        /**
         * Returns the shader source name, excluding directory prefix and name of shader (vertex/fragment/compute)
         * 
         * @return
         */
        public String getShaderSourceName() {
            return (getPassString() + getShadingString() + getCategoryString());
        }

        /**
         * Returns the shading as a lowercase string, or "" if not set.
         * 
         * @return
         */
        public String getShadingString() {
            return (shading != null ? shading.name().toLowerCase() : "");
        }

        /**
         * Returns the category as a lowercase string, or "" if not set
         * 
         * @return
         */
        public String getCategoryString() {
            return (category != null ? category.toLowerCase() : "");
        }

        /**
         * Returns the pass as a lowercase string, or "" if null.
         * 
         * @return
         */
        public String getPassString() {
            return (pass != null ? pass.name().toLowerCase() : "");
        }

        @Override
        public String toString() {
            return getShaderSourceName();
        }

    }

    /**
     * The basic function - will be returned
     */
    protected Function function;

    /**
     * The GL program object
     */
    private int program = Constants.NO_VALUE;

    private ShaderSource[] shaderSources;
    private int[] shaderNames;

    /**
     * This is the main array holding all active shader variables
     * It is set when {@link #addShaderVariable(ShaderVariable)} is called.
     * Use {@link #getShaderVariable(VariableMapping)} to fetch variable.
     */
    protected ShaderVariable[] shaderVariables;

    private AttribNameMapping attribNameMapping = null;

    /**
     * Calculated in create program
     */
    protected ShaderVariable[][] attributeVariables;

    protected GlobalLight globalLight = GlobalLight.getInstance();
    /**
     * The size of each buffer for the attribute variables
     */
    protected int[] attributesPerVertex;
    /**
     * List of attributes defined by a program, ie passed to {@link #setAttributeMapping(VariableMapping[])}
     */
    protected VariableMapping[] attributes;
    protected int attributeBufferCount;
    protected HashMap<Integer, ShaderVariable> blockVariables = new HashMap<>(); // Active block uniforms, index is the
                                                                                 // uniform index from GL
    /**
     * TODO - make static so only created once
     */
    protected ArrayList<Integer> commonVertexShaders;
    /**
     * TODO - make static so only created once
     */
    protected ArrayList<String> commonVertexSources;

    protected InterfaceBlock[] interfaceBlocks;

    /**
     * Samplers (texture units)
     */
    transient protected int[] samplers;

    /**
     * List of uniforms defined by a program, ie passed to {@link #setUniformMapping(VariableMapping[])}
     * 
     */
    protected VariableMapping[] sourceUniforms;
    /**
     * Uniforms, used when rendering - uniforms array shall belong to program since uniforms are a property of the
     * program. This data is quite small and the size depends on what program is used - and not the mesh.
     * The same mesh may be rendered with different programs, for instance different shadow passes and will have
     * different number of uniforms depending on the program.
     * 
     */
    transient protected float[] uniforms;

    /**
     * Unmapped variable types
     */
    protected List<Integer> unMappedTypes = new ArrayList<>();

    /**
     * Default is to dynamically map variable names, ie map based on used variables.
     * Only has effect if set before program is compiled/linked.
     */
    protected boolean useDynamicMapping = true;

    /**
     * Returns the program for the specified pass and shading, this is used to resolve the correct
     * program for different passes
     * 
     * @param gles
     * @param pass
     * @param shading
     */
    public abstract ShaderProgram getProgram(GLES20Wrapper gles, Pass pass, Texture2D.Shading shading);

    /**
     * Returns the offset within an attribute buffer where the property is, this is used to set specific properties
     * of a vertex.
     * This will be the same for all vertices, you only need to fetch this once. It will not change as long
     * as the same program is used.
     * 
     * @param property
     * @return Offset into attribute (buffer) where the storage for the specified property is, or -1 if the property
     * is not supported.
     */
    public int getPropertyOffset(Property property) {
        ShaderVariable v = null;
        switch (property) {
            case TRANSLATE:
                v = getShaderVariable(CommonShaderVariables.aTranslate);
                break;
            case ROTATE:
                v = getShaderVariable(CommonShaderVariables.aRotate);
                break;
            case SCALE:
                v = getShaderVariable(CommonShaderVariables.aScale);
                break;
            case FRAME:
                v = getShaderVariable(CommonShaderVariables.aFrameData);
                break;
            case COLOR_AMBIENT:
            case COLOR:
                v = getShaderVariable(CommonShaderVariables.aColor);
                break;
            default:
        }
        if (v != null) {
            return v.getOffset();
        } else {
            SimpleLogger.d(getClass(), "No ShaderVariable for " + property);
        }
        return -1;
    }

    /**
     * Returns the function for the shader type, default is to return the function that was specified when program was
     * created.
     * 
     * @param type GL_VERTEX_SHADER, GL_FRAGMENT_SHADER, GL_COMPUTE_SHADER
     * @return
     */
    protected Function getFunction(int type) {
        return function;
    }

    /**
     * Returns the number of defined attribute + uniform variables in the program.
     * This is to make it easier when developing so that temporarily unused variabled do not need to be removed.
     * 
     * @return Number of defined variables in the shader program, all variables do not need to be used.
     */
    public int getVariableCount() {
        return CommonShaderVariables.values().length;
    }

    /**
     * Creates a new shader program for the specified shading - used by subclasses
     * 
     * @param pass The pass this shader is for or null if not used
     * @param shading The shading function or null if not used
     * @param category The category of function or null of not used
     * @param mapping
     * @param shader
     */
    protected ShaderProgram(Pass pass, Texture2D.Shading shading, String category, VariableMapping[] mapping,
            Shaders shaders) {
        function = new Function(pass, shading, category);
        setMapping(mapping);
        this.shaders = shaders;
    }

    protected void setMapping(VariableMapping[] mapping) {
        setUniformMapping(mapping);
        setAttributeMapping(mapping);
    }

    /**
     * Loads the version correct shader sources for the sourceNames and types.
     * The shader sourcenames will be versioned, when this method returns the shaders sourcecode can be fetched
     * from the sources objects.
     * 
     * @param sources Name and type of shader sources to load, versioned source will be stored here
     * @throws IOException
     */
    protected void loadShaderSources(GLESWrapper gles, ShaderSource[] sources)
            throws IOException {

        int count = sources.length;
        for (int i = 0; i < count; i++) {
            gles.loadVersionedShaderSource(sources[i], false);
        }
    }

    /**
     * Sets the name of the shaders in this program and returns an array of {@link ShaderSource}, normally 2 - one for
     * vertex and one for fragment-shader.
     * This method must be called before the program is created.
     * 
     * @param version Highest level of GL that is supported
     */
    protected ShaderSource[] createShaderSource(Renderers version) {
        ShaderSource[] sources = null;
        int[] shaderTypes;
        switch (shaders) {
            case VERTEX_FRAGMENT:
                sources = new ShaderSource[2];
                shaderTypes = new int[] { GLES20.GL_VERTEX_SHADER, GLES20.GL_FRAGMENT_SHADER };
                break;
            case COMPUTE:
                sources = new ShaderSource[1];
                shaderTypes = new int[] { GLES31.GL_COMPUTE_SHADER };
                break;
            default:
                throw new IllegalArgumentException("Not implemented for " + shaders);
        }
        for (int i = 0; i < shaderTypes.length; i++) {
            sources[i] = getShaderSource(version, shaderTypes[i]);
        }
        return sources;
    }

    /**
     * Returns the name of the shader source for the specified type, this is taken from the function using pass, shading
     * and category.
     * Override in subclasses to point to other shader source
     * 
     * @param version Highest GL version that is supported, used to fetch versioned source name.
     * @param type The shader type to return source for, GL_VERTEX_SHADER, GL_FRAGMENT_SHADER, GL_COMPUTE_SHADER
     * @return
     */
    protected ShaderSource getShaderSource(Renderers version, int type) {
        switch (type) {
            case GLES20.GL_VERTEX_SHADER:
                return new ShaderSource(PROGRAM_DIRECTORY + getFunction(type) + VERTEX_TYPE,
                        getSourceNameVersion(version, type), type);
            case GLES20.GL_FRAGMENT_SHADER:
                return new ShaderSource(PROGRAM_DIRECTORY + getFunction(type) + FRAGMENT_TYPE,
                        getSourceNameVersion(version, type), type);
            case GLES31.GL_COMPUTE_SHADER:
                return new ShaderSource(PROGRAM_DIRECTORY + getFunction(type),
                        getSourceNameVersion(version, type), type);
            default:
                throw new IllegalArgumentException("Not implemented for type: " + type);

        }
    }

    /**
     * Called by {@link #getShaderSource(int)} to append shader (ESSL) version to sourcename.
     * This is used to be able to load different shader sources depending on current version of Renderer.
     * Override this if different source shall be used depending on available renderer/shader version.
     * 
     * @param version Highest GL version that is supported
     * @param type Shader type, GL_VERTEX_SHADER, GL_FRAGMENT_SHADER, GL_COMPUTE_SHADER
     * @return Empty string "", or shader version to append to source name if different shader source shall be used for
     * a specific shader version.
     */
    protected String getSourceNameVersion(Renderers version, int type) {
        return "";
    }

    /**
     * Returns the number of attribute buffers - as found when calling {@link #createProgram(GLES20Wrapper)}
     * 
     * @return
     */
    public int getAttributeBufferCount() {
        return attributeBufferCount;
    }

    /**
     * Adds the uniform mapping as defined by subclass
     * 
     * @param mapping The mappings to add to source uniforms - these are the uniform names that can be found by using
     * {@link VariableMapping}
     */
    protected void setUniformMapping(VariableMapping[] mapping) {
        ArrayList<VariableMapping> uniformList = new ArrayList<VariableMapping>();
        for (VariableMapping v : mapping) {
            if (v.getType() == VariableType.UNIFORM) {
                uniformList.add(v);
            }
        }
        sourceUniforms = uniformList.toArray(new VariableMapping[uniformList.size()]);
    }

    /**
     * Internal method
     * Fetches the attribute variable mappings, these are used to create per buffer attribute mappings.
     * The attribute buffer count is calculated.
     * 
     * @param mapping
     */
    protected void setAttributeMapping(VariableMapping[] mapping) {
        ArrayList<VariableMapping> attributeList = new ArrayList<VariableMapping>();
        for (VariableMapping v : mapping) {
            if (v.getType() == VariableType.ATTRIBUTE) {
                attributeList.add(v);
                if (v.getBufferIndex().index + 1 > attributeBufferCount) {
                    attributeBufferCount = v.getBufferIndex().index + 1;
                }
            }
        }
        attributes = attributeList.toArray(new VariableMapping[attributeList.size()]);
    }

    /**
     * Create the programs for the shader program implementation.
     * This method must be called before the program is used, or the other methods are called.
     * It will create the {@link #shaderSources} field, compile and link the shader sources specified.
     * 
     * @param gles The GLES20 wrapper to use when compiling and linking program.
     * @throws GLException If program could not be compiled and linked, possibly due to IOException
     */
    public void createProgram(GLES20Wrapper gles) throws GLException {
        shaderSources = createShaderSource(gles.getInfo().getRenderVersion());
        if (shaderSources == null) {
            throw new ShaderProgramException(MUST_SET_FIELDS);
        }
        createProgram(gles, shaderSources);
    }

    /**
     * Maps the attributes used based on BufferIndex - attribute variables are sorted based on buffer in the specified
     * result array.
     * Finds the shader attribute variables per buffer using VariableMapping, iterate through defined (by subclasses)
     * attribute variable mapping.
     * Put the result in the result array and set the {@linkplain ShaderVariable} offset based on used attributes.
     * Sorting shall be done in the order that variables are added to the {@link #attributes} array, as this will
     * preserve the order.
     * Meaning that if the declared ShaderVariables are used, and of same size as declared, then the resulting offsets
     * will match the offsets in ShaderVariables.
     * 
     * TODO Add check for mismatch of size, ie if ShaderVariables hase one variable as float3 and it is defined is
     * program as float4 then raise error.
     * 
     * @param resultArray Array to store shader variables for each attribute buffer in, attributes for buffer 1 will go
     * at index 0.
     */
    private void mapAttributeVariablePerBuffer(ShaderVariable[][] resultArray) {

        HashMap<BufferIndex, ArrayList<ShaderVariable>> svPerBuffer = new HashMap<BufferIndex, ArrayList<ShaderVariable>>();
        for (VariableMapping vm : attributes) {
            ArrayList<ShaderVariable> array = svPerBuffer.get(vm.getBufferIndex());
            if (array == null) {
                array = new ArrayList<ShaderVariable>();
                svPerBuffer.put(vm.getBufferIndex(), array);
            }
            ShaderVariable sv = getShaderVariable(vm);
            if (sv != null) {
                array.add(getShaderVariable(vm));
            }
        }
        for (BufferIndex key : svPerBuffer.keySet()) {
            ArrayList<ShaderVariable> defined = svPerBuffer.get(key);
            if (defined != null) {
                resultArray[key.index] = defined.toArray(new ShaderVariable[defined.size()]);
            }
        }
    }

    private void dynamicMapShaderOffset(ShaderVariable[] variables, VariableType type) {
        int offset = 0;
        int samplerOffset = 0;
        for (ShaderVariable v : variables) {
            if (v != null && v.getType() == type) {
                switch (v.getDataType()) {
                    case GLES20.GL_SAMPLER_2D:
                    case GLES30.GL_SAMPLER_2D_SHADOW:
                        v.setOffset(samplerOffset);
                        samplerOffset += v.getSizeInFloats();
                        break;
                    default:
                        v.setOffset(offset);
                        offset += v.getSizeInFloats();
                        break;
                }
            }
        }
    }

    /**
     * Returns the vertex stride for the program, use this when creating a mesh
     * 
     * @return
     */
    public int getVertexStride() {
        return attributesPerVertex[BufferIndex.VERTICES.index];
    }

    /**
     * Returns the number of attributes per vertex
     * TODO - rename this to dynamic, since it fetches the number of attributes per vertex for dynamic buffer
     * 
     * @param buffer The buffer to get attributes per vertex for
     * @return Number of attributes per vertex, 0 or -1 if not defined.
     */
    public int getAttributesPerVertex(BufferIndex buffer) {
        if (attributesPerVertex.length > buffer.index) {
            return attributesPerVertex[buffer.index];
        } else {
            // No attribute buffer for this program.
            return -1;
        }
    }

    /**
     * Creates the attribute buffers for the specified mesh, this is a factory method that shall create
     * the needed attribute buffers for the shader program.
     * 
     * @param mesh
     * @param verticeCount Number of vertices to allocate storage for
     * @return The created buffers as needed by the Mesh to render.
     */
    public AttributeBuffer[] createAttributeBuffers(Mesh mesh, int verticeCount) {
        AttributeBuffer[] buffers = new AttributeBuffer[attributeBufferCount];
        for (BufferIndex index : BufferIndex.values()) {
            if (index.index >= buffers.length) {
                break;
            }
            buffers[index.index] = createAttributeBuffer(index, verticeCount, mesh);
        }
        return buffers;
    }

    /**
     * Creates the storage for attributes that are not vertices, only creates the storage will not fill buffer.
     * For some subclasses this must also create a backing attribute array in the mesh that is used as
     * intermediate storage before the vertex buffer is updated.
     * 
     * @param index The attribute buffer to create
     * @param verticeCount Number of vertices
     * @param mesh
     * @return The buffer for attribute storage or null if not needed.
     */
    protected AttributeBuffer createAttributeBuffer(BufferIndex index, int verticeCount, Mesh mesh) {
        switch (index) {
            case ATTRIBUTES:
            case VERTICES:
                int attrs = getAttributesPerVertex(index);
                if (attrs > 0) {
                    AttributeBuffer buffer = new AttributeBuffer(verticeCount, attrs, GLES20.GL_FLOAT);
                    return buffer;
                }
                return null;
            case ATTRIBUTES_STATIC:
            default:
                throw new IllegalArgumentException("Not implemented");
        }
    }

    /**
     * Internal method, creates the array storage for for uniform samplers, sampler usage is specific to program
     * and does not need to be stored in mesh.
     * 
     * 
     */
    protected void createSamplerStorage() {
        if (shaderVariables == null) {
            throw new IllegalArgumentException("Shader variables is null, forgot to call createProgram()?");
        }
        int samplerSize = 0;
        if (shaderVariables != null) {
            samplerSize = getSamplerSize(shaderVariables);
            if (samplerSize > 0) {
                setSamplers(new int[samplerSize]);
            } else {
                SimpleLogger.d(getClass(), "No samplers used");
            }
        } else {
            throw new IllegalArgumentException("Shader variables is null, forgot to call createProgram()?");
        }
    }

    /**
     * Creates array store for uniform data that fits this shader program.
     * - try to use uniform blocks as much as possible instead of separate uniforms
     * 
     * @return
     */
    public float[] createUniformArray() {
        if (shaderVariables == null) {
            throw new IllegalArgumentException("Shader variables is null, forgot to call createProgram()?");
        }
        int uniformSize = getVariableSize(shaderVariables, VariableType.UNIFORM);
        return new float[uniformSize];
    }

    /**
     * Creates the block (uniform) buffers, if any are used.
     * 
     * @return Variable block buffers for this program, or null if not used.
     */
    public BlockBuffer[] createBlockBuffers() {
        BlockBuffer[] blockBuffers = null;
        if (interfaceBlocks != null) {
            blockBuffers = new BlockBuffer[interfaceBlocks.length];
            int blockSize = 0;
            for (int index = 0; index < interfaceBlocks.length; index++) {
                InterfaceBlock vb = interfaceBlocks[index];
                // TODO - need to add stride
                blockSize = getVariableSize(vb);
                blockBuffers[index] = createBlockBuffer(vb, blockSize);
            }
            if (blockSize > 0) {
                SimpleLogger.d(getClass(), "Data for uniform block " + blockSize);
            }
        }
        return blockBuffers;
    }

    /**
     * Initialize the attribute and uniform buffers with data, if data is static then it does not need to be
     * updated after this call.
     * Populate the attribute/uniform buffers in the mesh as needed by the shader program.
     * 
     * 
     * @param mesh
     */
    public abstract void initBuffers(Mesh mesh);

    /**
     * Set the attribute pointer(s) using the data in the vertexbuffer, this shall make the necessary calls to
     * set the pointers for used attributes, enable pointers as needed.
     * This will make the actual connection between the attribute data in the vertex buffer and the shader.
     * It is up to the caller to make sure that the attribute array(s) in the mesh contains valid data.
     * 
     * @param gles
     * @param mesh
     */
    public void updateAttributes(GLES20Wrapper gles, Mesh mesh) throws GLException {
        for (int i = 0; i < attributeVariables.length; i++) {
            AttributeBuffer buffer = mesh.getVerticeBuffer(i);
            if (buffer != null) {
                gles.glVertexAttribPointer(buffer, GLES20.GL_ARRAY_BUFFER, attributeVariables[i]);
                GLUtils.handleError(gles, "glVertexAttribPointers ");
            }
        }
    }

    /**
     * Updates the data uploaded to GL as uniforms, if uniforms are static then only the matrices needs to be updated.
     * Calls {@link #setUniformMatrices(float[][], Mesh)} to update uniform matrices.
     * Then call {@link #setUniformData(float[], Mesh)} to set program specific uniform data
     * Then sets uniforms to GL by calling {@link #setUniforms(GLES20Wrapper, VariableMapping[])}
     * When this method returns the uniform data has been uploaded to GL and is ready.
     * 
     * @param gles
     * @param matrices modelview, projection and renderpass matrices
     * @param mesh
     */
    public void updateUniforms(GLES20Wrapper gles, float[][] matrices, Mesh mesh)
            throws GLException {
        setUniformMatrices(matrices, mesh);
        setUniformData(uniforms, mesh);
        uploadUniforms(gles, uniforms, mesh, sourceUniforms);
    }

    protected void uploadUniforms(GLES20Wrapper gles, float[] uniformData, Mesh mesh, VariableMapping[] uniformMapping)
            throws GLException {

        uploadUniforms(gles, uniformData, uniformMapping);
        BlockBuffer[] blocks = mesh.getBlockBuffers();
        if (blocks != null) {
            int index = 0;
            for (BlockBuffer bb : blocks) {
                InterfaceBlock vars = bb.interfaceBlock;
                ((GLES30Wrapper) gles).glUniformBlockBinding(program, vars.blockIndex, index);
                ((GLES30Wrapper) gles).glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, vars.blockIndex,
                        bb.getBufferName());
                GLUtils.handleError(gles, "BindBufferBase for buffer " + bb.getBlockName());
            }
        }

    }

    /**
     * Internal method - upload uniforms to GL.
     * Sets the uniform data from the uniform data into the mapping provided by the attribute mapping.
     * 
     * @param gles
     * @param uniforms Source uniform array store
     * @param uniformMapping Variable mapping for the uniform data
     * @throws GLException
     */
    protected void uploadUniforms(GLES20Wrapper gles, float[] uniforms, VariableMapping[] uniformMapping)
            throws GLException {
        for (VariableMapping am : uniformMapping) {
            ShaderVariable v = getShaderVariable(am);
            // If null then declared in program but not used, silently ignore
            if (v != null) {
                if (v.getBlockIndex() != Constants.NO_VALUE) {
                    setUniformBlock(gles, interfaceBlocks[v.getBlockIndex()], v);
                } else {
                    setUniform(gles, uniforms, v);
                }
            }
        }
    }

    /**
     * Prepares each texture used before rendering starts.
     * This shall set texture parameters to used textures, ie activate texture, bind texture then set parameters.
     * 
     * @param gles
     * @param mesh
     * @throws GLException
     */
    public void prepareTextures(GLES20Wrapper gles, Mesh mesh) throws GLException {
        Texture2D texture = mesh.getTexture(Texture2D.TEXTURE_0);
        TextureUtils.prepareTexture(gles, texture, Texture2D.TEXTURE_0);
    }

    /**
     * Checks the status of the attribute name mapping and if enabled the attributes are bound to locations
     * that correspond to location that is appended or prefixed to the name.
     * 
     * @param gles
     * @throws GLException
     */
    protected void bindAttributeNames(GLES20Wrapper gles) throws GLException {
        if (attribNameMapping == null) {
            return;
        }
        for (ShaderVariable attribute : shaderVariables) {
            if (attribute.getType() == VariableType.ATTRIBUTE) {
                int location = 0;
                switch (attribNameMapping) {
                    case PREFIX:
                        location = Integer.parseInt(attribute.getName().substring(0, 2));
                        break;
                    case APPEND:
                        int length = attribute.getName().length();
                        location = Integer.parseInt(attribute.getName().substring(length - 2, length));
                        break;
                }
                gles.glBindAttribLocation(program, location, attribute.getName());
                GLUtils.handleError(gles, BIND_ATTRIBUTE_ERROR);
                attribute.setLocation(location);
            }
        }

    }

    /**
     * Sets the mode for automatic name mapping of attribute locations.
     * 
     * @see AttribNameMapping
     * @param attribMapping Or null to not automatically bind attributes.
     */
    public void setAttributeNameMapping(AttribNameMapping attribMapping) {
        this.attribNameMapping = attribMapping;
    }

    /**
     * Fetches the program info and stores in this class.
     * This will read active attribute and uniform names, call this after the program has been compiled and linked.
     * 
     * @param gles
     * @throws GLException If attribute or uniform locations could not be found.
     */
    protected void fetchProgramInfo(GLES20Wrapper gles) throws GLException {
        ProgramInfo info = gles.getProgramInfo(program);
        GLUtils.handleError(gles, GET_PROGRAM_INFO_ERROR);
        shaderVariables = new ShaderVariable[CommonShaderVariables.values().length];
        int uniformBlocks = info.getActiveVariables(VariableType.UNIFORM_BLOCK);
        if (uniformBlocks > 0) {
            interfaceBlocks = gles.getUniformBlocks(info);
            for (InterfaceBlock block : interfaceBlocks) {
                fetchActiveVariables(gles, VariableType.UNIFORM_BLOCK, info, block);
            }
        }
        fetchActiveVariables(gles, VariableType.ATTRIBUTE, info, null);
        fetchActiveVariables(gles, VariableType.UNIFORM, info, null);
        attributeVariables = new ShaderVariable[attributeBufferCount][];
        attributesPerVertex = new int[attributeBufferCount];
        mapAttributeVariablePerBuffer(attributeVariables);
        if (useDynamicVariables()) {
            dynamicMapVariables();
        }
        for (int i = 0; i < attributeBufferCount; i++) {
            // If only attribute buffer is used the first array index will be null
            if (attributeVariables[i] != null) {
                attributesPerVertex[i] = getVariableSize(attributeVariables[i], VariableType.ATTRIBUTE);
            }
        }
    }

    /**
     * Dynamically sets used shader variable offsets, for ATTRIBUTES and UNIFORMS
     * The offset will be tightly packed based on used variable size, the order of used variables will be the same.
     */
    private void dynamicMapVariables() {
        for (ShaderVariable[] sv : attributeVariables) {
            // In case only attribute buffer is used the first index will be null.
            if (sv != null) {
                dynamicMapShaderOffset(sv, VariableType.ATTRIBUTE);
            }
        }
        // Map the used uniforms from all used shader variables.
        dynamicMapShaderOffset(shaderVariables, VariableType.UNIFORM);
    }

    /**
     * Fetches active variables info of the specified type (attribute or uniform) and calls implementing shader program
     * class to store the shader variable.
     * This will create the mapping between the specific shader program and the location of shader variables
     * so that each shader variable can be indexed using the implementing shader program.
     * 
     * @param gles
     * @param type Type of variable to fetch info for.
     * @param info
     * @param block Variable block info or null - store block variables here
     * @throws GLException If attribute or uniform location(s) are -1, ie they could not be found using the name.
     */
    private void fetchActiveVariables(GLES20Wrapper gles, VariableType type, ProgramInfo info, InterfaceBlock block)
            throws GLException {
        int count = info.getActiveVariables(type);
        if (count == 0) {
            return;
        }
        byte[] nameBuffer = new byte[info.getMaxNameLength(type)];
        ShaderVariable variable = null;
        for (int i = 0; i < count; i++) {
            variable = null;
            if (block != null) {
                variable = gles.getActiveVariable(program, type, block.indices[i], nameBuffer);
                // Add to current block uniforms
                this.blockVariables.put(variable.getActiveIndex(), variable);
                addShaderVariable(variable);
            } else {
                // Check if uniform variable already has been fetched from block - then check that it is used in type of
                // shader.
                variable = getBlockVariable(type, i);
                if (variable != null) {
                    SimpleLogger.d(getClass(),
                            type.name() + " using block variable for index " + i + ", " + variable.getName());
                } else {
                    variable = gles.getActiveVariable(program, type, i, nameBuffer);
                    setVariableLocation(gles, program, variable);
                    setVariableStaticOffset(gles, program, variable);
                    addShaderVariable(variable);
                }
            }
        }
    }

    /**
     * Returns ShaderVariable from variable blocks, if the block variable is used in the type.
     * 
     * @param type Shader type
     * @param index
     * @return
     */
    protected ShaderVariable getBlockVariable(VariableType type, int index) {
        ShaderVariable var = blockVariables.get(index);
        if (var != null) {
            InterfaceBlock block = interfaceBlocks[var.getBlockIndex()];
            switch (block.usage) {
                case VERTEX_SHADER:
                    return type == VariableType.UNIFORM ? var : null;
                case FRAGMENT_SHADER:
                    return type == VariableType.ATTRIBUTE ? var : null;
                case VERTEX_FRAGMENT_SHADER:
                    return var;
            }
        }
        return null;
    }

    /**
     * Fetch the GLES shader variable location (name) and set in the ShaderVariable
     * This is the name (int) value to use to access the variable in GL
     * 
     * @param gles
     * @param program
     * @param variable
     * @throws GLException
     */
    protected void setVariableLocation(GLES20Wrapper gles, int program, ShaderVariable variable)
            throws GLException {
        switch (variable.getType()) {
            case ATTRIBUTE:
                variable.setLocation(gles.glGetAttribLocation(program, variable.getName()));
                break;
            case UNIFORM:
                variable.setLocation(gles.glGetUniformLocation(program, variable.getName()));
                break;
            case UNIFORM_BLOCK:
                // Location is already set - do nothing.
                break;
        }
        if (variable.getLocation() < 0) {
            throw new GLException(VARIABLE_LOCATION_ERROR + variable.getName(), 0);
        }
    }

    /**
     * Set the variable static offset, if dynamic offset is used it is computed after all variables has been collected.
     * 
     * @param gles
     * @param program
     * @param variable
     */
    protected void setVariableStaticOffset(GLES20Wrapper gles, int program, ShaderVariable variable) {
        VariableMapping v = getMappingByName(variable);
        // If variable is null then not defined in mapping used when class is created - treat this as an error
        if (v == null) {
            throw new IllegalArgumentException("No mapping for shader variable " + variable);
        }
        variable.setOffset(v.getOffset());
    }

    /**
     * Returns the VariableMapping from list of set attributes for the shader program.
     * 
     * @param name
     * @return
     */
    public VariableMapping getMappingByName(ShaderVariable variable) {
        String name = variable.getName();
        switch (variable.getType()) {
            case ATTRIBUTE:
                for (VariableMapping vm : attributes) {
                    if (name.contentEquals(vm.getName())) {
                        return vm;
                    }
                }
                break;
            case UNIFORM:
            case UNIFORM_BLOCK:
                for (VariableMapping vm : sourceUniforms) {
                    if (name.contentEquals(vm.getName())) {
                        return vm;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Not implemented for " + variable.getType());
        }
        return null;
    }

    /**
     * Links the specified vertex and fragment shaders to the specified program.
     * 
     * @param gles GLES20 platform specific wrapper.
     * @param program
     * @param shaderNames
     * @param commonVertexShaders List with shared vertex shaders or null
     * @throws GLException If the program could not be linked with the shaders.
     */
    public void linkProgram(GLES20Wrapper gles, int program, int[] shaderNames, ArrayList<Integer> commonVertexShaders)
            throws GLException {
        if (commonVertexShaders != null) {
            for (Integer shader : commonVertexShaders) {
                gles.glAttachShader(program, shader);
            }
        }
        for (int name : shaderNames) {
            gles.glAttachShader(program, name);
        }
        gles.glLinkProgram(program);
        SimpleLogger.d(getClass(), gles.glGetProgramInfoLog(program));
        GLUtils.handleError(gles, LINK_PROGRAM_ERROR);
    }

    private void logShaderSources(GLES20Wrapper gles, ArrayList<Integer> commonVertexShaders, int[] shaderNames) {
        SimpleLogger.d(getClass(), "Common vertex shaders:");
        if (commonVertexShaders != null) {
            for (Integer shader : commonVertexShaders) {
                SimpleLogger.d(getClass(), gles.glGetShaderSource(shader));
            }
        }
        // It could be an exception before shader names are allocated
        if (shaderNames != null) {
            int index = 1;
            for (int name : shaderNames) {
                SimpleLogger.d(getClass(), "Shader source for shader " + index++ + " : " + toString());
                SimpleLogger.d(getClass(), gles.glGetShaderSource(name));

            }
        }
    }

    /**
     * Creates the shader name, attaches the source and compiles the shader.
     * 
     * @param gles
     * @param source
     * @param library true if this is not the main shader
     * @return The created shader
     * @throws GLException If there is an error setting or compiling shader source.
     */
    public int compileShader(GLES20Wrapper gles, ShaderSource source, boolean library) throws GLException {
        int shader = gles.glCreateShader(source.type);
        if (shader == 0) {
            throw new GLException(CREATE_SHADER_ERROR, GLES20.GL_NO_ERROR);
        }
        if (commonVertexShaders == null) {
            source.versionedSource += getCommonSources(source.type);
        }
        compileShader(gles, source, shader, library);
        // } catch (IOException e) {
        // switch (type) {
        // case GLES20.GL_VERTEX_SHADER:
        // throw new RuntimeException("Could not load vertex shader: " + sourceName);
        // case GLES31.GL_FRAGMENT_SHADER:
        // throw new RuntimeException("Could not load fragment shader: " + sourceName);
        // case GLES31.GL_COMPUTE_SHADER:
        // throw new RuntimeException("Could not load compute shader: " + sourceName);
        // default:
        // throw new RuntimeException("Could not load shader: " + sourceName);
        // }
        return shader;
    }

    /**
     * Compiles the shader from the specified inputstream, the inputstream is not closed after reading.
     * It is up to the caller to close the stream.
     * The GL version will be appended to the source, calling {@link GLESWrapper#getShaderVersion()}
     * 
     * @param gles GLES20 platform specific wrapper.
     * @param source The shader source
     * @param shader OpenGL object to compile the shader to.
     * @param libray true if this is a library function for shader
     * @throws GLException If there is an error setting or compiling shader source.
     */
    public void compileShader(GLES20Wrapper gles, ShaderSource source, int shader, boolean library) throws GLException {
        try {
            if (source.versionedSource == null) {
                throw new IllegalArgumentException("Shader source is null for " + source.getFullSourceName());
            }
            gles.glShaderSource(shader, source.versionedSource);
            GLUtils.handleError(gles, SHADER_SOURCE_ERROR + source.getFullSourceName());
            gles.glCompileShader(shader);
            GLUtils.handleError(gles, COMPILE_SHADER_ERROR + source.getFullSourceName());
            checkCompileStatus(gles, source, shader);
        } catch (GLException e) {
            SimpleLogger.d(getClass(), e.getMessage() + " from source:" + System.lineSeparator());
            SimpleLogger.d(getClass(), source.versionedSource);
            throw e;
        }
    }

    /**
     * Checks the compile status of the specified shader program - if shader is not successfully compiled an exception
     * is thrown.
     * 
     * @param gles GLES20 platform specific wrapper.
     * @param source
     * @param shader
     * @throws GLException
     */
    public void checkCompileStatus(GLES20Wrapper gles, ShaderSource source, int shader) throws GLException {
        IntBuffer compileStatus = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        gles.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus);
        if (compileStatus.get(0) != GLES20.GL_TRUE) {
            throw new GLException(
                    COMPILE_STATUS_ERROR + source.getFullSourceName() + " : " + compileStatus.get(0) + "\n"
                            + gles.glGetShaderInfoLog(shader),
                    GLES20.GL_FALSE);
        }
    }

    /**
     * Checks the link status of the specified program - if link status returns false then an exception is thrown.
     * 
     * @param gles
     * @param program The program to check link status on
     * @throws GLException
     */
    public void checkLinkStatus(GLES20Wrapper gles, int program) throws GLException {
        int[] linkStatus = new int[1];
        gles.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            throw new GLException(LINK_PROGRAM_ERROR, GLES20.GL_FALSE);
        }
    }

    /**
     * Validates the program - only call this in debug mode.
     * Set uniform and texture data before calling this method.
     * 
     * @param gles
     */
    public void validateProgram(GLES20Wrapper gles) {
        gles.glValidateProgram(program);
        String result = gles.glGetProgramInfoLog(program);
        int[] status = new int[1];
        gles.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            SimpleLogger.d(getClass(), "Could not validate program\n");
            SimpleLogger.d(getClass(), result);
            throw new IllegalArgumentException("Could not validate program:\n" + result);
        } else {
            SimpleLogger.d(getClass(), "Program " + program + " validated OK.");
        }
    }

    /**
     * Returns the program object for this shader program.
     * 
     * @return The program object.
     */
    public int getProgram() {
        return program;
    }

    /**
     * Returns the uniform data, this shall be mapped to GL by the program.
     * 
     * @return
     */
    public float[] getUniformData() {
        return uniforms;
    }

    /**
     * Returns the shader variable for the {@link VariableMapping}
     * Use this to map attributes/uniforms to variables.
     * 
     * @param variable
     * @return The ShaderVariable for the variable, or null if not used in source.
     * @throws IllegalArgumentException If shader variables are null, the program has probably not been created.
     */
    public ShaderVariable getShaderVariable(VariableMapping variable) {
        if (shaderVariables == null) {
            throw new IllegalArgumentException(NULL_VARIABLES_ERROR);
        }
        return shaderVariables[variable.getIndex()];
    }

    /**
     * Utility method to return the name of a shader variable, this will remove unwanted characters such as array
     * declaration or '.' field access eg 'struct.field' will become 'struct'
     * 
     * @param nameBuffer
     * @param nameLength
     * @return Name of variable, without array declaration.
     */
    public String getVariableName(byte[] nameBuffer, int nameLength) {
        String name = StringUtils.createString(nameBuffer, 0, nameLength);
        if (name.endsWith("]")) {
            int end = name.indexOf("[");
            name = name.substring(0, end);
        }
        int dot = name.indexOf(".");
        if (dot == -1) {
            return name;
        }
        if (dot == 0) {
            return name.substring(1);
        }
        return name.substring(0, dot);
    }

    /**
     * Stores the shader variable in this program, if variable is of unmapped type, for instance Sampler, then it is
     * skipped. Also skip variables that are defined in code but not used in shader.
     * Variables are stored in an array using the VariableMapping index
     * 
     * @param variable
     * @throws IllegalArgumentException If shader variables are null, the program has probably not been created,
     * or if a variable has no mapping in the code.
     */
    protected void addShaderVariable(ShaderVariable variable) {
        if (shaderVariables == null) {
            throw new IllegalArgumentException(NULL_VARIABLES_ERROR);
        }
        // If variable type is is unMappedTypes then skip, for instance texture
        if (unMappedTypes.contains(variable.getDataType())) {
            return;
        }
        try {
            VariableMapping vm = getMappingByName(variable);
            // TODO Offset is set dynamically when dynamicMapShaderOffset() is called - create a setting so that
            // it is possible to toggle between the two modes.
            // variable.setOffset(vm.getOffset());
            shaderVariables[vm.getIndex()] = variable;

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Variable has no mapping to shader variable (ie used in shader but not defined in program "
                            + getClass().getSimpleName() + ") : "
                            + variable.getName());
        }
    }

    /**
     * Utility method to create the vertex and shader program using the specified shader names.
     * The shaders will be loaded, compiled and linked.
     * Vertex shader, fragment shader and program objects will be created.
     * If program compiles succesfully then the program info is fetched.
     * 
     * @param gles
     * @param sources Name of shaders to load, compile and link
     * @throws GLException If program could not be compiled and linked
     */
    protected void createProgram(GLES20Wrapper gles, ShaderSource[] sources) throws GLException {
        SimpleLogger.d(getClass(), "Creating program for: " + sources.length + " shaders");
        try {
            loadShaderSources(gles, sources);
            if (shaders != Shaders.COMPUTE && commonVertexShaders == null && commonVertexSources == null) {
                createCommonVertexShaders(gles, sources);
            }
            shaderNames = new int[sources.length];
            program = gles.glCreateProgram();
            for (int shaderIndex = 0; shaderIndex < sources.length; shaderIndex++) {
                SimpleLogger.d(getClass(),
                        "Compiling " + sources[shaderIndex].getFullSourceName());
                shaderNames[shaderIndex] = compileShader(gles, sources[shaderIndex], false);

            }
            linkProgram(gles, program, shaderNames, commonVertexShaders);
            checkLinkStatus(gles, program);
            fetchProgramInfo(gles);
            bindAttributeNames(gles);
            uniforms = createUniformArray();
            createSamplerStorage();
            setSamplers();
        } catch (GLException e) {
            logShaderSources(gles, commonVertexShaders, shaderNames);
            throw e;
        } catch (IOException e) {
            throw new GLException(e.toString(), -1);
        }
    }

    /**
     * Sets the uniform data into the block
     * 
     * @param gles
     * @param block
     * @param variable
     * @param offset
     * @throws GLException
     */
    protected void setUniformBlock(GLES20Wrapper gles, InterfaceBlock block, ShaderVariable variable)
            throws GLException {
    }

    /**
     * Sets one of more float uniforms for the specified variable, supports VEC2, VEC3, VEC4 and MAT2, MAT3, MAT4 types
     * 
     * @param gles
     * @param uniforms The uniform data
     * @param variable Shader variable to set uniform data for, datatype and size is read.
     * @param offset Offset into uniform array where data starts.
     * @throws GLException If there is an error setting a uniform to GL
     */
    protected final void setUniform(GLES20Wrapper gles, float[] uniforms, ShaderVariable variable)
            throws GLException {
        int offset = variable.getOffset();
        switch (variable.getDataType()) {
            case GLES20.GL_FLOAT:
                gles.glUniform1fv(variable.getLocation(), variable.getSize(), uniforms, offset);
                break;
            case GLES20.GL_FLOAT_VEC2:
                gles.glUniform2fv(variable.getLocation(), variable.getSize(), uniforms, offset);
                break;
            case GLES20.GL_FLOAT_VEC3:
                gles.glUniform3fv(variable.getLocation(), variable.getSize(), uniforms, offset);
                break;
            case GLES20.GL_FLOAT_VEC4:
                gles.glUniform4fv(variable.getLocation(), variable.getSize(), uniforms, offset);
                break;
            case GLES20.GL_FLOAT_MAT2:
                gles.glUniformMatrix2fv(variable.getLocation(), variable.getSize(), false, uniforms, offset);
                break;
            case GLES20.GL_FLOAT_MAT3:
                gles.glUniformMatrix3fv(variable.getLocation(), variable.getSize(), false, uniforms, offset);
                break;
            case GLES20.GL_FLOAT_MAT4:
                gles.glUniformMatrix4fv(variable.getLocation(), variable.getSize(), false, uniforms, offset);
                break;
            case GLES20.GL_SAMPLER_2D:
                gles.glUniform1iv(variable.getLocation(), variable.getSize(), samplers, offset);
                break;
            case GLES30.GL_SAMPLER_2D_SHADOW:
                gles.glUniform1iv(variable.getLocation(), variable.getSize(), samplers, offset);
                break;
            default:
                throw new IllegalArgumentException("Not implemented for dataType: " + variable.getDataType());
        }
        GLUtils.handleError(gles, "setUniform(), dataType: " + variable.getDataType());

    }

    /**
     * Returns the size of all the shader variables of the specified type, either ATTRIBUTE or UNIFORM
     * EXCLUDING the size of Sampler2D variables.
     * For uniforms this corresponds to the total size buffer size needed - the size of Sampler2D variables.
     * For attributes this corresponds to the total buffer size needed, normally attribute data is put in
     * dynamic and static buffers.
     * 
     * @param variables
     * @param type
     * @param index BufferIndex to the buffer that the variables belong to, or null
     * @return Total size, in floats, of all defined shader variables of the specified type
     */
    protected int getVariableSize(ShaderVariable[] variables, VariableType type) {
        int size = 0;
        for (ShaderVariable v : variables) {
            if (v != null && v.getType() == type && v.getDataType() != GLES20.GL_SAMPLER_2D) {
                size += v.getSizeInFloats();
            }
        }
        return alignVariableSize(size, type);
    }

    /**
     * returns the size, in basic machine units, of all active variables in the block.
     * 
     * @param block
     * @return
     */
    protected int getVariableSize(InterfaceBlock block) {
        int size = 0;
        for (int index : block.indices) {
            ShaderVariable variable = this.blockVariables.get(index);
            size += variable.getSizeInBytes();
        }
        return size;
    }

    /**
     * Align the size of the variables (per vertex) of the type in the buffer with index, override in
     * subclasses if for instance
     * Attributes shall be aligned to a specific size, eg vec4
     * 
     * @param size The packed size
     * @param type
     * @return The aligned size of variables per vertex.
     */
    protected int alignVariableSize(int size, VariableType type) {
        return size;
    }

    /**
     * Returns the size of Sampler2D variables
     * 
     * @param variables
     * @return
     */
    protected int getSamplerSize(ShaderVariable[] variables) {
        int size = 0;
        for (ShaderVariable v : variables) {
            if (v != null && v.getType() == VariableType.UNIFORM)
                switch (v.getDataType()) {
                    case GLES20.GL_SAMPLER_2D:
                    case GLES20.GL_SAMPLER_CUBE:
                    case GLES30.GL_SAMPLER_2D_SHADOW:
                    case GLES30.GL_SAMPLER_2D_ARRAY:
                    case GLES30.GL_SAMPLER_2D_ARRAY_SHADOW:
                    case GLES30.GL_SAMPLER_CUBE_SHADOW:
                    case GLES30.GL_SAMPLER_3D:
                        size += v.getSizeInFloats();
                }
        }
        return size;

    }

    /**
     * Returns a list with samplers from array of ShaderVariables
     * 
     * @param variables
     * @return
     */
    protected ArrayList<ShaderVariable> getSamplers(ShaderVariable[] variables) {
        ArrayList<ShaderVariable> samplers = new ArrayList<>();
        for (ShaderVariable v : variables) {
            if (v != null && v.getType() == VariableType.UNIFORM)
                switch (v.getDataType()) {
                    case GLES20.GL_SAMPLER_2D:
                    case GLES20.GL_SAMPLER_CUBE:
                    case GLES30.GL_SAMPLER_2D_SHADOW:
                    case GLES30.GL_SAMPLER_2D_ARRAY:
                    case GLES30.GL_SAMPLER_2D_ARRAY_SHADOW:
                    case GLES30.GL_SAMPLER_CUBE_SHADOW:
                    case GLES30.GL_SAMPLER_3D:
                        samplers.add(v);
                }
        }
        return samplers;
    }

    /**
     * Returns the size of shader variable that are mapped to the specified buffer index.
     * Use this to fetch the size per attribute for the different buffers.
     * 
     * @param variables
     * @param index
     * @return
     */
    protected int getVariableSize(ShaderVariable[] variables, BufferIndex index) {
        int size = 0;
        for (ShaderVariable v : variables) {
            if (v != null) {
                VariableMapping vm = getMappingByName(v);
                if (vm.getBufferIndex() == index) {
                    size += v.getSizeInFloats();
                }
            }
        }
        return size;
    }

    /**
     * 
     * Sets the data for the uniform matrices needed by the program - the default implementation will set the modelview
     * and projection matrices. Will NOT set uniforms to GL, only update the uniform array store
     * 
     * @param matrices Source matrices to set to uniform data array.
     * @param mesh
     */
    public void setUniformMatrices(float[][] matrices, Mesh mesh) {
        // Refresh the uniform matrixes - default is modelview and projection
        System.arraycopy(matrices[0], 0, uniforms,
                shaderVariables[CommonShaderVariables.uMVMatrix.index].getOffset(),
                Matrix.MATRIX_ELEMENTS);
        System.arraycopy(matrices[1], 0, uniforms,
                shaderVariables[CommonShaderVariables.uProjectionMatrix.index].getOffset(),
                Matrix.MATRIX_ELEMENTS);
    }

    /**
     * Gets the shader program specific uniform data, storing in in the uniformData array.
     * Subclasses shall set any uniform data needed - but not matrices which is set in
     * {@link #setUniformMatrices(float[][], Mesh)}
     * 
     * @param destinationUniforms
     * @param mesh
     */
    public abstract void setUniformData(float[] destinationUniform, Mesh mesh);

    /**
     * Sets UV fraction for the tiled texture + number of frames in x.
     * Use this for programs that use tiled texture behavior.
     * 
     * @param texture
     * @param uniforms Will store 1 / tilewidth, 1 / tilewidth, tilewidth, beginning at offset
     * @param variable The shader variable
     * @param offset Offset into destination where fraction is set
     */
    protected void setTextureUniforms(TiledTexture2D texture, float[] uniforms, ShaderVariable variable) {
        if (texture.getWidth() == 0 || texture.getHeight() == 0) {
            SimpleLogger.d(getClass(), "ERROR! Texture size is 0: " + texture.getWidth() + ", " + texture.getHeight());
        }
        int offset = variable.getOffset();
        uniforms[offset++] = (((float) texture.getWidth()) / texture.getTileWidth()) / (texture.getWidth());
        uniforms[offset++] = (((float) texture.getHeight()) / texture.getTileHeight()) / (texture.getHeight());
        uniforms[offset++] = texture.getTileWidth();
    }

    /**
     * Sets the screensize to uniform storage
     * 
     * @param uniforms
     * @param uniformScreenSize
     */
    protected void setScreenSize(float[] uniforms, ShaderVariable uniformScreenSize) {
        if (uniformScreenSize != null) {
            int screenSizeOffset = uniformScreenSize.getOffset();
            uniforms[screenSizeOffset++] = Window.getInstance().getWidth();
            uniforms[screenSizeOffset++] = Window.getInstance().getHeight();
        }
    }

    /**
     * Sets the data related to texture uniforms in the uniform float storage
     * 
     * @param uniforms
     * @param texture
     */
    protected void setTextureUniforms(float[] uniforms, Texture2D texture) {
        if (texture.getTextureType() == TextureType.TiledTexture2D) {
            setTextureUniforms((TiledTexture2D) texture, uniforms,
                    shaderVariables[CommonShaderVariables.uTextureData.index]);
        }
    }

    /**
     * Sets the ambient light color in uniform data
     * 
     * @param uniforms
     * @param uniformAmbient
     * @param material
     */
    protected void setAmbient(float[] uniforms, ShaderVariable uniformAmbient, float[] ambient) {
        int offset = uniformAmbient.getOffset();
        uniforms[offset++] = ambient[0];
        uniforms[offset++] = ambient[1];
        uniforms[offset++] = ambient[2];
        uniforms[offset++] = ambient[3];
    }

    /**
     * Returns the key value for this shader program, this is the classname and possible name of shader used.
     * This method is used by {@link AssetManager} when programs are compiled and stored.
     * 
     * @return Key value for this shader program.
     */
    public String getKey() {
        return getClass().getSimpleName() + function.getShadingString() + function.getCategoryString();
    }

    /**
     * Returns the shading that this program supports
     * 
     * @return
     */
    public Shading getShading() {
        return function.shading;
    }

    /**
     * Sets a reference to an array with float values for uniform samplers
     * 
     * @param samplers Sampler (texture unit) values
     * 
     */
    private void setSamplers(int[] samplers) {
        this.samplers = samplers;
    }

    /**
     * Creates the buffer to hold the block variable data
     * 
     * @param block The block to create the buffer for
     * @param size The size, in bytes to allocate.
     */
    protected BlockBuffer createBlockBuffer(InterfaceBlock block, int size) {
        // Size is in bytes, align to floats
        FloatBlockBuffer fbb = new FloatBlockBuffer(block, size >>> 2);
        return fbb;
    }

    /**
     * Sets the texture units to use for each sampler, default behavior is to start at unit 0 and increase for each
     * sampler.
     */
    protected void setSamplers() {
        ArrayList<ShaderVariable> samplersList = getSamplers(shaderVariables);
        for (int i = 0; i < samplersList.size(); i++) {
            samplers[i] = samplersList.get(i).getOffset();
        }
    }

    @Override
    public String toString() {
        return shaders + " : " + function.getShaderSourceName();
    }

    /**
     * Creates the common vertex shaders that can be used to share functions between shaders.
     * If platform supports multiple shader sources the common programs can be found in {@link #commonVertexShaders},
     * otherwise the source for common code can be found in {@link #commonVertexSources}
     * 
     * @param gles
     * @param sources The shader sources
     * @throws GLException
     */
    private void createCommonVertexShaders(GLES20Wrapper gles, ShaderSource[] sources) throws GLException, IOException {
        String[] common = new String[] { PROGRAM_DIRECTORY + COMMON_VERTEX_SHADER };
        ShaderSource[] commonSources = new ShaderSource[common.length];
        for (int i = 0; i < commonSources.length; i++) {
            commonSources[i] = new ShaderSource(common[i], sources[0].getSourceNameVersion(), sources[0].type);
        }
        loadShaderSources(gles, commonSources);
        if (gles.getInfo().hasExtensionSupport(GLES_EXTENSIONS.separate_shader_objects) && !appendCommonShaders) {
            SimpleLogger.d(getClass(), "Support for separate shader objects, compiling common vertex sources.");
            // Compile into shader names and link
            commonVertexShaders = new ArrayList<>();
            for (ShaderSource source : commonSources) {
                // Make sure sources and common shader have same version.
                if (sources[0].getVersionNumber() != source.getVersionNumber()) {
                    throw new IllegalArgumentException(
                            "Shader source version not same for shader source and common shader source: " +
                                    sources[0].getVersionNumber() + " vs " + source.getVersionNumber());
                }
                commonVertexShaders.add(compileShader(gles, source, true));
            }
        } else {
            SimpleLogger.d(getClass(),
                    "No support for separate shader objects, or flag to append shaders set, adding common sources.");
            createCommonVertexSources(gles, commonSources);
        }
    }

    /**
     * Creates the common vertex shaders that can be used to share functions between shaders, as a collection of source
     * strings.
     * Use this if platform does not have support for separate shader objects - append the source in commonVertexSources
     * to shaders that needs it
     * 
     * @param gles
     * @param commonSource Array with vertex shader common sources
     * @throws IOException
     */
    public void createCommonVertexSources(GLES20Wrapper gles, ShaderSource[] commonSource) throws IOException {
        commonVertexSources = new ArrayList<>();
        for (ShaderSource source : commonSource) {
            // If source has version it must be removed since shader is not in it's own program.
            if (source.versionString != null) {
                commonVertexSources.add(source.versionedSource.substring(source.versionString.length() + 1));
            } else {
                commonVertexSources.add(source.versionedSource);
            }
        }
    }

    /**
     * Returns the common shader source for the specified type - call this when appending the common shader source
     * to a shader program.
     * 
     * @param type Shader type GL_VERTEX_SHADER, GL_FRAGMENT_SHADER, GL_COMPUTE_SHADER
     * @return
     */
    private String getCommonSources(int type) {
        // common sources can be null if Compute program
        if (commonVertexSources == null || type != GLES20.GL_VERTEX_SHADER) {
            return "";
        }
        String result = new String();
        for (String source : commonVertexSources) {
            result += source;
        }
        return result;
    }

    /**
     * Returns true if shader variables, and offsets, shall be dynamically mapped, false to keep static
     * TODO Static mapping does not work if size of declared ShaderVariable does not match with size used in shader.
     * Attribute buffers are always allocated based on used size of variables.
     * 
     * @return
     */
    protected boolean useDynamicVariables() {
        return useDynamicMapping;
    }

}
