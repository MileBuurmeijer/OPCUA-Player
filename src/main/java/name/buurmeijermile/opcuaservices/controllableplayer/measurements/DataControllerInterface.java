/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.util.List;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public interface DataControllerInterface {
    
    public void setRunStateUANode( UaVariableNode aUaVariableNode);
    
    public Integer doRemotePlayerControl( Integer command);
    
    public List<Asset> getHierarchicalAssetList();
    public boolean isJsonConfig();
    public List<OpcNodeConfig> getOpcNodeConfigs();
    
    public void startUp();
    public void setNamespace(Object namespace);
    public void updateNodeValue(org.eclipse.milo.opcua.stack.core.types.builtin.NodeId nodeId, String valueString, java.time.LocalDateTime timestamp, java.time.ZoneOffset zoneOffset);
    
}
