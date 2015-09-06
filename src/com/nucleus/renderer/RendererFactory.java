package com.nucleus.renderer;

import com.nucleus.opengl.GLES20Wrapper;
import com.nucleus.renderer.NucleusRenderer.MatrixEngine;
import com.nucleus.texturing.ImageFactory;

/**
 * Creates an implementation of the nucleus renderer interface.
 * 
 * @author Richard Sahlin
 *
 */
public class RendererFactory {

    private final static String WRONG_GLES = "GLES is wrong class: ";
    private final static String NOT_IMPLEMENTED_ERROR = "Not implemented support for: ";

    /**
     * The supported renderers
     * 
     * @author Richard Sahlin
     *
     */
    public enum Renderers {
        GLES20();

        private Renderers() {
        };
    }

    /**
     * Creates a new nucleus renderer with the specified version.
     * Currently only supports GLES20
     * 
     * @param version
     * @param gles The GLESWrapper for the specified version, {@link GLES20Wrapper} for GLES20
     * @param imageFactory The image factory to be used
     * @param matrixEngine The matrix engine to be used
     * @return New instance of nucleus renderer
     * @throws IllegalArgumentException If gles is not matching for the renderer version.
     */
    public static NucleusRenderer getRenderer(Renderers version, Object gles, ImageFactory imageFactory,
            MatrixEngine matrixEngine) {
        NucleusRenderer renderer = null;
        switch (version) {
        case GLES20:
            if (!(gles instanceof GLES20Wrapper)) {
                throw new IllegalArgumentException(WRONG_GLES + gles.getClass().getName());
            }
            renderer = new BaseRenderer((GLES20Wrapper) gles, imageFactory, matrixEngine);
            break;
        default:
            throw new IllegalArgumentException(NOT_IMPLEMENTED_ERROR + version);
        }
        return renderer;
    }
}
