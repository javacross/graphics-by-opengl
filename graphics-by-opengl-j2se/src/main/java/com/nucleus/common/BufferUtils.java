package com.nucleus.common;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import com.nucleus.SimpleLogger;

public class BufferUtils {

    /**
     * Outputs count number of values from the buffer to log - use this for debugging
     * 
     * @param buffer
     * @param count
     */
    public static void logBuffer(Buffer buffer, int count) {
        if (buffer instanceof FloatBuffer) {
            logBuffer((FloatBuffer) buffer, count);
        } else {
            throw new IllegalArgumentException("Not implemented for " + buffer.getClass().getCanonicalName());
        }
    }

    /**
     * Look for the byte array within the buffer - returns -1 if not found
     * Searches from current position to the limit.
     * Position is set to the offset of marker if found otherwise the original position is restored.
     * 
     * @param buffer
     * @param marker
     * @return Offset where marker array is found or -1
     */
    public static int findByteOffset(ByteBuffer buffer, byte[] marker) {
        int position = buffer.position();
        int loop = (buffer.limit() - position) / marker.length;
        while (loop > 0) {
            int index = 0;
            while (buffer.get() == marker[index++]) {
                if (index == marker.length) {
                    buffer.position(position);
                    return position;
                }
            }
            loop--;
            position++;
            buffer.position(position);
        }

        buffer.position(position);
        return -1;
    }

    /**
     * Outputs count number of values from the buffer to log - use this for debugging
     * 
     * @param buffer
     * @param count
     */
    public static void logBuffer(FloatBuffer buffer, int count) {
        StringBuffer sb = new StringBuffer();
        float[] storage = new float[count];
        buffer.get(storage);
        for (int i = 0; i < count; i++) {
            sb.append(Float.toString(storage[i]) + ", ");
        }
        SimpleLogger.d(BufferUtils.class, "Contents of float buffer:\n" + sb.toString());
    }

    /**
     * Returns the contents of buffer as a String, adjusting length to match position and capacity
     * 
     * @param position
     * @param length
     * @param buffer
     * @return
     */
    public static String getContentAsString(int position, int length, ByteBuffer buffer) {
        if (buffer != null) {
            int currentPos = buffer.position();
            StringBuffer result = new StringBuffer();
            length = length < buffer.capacity() - position ? length : buffer.capacity() - position;
            buffer.position(position);
            for (int i = 0; i < length; i++) {
                result.append(Integer.toString(buffer.get()) + ", ");
            }
            buffer.position(currentPos);
            return result.toString();
        }
        return "Null buffer";
    }

    /**
     * Returns the contents of buffer as a String, adjusting length to match position and capacity
     * 
     * @param position
     * @param length
     * @param buffer
     * @return
     */
    public static String getContentAsString(int position, int length, ShortBuffer buffer) {
        if (buffer != null) {
            StringBuffer result = new StringBuffer();
            length = length < buffer.capacity() - position ? length : buffer.capacity() - position;
            buffer.position(position);
            for (int i = 0; i < length; i++) {
                result.append(Integer.toString(buffer.get()) + ", ");
            }
            return result.toString();
        }
        return "Null buffer";
    }

    /**
     * Returns the contents of buffer as a String, adjusting length to match position and capacity
     * 
     * @param position
     * @param length
     * @param buffer
     * @return
     */
    public static String getContentAsString(int position, int length, FloatBuffer buffer) {
        if (buffer != null) {
            StringBuffer result = new StringBuffer();
            length = length < buffer.capacity() - position ? length : buffer.capacity() - position;
            buffer.position(position);
            for (int i = 0; i < length; i++) {
                result.append(Float.toString(buffer.get()) + ", ");
            }
            return result.toString();
        }
        return "Null buffer";
    }

    /**
     * Allocates a direct byte buffer with the specified number of bytes.
     * Ordering will be nativeOrder
     * Use this method to allocate byte buffers instead of calling java.nio.ByteBuffer direct
     * 
     * @param bytes
     * @return
     */
    public static ByteBuffer createByteBuffer(int bytes) {
        SimpleLogger.d(BufferUtils.class, "Creating byte buffer with byte size: " + bytes);
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    /**
     * Allocates a direct float buffer with the specified number of floats.
     * Ordering will be nativeOrder.
     * Use this method to allocate float buffers instead of calling java.nio.ByteBuffer direct
     * 
     * @param floats
     * @return
     */
    public static FloatBuffer createFloatBuffer(int floats) {
        SimpleLogger.d(BufferUtils.class, "Creating float buffer with float size: " + floats);
        return ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /**
     * Allocates a direct int buffer with the specified number of ints.
     * Ordering will be nativeOrder.
     * Use this method to allocate int buffers instead of calling java.nio.ByteBuffer direct
     * 
     * @param ints
     * @return
     */
    public static IntBuffer createIntBuffer(int ints) {
        SimpleLogger.d(BufferUtils.class, "Creating int buffer with int size: " + ints);
        return ByteBuffer.allocateDirect(ints * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
    }

    /**
     * Allocates a direct long buffer with the specifried number of longs
     * Ordering will be nativeorder.
     * Use this method to allocate long buffers instead of calling java.nio.LongBUffer direct
     * 
     * @param longs
     * @return
     */
    public static LongBuffer createLongBuffer(int longs) {
        SimpleLogger.d(BufferUtils.class, "Creating long buffer with int size: " + longs);
        return ByteBuffer.allocateDirect(longs * 8).order(ByteOrder.nativeOrder()).asLongBuffer();

    }

}
