package com.nucleus.gltf;

import org.junit.Assert;
import org.junit.Test;

import com.nucleus.BaseTestCase;
import com.nucleus.scene.gltf.Buffer;
import com.nucleus.scene.gltf.BufferView;
import com.nucleus.scene.gltf.BufferView.Target;

public class BufferViewTest extends BaseTestCase {

    private static final int BYTE_OFFSET = 4;
    private static final int BYTE_SIZE = 100;
    private static final int BYTE_STRIDE = 10;
    private static final Target TARGET = Target.ARRAY_BUFFER;

    @Test
    public void testConstructor() {

        BufferView bv = new BufferView(new Buffer(BYTE_SIZE), BYTE_OFFSET, BYTE_STRIDE, TARGET);
        Assert.assertTrue(bv.getByteOffset() == BYTE_OFFSET);
        Assert.assertTrue(bv.getByteLength() == BYTE_SIZE);
        Assert.assertTrue(bv.getByteStride() == BYTE_STRIDE);
        Assert.assertTrue(bv.getTarget() == TARGET);
    }

}