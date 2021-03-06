package com.nucleus.component;

import com.nucleus.Backend;
import com.nucleus.SimpleLogger;
import com.nucleus.common.Constants;
import com.nucleus.geometry.AttributeUpdater.BufferIndex;
import com.nucleus.renderer.NucleusRenderer;
import com.nucleus.shader.VariableIndexer;
import com.nucleus.shader.VariableIndexer.Property;
import com.nucleus.texturing.Texture2D;
import com.nucleus.texturing.TextureType;
import com.nucleus.texturing.UVAtlas;
import com.nucleus.texturing.UVTexture2D;
import com.nucleus.vecmath.AxisAngle;
import com.nucleus.vecmath.Transform;

/**
 * Sprite / Quad expander this is the connection between the source (entity) buffer, that keeps track of one set of
 * variables for an on screen quad, and the destination buffer that tracks 4 set of attribute values.
 * The source and destination shall have a one to one mapping in variable offsets, eg to expand the source data it shall
 * be enough to copy it into 4 places (one for each vertex)
 * If expander is CPU based methods to set data shall update both source and destination, meaning that
 * {@link #updateAttributeData(NucleusRenderer)} only needs to copy
 * the destination data into the attribute buffer.
 *
 *
 */
public class CPUQuadExpander extends AttributeExpander {

    /**
     * The source data, for instance entity/sprite data
     * Data shall be written here and expanded into AttributeBuffer when {@link #updateAttributeData(NucleusRenderer)}
     * is called.
     */
    protected CPUComponentBuffer source;
    protected CPUComponentBuffer destination;
    protected float[] sourceData;
    protected float[] destinationData;
    protected final float[] DEFAULT_SCALE = new float[] { 1, 1, 1 };

    /**
     * Only used for uniform block no uniform block in shader
     */
    private transient float[][] uvData;
    /**
     * Used as temp entity data
     */
    private transient float[] tempData;

    private transient int sizePerVertex;

    private transient int frameOffset;
    private transient int albedoOffset;
    private transient int translateOffset;
    private transient int rotateOffset;
    private transient int scaleOffset;

    /**
     * 
     * @param texture
     * @param mapper
     * @param source
     * @param destination
     */
    public CPUQuadExpander(Texture2D texture, VariableIndexer mapper, CPUComponentBuffer source,
            CPUComponentBuffer destination) {
        super(mapper, destination, 4);
        this.source = source;
        this.sourceData = source.data;
        this.destination = destination;
        this.destinationData = destination.data;
        this.sizePerVertex = mapper.getSizePerVertex(BufferIndex.ATTRIBUTES.index);
        this.frameOffset = mapper.getOffset(Property.FRAME.getLocation());
        this.albedoOffset = mapper.getOffset(Property.ALBEDO.getLocation());
        this.translateOffset = mapper.getOffset(Property.TRANSLATE.getLocation());
        this.rotateOffset = mapper.getOffset(Property.ROTATE.getLocation());
        this.scaleOffset = mapper.getOffset(Property.SCALE.getLocation());
        if (texture.getTextureType() == TextureType.UVTexture2D) {
            // TODO - how to sync this with the creation of uniform block buffer in shader program?
            if (Backend.getInstance().getVersion().major < 3) {
                SimpleLogger.d(getClass(), "GLES version < 3 - not using uniform block buffers for UV data");
                copyUVAtlas(((UVTexture2D) texture).getUVAtlas());
                tempData = new float[this.sizePerVertex];
            }
        }
    }

    private void copyUVAtlas(UVAtlas uvAtlas) {
        int frames = uvAtlas.getFrameCount();
        uvData = new float[frames][];
        for (int i = 0; i < frames; i++) {
            uvData[i] = new float[8];
            uvAtlas.getUVFrame(i, uvData[i], 0);
        }
    }

    @Override
    public void updateAttributeData(NucleusRenderer renderer) {
        // Use special case if shader does not support uniform block where the frames are.
        if (tempData != null) {
            int uvIndex = 0;
            int frame;
            buffer.setBufferPosition(0);
            for (int i = 0; i < source.getEntityCount(); i++) {
                uvIndex = 0;
                frame = (int) tempData[frameOffset];
                // data.get(i, tempData);
                for (int expand = 0; expand < multiplier; expand++) {
                    // Store the UV for the vertex
                    tempData[frameOffset] = uvData[frame][uvIndex++];
                    tempData[frameOffset + 1] = uvData[frame][uvIndex++];
                    buffer.put(tempData);
                }
            }
        } else {
            // Move from data to buffer
            buffer.setBufferPosition(0);
            buffer.put(destinationData);
        }
    }

