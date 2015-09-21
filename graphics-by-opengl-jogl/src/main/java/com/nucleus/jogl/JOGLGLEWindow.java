package com.nucleus.jogl;

import java.awt.Frame;

import com.jogamp.nativewindow.util.Dimension;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;
import com.nucleus.CoreApp;
import com.nucleus.opengl.GLESWrapper;
import com.nucleus.renderer.NucleusRenderer.RenderContextListener;

/**
 * 
 * @author Richard Sahlin
 * The JOGL abstract native window, use by subclasses to render GL content
 * The window shall drive rendering by calling {@link CoreApp#drawFrame()} on a thread that
 * has GL access. This is normally done in the {@link #display(GLAutoDrawable)} method.
 *
 */
public abstract class JOGLGLEWindow implements GLEventListener {

    private int SCREEN_ID = 0;
    private Dimension windowSize;
    private boolean undecorated = false;
    private boolean alwaysOnTop = false;
    private boolean fullscreen = false;
    private boolean mouseVisible = true;
    private boolean mouseConfined = false;
    protected volatile boolean contextCreated = false;
    protected RenderContextListener listener;
    protected GLCanvas canvas;
    protected Frame frame;
    protected GLWindow glWindow;

    protected GLESWrapper glesWrapper;
    protected CoreApp coreApp;

    public JOGLGLEWindow(int width, int height, GLProfile glProfile, RenderContextListener listener) {
        this.listener = listener;
        windowSize = new Dimension(width, height);
        createNEWTWindow(width, height, glProfile);
    }

    /**
     * Creates the JOGL display and OpenGLES
     * 
     * @param width
     * @param height
     */
    private void createNEWTWindow(int width, int height, GLProfile glProfile) {
        // Display display = NewtFactory.createDisplay(null);
        // Screen screen = NewtFactory.createScreen(display, SCREEN_ID);
        GLCapabilities glCapabilities = new GLCapabilities(glProfile);
        // Window window = NewtFactory.createWindow(glCapabilities);
        glWindow = GLWindow.create(glCapabilities);

        glWindow.setSize(windowSize.getWidth(), windowSize.getHeight());
        glWindow.setUndecorated(undecorated);
        glWindow.setAlwaysOnTop(alwaysOnTop);
        glWindow.setFullscreen(fullscreen);
        glWindow.setPointerVisible(mouseVisible);
        glWindow.confinePointer(mouseConfined);
        GLProfile.initSingleton();
        Animator animator = new Animator();
        animator.add(glWindow);
        animator.start();
    }

    private void createAWTWindow(int width, int height, GLProfile glProfile) {

        GLCapabilities caps = new GLCapabilities(glProfile);
        caps.setBackgroundOpaque(false);
        GLWindow glWindow = GLWindow.create(caps);

        frame = new java.awt.Frame("Nucleus");
        frame.setSize(width, height);
        frame.setLayout(new java.awt.BorderLayout());
        canvas = new GLCanvas();
        frame.add(canvas, java.awt.BorderLayout.CENTER);
        frame.validate();

        // GLProfile glp = GLProfile.getDefault();
        // GLCapabilities caps = new GLCapabilities(glp);
        // canvas = new GLCanvas(caps);
        // frame = new Frame("JOGL GLESWindow");
        // frame.setSize(width, height);
        // frame.add(canvas);
        // frame.validate();
        // GLProfile.initSingleton();

    }

    /**
     * Sets the CoreApp in this window.
     * This is used to drive rendering in the {@link #display(GLAutoDrawable)} method.
     * 
     * @param coreApp
     */
    public void setCoreApp(CoreApp coreApp) {
        this.coreApp = coreApp;
    }

    public void setGLEVentListener() {
        if (glWindow != null) {
            glWindow.addGLEventListener(this);
        }
        if (canvas != null) {
            canvas.addGLEventListener(this);
        }
    }

    public void setVisible(boolean visible) {
        if (glWindow != null) {
            glWindow.setVisible(visible);
        }
        if (frame != null) {
            frame.setVisible(visible);
        }

    }

    /**
     * Returns the GLESWrapper for this window - must be created by subclasses in the
     * {@link #init(com.jogamp.opengl.GLAutoDrawable)} method.
     * 
     * @return The GLESWrapper for this window, or null if {@link #init(com.jogamp.opengl.GLAutoDrawable)} has not been
     * called by the system.
     * This normally means that the window has not been made visible.
     */
    public abstract GLESWrapper getGLESWrapper();

    @Override
    public void init(GLAutoDrawable drawable) {
        contextCreated = true;
        listener.contextCreated(getWidth(), getHeight());
        drawable.getGL().setSwapInterval(1);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        System.out.println("reshape: x,y= " + x + ", " + y + " width,height= " + width + ", " + height);
        windowSize.setWidth(width);
        windowSize.setHeight(height);
    }

    /**
     * Returns the width as reported by the {@link #reshape(GLAutoDrawable, int, int, int, int)} method.
     * 
     * @return
     */
    public int getWidth() {
        return windowSize.getWidth();
    }

    /**
     * Returns the height as reported by the {@link #reshape(GLAutoDrawable, int, int, int, int)} method
     * 
     * @return
     */
    public int getHeight() {
        return windowSize.getHeight();
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        coreApp.drawFrame();
    }

}