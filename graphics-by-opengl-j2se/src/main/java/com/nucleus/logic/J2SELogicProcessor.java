package com.nucleus.logic;

import com.nucleus.logic.ActorController.State;
import com.nucleus.scene.Node;

/**
 * Logic processor implementation
 * 
 * @author Richard Sahlin
 *
 */
public class J2SELogicProcessor implements LogicProcessor {

    @Override
    public void processNode(Node node, float deltaTime) {
        if (node == null) {
            return;
        }
        if (node instanceof ActorNode) {
            ActorNode logicNode = (ActorNode) node;
            if (logicNode.getControllerState() == State.CREATED) {
                logicNode.init();
            }
            ActorContainer[] lcArray = ((ActorNode) node).getLogicContainer();
            if (lcArray != null) {
                for (ActorContainer lc : lcArray) {
                    if (lc != null) {
                        lc.process(deltaTime);
                    }
                }
            }
        }
        // Process children
        for (Node child : node.getChildren()) {
            processNode(child, deltaTime);
        }
    }

}
