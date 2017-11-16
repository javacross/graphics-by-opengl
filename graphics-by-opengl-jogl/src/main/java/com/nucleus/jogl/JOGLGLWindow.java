package com.nucleus.jogl;

import java.awt.Frame;

import com.jogamp.nativewindow.util.Dimension;
import com.jogamp.nativewindow.util.InsetsImmutable;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.event.WindowUpdateEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;
import com.nucleus.CoreApp;
import com.nucleus.CoreApp.CoreAppStarter;
import com.nucleus.J2SEWindow;
import com.nucleus.SimpleLogger;
import com.nucleus.WindowListener;
import com.nucleus.mmi.PointerData;
import com.nucleus.mmi.PointerData.PointerAction;
import com.nucleus.opengl.GLESWrapper;

/**
 * 
 * @author Richard Sahlin
 * The JOGL abstract native window, use by subclasses to render GL content
 * The window shall drive rendering by calling {@link CoreApp#drawFrame()} on a thread that
 * has GL access. This is normally done in the {@link #display(GLAutoDrawable)} method.
 *
 */
public abstract class JOGLGLWindow extends J2SEWindow
        implements GLEventListener, MouseListener, com.jogamp.newt.event.WindowListener, KeyListener {

    /**
     * A zoom on the wheel equals 1 / 5 screen height
     */
    private final static float ZOOM_FACTOR = 100f;

    private Dimension windowSize;
    private boolean undecorated = false;
    private boolean alwaysOnTop = false;
    private boolean fullscreen = false;
    private boolean mouseVisible = true;
    private boolean mouseConfined = false;
    private int swapInterval = 1;
    protected volatile boolean contextCreated = false;
    protected GLCanvas canvas;
    protected Frame frame;
    protected GLWindow glWindow;
    WindowListener windowListener;

    /**
     * Creates a new JOGL window with the specified {@link CoreAppStarter} and swapinterval
     * 
     * @param width
     * @param height
     * @param undecorated
     * @param fullscreen
     * @param glProfile
     * @param coreAppStarter
     * @param swapInterval
     * @throws IllegalArgumentException If coreAppStarter is null
     */
    public JOGLGLWindow(int width, int height, boolean undecorated, boolean fullscreen, GLProfile glProfile,
            CoreApp.CoreAppStarter coreAppStarter, int swapInterval) {
        super(coreAppStarter, width, height);
        this.swapInterval = swapInterval;
        this.undecorated = undecorated;
        this.fullscreen = fullscreen;
        create(width, height, glProfile, coreAppStarter);
    }

    private void create(int width, int height, GLProfile glProfile, CoreApp.CoreAppStarter coreAppStarter) {
        if (coreAppStarter == null) {
            throw new IllegalArgumentException("CoreAppStarter is null");
        }
        this.coreAppStarter = coreAppStarter;
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
        glCapabilities.setSampleBuffers(true);
        glCapabilities.setNumSamples(8);
        glCapabilities.setAlphaBits(8);
        // Window window = NewtFactory.createWindow(glCapabilities);
        glWindow = GLWindow.create(glCapabilities);
        glWindow.setUndecorated(undecorated);
        InsetsImmutable insets = glWindow.getInsets();
        glWindow.setSize(undecorated ? windowSize.getWidth() : windowSize.getWidth() + insets.getTotalWidth(),
                undecorated ? windowSize.getHeight() : windowSize.getHeight() + insets.getTotalHeight());
        glWindow.setAlwaysOnTop(alwaysOnTop);
        glWindow.setFullscreen(fullscreen);
        glWindow.setPointerVisible(mouseVisible);
        glWindow.confinePointer(mouseConfined);
        glWindow.addMouseListener(this);
        glWindow.addWindowListener(this);
        glWindow.addKeyListener(this);
        GLProfile.initSingleton();
        Animator animator = new Animator();
        animator.add(glWindow);
        animator.start();
    }

    private void createAWTWindow(int width, int height, GLProfile glProfile) {
        GLCapabilities caps = new GLCapabilities(glProfile);
        caps.setBackgroundOpaque(false);
        glWindow = GLWindow.create(caps);

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

    public void setTitle(String title) {
        if (frame != null) {
            frame.setTitle(title);
        }
        if (glWindow != null) {
            glWindow.setTitle(title);
        }
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

    @Override
    public void init(GLAutoDrawable drawable) {
        internalCreateCoreApp(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        drawable.swapBuffers();
        drawable.getGL().setSwapInterval(swapInterval);
        internalContextCreated(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        System.out.println("reshape: x,y= " + x + ", " + y + " width,height= " + width + ", " + height);
        windowSize.setWidth(width);
        windowSize.setHeight(height);
        if (windowListener != null) {
            windowListener.resize(x, y, width, height);
        }
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

    protected void handleMouseEvent(MouseEvent e, PointerAction action) {
        int[] xpos = e.getAllX();
        int[] ypos = e.getAllY();
        int count = e.getPointerCount();
        for (int i = 0; i < count; i++) {
            handleMouseEvent(xpos[i], ypos[i], e.getPointerId(i), e.getWhen(), action);
        }
    }

    protected void handleKeyEvent(KeyEvent event) {
        switch (event.getEventType()) {
        case KeyEvent.EVENT_KEY_PRESSED:
            switch (event.getKeyCode()) {
            case KeyEvent.VK_ESCAPE:
                backPressed();
            }
            break;
        case KeyEvent.EVENT_KEY_RELEASED:
        }
    }

    /**
     * Sets the windowlistener to get callbacks when the window has changed.
     * 
     * @param listener
     */
    public void setWindowListener(WindowListener listener) {
        this.windowListener = listener;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mouseExited(MouseEvent e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void mousePressed(MouseEvent e) {
        handleMouseEvent(e, PointerAction.DOWN);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        handleMouseEvent(e, PointerAction.UP);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        handleMouseEvent(e, PointerAction.MOVE);
    }

    @Override
    public void mouseWheelMoved(MouseEvent e) {
        float factor = ZOOM_FACTOR;
        coreApp.getInputProcessor().pointerEvent(PointerAction.ZOOM, e.getWhen(), PointerData.POINTER_1, new float[] {
                e.getRotation()[1] * factor, e.getRotation()[1] * factor });
    }

    @Override
    public void windowResized(WindowEvent e) {
    }

    @Override
    public void windowMoved(WindowEvent e) {
    }

    @Override
    public void windowDestroyNotify(WindowEvent e) {
        windowListener.windowClosed();
    }

    @Override
    public void windowDestroyed(WindowEvent e) {
    }

    @Override
    public void windowGainedFocus(WindowEvent e) {
    }

    @Override
    public void windowLostFocus(WindowEvent e) {
    }

    @Override
    public void windowRepaint(WindowUpdateEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        handleKeyEvent(e);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        handleKeyEvent(e);
    }

    private void backPressed() {
        SimpleLogger.d(getClass(), "backPressed()");
        if (fullscreen) {
            fullscreen = false;
            glWindow.setFullscreen(false);
            glWindow.setPosition(glWindow.getWidth()/ 2, glWindow.getHeight() / 2);
        } else {
            if (coreApp.onBackPressed()) {
                coreApp.setDestroyFlag();
                glWindow.destroy();
                System.exit(0);
            }
        }
    }

}