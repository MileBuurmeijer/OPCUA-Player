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

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.AnalogItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.DiscreteItemTypeNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.BaseDataVariableTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.structured.EUInformation;
import org.eclipse.milo.opcua.stack.core.types.structured.Range;
import org.eclipse.milo.opcua.sdk.server.Lifecycle;
import org.eclipse.milo.opcua.sdk.server.api.DataTypeDictionaryManager;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.TwoStateVariableTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.factories.NodeFactory;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;

public class PlayerNamespace extends ManagedNamespaceWithLifecycle {

    // class variables
    public static final String PLAYERCONTROLFOLDER = "Player-Control";
 
    // instance variables
    private final SubscriptionModel subscriptionModel;
    private final OpcUaServer server;
    private final DataControllerInterface dataController;
    private final RestrictedAccessFilter restrictedAccessFilter;
    private List<UaVariableNode> variableNodes = null;
    private List<Asset> assets = null;
    private volatile Thread eventThread;
    private volatile boolean keepPostingEvents = true;
    private DataTypeDictionaryManager dictionaryManager;

    /**
     * The intended namespace for the OPC UA Player server.
     *
     * @param server the OPC UA server
     * @param aDataController the back end controller that exposes its
     * measurement points in this namespace
     * @param configuration the configuration for this namespace
     */
    public PlayerNamespace(OpcUaServer server, DataControllerInterface aDataController, PlayerConfiguration configuration) {

        super(server, configuration.getNamespace()); // name space from configuration
        // store parameters
        this.server = server;
        // create subscription model for this server
        this.subscriptionModel = new SubscriptionModel(server, this);

        this.dictionaryManager = new DataTypeDictionaryManager(getNodeContext(), configuration.getNamespace());

        this.getLifecycleManager().addLifecycle(dictionaryManager);
        this.getLifecycleManager().addLifecycle(subscriptionModel);

        this.getLifecycleManager().addStartupTask(this::onStartup);

        this.getLifecycleManager().addLifecycle(new Lifecycle() {
            @Override
            public void startup() {
                startBogusEventNotifier();
            }

            @Override
            public void shutdown() {
                try {
                    keepPostingEvents = false;
                    eventThread.interrupt();
                    eventThread.join();
                } catch (InterruptedException ignored) {
                    // ignored
                }
            }
        });

        this.variableNodes = new ArrayList<>();
        this.restrictedAccessFilter = new RestrictedAccessFilter(identity -> {
            if ( OPCUAPlayerServer.ADMINNAME.equals(identity)) {
                return AccessLevel.READ_WRITE;
            } else {
                return AccessLevel.READ_ONLY;
            }
        });
        
        // set data backend for retreiving measurements
        this.dataController = aDataController;
    }
    
    protected void startBogusEventNotifier() {
        // do nothing
    }

    protected void onStartup() {
        // get the hierarchically orderd assets from back end controller
        this.assets = this.dataController.getHierarchicalAssetList();
        // create node list in this namespace based on the available assets in the backend controlller
        this.createUANodeList(this.assets, null);
        // add the remote control OPC UA method to this servernamespace so that the OPC UA player can be remotely controlled by OPC UA clients
        this.addRemoteControlMethodNode();
    }

    private String getFullDottedName(Asset anAsset) {
        List<String> nameList = new ArrayList<>();
        nameList.add(anAsset.getShortName());
        while (anAsset.getParent() != null) {
            nameList.add("."); // add dot to the list
            // traverse up the parent branch
            anAsset = anAsset.getParent();
            nameList.add(anAsset.getShortName());
        }
        // OK we have got them but in the reverse order
        String fullDottedName = "";
        for (int i = nameList.size() - 1; i >= 0; i--) {
            fullDottedName = fullDottedName + nameList.get(i);
        };
        return fullDottedName;
    }

