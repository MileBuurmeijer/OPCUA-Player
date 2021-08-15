/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.client;

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class Sample {
    private NodeId nodeId;
    private DataValue value;
    
    public Sample( NodeId anItem, DataValue aValue) {
        this.nodeId = anItem;
        this.value = aValue;
    }
    
    public NodeId getNodeId() {
        return nodeId;
    }

    /**
     * @return the value
     */
    public DataValue getValue() {
        return value;
    }
}
