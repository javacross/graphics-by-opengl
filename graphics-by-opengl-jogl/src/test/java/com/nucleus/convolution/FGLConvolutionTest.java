package com.nucleus.convolution;

import org.junit.Test;

import com.nucleus.camera.ViewFrustum;
import com.nucleus.geometry.Mesh;
import com.nucleus.io.ExternalReference;
import com.nucleus.jogl.NucleusApplication;
import com.nucleus.mmi.MMIEventListener;
import com.nucleus.mmi.MMIPointerEvent;
import com.nucleus.opengl.GLESWrapper.GLES20;
import com.nucleus.opengl.GLESWrapper.Renderers;
import com.nucleus.opengl.GLException;
import com.nucleus.renderer.NucleusRenderer;
import com.nucleus.renderer.NucleusRenderer.FrameListener;
import com.nucleus.renderer.NucleusRenderer.Layer;
import com.nucleus.renderer.RenderSettings;
import com.nucleus.resource.ResourceBias.RESOLUTION;
import com.nucleus.scene.BaseRootNode;
import com.nucleus.scene.ViewNode;
import com.nucleus.shader.ShaderVariable;
import com.nucleus.texturing.Convolution;
import com.nucleus.texturing.TexParameter;
import com.nucleus.texturing.Texture2D;
import com.nucleus.texturing.TextureFactory;
import com.nucleus.texturing.TextureParameter;

public class FGLConvolutionTest extends NucleusApplication implements FrameListener,
        MMIEventListener {

    private final static float[] kernel1 = new float[] { 1, 2, 1, 2, 4, 2, 1, 2, 1 };
    private final static float[] kernel2 = new float[] { -1, -1, -1, -1, 8, -1, -1, -1, -1 };
    private final static float[] kernel3 = new float[] { 0, -1, 0, -1, 5, -1, 0, -1, 0 };
    private final static boolean[] absNormalize = new boolean[] { false, true, false };

    private float[][] kernel = new float[][] { kernel1, kernel2, kernel3 };
    private float[] normalizedKernel = new float[9];

    private float factor = 1f;
    private int kernelIndex = 2;
    Mesh mesh;
    int counter = 0;
    long start = 0;
    private ShaderVariable uKernel;

    public FGLConvolutionTest() {
        super(new String[] {}, Renderers.GLES20, null);
    }

    public static void main(String[] args) {
        FGLConvolutionTest main = new FGLConvolutionTest();
        main.createCoreWindows(Renderers.GLES20);
    }

    @Test
    public void testGLConvolution() throws GLException {
        createCoreWindows(Renderers.GLES20);
    }

    @Override
    public void createCoreWindows(Renderers version) {
        windowWidth = 1920;
        windowHeight = 1080;
        swapInterval = 0;
        super.createCoreWindows(version);
    }

    @Override
    public void createCoreApp(int width, int height) {
        super.createCoreApp(width, height);
        NucleusRenderer renderer = getRenderer();
        RenderSettings rs = renderer.getRenderSettings();
        rs.setCullFace(GLES20.GL_NONE);
        rs.setDepthFunc(GLES20.GL_NONE);
        coreApp.getInputProcessor().addMMIListener(this);

        mesh = new Mesh();
        ConvolutionProgram c = new ConvolutionProgram();
        uKernel = c.getShaderVariable(ConvolutionProgram.VARIABLES.uKernel);
        c.createProgram(renderer.getGLES());
        ViewNode node = new ViewNode();
        node.setLayer(Layer.SCENE);
        ViewFrustum vf = new ViewFrustum();
        vf.setOrthoProjection(-0.5f, 0.5f, 0.5f, -0.5f, 0, 10);
        node.setViewFrustum(vf);
        TextureParameter texParam = new TextureParameter();
        texParam.setValues(new TexParameter[] { TexParameter.NEAREST, TexParameter.NEAREST, TexParameter.CLAMP,
                TexParameter.CLAMP });
        Texture2D tex = TextureFactory.createTexture(renderer.getGLES(), renderer.getImageFactory(),
                "texture", new ExternalReference("assets/testimage.jpg"), RESOLUTION.HD, texParam, 1);
        c.buildMesh(mesh, tex, 1f, 1f, 0, kernel[kernelIndex]);
        node.addMesh(mesh);
        BaseRootNode root = new BaseRootNode();
        root.setScene(node);
        coreApp.setRootNode(root);
        renderer.addFrameListener(this);
    }

    @Override
    public void processFrame(float deltaTime) {
        factor += deltaTime * 0.2f;
        if (factor > 2.5) {
            factor = 0.3f;
        }
    }

    @Override
    public void updateGLData() {

        if (start == 0) {
            start = System.currentTimeMillis();
        }
        counter++;
        if (counter > 500) {
            long end = System.currentTimeMillis();
            String fillrateStr = "";
            int size = windowWidth * windowHeight;
            int fillrate = (size * counter) / (int) (end - start);
            fillrateStr = " " + Float.toString(fillrate / 1000) + ", mpixels/s";
            window.setTitle(fillrateStr);
            start = System.currentTimeMillis();
            counter = 0;
        }
        Convolution.normalize(kernel[kernelIndex], normalizedKernel, absNormalize[kernelIndex], factor);
        System.arraycopy(normalizedKernel, 0, mesh.getUniforms(), uKernel.getOffset(),
                normalizedKernel.length);

    }

    @Override
    public void inputEvent(MMIPointerEvent event) {

        switch (event.getAction()) {
        case ACTIVE:
            kernelIndex++;
            if (kernelIndex >= kernel.length) {
                kernelIndex = 0;
            }
            break;
        case INACTIVE:
        case ZOOM:
        case MOVE:
        }

    }
}
