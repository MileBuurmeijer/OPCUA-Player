/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.ApplicationDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import org.eclipse.milo.opcua.stack.core.util.ConversionUtil;

import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration;
import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration.ExitCode;
import name.buurmeijermile.opcuaservices.utils.Waiter;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;
import static org.eclipse.milo.opcua.stack.core.types.enumerated.IdType.Numeric;
import static org.eclipse.milo.opcua.stack.core.types.enumerated.IdType.String;
import static name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration.ExitCode.CONNECTIONFAILED;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class RecorderClient {

    private static final String CLIENTAPPLICATIONNAME = "Smiles OPC-UA client";
    private static final String CLIENTAPPLICATIONURI = "urn:smiles:player:examples:client" + UUID.randomUUID();

    private static final String SERVERAPPLICATIONURI = "urn:SmileSoft:OPCUA:playerserver";
    private static final String VERSION = "0.5.5";
    private static final String PRODUCTURI = "urn:SmileSoft:OPCUA:player-server";
    private static final String SERVERNAME = "OPCUA-Player";

    private final Logger logger = Logger.getLogger(RecorderClient.class.getName());

    private final AtomicLong clientHandleIdCounter = new AtomicLong(1L);
    private long valueUpdatedCounter = 0;
    private OpcUaClient client;
    private Configuration configuration = Configuration.getConfiguration();
    private NodeListFileController nodeListFileController;
    private int monitoredItemQueueSize = 10_000; // default queue size
    private Deque<Sample> sampleQueue = new ConcurrentLinkedDeque<>();

    public RecorderClient() {
        client = null;
        try {
            Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), Configuration.getConfiguration().getSecurityFolderName());
            Files.createDirectories(securityTempDir);
            if (!Files.exists(securityTempDir)) {
                logger.log(Level.SEVERE, "temp dir does not exist");
                System.exit(ExitCode.TMPDIRFAILS.ordinal());
            } else {
                logger.log(Level.INFO, "security temp dir: " + securityTempDir.toAbsolutePath());
            }

            KeystoreLoader loader = new KeystoreLoader().load(securityTempDir);

            // create the application description of the server from where we want to record the data
            ApplicationDescription applicationDescription
                    = new ApplicationDescription(
                            SERVERAPPLICATIONURI,
                            PRODUCTURI,
                            LocalizedText.english("Smiles"),
                            ApplicationType.Server,
                            null,
                            null,
                            null
                    );
            // create appropriate use token policy, currently only anonymous is supported
            // TODO: make this configurable
            UserTokenPolicy[] userTokenPolicies = {new UserTokenPolicy("anonymous", UserTokenType.Anonymous, null, null, null)};
            // create end point description of the target OPC UA server
            String serverUri = configuration.getUri();
            EndpointDescription endpoint
                    = new EndpointDescription(
                            serverUri,
                            applicationDescription,
                            new ByteString(loader.getClientCertificate().getEncoded()),
                            MessageSecurityMode.None,
                            SecurityPolicy.None.getUri(),
                            userTokenPolicies,
                            Stack.TCP_UASC_UABINARY_TRANSPORT_URI,
                            UByte.valueOf(0)
                    );

            OpcUaClientConfig config = OpcUaClientConfig.builder()
                    .setApplicationName(LocalizedText.english(CLIENTAPPLICATIONNAME))
                    .setApplicationUri(CLIENTAPPLICATIONURI)
                    .setEndpoint(endpoint)
                    //                .setIdentityProvider( new UsernameProvider("user","8h5%32@!~"))
                    .setRequestTimeout(Unsigned.uint(5000))
                    .setCertificate(loader.getClientCertificate())
                    .setKeyPair(loader.getClientKeyPair())
                    .build();
            
            logger.log(Level.INFO, "Before creating the OPC UA client");
            client = OpcUaClient.create(config);
            logger.log(Level.INFO, "After the OPC UA client is created");
        } catch (UaException | CertificateEncodingException | IOException ex) {
            logger.log(Level.SEVERE, "Exception occured in creating the client", ex);
            client = null;
        }
    }

    private void connect() {
        if (client != null) {
            try {
                client.connect().get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Connecting of client failed", ex);
                System.exit(CONNECTIONFAILED.ordinal());
            }
        } else {
            logger.log(Level.SEVERE, "Can not connect, because client is null");
        }
    }
    
    public void start() {
        this.connect();
        nodeListFileController = new NodeListFileController();
        if ( configuration.isCaptureInformationModel()) {
            this.getServerConfiguration();
        } else {
            this.recordServerData();
        }
    }
    
    private void getServerConfiguration() {
        try {
            String startNodeString = Configuration.getConfiguration().getStartNode();
            NodeId startNode = NodeId.parse(startNodeString);
            if (startNode != null) {
                List<NodeId> nodeIdList = new ArrayList<>();
                this.browseNode( nodeIdList, client, startNode);
                String configFileName = Configuration.getConfiguration().getConfigFile().getPath();
                nodeListFileController.writeNodeIdConfigFile( nodeIdList);
            }
            client.disconnect().get();
            logger.log( Level.INFO, "Ready getting the node configuration from the server");
        } catch (InterruptedException | ExecutionException ex) {
            logger.log(Level.SEVERE, "Error in disconnecting the client", ex);
        }
    }
    
    private void recordServerData( ) {
        try{
            Double publishingInterval = configuration.getPublishingInterval();
            UaSubscription subscription = client.getSubscriptionManager().createSubscription( publishingInterval).get();
            // when creating items in MonitoringMode.Reporting this callback is where each item needs to have its
            // value/event consumer hooked up. The alternative is to create the item in sampling mode, hook up the
            // consumer after the creation call completes, and then change the mode for all items to reporting.
            UaSubscription.ItemCreationCallback onItemCreated
                    = (item, id) -> item.setValueConsumer(this::onSubscriptionValue);
            // Read node list
            List<NodeId> nodeIdList = nodeListFileController.readNodeIdConfigFile();
            // create a list of monitored item 'create' requests
            List<MonitoredItemCreateRequest> monitoredItemCreateRequests = new ArrayList<>();

            // loop through nodes for which monitored item create requests need to be created
            for (NodeId aNodeId : nodeIdList) {
                // important: client handle must be unique per item
                UInteger clientHandleId = Unsigned.uint(clientHandleIdCounter.getAndIncrement());
                // set monitoring parameters, since it includes a unique client handle it needs to be created for each item
                Double samplingInterval = configuration.getSamplingInterval();
                MonitoringParameters monitoringParameters = new MonitoringParameters(
                        clientHandleId,
                        samplingInterval, // sampling interval 0.0 means recieve all reported value changes
                        null, // filter, null means use default
                        Unsigned.uint( monitoredItemQueueSize),// queue size
                        true // discard oldest
                );
                // subscribe to the value attribute of the nodes in the node list
                ReadValueId aReadValueId
                        = new ReadValueId(
                                aNodeId,
                                AttributeId.Value.uid(),
                                null,
                                QualifiedName.NULL_VALUE
                        );
                // create a request for this read value id
                MonitoredItemCreateRequest aRequest = new MonitoredItemCreateRequest(
                        aReadValueId, MonitoringMode.Reporting, monitoringParameters);
                monitoredItemCreateRequests.add(aRequest);
                logger.log(Level.INFO, "monitoredItemCreateRequest created for nodeId=", aNodeId);
            }
            // start data logger controller who is responsible for writing the data to disk
            // this controller works on the sample queue
            DataLoggerController dataLoggerController = new DataLoggerController( this.sampleQueue);
            dataLoggerController.startWriting();
            
            LocalDateTime startRecordingTimestamp = LocalDateTime.now(); // save timestamp at start of monitoring

            // create the monitored items within the subscription
            List<UaMonitoredItem> uaMonitoredItems = subscription.createMonitoredItems(
                    TimestampsToReturn.Both,
                    monitoredItemCreateRequests,
                    onItemCreated
            ).get(); // returns when subscribing is finished.
            logger.log(Level.INFO, "subscription created with the monitoredItemCreateRequests, isPublishingEnabled: " + subscription.isPublishingEnabled());
            // check which monitored items became part of the subscription
            List<UaMonitoredItem> monitoredItems = subscription.getMonitoredItems();
            for (UaMonitoredItem monitoredItem : monitoredItems) {
                System.out.println("Subscribed item: " + monitoredItem.getReadValueId().getNodeId());
            }
            // list the items that are returned by the subscription creation
            for (UaMonitoredItem uaMontoredItem : uaMonitoredItems) {
                if (uaMontoredItem.getStatusCode().isGood()) {
                    logger.log(Level.INFO, "item created for nodeId=" + uaMontoredItem.getReadValueId().getNodeId());
                } else {
                    logger.log(Level.WARNING, "failed to create item for nodeId=" + uaMontoredItem.getReadValueId().getNodeId() + " (status=)" + uaMontoredItem.getStatusCode());
                }
            }
            // wait for the set duration to record
            Duration durationToRecord = configuration.getRecordingDuration();
            Waiter.waitADuration(durationToRecord);
            
            // OK ready with recording, wind down & cleanup
            // create astop recording timestamp
            LocalDateTime stopRecordingTimestamp = LocalDateTime.now();
            // delete the subscriptions
            subscription.deleteMonitoredItems(uaMonitoredItems);
            // calculate the exact runtime duration
            Duration duration = Duration.between(startRecordingTimestamp, stopRecordingTimestamp);
            logger.log(Level.INFO, "Duration of actual recording: " + duration);
            // log the number of item values recieved
            logger.log(Level.INFO, "Item values received: " + valueUpdatedCounter);
            // calc samples per second
            double samplesPerSecond = this.valueUpdatedCounter / (duration.getNano() / 1E9d + duration.getSeconds());
            logger.log(Level.INFO, "Recorded samples per second: " + samplesPerSecond);

            // close client properly
            client.disconnect();
            // close data logger
            dataLoggerController.stopWriting();
        } catch (InterruptedException | ExecutionException ex) {
            logger.log(Level.SEVERE, "Exceoption occured", ex);
        }
    }

    private void browseNode(List<NodeId> resultNodeIdList, OpcUaClient client, NodeId browseRoot) {
        try {
            BrowseDescription browse = new BrowseDescription(
                    browseRoot,
                    BrowseDirection.Forward,
                    Identifiers.References,
                    true,
                    Unsigned.uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
                    Unsigned.uint(BrowseResultMask.All.getValue())
            );
            // get browse results from OPC UA server that this client is connected to
            BrowseResult browseResult = client.browse(browse).get();
            
            List<ReferenceDescription> references = ConversionUtil.toList(browseResult.getReferences());
            
            for (ReferenceDescription rd : references) {
                try {
                    NodeId nodeId = rd.getNodeId().toNodeIdOrThrow(client.getNamespaceTable());
                    String nodeIdString = rd.getNodeId().getIdentifier().toString();
                    boolean skip = nodeId.getNamespaceIndex().intValue() == Identifiers.Server.getNamespaceIndex().intValue() && nodeIdString != null
                            && (nodeIdString.equals(Identifiers.Server.getIdentifier().toString())
                            || nodeIdString.equals(Identifiers.ViewsFolder.getIdentifier().toString())
                            || nodeIdString.equals(Identifiers.TypesFolder.getIdentifier().toString()));
                    if (!skip) {
                        logger.log( Level.INFO, "BrowseName=" + rd.getBrowseName().getName() + " NodeID= "+ rd.getNodeId().getIdentifier() + " NodeTypeId=" + rd.getReferenceTypeId() + "Parent=" + browseRoot.getIdentifier());
                        resultNodeIdList.add(nodeId);
                        // recursively browse to children
                        browseNode(resultNodeIdList, client, nodeId);
                    } else {
                        // skip
                        logger.log( Level.INFO, "Skipping Node=" + rd.getBrowseName().getName() + " NodeID= "+ rd.getNodeId().getIdentifier() + " Parent=" + browseRoot.getIdentifier());
                    }
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        } catch (InterruptedException | ExecutionException ex) {
            logger.log(Level.SEVERE, "Exception occured in browsing", ex);
        }
    }

    private void onSubscriptionValue(UaMonitoredItem item, DataValue value) {
        NodeId nodeId = item.getReadValueId().getNodeId();
        String timestampString;
        DateTime timestamp = value.getServerTime();
        if (timestamp != null) {
            timestampString = timestamp.toString();
        } else {
            timestampString = "null";
        }
//        logger.log(Level.INFO, "Subscription value received: item={0}, value={1}, server timestamp={2}", new Object[]{nodeId, value, value.getServerTime()});
        valueUpdatedCounter++;
        Sample aSample = new Sample( nodeId, value);
        this.sampleQueue.add(aSample); // add the sample at the tail of the queue
    }
}
