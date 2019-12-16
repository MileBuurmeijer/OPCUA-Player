/* 
 * The MIT License
 *
 * Copyright 2019 Milé Buurmeijer <mbuurmei at netscape.net>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.server;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.EnumSet;
import java.util.Set;

import name.buurmeijermile.opcuaservices.controllableplayer.measurements.Asset;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataControllerInterface;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.MeasurementPoint;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.PointInTime;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.PointInTime.BASE_UNIT_OF_MEASURE;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.AnalogItemNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.DataItemNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.TwoStateDiscreteNode;

import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.structured.EUInformation;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespace;


public class PlayerNamespace extends ManagedNamespace {
    // class variables
    private static final String PLAYERCONTROLFOLDER = "Player-Control";
    
    // instance variables
    private final SubscriptionModel subscriptionModel;
    private final OpcUaServer server;
    private final DataControllerInterface dataController;
    private final RestrictedAccessDelegate restrictedDelegateAccess;
    private List<UaVariableNode> variableNodes = null;
    private List<Asset> assets = null;

    /**
     * The intended namespace for the OPC UA Player server.
     * @param server the OPC UA server
     * @param aDataController the back end controller that exposes its measurement points in this namespace
     * @param configuration the configuration for this namespace
     */
    public PlayerNamespace(OpcUaServer server, DataControllerInterface aDataController, PlayerConfiguration configuration) {
        
        super( server, configuration.getNamespace()); // name space from configuration
        // store parameters
        this.server = server;
        // create subscription model for this server
        this.subscriptionModel = new SubscriptionModel(server, this);

        this.variableNodes = new ArrayList<>();
        // create basic restriction access delegate to be used for all created nodes in this namespace
        this.restrictedDelegateAccess = new RestrictedAccessDelegate(identity -> {
            switch ( identity.toString()) {
                case OPCUAPlayerServer.ADMINNAME: {
                    return AccessLevel.READ_WRITE;
                }
                case OPCUAPlayerServer.USERNAME: {
                    return AccessLevel.READ_ONLY;
                }
                default: {
                    return AccessLevel.READ_ONLY;
                }
            }
        });
        // set data backend for retreiving measurements
        this.dataController = aDataController;
    }
    
    @Override
    protected void onStartup() {
        super.onStartup();
        // get the hierarchically orderd assets from back end controller
        this.assets = this.dataController.getHierarchicalAssetList();
       
        // create node list in this namespace based on the available assets in the backend controlller
        this.createUANodeList( this.assets, null);
        // add the remote control OPC UA method to this servernamespace so that the OPC UA player can be remotely controlled by OPC UA clients
        this.addRemoteControlMethodNode();
    }
    
    private String getFullDottedName( Asset anAsset) {
        List<String> nameList = new ArrayList<>();
        nameList.add( anAsset.getShortName());
        while (anAsset.getParent() != null) {
            nameList.add( "."); // add dot to the list
            // traverse up the parent branch
            anAsset = anAsset.getParent();
            nameList.add( anAsset.getShortName());
        }
        // OK we have got them but in the reverse order
        String fullDottedName = "";
        for (int i = nameList.size() -1 ; i >= 0; i--) {
            fullDottedName = fullDottedName + nameList.get(i);
        };
        return fullDottedName;
    }
    
    /**
     * Creates the UA node list for this namespace based on the assets from the backend
     * @param assets the assets with its measurement points
     */
    private void createUANodeList( List<Asset> assets, UaFolderNode parentFolder) {
        // add all assets and their measurement points to the namespace
        for ( Asset anAsset: assets) { 
            // first create folder node for the asset with this node id
            String folderName = getFullDottedName( anAsset);
            UaFolderNode assetFolder = new UaFolderNode(
                    this.getNodeContext(),
                    newNodeId( folderName),
                    newQualifiedName( anAsset.getName()),
                    LocalizedText.english( anAsset.getName())
            );
            // add node to nodes
            this.getNodeManager().addNode( assetFolder);
            // add the folder correctly in the hierarchy, check if under root/object
            if (parentFolder == null) {
                // and into the folder structure under root/objects by adding a reference to it
                assetFolder.addReference(new Reference(
                    assetFolder.getNodeId(),
                    Identifiers.Organizes,
                    Identifiers.ObjectsFolder.expanded(),
                    false
                ));
            } else { // we are no longer on the top level
                parentFolder.addOrganizes( assetFolder);
            }
            // then add all the measurement points to this asset folder node
            for (MeasurementPoint aMeasurementPoint : anAsset.getMeasurementPoints()) {
                // for each measurement point create a variable node
                // set main info for the variable node
                String name = aMeasurementPoint.getName();
                String measurementPointID = folderName + "." + aMeasurementPoint.getName();
                NodeId typeId = this.getNodeType( aMeasurementPoint);
                Set<AccessLevel> accessLevels = this.getAccessLevel( aMeasurementPoint.getAccessRight());
                // create variable node based on this info [several steps]
                DataItemNode dataItemNode = null;
                // [step 1] retrieve the unit of measure from the measurement point
                BASE_UNIT_OF_MEASURE aBaseUnitOfMeasure = aMeasurementPoint.getTheBaseUnitOfMeasure();
                // [step 2] check if it has a unit of measure and do all [steps *a] when nu UoM or else all [steps *b]
                if (aBaseUnitOfMeasure != BASE_UNIT_OF_MEASURE.NoUoM) {
                    // [step 3a] it has a UoM => create OPC UA analog item node
                    AnalogItemNode analogItemNode = 
                            new AnalogItemNode( 
                                    this.getNodeContext(), 
                                    newNodeId( measurementPointID), 
                                    newQualifiedName( name),
                                    LocalizedText.english(name),
                                    LocalizedText.english("an analog variable node"),
                                    UInteger.MIN,
                                    UInteger.MIN,
                                    null,
                                    typeId,
                                    ValueRanks.Scalar,
                                    null,
                                    Unsigned.ubyte( AccessLevel.getMask( accessLevels)),
                                    Unsigned.ubyte( AccessLevel.getMask( accessLevels)), 
                                    0.0,
                                    false
                            );
                    // [step 4a] create UoM information object
                    EUInformation euInformation = 
                            new EUInformation(
                                    this.getNamespaceUri(), 
                                    0, 
                                    LocalizedText.english(
                                            aMeasurementPoint.getTheBaseUnitOfMeasure().toString().subSequence(0, 1).toString()), 
                                    LocalizedText.english(
                                            aMeasurementPoint.getTheBaseUnitOfMeasure().toString())
                            );
                    // [step 5a] set UoM to node
                    analogItemNode.setEngineeringUnits( euInformation);
                    // set measurement range
                    analogItemNode.setEURange( new Range( 0.0, 20.0));
                    dataItemNode = analogItemNode;
                } else {
                    // [step 3b] it has no UoM => create two state discreteItemNode
                    TwoStateDiscreteNode twoStateDiscreteItemNode = 
                            new TwoStateDiscreteNode( 
                                    this.getNodeContext(), // server node map
                                    newNodeId( measurementPointID), // nodeId
                                    newQualifiedName( name), // browse name
                                    LocalizedText.english(name), // display name
                                    LocalizedText.english("a boolean variable node"), // description
                                    UInteger.MIN, // write mask
                                    UInteger.MIN, // user write mask
                                    null, // data value
                                    typeId, // data type nodeId
                                    ValueRanks.Scalar, // value rank
                                    null, // array dimensions
                                    Unsigned.ubyte(AccessLevel.getMask(AccessLevel.CurrentRead)), // access level
                                    Unsigned.ubyte(AccessLevel.getMask(AccessLevel.CurrentRead)), // user access level
                                    0.0, // minimum sampling interval
                                    false // no historizing
                            ); //
                    dataItemNode = twoStateDiscreteItemNode;
                }
                // create reference to this OPC UA varable node in the measurement point, 
                // so that the node value can be updated when the measurement points value changes
                aMeasurementPoint.setUaVariableNode( dataItemNode);
                // set the restricted access delegate of this node
                dataItemNode.setAttributeDelegate( this.restrictedDelegateAccess);
                // add to proper OPC UA structures
                this.getNodeManager().addNode( dataItemNode);
                // add reference back and forth between the current folder and this containing variable node
                assetFolder.addOrganizes( dataItemNode);
                // add this variable node to the list of variable node so it can be queried by the data backend
                this.variableNodes.add( dataItemNode);
            }
            // get this assets children
            List<Asset> children = anAsset.getChildren();
            // recursively call this same method, it will not do anything if there are no children
            createUANodeList( children, assetFolder);
        }
    }

    private NodeId getNodeType( MeasurementPoint aMeasurementPoint) {
        if (aMeasurementPoint.getTheBaseUnitOfMeasure() == BASE_UNIT_OF_MEASURE.NoUoM) {
            return Identifiers.Boolean;
        } else {
            return Identifiers.Double;
        }
    }

    private void addRemoteControlMethodNode() {
        try {
            // create a "PlayerControl" folder and add it to the node manager
            NodeId remoteControlNodeId = this.newNodeId( PLAYERCONTROLFOLDER);
            UaFolderNode remoteControlFolderNode = new UaFolderNode(
                this.getNodeContext(),
                remoteControlNodeId,
                this.newQualifiedName( PLAYERCONTROLFOLDER),
                LocalizedText.english( PLAYERCONTROLFOLDER)
            );
            // add this method node to servers node map
            this.getNodeManager().addNode( remoteControlFolderNode);
            // and into the folder structure under root/objects by adding a reference to it
            remoteControlFolderNode.addReference(new Reference(
                remoteControlFolderNode.getNodeId(),
                Identifiers.Organizes,
                Identifiers.ObjectsFolder.expanded(),
                false
            ));
            // bulld the method node
            UaMethodNode methodNode = UaMethodNode.builder( this.getNodeContext())
                .setNodeId( newNodeId( PLAYERCONTROLFOLDER + "/remote-control(x)"))
                .setBrowseName( newQualifiedName( "remote-control(x)"))
                .setDisplayName( new LocalizedText(null, "remote-control(x)"))
                .setDescription(
                    LocalizedText.english("Remote controle for this player: input '1' => Play, '5' => Pause, '6' => Stop, '7' => Endlessly loop input file"))
                .build();
            // add an invocation handler point towards the control method and the actual class that can be 'controlled'
            RemoteControlMethod remoteControlMethod = new RemoteControlMethod( methodNode, this.dataController);
            // set the method input and output properties and the created invocation handler
            methodNode.setProperty( UaMethodNode.InputArguments, remoteControlMethod.getInputArguments());
            methodNode.setProperty( UaMethodNode.OutputArguments, remoteControlMethod.getOutputArguments());
            methodNode.setInvocationHandler( remoteControlMethod);
            // set the access restriction delegate
            methodNode.setAttributeDelegate( this.restrictedDelegateAccess);
            // add the method node to the namespace
            this.getNodeManager().addNode(methodNode);
            // and add a reference to the created folder node refering to the method node   
            methodNode.addReference(new Reference(
                methodNode.getNodeId(),
                Identifiers.HasComponent,
                remoteControlFolderNode.getNodeId().expanded(),
                false
            ));
            // add in same folder a varaiable node that shows the current state
            String nodeName = "RunState";
            // create variable node
            UaVariableNode runStateVariableNode = UaVariableNode.builder( this.getNodeContext())
                .setNodeId( newNodeId( PLAYERCONTROLFOLDER + "/RunState"))
                .setBrowseName( newQualifiedName( nodeName))
                .setDisplayName(LocalizedText.english(nodeName))
                .setDescription(
                    LocalizedText.english("Run state of remotely controllable player"))
                .setDataType( Identifiers.String)
                .build();
            // make this varable node known to data backen end controller so that it can updates to the runstate into this node
            this.dataController.setRunStateUANode( runStateVariableNode);
            // add node to server map
            this.getNodeManager().addNode( runStateVariableNode);
            // add node to this player folder
            remoteControlFolderNode.addOrganizes( runStateVariableNode);
        } catch (NumberFormatException ex) {
            Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE, "number format wrong: " + ex.getMessage(), ex);
        }
    }

    public void activateSimulation() {
        // get the node we want to simulate the value of
        UaVariableNode aNode = this.variableNodes.get(1);
        // create thread to alter the value of the simulated variable node over and over
        Thread simulator = new Thread() {
            // the internal state that will change all the time
            boolean state = false;
            @Override
            public void run() {
                while (true) {
                    try {
                        // flip state
                        state = !state;
                        // set the selected node value
                        aNode.setValue( new DataValue(new Variant( state)));
                        Logger.getLogger(PlayerNamespace.class.getName()).log(Level.INFO, "Simulated value set to " + state);
                        // wait for one second
                        Thread.sleep( 1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE, "Interrupted in activate simulation", ex);
                    }
                }
            }
        };
        // start this thread
        simulator.start();
    }

    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsCreated(dataItems);
    }

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsModified(dataItems);
    }

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {
        this.subscriptionModel.onDataItemsDeleted(dataItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        this.subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

    private Set<AccessLevel> getAccessLevel(PointInTime.ACCESS_RIGHT accessRight) {
        Set<AccessLevel> resultSet = EnumSet.noneOf(AccessLevel.class);
        switch ( accessRight) {
            case Read: { 
                resultSet.add(AccessLevel.CurrentRead);
                break; 
            }
            case Write: {
                resultSet.add(AccessLevel.CurrentWrite);
                break; 
            }
            case Both: {
                resultSet.add(AccessLevel.CurrentRead);
                resultSet.add(AccessLevel.CurrentWrite);
                break; 
            }
            default: {
                Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE, "No valid accessright passed to getAccessLevel");
                break;
            }
        }
        return resultSet;
    }
}
