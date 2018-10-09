package com.nucleus.shader;

import com.nucleus.geometry.AttributeUpdater;
import com.nucleus.opengl.GLES20Wrapper;
import com.nucleus.opengl.GLException;
import com.nucleus.renderer.Pass;
import com.nucleus.texturing.Texture2D.Shading;

public abstract class ShadowPassProgram extends ShaderProgram {

    /**
     * The program that should be used to render the object casting shadow
     */
    protected ShaderProgram objectProgram;

    /**
     * TODO Look into the shader programs using this constructor - maybe they can be unified?
     * 
     * @param objectProgram The program for rendering the object casting shadow
     * @param categorizer
     * @param shaders
     */
    public ShadowPassProgram(ShaderProgram objectProgram, Categorizer categorizer, ProgramType shaders) {
        super(categorizer, shaders);
        setIndexer(objectProgram.variableIndexer);
        this.objectProgram = objectProgram;
    }

    @Override
    public ShaderProgram getProgram(GLES20Wrapper gles, Pass pass, Shading shading) {
        throw new IllegalArgumentException("Not valid");
    }

    @Override
    public void updateAttributes(GLES20Wrapper gles, AttributeUpdater mesh) throws GLException {
        objectProgram.updateAttributes(gles, mesh);
    }

    @Override
    public void updateUniforms(GLES20Wrapper gles, float[][] matrices) throws GLException {
        /**
         * Currently calls ShaderProgram#setUniformData() in order to set necessary data from the program int
         * uniform storage.
         * This could potentially break the shadow program if needed uniform data is set in some other method.
         * TODO - Make sure that the interface declares and mandates that uniform data shall be set in #setUniformData()
         */
        objectProgram.updateUniformData(uniforms);
        super.updateUniforms(gles, matrices);
    }

    @Override
    protected String getShaderSourceName(int shaderType) {
        /**
         * Shadow programs may need to call the objectProgram to get the sources, this is known if categorizer returns
         * null.
         * returns null.
         */
        String name = function.getShaderSourceName(shaderType);
        if (name == null) {
            name = objectProgram.getShaderSourceName(shaderType);
        }
        return name;
    }

}