    /**
     * Sets the color
     * 
     * @param quad
     * @param color
     */
    public void setColor(int quad, float[] color) {
        int index = quad * source.sizePerEntity + albedoOffset;
        int destIndex = quad * destination.sizePerEntity + albedoOffset;
        sourceData[index++] = color[0];
        sourceData[index++] = color[1];
        sourceData[index++] = color[2];
        sourceData[index] = color[3];
        for (int i = 0; i < 4; i++) {
            destinationData[destIndex++] = color[0];
            destinationData[destIndex++] = color[1];
            destinationData[destIndex++] = color[2];
            destinationData[destIndex] = color[3];
            destIndex += sizePerVertex - 3;
        }
    }

    /**
     * Sets the frame number
     * 
     * @param quad
     * @param frame
     */
    public final void setFrame(int quad, int frame) {
        if (frameOffset != Constants.NO_VALUE) {
            int index = quad * source.sizePerEntity + frameOffset;
            sourceData[index] = frame;
            index = quad * destination.sizePerEntity + frameOffset;
            destinationData[index] = frame;
            index += sizePerVertex;
            destinationData[index] = frame;
            index += sizePerVertex;
            destinationData[index] = frame;
            index += sizePerVertex;
            destinationData[index] = frame;
        }
    }

    /**
     * Sets the transform in the source and destination buffer buffer.
     * This method will call {@link #expandQuadData(int)} after setting the source data
     * 
     * @param quad
     * @param transform
     */
    public final void setData(int quad, Transform transform) {
        if (transform.isMatrixMode()) {
            throw new IllegalArgumentException(
                    "Updating expander data using transform in matrix mode is not supported");
        }
        float[] translate = transform.getTranslate();
        int start = quad * source.sizePerEntity;
        if (translate != null) {
            int index = start + translateOffset;
            for (int i = 0; i < 4; i++) {
                sourceData[index++] = translate[0];
                sourceData[index++] = translate[1];
                sourceData[index] = translate[2];
                index += sizePerVertex - 2;
            }
        }
        if (transform.getAxisAngle() != null) {
            int index = start + rotateOffset;
            float[] axisangle = transform.getAxisAngle().getValues();
            float angle = axisangle[AxisAngle.ANGLE];
            for (int i = 0; i < 4; i++) {
                sourceData[index++] = axisangle[AxisAngle.X] * angle;
                sourceData[index++] = axisangle[AxisAngle.Y] * angle;
                sourceData[index] = axisangle[AxisAngle.Z] * angle;
                index += sizePerVertex - 2;
            }
        }
        float[] scale = transform.getScale();
        // Must set scale
        if (scale == null) {
            scale = DEFAULT_SCALE;
        }
        int index = start + scaleOffset;
        for (int i = 0; i < 4; i++) {
            sourceData[index++] = scale[0];
            sourceData[index++] = scale[1];
            sourceData[index] = scale[2];
            index += sizePerVertex - 2;
        }
        expandQuadData(quad);
    }

    /**
     * Sets the data for the specified quad, the caller shall make sure the data is indexed using the appropriate
     * mapper. The data will be expanded into quad.
     * Use this method when initializing - not performance optimized.
     * 
     * @param quad
     * @param quadOffset
     * @param data
     * @param offset Offset into data where values are read
     * @param length Number of values to set
     */
    public final void setData(int quad, int quadOffset, float[] data, int offset, int length) {
        int index = quad * source.sizePerEntity + quadOffset;
        int quadIndex = quad * destination.sizePerEntity + quadOffset;
        float val;
        for (int i = 0; i < length; i++) {
            val = data[offset++];
            sourceData[index++] = val;
            if (i + quadOffset < sizePerVertex) {
                destinationData[quadIndex] = val;
                destinationData[quadIndex + sizePerVertex] = val;
                destinationData[quadIndex + sizePerVertex * 2] = val;
                destinationData[quadIndex + sizePerVertex * 3] = val;
                quadIndex++;
            }
        }
    }

    /**
     * Expands the data for one quad, from source into destination for all 4 vertices of the quad.
     * Not optimized - only use this when setting a limited number of quads or outside game loop.
     * 
     * @param quad
     * @param srcData
     * @param offset
     * @return
     */
    protected final void expandQuadData(int quad) {
        int index = quad * destination.sizePerEntity;
        int sourceIndex = quad * source.sizePerEntity;
        System.arraycopy(sourceData, sourceIndex, destinationData, index, sizePerVertex);
        index += sizePerVertex;
        System.arraycopy(sourceData, sourceIndex, destinationData, index, sizePerVertex);
        index += sizePerVertex;
        System.arraycopy(sourceData, sourceIndex, destinationData, index, sizePerVertex);
        index += sizePerVertex;
        System.arraycopy(sourceData, sourceIndex, destinationData, index, sizePerVertex);
        index += sizePerVertex;
    }

}
