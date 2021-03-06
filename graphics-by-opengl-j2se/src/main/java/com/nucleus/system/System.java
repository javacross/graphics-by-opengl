package com.nucleus.system;

import com.nucleus.common.TypeResolver;
import com.nucleus.component.Component;
import com.nucleus.renderer.NucleusRenderer;
import com.nucleus.scene.RootNode;

/**
 * The system handling one or more components, one system shall handle all controllers of the same kind.
 * The system shall not contain the data needed to perform processing - that shall be held by the Component.
 * There shall only be one system of each kind, which can handle multiple components (instances of data)
 */
public abstract class System<T extends Component> {

    private String type;

    /**
     * Keep track if system is initialized, for instance make sure
     * {@link #initSystem(NucleusRenderer, RootNode)} not called more than once.
     */
    protected boolean initialized = false;

    /**
     * Updates the component using this system.
     * 
     * @param component The component to update
     * @param deltaTime The time lapsed since last call to process
     * @throws IllegalStateException If {@link #initComponent(NucleusRenderer, RootNode, Component)} has not been
     * called.
     */
    public abstract void process(T component, float deltaTime);

    /**
     * Initializes the system, will be called once before
     * {@link #initComponent(NucleusRenderer, RootNode, Component)}
     * is called.
     * Implementors MUST set the {@link #initialized} flag to true
     * 
     * @param renderer
     * @param root
     */
    public abstract void initSystem(NucleusRenderer renderer, RootNode root);

    /**
     * Inits the component for the system, this must be called before {@link #process(T, float)} is called.
     * 
     * @param renderer
     * @param root
     * @param component The component to be used in the system
     */
    public abstract void initComponent(NucleusRenderer renderer, RootNode root, T component);

    /**
     * Returns the type of component, this is tied to the implementing class by {@link TypeResolver}
     * 
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the system, this is normally only done when creating the system.
     * 
     * @param type
     * @return
     */
    protected void setType(String type) {
        this.type = type;
    }

    /**
     * Returns true if this system has been initialized
     * 
     * @return
     */
    public boolean isInitialized() {
        return initialized;
    }

}
