package com.nucleus.scene;

import java.util.ArrayList;

import com.nucleus.SimpleLogger;
import com.nucleus.common.Constants;
import com.nucleus.event.EventManager;
import com.nucleus.mmi.MMIEventListener;
import com.nucleus.mmi.MMIPointerEvent;
import com.nucleus.mmi.ObjectInputListener;
import com.nucleus.mmi.core.InputProcessor;
import com.nucleus.properties.Property;
import com.nucleus.scene.Node.State;

/**
 * Handles pointer input checking on nodes
 * Takes {@link MMIEventListener} events and checks the registered node tree for pointer hits.
 * This class must be registred to {@link InputProcessor} for it to get mmi event callbacks.
 */
public class J2SENodeInputListener implements NodeInputListener, MMIEventListener {

    private final RootNode root;
    private final ArrayList<Node> visibleNodes = new ArrayList<>();
    private int nodeId = Constants.NO_VALUE;

    private float[] down = new float[2];

    public J2SENodeInputListener(RootNode root) {
        this.root = root;
    }

    @Override
    public boolean onInputEvent(ArrayList<Node> nodes, MMIPointerEvent event) {
        int count = nodes.size() - 1;
        Node node = null;
        for (int i = count; i >= 0; i--) {
            node = nodes.get(i);
            switch (node.getState()) {
                case ON:
                case ACTOR:
                    if (onPointerEvent(node, event)) {
                        return true;
                    }
                    break;
                default:
                    SimpleLogger.d(getClass(), "Not handling input, node in state: " + node.getState());
                    // Do nothing
            }
        }
        return false;
    }

    /**
     * Checks this node for pointer event.
     * This default implementation will check bounds and check {@link #ONCLICK} property and send to
     * {@link EventManager#post(Node, String, String)} if defined.
     * TODO Instead of transforming the bounds the inverse matrix should be used.
     * Will stop when a node is in state {@link State#OFF} or {@link State#RENDER}
     * 
     * @param event
     * @return True if the input event was consumed, false otherwise.
     */
    protected boolean onPointerEvent(Node node, MMIPointerEvent event) {
        float[] position = event.getPointerData().getCurrentPosition();
        if (position == null) {
            return false;
        }
        if (node.isInside(position)) {
            if (node instanceof MMIEventListener) {
                ((MMIEventListener) node).onInputEvent(event);
            }
            ObjectInputListener listener = root.getObjectInputListener();
            switch (event.getAction()) {
                case ACTIVE:
                    down[0] = position[0];
                    down[1] = position[1];
                    SimpleLogger.d(getClass(), "HIT: " + node);
                    String onclick = node.getProperty(ONCLICK, null);
                    if (onclick != null) {
                        Property p = Property.create(onclick);
                        if (p != null) {
                            EventManager.getInstance().post(node, p.getKey(), p.getValue());
                        } else {
                            SimpleLogger.d(getClass(), "Invalid property for node " + node.getId() + " : " + onclick);
                        }
                    }
                    if (listener != null) {
                        listener.onInputEvent(node, event.getPointerData().getCurrent());
                    }
                    return true;
                case INACTIVE:
                    if (listener != null) {
                        listener.onInputEvent(node, event.getPointerData().getCurrent());
                    }
                    return true;
                case MOVE:
                    if (listener != null) {
                        listener.onDrag(node, event.getPointerData());
                    }
                    return true;
                case ZOOM:
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    /**
     * Checks children for pointer event, calling {@link #onPointerEvent(MMIPointerEvent)} recursively and stopping when
     * a child returns true.
     * 
     * @param event
     * @return true if one of the children has a match for the pointer event, false otherwise
     */
    protected boolean checkChildren(Node node, MMIPointerEvent event) {
        State state = node.getState();
        if (state == State.ON || state == State.ACTOR) {
            for (Node n : node.getChildren()) {
                if (onPointerEvent(n, event)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onInputEvent(MMIPointerEvent event) {
        nodeId = root.getVisibleNodeList(visibleNodes, nodeId);
        onInputEvent(visibleNodes, event);
    }

}
