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
import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.PointInTime;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.PointInTime.ACCESS_RIGHT;
import static name.buurmeijermile.opcuaservices.controllableplayer.measurements.PointInTime.ACCESS_RIGHT.Both;
import static name.buurmeijermile.opcuaservices.controllableplayer.measurements.PointInTime.ACCESS_RIGHT.Read;
import static name.buurmeijermile.opcuaservices.controllableplayer.measurements.PointInTime.ACCESS_RIGHT.Write;

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
import org.eclipse.milo.opcua.sdk.server.dtd.DataTypeDictionaryManager;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.TwoStateVariableTypeNode;
import org.eclipse.milo.opcua.sdk.server.nodes.factories.NodeFactory;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.OpcNodeConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaObjectNode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;

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
     * @param server          the OPC UA server
     * @param aDataController the back end controller that exposes its measurement
     *                        points in this namespace
     * @param configuration   the configuration for this namespace
     */
    public PlayerNamespace(OpcUaServer server, DataControllerInterface aDataController, Configuration configuration) {

        super(server, configuration.getNamespace()); // name space from configuration
        // store parameters
        this.server = server;
        this.dataController = aDataController;

        // create a subscription model for this server
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

        this.restrictedAccessFilter = new RestrictedAccessFilter(identity -> {
            if (configuration.getAdminUser().equals(identity)) {
                return AccessLevel.READ_WRITE;
            } else {
                return AccessLevel.READ_ONLY;
            }
        });

        this.variableNodes = new ArrayList<>();
    }

    protected void startBogusEventNotifier() {
        // do nothing
    }

    protected void onStartup() {
        // get the hierarchically orderd assets from back end controller
        this.assets = this.dataController.getHierarchicalAssetList();
        if (this.dataController.isJsonConfig()) {
            this.reconstructFromJson(this.dataController.getOpcNodeConfigs());
        } else {
            // create node list in this namespace based on the available assets in the
            // backend controlller
            this.createUANodeList(this.assets, null);
        }
        // add the remote control OPC UA method to this servernamespace so that the OPC
        // UA player can be remotely controlled by OPC UA clients
        this.addRemoteControlMethodNode();

        int totalCount = countMeasurementPoints(this.assets, false);
        int boundCount = countMeasurementPoints(this.assets, true);
        Logger.getLogger(PlayerNamespace.class.getName()).log(Level.INFO, 
            "Namespace initialization summary: Created " + totalCount + " measurement points, bound " + boundCount + " to OPC UA variable nodes.");
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
        }
        ;
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
                    LocalizedText.english(anAsset.getName()));
            // add node to nodes
            this.getNodeManager().addNode(assetFolder);
            // add the folder correctly in the hierarchy, check if under root/object
            if (parentFolder == null) {
                // and into the folder structure under root/objects by adding a reference to it
                assetFolder.addReference(new Reference(
                        assetFolder.getNodeId(),
                        Identifiers.Organizes,
                        Identifiers.ObjectsFolder.expanded(),
                        false));
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
                    NodeId nodeToCreateId = aMeasurementPoint.getCustomNodeId();
                    if (nodeToCreateId == null) {
                        nodeToCreateId = newNodeId(measurementPointID);
                    } else {
                        Object identifier = nodeToCreateId.getIdentifier();
                        if (identifier instanceof String) {
                            nodeToCreateId = new NodeId(this.getNamespaceIndex(), (String) identifier);
                        } else if (identifier instanceof org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger) {
                            nodeToCreateId = new NodeId(this.getNamespaceIndex(),
                                    (org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger) identifier);
                        } else if (identifier instanceof java.util.UUID) {
                            nodeToCreateId = new NodeId(this.getNamespaceIndex(), (java.util.UUID) identifier);
                        } else if (identifier instanceof org.eclipse.milo.opcua.stack.core.types.builtin.ByteString) {
                            nodeToCreateId = new NodeId(this.getNamespaceIndex(),
                                    (org.eclipse.milo.opcua.stack.core.types.builtin.ByteString) identifier);
                        } else if (identifier instanceof Integer) {
                            nodeToCreateId = new NodeId(this.getNamespaceIndex(),
                                    org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned
                                            .uint((Integer) identifier));
                        }
                    }
                    NodeId typeId = aMeasurementPoint.getDataType();
                    // Set<AccessLevel> accessLevels =
                    // this.getAccessLevel(aMeasurementPoint.getAccessRight());
                    // create variable node based on this info [several steps]
                    BaseDataVariableTypeNode dataItemTypeNode = null;
                    // [step 1] check if datatype links to analog item node
                    if (PointInTime.ANALOGNODEITEMS.contains(typeId)) {
                        // [step 2] create OPC UA analog item node
                        AnalogItemTypeNode analogItemTypeNode = (AnalogItemTypeNode) getNodeFactory().createNode(
                                nodeToCreateId,
                                Identifiers.AnalogItemType,
                                new NodeFactory.InstantiationCallback() {
                                    @Override
                                    public boolean includeOptionalNode(NodeId typeDefinitionId,
                                            QualifiedName browseName) {
                                        return true;
                                    }
                                });
                        analogItemTypeNode.setDataType(typeId);
                        analogItemTypeNode.setBrowseName(newQualifiedName(name));
                        analogItemTypeNode.setDisplayName(LocalizedText.english(name));
                        analogItemTypeNode.setDescription(LocalizedText.english("an analog variable node"));
                        // [step 3] create UoM information object
                        PointInTime.BASE_UNIT_OF_MEASURE aBaseUnitOfMeasure = aMeasurementPoint
                                .getTheBaseUnitOfMeasure();
                        EUInformation euInformation = new EUInformation(
                                this.getNamespaceUri(),
                                0,
                                LocalizedText.english(aBaseUnitOfMeasure.toString().subSequence(0, 1).toString()),
                                LocalizedText.english(aBaseUnitOfMeasure.toString()));
                        // [step 4] set UoM to node and range
                        analogItemTypeNode.setEngineeringUnits(euInformation);
                        analogItemTypeNode.setEURange(new Range(0.0, 20.0)); // TODO: this is fixed now, but should come
                                                                             // from config file
                        dataItemTypeNode = analogItemTypeNode;
                    } else {
                        // [step 1] check if data type links to two state discrete node
                        if (typeId.equals(Identifiers.Boolean)) {
                            // [step 2] create two state discreteItemNode
                            TwoStateVariableTypeNode twoStateVariableTypeNode = (TwoStateVariableTypeNode) getNodeFactory()
                                    .createNode(
                                            nodeToCreateId,
                                            Identifiers.TwoStateVariableType,
                                            new NodeFactory.InstantiationCallback() {
                                                @Override
                                                public boolean includeOptionalNode(NodeId typeDefinitionId,
                                                        QualifiedName browseName) {
                                                    return true;
                                                }
                                            });
                            twoStateVariableTypeNode.setDataType(typeId);
                            twoStateVariableTypeNode.setBrowseName(newQualifiedName(name));
                            twoStateVariableTypeNode.setDisplayName(LocalizedText.english(name));
                            twoStateVariableTypeNode.setDescription(
                                    LocalizedText.english("a boolean variable node"));
                            dataItemTypeNode = twoStateVariableTypeNode;
                        } else {
                            // [step 1] check if data type links to two state discrete node
                            if (PointInTime.DISCRETENODEITEMS.contains(typeId)) {
                                // [step 2] create normal discreteItemNode
                                DiscreteItemTypeNode discreteItemTypeNode = (DiscreteItemTypeNode) getNodeFactory()
                                        .createNode(
                                                nodeToCreateId,
                                                Identifiers.DiscreteItemType,
                                                new NodeFactory.InstantiationCallback() {
                                                    @Override
                                                    public boolean includeOptionalNode(NodeId typeDefinitionId,
                                                            QualifiedName browseName) {
                                                        return true;
                                                    }
                                                });
                                discreteItemTypeNode.setDataType(typeId);
                                discreteItemTypeNode.setBrowseName(newQualifiedName(name));
                                discreteItemTypeNode.setDisplayName(LocalizedText.english(name));
                                discreteItemTypeNode.setDescription(LocalizedText.english("a discrete variable node"));
                                dataItemTypeNode = discreteItemTypeNode;
                            } else {
                                // [step 1] check if data type links to speical node types
                                if (PointInTime.SPECIALNODEITEMS.contains(typeId)) {
                                    // [step 2] create base data variable node
                                    BaseDataVariableTypeNode baseDataVariableTypeNode = (BaseDataVariableTypeNode) getNodeFactory()
                                            .createNode(
                                                    nodeToCreateId,
                                                    Identifiers.DiscreteItemType,
                                                    new NodeFactory.InstantiationCallback() {
                                                        @Override
                                                        public boolean includeOptionalNode(NodeId typeDefinitionId,
                                                                QualifiedName browseName) {
                                                            return true;
                                                        }
                                                    });
                                    baseDataVariableTypeNode.setDataType(typeId);
                                    baseDataVariableTypeNode.setBrowseName(newQualifiedName(name));
                                    baseDataVariableTypeNode.setDisplayName(LocalizedText.english(name));
                                    baseDataVariableTypeNode
                                            .setDescription(LocalizedText.english("a special variable node"));
                                    dataItemTypeNode = baseDataVariableTypeNode;
                                }
                            }
                        }
                    }
                    ACCESS_RIGHT configuredAccessRight = aMeasurementPoint.getAccessRight(); // get access right from
                                                                                             // measurement point
                                                                                             // configuration
                    if (dataItemTypeNode != null) {
                        // set the corresponding access right of the node at hand
                        UByte anAccessLevel = UByte.MIN;
                        switch (configuredAccessRight) {
                            case Both: {
                                anAccessLevel = AccessLevel.toValue(AccessLevel.CurrentRead, AccessLevel.CurrentWrite);
                                break;
                            }
                            case Read: {
                                anAccessLevel = AccessLevel.toValue(AccessLevel.CurrentRead);
                                break;
                            }
                            case Write: {
                                anAccessLevel = AccessLevel.toValue(AccessLevel.CurrentWrite);
                                break;
                            }
                            default: {
                                // set nothing
                                break;
                            }
                        }
                        dataItemTypeNode.setUserAccessLevel(anAccessLevel);
                        dataItemTypeNode.setAccessLevel(anAccessLevel);
                        // set the restricted access filter for this node
                        // dataItemTypeNode.getFilterChain().addLast( this.restrictedAccessFilter);
                    } else {
                        Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE, "dataItemTypeNode is null");
                    }
                    // create reference to this OPC UA varable node in the measurement point,
                    // so that the node value can be updated when the value of the measurement point
                    // changes
                    aMeasurementPoint.setUaVariableNode(dataItemTypeNode);
                    // add to proper OPC UA structures
                    this.getNodeManager().addNode(dataItemTypeNode);
                    // add reference back and forth between the current folder and this containing
                    // variable node
                    assetFolder.addOrganizes(dataItemTypeNode);
                    // add this variable node to the list of variable node so it can be queried by
                    // the data backend
                    this.variableNodes.add(dataItemTypeNode);
                } catch (UaException e) {
                    Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE,
                            "Error creating specific UAVariableNode type instance");
                }
            } // end for loop
              // get this assets children
            List<Asset> children = anAsset.getChildren();
            // recursively call this same method, it will not do anything if there are no
            // children
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
                    LocalizedText.english(PLAYERCONTROLFOLDER));
            // add this method node to servers node map
            this.getNodeManager().addNode(remoteControlFolderNode);
            // and into the folder structure under root/objects by adding a reference to it
            remoteControlFolderNode.addReference(new Reference(
                    remoteControlFolderNode.getNodeId(),
                    Identifiers.Organizes,
                    Identifiers.ObjectsFolder.expanded(),
                    false));
            // bulld the method node
            UaMethodNode methodNode = UaMethodNode.builder(this.getNodeContext())
                    .setNodeId(newNodeId(PLAYERCONTROLFOLDER + "/remote-control(x)"))
                    .setBrowseName(newQualifiedName("remote-control(x)"))
                    .setDisplayName(new LocalizedText(null, "remote-control(x)"))
                    .setDescription(
                            LocalizedText.english(
                                    "Remote controle for this player: input '1' => Play, '5' => Pause, '6' => Stop, '7' => Endlessly loop input file"))
                    .build();
            // add an invocation handler point towards the control method and the actual
            // class that can be 'controlled'
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
                    false));
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
            // make this varable node known to data backend controller so that it can
            // updates to the runstate into this node
            this.dataController.setRunStateUANode(runStateVariableNode);
            // add node to server mapRunState"
            this.getNodeManager().addNode(runStateVariableNode);
            // add node to this player folder
            remoteControlFolderNode.addOrganizes(runStateVariableNode);
        } catch (NumberFormatException ex) {
            Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE,
                    "number format wrong: " + ex.getMessage(), ex);
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
                Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE,
                        "No valid accessright passed to getAccessLevel");
                break;
            }
        }
        return resultSet;
    }

    private boolean isPlayerControlNode(NodeId nodeId) {
        if (nodeId == null) return false;
        if (nodeId.getNamespaceIndex().intValue() == getNamespaceIndex().intValue() && nodeId.getIdentifier() instanceof String) {
            String idStr = (String) nodeId.getIdentifier();
            return idStr.equals(PLAYERCONTROLFOLDER) || idStr.startsWith(PLAYERCONTROLFOLDER + "/");
        }
        return false;
    }

    private void reconstructFromJson(List<OpcNodeConfig> configs) {
        if (configs == null) return;
        
        java.util.Map<NodeId, NodeId> resolvedNodeIdMap = new java.util.HashMap<>();

        // Find all target nodes of forward references in the config to identify root nodes
        java.util.Set<NodeId> referencedTargets = new java.util.HashSet<>();
        for (OpcNodeConfig config : configs) {
            if (config.references != null) {
                for (OpcNodeConfig.OpcReference ref : config.references) {
                    if (ref.isForward) {
                        try {
                            NodeId targetNodeId = NodeId.parse(ref.targetNodeId);
                            if (!isPlayerControlNode(targetNodeId)) {
                                referencedTargets.add(targetNodeId);
                            }
                        } catch (Exception e) {
                            // ignore parse error
                        }
                    }
                }
            }
        }

        // Pass 1: Node Instantiation & Attribute Update
        for (OpcNodeConfig config : configs) {
            try {
                NodeId nodeId = NodeId.parse(config.nodeId);
                if (isPlayerControlNode(nodeId)) {
                    continue;
                }
                
                NodeId lookupId = nodeId;
                if (config.references != null && config.browseName != null) {
                    for (OpcNodeConfig.OpcReference ref : config.references) {
                        if (!ref.isForward && (ref.referenceTypeId.equals("ns=0;i=46") || ref.referenceTypeId.equals("ns=0;i=47"))) {
                            try {
                                NodeId parentId = NodeId.parse(ref.targetNodeId);
                                UaNode parentNode = this.server.getAddressSpaceManager().getManagedNode(parentId).orElse(null);
                                if (parentNode != null) {
                                    String propBrowseName = config.browseName.name;
                                    UaNode existingChild = parentNode.getReferences().stream()
                                        .filter(r -> r.isForward() && (r.getReferenceTypeId().equals(Identifiers.HasProperty) || r.getReferenceTypeId().equals(Identifiers.HasComponent)))
                                        .map(r -> {
                                            try {
                                                return this.server.getAddressSpaceManager().getManagedNode(r.getTargetNodeId().toNodeIdOrThrow(parentNode.getNodeContext().getNamespaceTable())).orElse(null);
                                            } catch (Exception e) {
                                                return null;
                                            }
                                        })
                                        .filter(n -> n != null && n.getBrowseName().getName().equals(propBrowseName))
                                        .findFirst()
                                        .orElse(null);
                                    if (existingChild != null) {
                                        lookupId = existingChild.getNodeId();
                                        resolvedNodeIdMap.put(nodeId, lookupId);
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                }
                
                NodeClass nodeClass = NodeClass.valueOf(config.nodeClass);
                
                UaNode existingNode = this.server.getAddressSpaceManager().getManagedNode(lookupId).orElse(null);
                
                if (existingNode != null) {
                    // Update attributes of existing node
                    if (config.displayName != null) {
                        existingNode.setDisplayName(LocalizedText.english(config.displayName));
                    }
                    if (config.description != null) {
                        existingNode.setDescription(LocalizedText.english(config.description));
                    }
                    boolean isProperty = false;
                    if (config.typeDefinition != null && (config.typeDefinition.equals("ns=0;i=68") || config.typeDefinition.endsWith("i=68"))) {
                        isProperty = true;
                    } else if (config.references != null) {
                        for (OpcNodeConfig.OpcReference ref : config.references) {
                            if (!ref.isForward && (ref.referenceTypeId.equals("ns=0;i=46") || ref.referenceTypeId.endsWith("i=46"))) {
                                isProperty = true;
                                break;
                            }
                        }
                    }
                    if (existingNode instanceof UaVariableNode && config.dataType != null && !isProperty) {
                        UaVariableNode varNode = (UaVariableNode) existingNode;
                        bindMeasurementPointToNode(varNode, nodeId);
                        varNode.setDataType(NodeId.parse(config.dataType));
                        if (config.accessLevel != null) {
                            varNode.setAccessLevel(UByte.valueOf(config.accessLevel));
                        }
                        if (config.userAccessLevel != null) {
                            varNode.setUserAccessLevel(UByte.valueOf(config.userAccessLevel));
                        }
                        if (config.value != null) {
                            Object val = parseJsonToValue(config.value, NodeId.parse(config.dataType));
                            if (val != null) {
                                varNode.setValue(new org.eclipse.milo.opcua.stack.core.types.builtin.DataValue(new org.eclipse.milo.opcua.stack.core.types.builtin.Variant(val)));
                            }
                        }
                    }
                } else {
                    // Create new node
                    UaNode node = null;
                    if (nodeClass == NodeClass.Object) {
                        if (config.typeDefinition != null && (config.typeDefinition.endsWith("i=61") || config.typeDefinition.contains("FolderType"))) {
                            node = new UaFolderNode(
                                getNodeContext(),
                                nodeId,
                                newQualifiedName(config.browseName.name),
                                LocalizedText.english(config.displayName)
                            );
                        } else {
                            node = new UaObjectNode(
                                getNodeContext(),
                                nodeId,
                                newQualifiedName(config.browseName.name),
                                LocalizedText.english(config.displayName)
                            );
                        }
                    } else if (nodeClass == NodeClass.Variable) {
                        NodeId typeDefId = config.typeDefinition != null ? NodeId.parse(config.typeDefinition) : Identifiers.BaseDataVariableType;
                        
                        if (typeDefId.equals(Identifiers.AnalogItemType)) {
                            AnalogItemTypeNode analogItemTypeNode = (AnalogItemTypeNode) getNodeFactory().createNode(
                                nodeId,
                                Identifiers.AnalogItemType,
                                new NodeFactory.InstantiationCallback() {
                                    @Override
                                    public boolean includeOptionalNode(NodeId typeDefinitionId, QualifiedName browseName) {
                                        return true;
                                    }
                                }
                            );
                            node = analogItemTypeNode;
                        } else if (typeDefId.equals(Identifiers.TwoStateVariableType)) {
                            TwoStateVariableTypeNode twoStateVariableTypeNode = (TwoStateVariableTypeNode) getNodeFactory().createNode(
                                nodeId,
                                Identifiers.TwoStateVariableType,
                                new NodeFactory.InstantiationCallback() {
                                    @Override
                                    public boolean includeOptionalNode(NodeId typeDefinitionId, QualifiedName browseName) {
                                        return true;
                                    }
                                }
                            );
                            node = twoStateVariableTypeNode;
                        } else if (typeDefId.equals(Identifiers.DiscreteItemType)) {
                            DiscreteItemTypeNode discreteItemTypeNode = (DiscreteItemTypeNode) getNodeFactory().createNode(
                                nodeId,
                                Identifiers.DiscreteItemType,
                                new NodeFactory.InstantiationCallback() {
                                    @Override
                                    public boolean includeOptionalNode(NodeId typeDefinitionId, QualifiedName browseName) {
                                        return true;
                                    }
                                }
                            );
                            node = discreteItemTypeNode;
                        } else {
                            node = new UaVariableNode.UaVariableNodeBuilder(getNodeContext())
                                .setNodeId(nodeId)
                                .setBrowseName(newQualifiedName(config.browseName.name))
                                .setDisplayName(LocalizedText.english(config.displayName))
                                .build();
                        }
                        
                        // Set attributes
                        UaVariableNode varNode = (UaVariableNode) node;
                        boolean isProperty = false;
                        if (config.typeDefinition != null && (config.typeDefinition.equals("ns=0;i=68") || config.typeDefinition.endsWith("i=68"))) {
                            isProperty = true;
                        } else if (config.references != null) {
                            for (OpcNodeConfig.OpcReference ref : config.references) {
                                if (!ref.isForward && (ref.referenceTypeId.equals("ns=0;i=46") || ref.referenceTypeId.endsWith("i=46"))) {
                                    isProperty = true;
                                    break;
                                }
                            }
                        }
                        if (!isProperty) {
                            bindMeasurementPointToNode(varNode, nodeId);
                        }
                        if (config.browseName != null) {
                            varNode.setBrowseName(newQualifiedName(config.browseName.name));
                        }
                        if (config.displayName != null) {
                            varNode.setDisplayName(LocalizedText.english(config.displayName));
                        }
                        if (config.description != null) {
                            varNode.setDescription(LocalizedText.english(config.description));
                        }
                        if (config.dataType != null) {
                            varNode.setDataType(NodeId.parse(config.dataType));
                        }
                        if (config.accessLevel != null) {
                            varNode.setAccessLevel(UByte.valueOf(config.accessLevel));
                        }
                        if (config.userAccessLevel != null) {
                            varNode.setUserAccessLevel(UByte.valueOf(config.userAccessLevel));
                        }
                        if (config.value != null && config.dataType != null) {
                            Object val = parseJsonToValue(config.value, NodeId.parse(config.dataType));
                            if (val != null) {
                                varNode.setValue(new org.eclipse.milo.opcua.stack.core.types.builtin.DataValue(new org.eclipse.milo.opcua.stack.core.types.builtin.Variant(val)));
                            }
                        }
                    } else if (nodeClass == NodeClass.Method) {
                        node = UaMethodNode.builder(getNodeContext())
                            .setNodeId(nodeId)
                            .setBrowseName(newQualifiedName(config.browseName.name))
                            .setDisplayName(new LocalizedText(null, config.displayName))
                            .build();
                    }
                    
                    if (node != null && nodeId.getNamespaceIndex().intValue() == getNamespaceIndex().intValue()) {
                        getNodeManager().addNode(node);
                    }
                }

                // Link top-level root nodes to ObjectsFolder
                UaNode resolvedNode = this.server.getAddressSpaceManager().getManagedNode(nodeId).orElse(null);
                if (resolvedNode != null) {
                    if (nodeId.getNamespaceIndex().intValue() == getNamespaceIndex().intValue()
                        && (nodeClass == NodeClass.Object || nodeClass == NodeClass.Variable)
                        && !referencedTargets.contains(nodeId)) {
                        boolean hasObjectsFolderRef = resolvedNode.getReferences().stream()
                            .anyMatch(r -> r.getReferenceTypeId().equals(Identifiers.Organizes) 
                                        && r.getTargetNodeId().equals(Identifiers.ObjectsFolder.expanded()) 
                                        && !r.isForward());
                        if (!hasObjectsFolderRef) {
                            resolvedNode.addReference(new org.eclipse.milo.opcua.sdk.core.Reference(
                                resolvedNode.getNodeId(),
                                Identifiers.Organizes,
                                Identifiers.ObjectsFolder.expanded(),
                                false
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE, "Error in Pass 1 for node " + config.nodeId, e);
            }
        }

        // Pass 2: Reference Linking
        for (OpcNodeConfig config : configs) {
            try {
                NodeId nodeId = NodeId.parse(config.nodeId);
                NodeId sourceNodeId = resolvedNodeIdMap.getOrDefault(nodeId, nodeId);
                if (isPlayerControlNode(sourceNodeId)) {
                    continue;
                }
                UaNode sourceNode = this.server.getAddressSpaceManager().getManagedNode(sourceNodeId).orElse(null);
                if (sourceNode == null || config.references == null) continue;
                
                for (OpcNodeConfig.OpcReference ref : config.references) {
                    NodeId refTypeId = NodeId.parse(ref.referenceTypeId);
                    NodeId targetNodeId = NodeId.parse(ref.targetNodeId);
                    NodeId resolvedTargetId = resolvedNodeIdMap.getOrDefault(targetNodeId, targetNodeId);
                    if (isPlayerControlNode(resolvedTargetId)) {
                        continue;
                    }
                    
                    // Avoid adding duplicate references
                    boolean hasRef = sourceNode.getReferences().stream()
                        .anyMatch(r -> r.getReferenceTypeId().equals(refTypeId) 
                                    && r.getTargetNodeId().equals(resolvedTargetId.expanded()) 
                                    && r.isForward() == ref.isForward);
                    if (!hasRef) {
                        sourceNode.addReference(new org.eclipse.milo.opcua.sdk.core.Reference(
                            sourceNode.getNodeId(),
                            refTypeId,
                            resolvedTargetId.expanded(),
                            ref.isForward
                        ));
                    }
                }
            } catch (Exception e) {
                Logger.getLogger(PlayerNamespace.class.getName()).log(Level.SEVERE, "Error in Pass 2 for node " + config.nodeId, e);
            }
        }
    }

    private Object parseJsonToValue(JsonElement element, NodeId dataType) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        
        try {
            if (dataType.equals(Identifiers.Boolean)) {
                return element.getAsBoolean();
            } else if (dataType.equals(Identifiers.Float)) {
                return element.getAsFloat();
            } else if (dataType.equals(Identifiers.Double)) {
                return element.getAsDouble();
            } else if (dataType.equals(Identifiers.String)) {
                return element.getAsString();
            } else if (dataType.equals(Identifiers.Int32)) {
                return element.getAsInt();
            } else if (dataType.equals(Identifiers.UInt32)) {
                return org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(element.getAsLong());
            } else if (dataType.equals(Identifiers.Int16)) {
                return element.getAsShort();
            } else if (dataType.equals(Identifiers.UInt16)) {
                return org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort(element.getAsInt());
            } else if (dataType.equals(Identifiers.Byte)) {
                return org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte(element.getAsShort());
            } else if (dataType.equals(Identifiers.SByte)) {
                return element.getAsByte();
            } else if (dataType.equals(Identifiers.Int64)) {
                return element.getAsLong();
            } else if (dataType.equals(Identifiers.UInt64)) {
                return org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong(element.getAsBigInteger());
            } else if (dataType.equals(new NodeId(0, 21))) { // LocalizedText
                JsonObject obj = element.getAsJsonObject();
                String locale = obj.has("locale") ? obj.get("locale").getAsString() : null;
                String text = obj.has("text") ? obj.get("text").getAsString() : "";
                return new LocalizedText(locale, text);
            } else if (dataType.equals(new NodeId(0, 20))) { // QualifiedName
                JsonObject obj = element.getAsJsonObject();
                int nsIdx = obj.get("namespaceIndex").getAsInt();
                String name = obj.get("name").getAsString();
                return new QualifiedName(nsIdx, name);
            } else if (dataType.equals(new NodeId(0, 884))) { // Range
                JsonObject obj = element.getAsJsonObject();
                double low = obj.get("low").getAsDouble();
                double high = obj.get("high").getAsDouble();
                return new Range(low, high);
            } else if (dataType.equals(new NodeId(0, 887))) { // EUInformation
                JsonObject obj = element.getAsJsonObject();
                String nsUri = obj.get("namespaceUri").getAsString();
                int unitId = obj.get("unitId").getAsInt();
                
                JsonObject dnObj = obj.getAsJsonObject("displayName");
                LocalizedText dn = new LocalizedText(dnObj.has("locale") ? dnObj.get("locale").getAsString() : null, dnObj.get("text").getAsString());
                
                JsonObject descObj = obj.getAsJsonObject("description");
                LocalizedText desc = new LocalizedText(descObj.has("locale") ? descObj.get("locale").getAsString() : null, descObj.get("text").getAsString());
                
                return new EUInformation(nsUri, unitId, dn, desc);
            }
        } catch (Exception e) {
            Logger.getLogger(PlayerNamespace.class.getName()).log(Level.WARNING, "Failed to parse json value: " + element.toString() + " for dataType: " + dataType, e);
        }
        return element.getAsString();
    }

    private void bindMeasurementPointToNode(UaVariableNode varNode, NodeId nodeId) {
        if (this.assets == null) {
            Logger.getLogger(PlayerNamespace.class.getName()).log(Level.INFO, "bindMeasurementPointToNode: assets is null for node: " + nodeId);
            return;
        }
        MeasurementPoint mp = findMeasurementPointInList(this.assets, nodeId);
        if (mp != null) {
            mp.setUaVariableNode(varNode);
        } else {
            Logger.getLogger(PlayerNamespace.class.getName()).log(Level.INFO, "bindMeasurementPointToNode: Could not find measurement point for node: " + nodeId);
        }
    }

    private MeasurementPoint findMeasurementPointInList(List<Asset> assetsList, NodeId nodeId) {
        for (Asset asset : assetsList) {
            for (MeasurementPoint mp : asset.getMeasurementPoints()) {
                if (mp.getCustomNodeId() != null && mp.getCustomNodeId().equals(nodeId)) {
                    return mp;
                }
            }
            MeasurementPoint childMp = findMeasurementPointInList(asset.getChildren(), nodeId);
            if (childMp != null) {
                return childMp;
            }
        }
        return null;
    }

    private int countMeasurementPoints(List<Asset> assetsList, boolean onlyBound) {
        if (assetsList == null) {
            return 0;
        }
        int count = 0;
        for (Asset asset : assetsList) {
            for (MeasurementPoint mp : asset.getMeasurementPoints()) {
                if (!onlyBound || mp.getUaVariableNode() != null) {
                    count++;
                }
            }
            count += countMeasurementPoints(asset.getChildren(), onlyBound);
        }
        return count;
    }
}
