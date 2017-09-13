package com.nucleus.renderer;

import com.nucleus.camera.ViewPort;
import com.nucleus.opengl.GLESWrapper.GLES20;

/* Copyright 2015 Richard Sahlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Class that holds the runtime render settings, this is depth test, culling, cull-face, dither
 * and other options related to geometry/rasterization.
 * 
 * @author Richard Sahlin
 *
 */
public class RenderSettings {

    protected final static String INVALID_CULLFACE_STR = "Invalid cullFace:";
    protected final static String INVALID_CLEARFLAG_STR = "Invalid clearFlag:";
    protected final static String INVALID_DEPTHFUNC_STR = "Invalid depthFunc:";
    protected final static String INVALID_CLEARCOLOR_STR = "Invalid clear color array.";

    public final static int DEFAULT_DEPTHFUNC = GLES20.GL_LEQUAL;
    public final static float DEFAULT_DEPTHRANGE_NEAR = 0.000001f;
    public final static float DEFAULT_DEPTHRANGE_FAR = 1f;
    public final static float DEFAULT_CLEARDEPTH = DEFAULT_DEPTHRANGE_FAR;
    public final static int DEFAULT_CULLFACE = GLES20.GL_BACK;
    public final static int DEFAULT_CLEARFLAG = GLES20.GL_COLOR_BUFFER_BIT |
            GLES20.GL_DEPTH_BUFFER_BIT;
    public final static boolean DEFAULT_MULTISAMPLING = false;

    public final static int CHANGE_FLAG_ALL = -1; // Flag that all values should be updated
    public final static int CHANGE_FLAG_NONE = 0; // NO values should be updated.
    public final static int CHANGE_FLAG_CLEARCOLOR = 1; // Clearcolor has changed.
    public final static int CHANGE_FLAG_DEPTH = 2; // Depth related functions has changed.
    public final static int CHANGE_FLAG_CULLFACE = 4; // Cullface has changed
    public final static int CHANGE_FLAG_MULTISAMPLE = 8; // Multisample has changed
    public final static int CHANGE_FLAG_VIEWPORT = 16; // viewport has changed

    /**
     * What parameters have changed, used for some settings.
     */
    private int changeFlag = CHANGE_FLAG_ALL;

    /**
     * Should multisampling be enabled, default is true - must also be supported in the surface (EGL)
     */
    protected boolean enableMultisampling = DEFAULT_MULTISAMPLING;

    /**
     * Depth func used if depth test is enabled, default is less or equal.
     */
    protected int depthFunc = DEFAULT_DEPTHFUNC;

    /**
     * Near value for depthrange
     */
    protected float depthRangeNear = DEFAULT_DEPTHRANGE_NEAR;

    /**
     * Far value for depthrange.
     */
    protected float depthRangeFar = DEFAULT_DEPTHRANGE_FAR;

    /**
     * Depth clear value.
     */
    protected float clearDepth = DEFAULT_CLEARDEPTH;

    /**
     * If depth test is enabled display is cleared to this color at beginFrame()
     */
    protected float[] clearColor = new float[] { 0.3f, 0.3f, 0.5f, 1f };

    /**
     * Clear stencilbuffer with this value if the STENCIL_BUFFER_BIT is set in the clear flags.
     */
    protected int clearStencil = 0;

    /**
     * If culling is enabled, what faces should be culled.
     *
     */
    protected int cullFace = DEFAULT_CULLFACE;

    /**
     * This value is read in Renderer.beginFrame() and used to decide if buffer should be cleared
     * Defaults to clearing depth and color-buffer.
     */
    protected int clearFlags = DEFAULT_CLEARFLAG;

    /**
     * The viewport setting
     */
    protected ViewPort viewPort = new ViewPort();

    /**
     * Constructs a new RenderSetting with default values.
     * Depth function is LEQUAL.
     * Cull flag is CULL_BACK
     * Clear flags are COLOR_BUFFER_BIT | DEPTH_BUFFER_BIT
     *
     */
    public RenderSettings() {

    }

