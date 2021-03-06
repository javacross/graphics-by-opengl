package com.nucleus.mmi;

import com.nucleus.mmi.core.RawPointerInput;

/**
 * Data for a low level (raw) pointer input - this is actions that is detected from an input device such as
 * mouse or touch.
 */
public class Pointer {

    public final static float DOWN_PRESSURE = 1f;
    /**
     * A zoom on the wheel equals a fraction of screen
     */
    public final static float ZOOM_FACTOR = 0.05f;

    /**
     * The type of event, ie what the source of the action is
     *
     */
    public enum Type {
        STYLUS(0),
        ERASER(1),
        MOUSE(2),
        FINGER(3);

        /**
         * Can be used to offset value into array - must start at 0 and increase
         */
        public final int index;

        private Type(int index) {
            this.index = index;
        }

    }

    /**
     * The different pointer actions
     *
     */
    public enum PointerAction {
        /**
         * Pointer down action, this means that the pointer is in a 'pressed' state.
         * If the following action is MOVE it shall be regarded as a pressed motion event, ie touch move or
         * mouse button pressed move.
         */
        DOWN(0),
        /**
         * Pointer up action, this means that the pointer is in an 'not-pressed' state.
         * If the following action is MOVE it shall be regarded as move without press, ie hover move or mouse move
         * (without button pressed)
         */
        UP(1),
        /**
         * Pointer move action, keep track of the UP/DOWN action to know if this is a pressed move (eg touch move).
         */
        MOVE(2),
        /**
         * Zoom action from the input device - note that not all input devices can support this.
         */
        ZOOM(3);

        private int action;

        private PointerAction(int action) {
            this.action = action;
        }

    }

    /**
     * The index for the first pointer
     */
    public final static int POINTER_1 = 0;
    /**
     * The index for the second pointer
     */
    public final static int POINTER_2 = 1;
    /**
     * The index for the third pointer
     */
    public final static int POINTER_3 = 2;
    /**
     * The index for the fourth pointer
     */
    public final static int POINTER_4 = 3;
    /**
     * The index for the fifth pointer
     */
    public final static int POINTER_5 = 4;
    /**
     * The current pointer data
     */
    public final float[] data;
    /**
     * Pointer index, 0 and up
     */
    public final int pointer;

    public final long timeStamp;

    /**
     * Touch pressure, if reported.
     */
    public final float pressure;

    /**
     * The pointer action, ie what the type of input action, DOWN, MOVE or UP
     */
    public final PointerAction action;

    /**
     * The pointer type
     */
    public final Pointer.Type type;

    /**
     * Creates a new pointerdata with pointer index and x,y pos
     * 
     * @param action The pointer action, DOWN, MOVE or UP
     * @param type The type that the pointerevent originates from
     * @param timestamp The time of the event, in milliseconds.
     * @param pointer Pointer index, eg the touch finger index.
     * @param data Array with x and y position.
     * @param pressure Touch pressure
     */
    protected Pointer(PointerAction action, Type type, long timestamp, int pointer, float[] data, float pressure) {
        this.action = action;
        this.type = type;
        this.timeStamp = timestamp;
        this.pointer = pointer;
        this.pressure = pressure;
        this.data = new float[] { data[RawPointerInput.X], data[RawPointerInput.Y] };
    }

    /**
     * Creates a new pointerdata with pointer index and x,y pos
     * 
     * @param action The pointer action, DOWN, MOVE or UP
     * @param type The type that the pointerevent originates from
     * @param timestamp The time of the event, in milliseconds.
     * @param pointer Pointer index, eg the touch finger index.
     * @param x x value
     * @param y y value
     * @param pressure Touch pressure
     */
    protected Pointer(PointerAction action, Type type, long timestamp, int pointer, float x, float y,
            float pressure) {
        this.action = action;
        this.type = type;
        this.timeStamp = timestamp;
        this.pointer = pointer;
        this.pressure = pressure;
        this.data = new float[] { x, y };
    }

}
