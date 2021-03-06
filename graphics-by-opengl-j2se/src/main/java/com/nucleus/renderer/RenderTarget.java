package com.nucleus.renderer;

import java.util.ArrayList;

import com.google.gson.annotations.SerializedName;
import com.nucleus.common.Constants;
import com.nucleus.io.BaseReference;
import com.nucleus.texturing.BufferImage.ImageFormat;
import com.nucleus.texturing.Texture2D;

/**
 * Represents a render target, currently only supports window framebuffer
 * Future versions will add support for changing render target.
 * This class can be serialized using GSON
 */
public class RenderTarget extends BaseReference {

    private static final String TARGET = "target";
    private static final String ATTACHEMENTS = "attachements";

    public enum Attachement {
        COLOR(0),
        DEPTH(1),
        STENCIL(2);

        public final int index;

        private Attachement(int index) {
            this.index = index;
        }
    }

    public enum Target {
        FRAMEBUFFER(0),
        RENDERBUFFER(1),
        TEXTURE(2);

        public final int index;

        private Target(int index) {
            this.index = index;
        }

    }

    public static class AttachementData {

        private Attachement attachement;
        /**
         * The image format for the attachement, this depends on what the {@link Attachement} is.
         * 
         */
        private ImageFormat format;
        /**
         * The scale of the target, compared to Window size, in x and y axis
         */
        private float[] scale;
        /**
         * Init (clear) value for the target
         */
        private float[] init;
        transient private int[] size;
        /**
         * Used if target is TEXTURE
         */
        transient private Texture2D texture;

        public AttachementData() {

        }

        /**
         * Returns the attachement point
         * 
         * @return
         */
        public Attachement getAttachement() {
            return attachement;
        }

        public ImageFormat getFormat() {
            return format;
        }

        /**
         * Returns the value to init the target to after creation, or null if not set
         * 
         * @return
         */
        public float[] getInitValue() {
            return init;
        }

        /**
         * Returns the x and y axis scale, if set.
         * 
         * @return X and Y axis scale, or null if not set
         */
        public float[] getScale() {
            return scale;
        }

        /**
         * Returns the texture object, if the target TEXTURE
         * 
         * @return
         */
        public Texture2D getTexture() {
            return texture;
        }

        public void setTexture(Texture2D texture) {
            this.texture = texture;
        }

        /**
         * Calculates the size of the rendertarget, based on Window size and scale
         */
        private void calculateSize() {
            size = new int[] { (int) (Window.getInstance().getWidth() * scale[0]),
                    (int) (Window.getInstance().getHeight() * scale[1]) };
        }

        public int[] getSize() {
            if (size == null) {
                calculateSize();
            }
            return size;
        }

    }

    @SerializedName(TARGET)
    private Target target;

    @SerializedName(ATTACHEMENTS)
    private ArrayList<AttachementData> attachements;

    /**
     * Name of the destination texture/renderbuffer/framebuffer
     */
    transient private int targetName = Constants.NO_VALUE;
    /**
     * Name of the framebuffer object the target is attached to
     */
    transient private int framebufferName = Constants.NO_VALUE;

    public RenderTarget() {
    }

    public RenderTarget(Target target, ArrayList<AttachementData> attachements) {
        this.target = target;
        this.attachements = attachements;
    }

    /**
     * Returns the rendertarget
     * 
     * @return
     */
    public Target getTarget() {
        return target;
    }

    /**
     * Returns the target buffer name,
     * name (id) of the destination texture/renderbuffer/framebuffer
     * 
     * @return
     */
    public int getTargetName() {
        return targetName;
    }

    /**
     * Sets the name of the buffer object
     * name (id) of the destination texture/renderbuffer/framebuffer
     * 
     * @param name Generated buffer/texture object name
     */
    public void setTargetName(int name) {
        this.targetName = name;
    }

    /**
     * Sets the name (id) of the framebuffer object the target is attached to
     * 
     * @param framebufferName Name (id) of the framebuffer object that the target is attached to.
     */
    public void setFramebufferName(int framebufferName) {
        this.framebufferName = framebufferName;
    }

    /**
     * Returns the name (id) of the framebuffer object the target is attached to
     * 
     * @return
     */
    public int getFramebufferName() {
        return framebufferName;
    }

    /**
     * Returns the attachement data for the attachement, or null if not set
     * 
     * @param attachement
     * @return
     */
    public AttachementData getAttachement(Attachement attachement) {
        if (attachements == null) {
            return null;
        }
        for (AttachementData ad : attachements) {
            if (ad.attachement == attachement) {
                return ad;
            }
        }
        return null;
    }

    public ArrayList<AttachementData> getAttachements() {
        return attachements;
    }

    /**
     * Returns the id of the attachement data (point), this is the name of the attachement point + rendertarget id
     * Eg, use this to store and fetch texture
     * 
     * @param attachement
     * @return Id of attachement point
     */
    public String getAttachementId(AttachementData attachement) {
        return attachement.getAttachement().name() + getId();
    }

}