    /**
     * Creates a new RenderSettings class with the specified values, other values will default.
     * 
     * @param clearFlags Set to what buffers to clear between frames, values are ored together
     * ConstantValues.DEPTH_BUFFER_BIT | ConstantValues.STENCIL_BUFFER_BIT |
     * ConstantValues.COLOR_BUFFER_BIT
     * Set to ConstantValues.NONE to disable clearing.
     * @param cullFace What faces to cull, set to ConstantValues.NONE to disable culling.
     * One of:
     * ConstantValues.CULL_BACK, ConstantValues.CULL_FRONT,
     * ConstantValues.CULL_FRONT_AND_BACK or ConstantValues.NONE
     * @param depthTest Set to true to enable depth test.
     * If true depth test is set to LEQUAL, if other depth test function is requred use another constuctor.
     * @param multisampling Set to true to enable multisampling, must also be configured in EGL.
     * @throws IllegalArgumentException If clearFlags or cullFace is not valid.
     */
    public RenderSettings(int clearFlags, int cullFace, boolean depthTest, boolean multisampling) {
        setClearFunction(clearFlags);
        setCullFace(cullFace);
        this.enableMultisampling = multisampling;
        if (depthTest) {
            depthFunc = GLES20.GL_LEQUAL;
        }
        else {
            depthFunc = GLES20.GL_NONE;
        }

    }

    /**
     * Enables or disables multisampling, to be disabled the surface must also support GLES_EXTENSIONS.MULTISAMPLE_EXT
     * This is checked before multisample is enabled/disable in the renderer.
     * 
     * @param enableMultisampling
     */
    public void enableMultisampling(boolean enableMultisampling) {
        this.enableMultisampling = enableMultisampling;
        changeFlag |= CHANGE_FLAG_MULTISAMPLE;
    }

    /**
     * Sets the change flag, this can be used to clear updates to setting or to force updates.
     * 
     * @param flag CHANGE_FLAG_NONE to not update any setting or a CHANGLE_FLAG
     */
    public void setChangeFlag(int flag) {
        changeFlag = flag;
    }

    /**
     * Returns flags for what settings have changed, this is used for some settings such
     * as the clearcolor to enable faster changing of some settings.
     * 
     * @return Change flag, CHANGE_FLAG_ALL or a number of CHANGE_FLAG_XX values ored together.
     */
    public int getChangeFlag() {
        return changeFlag;
    }

    /**
     * State of the flag indicating if multisampling should be enabled or not.
     * For multisampling to work a surface with multisample buffer must be configured.
     * 
     * @return True to enable multisampling.
     */
    public boolean isMultisampling() {
        return this.enableMultisampling;
    }

    /**
     * Set the depth function value, set NONE to disable depth test.
     * 
     * @param the Depth function.
     * Valid values from ConstantValues are:
     * NONE, NEVER, LESS, EQUAL, LEQUAL, GREATER, GEQUAL, ALWAYS
     * @throws IllegalArgumentException If depthFunc is not valid
     */
    public void setDepthFunc(int depthFunc) {
        switch (depthFunc) {
        case GLES20.GL_NONE:
        case GLES20.GL_NEVER:
        case GLES20.GL_LESS:
        case GLES20.GL_EQUAL:
        case GLES20.GL_LEQUAL:
        case GLES20.GL_GREATER:
        case GLES20.GL_GEQUAL:
        case GLES20.GL_ALWAYS:
            break;
        default:
            throw new IllegalArgumentException(INVALID_DEPTHFUNC_STR);
        }
        changeFlag |= CHANGE_FLAG_DEPTH;
        this.depthFunc = depthFunc;
    }

    /**
     * Return the depth function value, this value controls what pixels
     * pass the depth test. NONE to disable depth test.
     * Valid values are: NONE, NEVER, LESS, EQUAL, LEQUAL, GREATER, GEQUAL, ALWAYS
     * 
     * @return Depth function.
     */
    public int getDepthFunc() {
        return depthFunc;
    }

    /**
     * Returns the flags that contol what buffers should be cleared between frames.
     * ConstantValues.NONE for no clearing of buffers
     * The following flags are ored together to enable clearing of multiple buffers.
     * ConstantValues.DEPTH_BUFFER_BIT to clear depth buffer between frames.
     * ConstantValues.COLOR_BUFFER_BIT to clear color buffer between frames.
     * ConstantValues.STENCIL_BUFFER_BIT to clear stencil buffer between frames.
     * 
     * @return Flags to control what buffer should be cleared between frames.
     */
    public int getClearFunction() {
        return clearFlags;
    }

    /**
     * Set the clearDepth value
     * 
     * @param clearDepth
     */
    public void setClearDepth(float clearDepth) {
        this.clearDepth = clearDepth;
        changeFlag |= CHANGE_FLAG_DEPTH;
    }

    /**
     * Return the clear depth value.
     * 
     * @return Clear depth.
     */
    public float getClearDepth() {
        return clearDepth;
    }

