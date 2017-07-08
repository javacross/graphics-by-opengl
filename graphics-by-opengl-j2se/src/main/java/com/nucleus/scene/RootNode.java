package com.nucleus.scene;

import java.util.ArrayList;
import java.util.Hashtable;

import com.google.gson.annotations.SerializedName;
import com.nucleus.renderer.NucleusRenderer.Layer;

/**
 * Starting point of a nodetree, the root has a collection of nodes the each represent a scene.
 * There shall only be one rootnode at any given time, the root node defines the possible resource that may be
 * needed for the tree.
 * A root node shall be self contained, reference textures and large data sets.
 * This class can be serialized using GSON
 * All childnodes added must have the same RootNode reference
 * 
 * @author Richard Sahlin
 *
 */
public abstract class RootNode {

    /**
     * Pre defined ids that can be used for scenes and make it more convenient find a scene.
     * 
     */
    public enum Scenes {
        credit(),
        select(),
        settings(),
        game(),
        about(),
    }

    @SerializedName("scene")
    private Node scene;

    /**
     * Set to true when node is added or removed
     */
    transient private boolean updated = false;
    transient private Hashtable<NodeIterator, ArrayList<Node>> iterators = new Hashtable();
    /**
     * When a node is rendered it is added to this list, this is for the current frame - will change as nodes are
     * rendered
     */
    transient private ArrayList<Node> renderNodeList = new ArrayList<>();
    /**
     * List of last displayed frame rendered nodes - this is the currently visible nodes.
     */
    transient private ArrayList<Node> visibleNodeList = new ArrayList<>();

    /**
     * Sets the root scene node, this rootnode shall be rootnode of all added children.
     * 
     * @param node
     * @throws IllegalArgumentException If a node has already been set with a call to this method, or rootnode is not
     * set in scene
     */
    public void setScene(Node scene) {
        if (this.scene != null) {
            throw new IllegalArgumentException("Scene node has already been set");
        }
        if (scene.getRootNode() == null) {
            throw new IllegalArgumentException("Node does not have a root node: " + scene);
        }
        this.scene = scene;
        scene.parent = null;
    }

    /**
     * Creates a new instance of RootNode, implement in RootNode subclasses to return the implementation instance.
     * 
     * @return A new instance of RootNode implementation
     */
    public abstract RootNode createInstance();

    public Node getScene() {
        return scene;
    }

    /**
     * Returns the first matching viewnode, this is a conveniance method to find node with view
     * 
     * @param layer Which layer the ViewNode to return belongs to.
     * @return The viewnode or null if not found
     */
    public LayerNode getViewNode(Layer layer) {
        if (scene == null) {
            return null;
        }
        return getViewNode(layer, scene);
    }

    private LayerNode getViewNode(Layer layer, Node node) {
        return getViewNode(layer, node.getChildren());
    }

    private LayerNode getViewNode(Layer layer, ArrayList<Node> children) {
        for (Node n : children) {
            if (n.getType().equals(NodeType.layernode.name())) {
                if (((LayerNode) n).getLayer() == layer) {
                    return (LayerNode) n;
                }
            }
        }
        // Search through children recusively
        for (Node n : children) {
            LayerNode view = getViewNode(layer, n);
            if (view != null) {
                return view;
            }
        }
        return null;
    }
    
    /**
     * Call this when a node is added or removed to the nodetree.
     */
    public void updated() {
        updated = true;
    }

    /**
     * Returns a list with all nodes, starting with the scene node, listed using the specified node iterator.
     * If scene has been updated, node added or removed, then the nodelist is recreated, otherwise the same list is
     * returned.
     * 
     * @param iterator
     * @return List with nodes listed by the iterator - do NOT modify this list, it will be returned in subsequent calls
     * unless the scene
     * tree has been altered (node added or removed)
     */
    public ArrayList<Node> getNodes(NodeIterator iterator) {
        ArrayList<Node> result = iterators.get(iterator);
        if (result == null || updated) {
            result = createNodeList(iterator, result);
        }
        return result;
    }

    private ArrayList<Node> createNodeList(NodeIterator iterator, ArrayList<Node> result) {
        if (result == null) {
            result = new ArrayList<>();
            iterators.put(iterator, result);
        }
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    /**
     * Adds node to current list of currently rendered nodes
     * 
     * @param node
     */
    public void addRenderedNode(Node node) {
        renderNodeList.add(node);
    }

    /**
     * Returns a list with previous frames rendered nodes.
     * 
     * @return
     */
    public ArrayList<Node> getRenderedNodes() {
        return visibleNodeList;
    }

    /**
     * Swaps the list of rendernodes - the current nodelist will be the visible nodelist and the current nodelist is
     * cleared.
     * Call this method when buffers are swapped so that next call to {@link #getVisibleNodeList()} will return list of
     * visible nodes
     */
    public void swapNodeList() {
        visibleNodeList = renderNodeList;
        renderNodeList = null;
        renderNodeList = new ArrayList<>();
    }


    /**
     * Returns list of visible nodes, this is the list of nodes that have been rendered and is currently visible.
     * DO NOT MODIFY THIS LIST
     * 
     * @return
     */
    public ArrayList<Node> getVisibleNodeList() {
        return visibleNodeList;
    }

}