    /**
     * Creates the UA node list for this namespace based on the assets from the
     * backend
     *
     * @param assets the assets with its measurement points
     */
    private void createUANodeList(List<Asset> assets, UaFolderNode parentFolder) {
        // add all assets and their measurement points to the namespace
        for (Asset anAsset : assets) {
            // first create folder node for the asset with this node id
            String folderName = getFullDottedName(anAsset);
            UaFolderNode assetFolder = new UaFolderNode(
                    this.getNodeContext(),
                    newNodeId(folderName),
                    newQualifiedName(anAsset.getName()),
                    LocalizedText.english(anAsset.getName())
            );
            // add node to nodes
            this.getNodeManager().addNode(assetFolder);
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
                parentFolder.addOrganizes(assetFolder);
            }
            // then add all the measurement points to this asset folder node
            for (MeasurementPoint aMeasurementPoint : anAsset.getMeasurementPoints()) {
                try {
                // for each measurement point create a variable node
                // set main info for the variable node
                String name = aMeasurementPoint.getName();
                String measurementPointID = folderName + "." + aMeasurementPoint.getName();
                NodeId typeId = aMeasurementPoint.getDataType();
                Set<AccessLevel> accessLevels = this.getAccessLevel(aMeasurementPoint.getAccessRight());
                // create variable node based on this info [several steps]
                BaseDataVariableTypeNode dataItemTypeNode = null;
                // [step 1] check if datatype links to analog item node
                if ( PointInTime.ANALOGNODEITEMS.contains( typeId)) {
                    // [step 2] create OPC UA analog item node
                    AnalogItemTypeNode analogItemTypeNode = (AnalogItemTypeNode) getNodeFactory().createNode(
                        newNodeId(measurementPointID),
                        Identifiers.AnalogItemType,
                        new NodeFactory.InstantiationCallback() {
                            @Override
                            public boolean includeOptionalNode(NodeId typeDefinitionId, QualifiedName browseName) {
                                return true;
                            }
                        }
                    );
                    analogItemTypeNode.setDataType( typeId);
                    analogItemTypeNode.setBrowseName( newQualifiedName(name));
                    analogItemTypeNode.setDisplayName( LocalizedText.english(name));
                    analogItemTypeNode.setDescription(LocalizedText.english("an analog variable node"));
                    // [step 3] create UoM information object
                    PointInTime.BASE_UNIT_OF_MEASURE aBaseUnitOfMeasure = aMeasurementPoint.getTheBaseUnitOfMeasure();
                    EUInformation euInformation
                            = new EUInformation(
                                    this.getNamespaceUri(),
                                    0,
                                    LocalizedText.english( aBaseUnitOfMeasure.toString().subSequence(0, 1).toString()),
                                    LocalizedText.english( aBaseUnitOfMeasure.toString())
                            );
                    // [step 4] set UoM to node and range
                    analogItemTypeNode.setEngineeringUnits(euInformation);
                    analogItemTypeNode.setEURange(new Range(0.0, 20.0)); // TODO: this is fixed now, but should come from config file
                    dataItemTypeNode = analogItemTypeNode;
                } else {
                    // [step 1] check if data type links to two state discrete node
                    if ( typeId.equals( Identifiers.Boolean)) {
                        // [step 2] create two state discreteItemNode
                        TwoStateVariableTypeNode twoStateVariableTypeNode = (TwoStateVariableTypeNode) getNodeFactory().createNode(
                            newNodeId(measurementPointID),
                            Identifiers.TwoStateVariableType,
                            new NodeFactory.InstantiationCallback() {
                                @Override
                                public boolean includeOptionalNode(NodeId typeDefinitionId, QualifiedName browseName) {
                                    return true;
                                }
                            }
                        );
                        twoStateVariableTypeNode.setDataType( typeId);
                        twoStateVariableTypeNode.setBrowseName( newQualifiedName(name));
                        twoStateVariableTypeNode.setDisplayName( LocalizedText.english(name));
                        twoStateVariableTypeNode.setDescription(LocalizedText.english("a boolean variable node"));
                        dataItemTypeNode = twoStateVariableTypeNode;
                    } else {
                        // [ste[p 1] check if data type links to two state discrete node
                        if ( PointInTime.DISCRETENODEITEMS.contains( typeId)) {
                            // [step 2] create normal discreteItemNode
                            DiscreteItemTypeNode discreteItemTypeNode = (DiscreteItemTypeNode) getNodeFactory().createNode(
                                newNodeId(measurementPointID),
                                Identifiers.DiscreteItemType,
                                new NodeFactory.InstantiationCallback() {
                                    @Override
                                    public boolean includeOptionalNode(NodeId typeDefinitionId, QualifiedName browseName) {
                                        return true;
                                    }
                                }
                            );
                            discreteItemTypeNode.setDataType( typeId);
                            discreteItemTypeNode.setBrowseName( newQualifiedName(name));
                            discreteItemTypeNode.setDisplayName( LocalizedText.english(name));
                            discreteItemTypeNode.setDescription( LocalizedText.english("a discrete variable node"));
                            dataItemTypeNode = discreteItemTypeNode;
                        } else {
                            // [step 1] check if data type links to speical node types
                            if ( PointInTime.SPECIALNODEITEMS.contains( typeId)) {
                                // [step 2] create base data variable node
                                BaseDataVariableTypeNode baseDataVariableTypeNode = (BaseDataVariableTypeNode) getNodeFactory().createNode(
                                    newNodeId(measurementPointID),
                                    Identifiers.DiscreteItemType,
                                    new NodeFactory.InstantiationCallback() {
                                        @Override
                                        public boolean includeOptionalNode(NodeId typeDefinitionId, QualifiedName browseName) {
                                            return true;
                                        }
                                    }
                                );
                                baseDataVariableTypeNode.setDataType( typeId);
                                baseDataVariableTypeNode.setBrowseName( newQualifiedName(name));
                                baseDataVariableTypeNode.setDisplayName( LocalizedText.english(name));
                                baseDataVariableTypeNode.setDescription( LocalizedText.english("a special variable node"));
                                dataItemTypeNode = baseDataVariableTypeNode;
                            }
                        }
                    }
                }
                // create reference to this OPC UA varable node in the measurement point, 
                // so that the node value can be updated when the value of the measurement point changes
                aMeasurementPoint.setUaVariableNode(dataItemTypeNode);
                // set the restricted access filter for this node
                dataItemTypeNode.getFilterChain().addLast( this.restrictedAccessFilter);
                // add to proper OPC UA structures
                this.getNodeManager().addNode(dataItemTypeNode);
                // add reference back and forth between the current folder and this containing variable node
                assetFolder.addOrganizes(dataItemTypeNode);
                // add this variable node to the list of variable node so it can be queried by the data backend
                this.variableNodes.add(dataItemTypeNode);
                } catch (UaException e) {
                    Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE, "Error creating specific UAVariableNode type instance");
                }
            } // end for loop
            // get this assets children
            List<Asset> children = anAsset.getChildren();
            // recursively call this same method, it will not do anything if there are no children
            createUANodeList(children, assetFolder);
        }
    }

    private void addRemoteControlMethodNode() {
        try {
            // create a "PlayerControl" folder and add it to the node manager
            NodeId remoteControlNodeId = this.newNodeId(PLAYERCONTROLFOLDER);
            UaFolderNode remoteControlFolderNode = new UaFolderNode(
                    this.getNodeContext(),
                    remoteControlNodeId,
                    this.newQualifiedName(PLAYERCONTROLFOLDER),
                    LocalizedText.english(PLAYERCONTROLFOLDER)
            );
            // add this method node to servers node map
            this.getNodeManager().addNode(remoteControlFolderNode);
            // and into the folder structure under root/objects by adding a reference to it
            remoteControlFolderNode.addReference(new Reference(
                    remoteControlFolderNode.getNodeId(),
                    Identifiers.Organizes,
                    Identifiers.ObjectsFolder.expanded(),
                    false
            ));
            // bulld the method node
            UaMethodNode methodNode = UaMethodNode.builder(this.getNodeContext())
                    .setNodeId(newNodeId(PLAYERCONTROLFOLDER + "/remote-control(x)"))
                    .setBrowseName(newQualifiedName("remote-control(x)"))
                    .setDisplayName(new LocalizedText(null, "remote-control(x)"))
                    .setDescription(
                            LocalizedText.english("Remote controle for this player: input '1' => Play, '5' => Pause, '6' => Stop, '7' => Endlessly loop input file"))
                    .build();
            // add an invocation handler point towards the control method and the actual class that can be 'controlled'
            RemoteControlMethod remoteControlMethod = new RemoteControlMethod(methodNode, this.dataController);
            // set the method input and output properties and the created invocation handler
            methodNode.setInputArguments(remoteControlMethod.getInputArguments());
            methodNode.setOutputArguments(remoteControlMethod.getOutputArguments());
            methodNode.setInvocationHandler(remoteControlMethod);
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
            UaVariableNode runStateVariableNode = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                .setNodeId(newNodeId(PLAYERCONTROLFOLDER + "/" + nodeName))
                .setAccessLevel(AccessLevel.READ_ONLY)
                .setUserAccessLevel(AccessLevel.READ_ONLY)
                .setBrowseName(newQualifiedName(nodeName))
                .setDisplayName(LocalizedText.english(nodeName))
                .setDataType(Identifiers.String)
                .setTypeDefinition(Identifiers.BaseDataVariableType)
                .build();
            // make this varable node known to data backend controller so that it can updates to the runstate into this node
            this.dataController.setRunStateUANode(runStateVariableNode);
            // add node to server mapRunState"
            this.getNodeManager().addNode(runStateVariableNode);
            // add node to this player folder
            remoteControlFolderNode.addOrganizes(runStateVariableNode);
        } catch (NumberFormatException ex) {
            Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE, "number format wrong: " + ex.getMessage(), ex);
        }
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
        switch (accessRight) {
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