    /**
     * Set the value used for culling, set to NONE to disable culling.
     * Valid values are: NONE, FRONT, BACK, FRONT_AND_BACK
     * 
     * @param cullFace What faces to cull, set to ConstantValues.NONE to disable culling.
     * ConstantValues.CULL_BACK, ConstantValues.CULL_FRONT,
     * ConstantValues.CULL_FRONT_AND_BACK or ConstantValues.NONE
     * @throws IllegalArgumentException If cullface is illegal value.
     */
    public void setCullFace(int cullFace) {
        if (cullFace != GLES20.GL_BACK &&
                cullFace != GLES20.GL_FRONT &&
                cullFace != GLES20.GL_FRONT_AND_BACK &&
                cullFace != GLES20.GL_NONE) {
            throw new IllegalArgumentException(INVALID_CULLFACE_STR + cullFace);
        }

        this.cullFace = cullFace;
        changeFlag |= CHANGE_FLAG_CULLFACE;
    }

    /**
     * Return the cull mode.
     * 
     * @return Either of NONE, FRONT, BACK, FRONT_AND_BACK
     */
    public int getCullFace() {
        return cullFace;
    }

    /**
     * Set the farthest value for the depthrange.
     * 
     * @param far
     */
    public void setDepthRangeFar(float far) {
        depthRangeFar = far;
        changeFlag |= CHANGE_FLAG_DEPTH;
    }

    /**
     * Return the farthest value for the depthrange, as set by
     * setDepthRangeFar
     * 
     * @return The far depth value.
     */
    public float getDepthRangeFar() {
        return depthRangeFar;
    }

    /**
     * Set the nearest value for the depthrange.
     * 
     * @param near
     */
    public void setDepthRangeNear(float near) {
        depthRangeNear = near;
        changeFlag |= CHANGE_FLAG_DEPTH;
    }

    /**
     * Return the near depth value as set by setDepthRangeNear
     * 
     * @return The near depth value.
     */
    public float getDepthRangeNear() {
        return depthRangeNear;
    }

    /**
     * Returns a reference to the clear color component, red, green, blue, alpha in an array. Red at offset 0.
     * Do not write to these values without updating the change flag by calling {@link #setChangeFlag(int)}
     * Otherwise the new color values will not be written to gl.
     * 
     * @return Clear color components.
     */
    public float[] getClearColor() {
        return clearColor;
    }

    /**
     * Sets the value to use for clearing the stencil buffer if the STENCIL_BUFFER_BIT is set
     * in the clear flags.
     */
    public void setClearStencil(int clearStencil) {
        this.clearStencil = clearStencil;
    }

    /**
     * Fetches the value to use when clearing the stencilbuffer, if STENCIL_BUFFER_BIT is set
     * in the clear flags
     */
    public int getClearStencil() {
        return clearStencil;
    }

    /**
     * Sets the color to clear color buffer to when COLOR_BUFFER_BIT is enabled in setClearFunction
     * Array must contain at least 4 values.
     * 
     * @param clearColor Array containing RGBA values for the clear color, red at index 0,
     * alpha at index 3.
     * @throws IllegalArgumentException If clearColor is null or does not contain at least 4 values.
     */
    public void setClearColor(float[] clearColor) {
        if (clearColor == null || clearColor.length < 4) {
            throw new IllegalArgumentException(INVALID_CLEARCOLOR_STR);
        }
        this.clearColor = clearColor;
    }

    /**
     * Set the flags for the clear function, this is checked in beginFrame.
     * 
     * @param clearFlags The flags for clear function in beginFrame() method of Renderer.
     * The following flags are ored together to enable clearing of multiple buffers.
     * ConstantValues.DEPTH_BUFFER_BIT, ConstantValues.STENCIL_BUFFER_BIT,
     * ConstantValues.COLOR_BUFFER_BIT or ConstantValues.NONE
     * @throws IllegalArgumentException If clearFlags is invalid.
     */
    public void setClearFunction(int clearFlags) {
        if ((clearFlags ^ (clearFlags & (GLES20.GL_DEPTH_BUFFER_BIT |
                GLES20.GL_STENCIL_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT))) != 0) {
            throw new IllegalArgumentException(INVALID_CLEARFLAG_STR);
        }
        this.clearFlags = clearFlags;

    }

    /**
     * Sets the size, in pixels, for the screen viewport.
     * 
     * @param x
     * @param y
     * @param width
     * @param height
     */
    public void setViewPort(int x, int y, int width, int height) {
        viewPort.setViewPort(x, y, width, height);
        changeFlag |= CHANGE_FLAG_VIEWPORT;
    }

    /**
     * Returns the viewport values - DO NOT MODIFY
     * 
     * @return
     */
    protected int[] getViewPort() {
        return viewPort.getViewPort();
    }

}
