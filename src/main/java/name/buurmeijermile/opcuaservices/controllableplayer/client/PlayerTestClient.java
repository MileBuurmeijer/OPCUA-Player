/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.UaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ApplicationType;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.UserTokenType;
import org.eclipse.milo.opcua.stack.core.types.structured.ApplicationDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.UserTokenPolicy;
import static org.eclipse.milo.opcua.stack.core.util.ConversionUtil.toList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class PlayerTestClient {

    private static String SERVERURL = "opc.tcp://localhost:4840/";
    private static final String CLIENTAPPLICATIONNAME = "Smiles OPC-UA client";
    private static final String CLIENTAPPLICATIONURI = "urn:smiles:player:examples:client" + UUID.randomUUID();

    private static final String SERVERAPPLICATIONURI = "urn:SmileSoft:OPCUA:playerserver";
    private static final String VERSION = "0.5.5";
    private static final String PRODUCTURI = "urn:SmileSoft:OPCUA:player-server";
    private static final String SERVERNAME = "OPCUA-Player";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AtomicLong clientHandleIdCounter = new AtomicLong(1L);
    private long valueUpdatedCounter = 0;


    public PlayerTestClient() throws Exception {
        Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "security");
        Files.createDirectories(securityTempDir);
        if (!Files.exists(securityTempDir)) {
            throw new Exception("unable to create security dir: " + securityTempDir);
        }

        LoggerFactory.getLogger(getClass())
            .info("security temp dir: {}", securityTempDir.toAbsolutePath());

        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);
        
        // public ApplicationDescription(String applicationUri, String productUri, LocalizedText applicationName, ApplicationType applicationType, String gatewayServerUri, String discoveryProfileUri, String[] discoveryUrls) {
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
        // public UserTokenPolicy(String policyId, UserTokenType tokenType, String issuedTokenType, String issuerEndpointUrl, String securityPolicyUri) {
        UserTokenPolicy[] userTokenPolicies = { new UserTokenPolicy( "username", UserTokenType.UserName, null, null, null)}; 
        // public EndpointDescription(String endpointUrl, ApplicationDescription server, ByteString serverCertificate, MessageSecurityMode securityMode, String securityPolicyUri, UserTokenPolicy[] userIdentityTokens, String transportProfileUri, UByte securityLevel) {
        EndpointDescription endpoint = 
                new EndpointDescription(
                        SERVERURL, 
                        applicationDescription,
                        new ByteString( loader.getClientCertificate().getEncoded()),
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
                .setIdentityProvider( new UsernameProvider("user","8h5%32@!~"))
                .setRequestTimeout( uint(5000))
                .setCertificate( loader.getClientCertificate())
                .setKeyPair( loader.getClientKeyPair())
                .build();

        OpcUaClient client = OpcUaClient.create(config);
        
        // asynchronous connect
        CompletableFuture<UaClient> future = client.connect();
        future.get();
        
        
        // start browsing at root folder
        browseNode("", client, Identifiers.RootFolder);

        future.complete(client);

        // synchronous read request via VariableNode
        UaVariableNode node = client.getAddressSpace().getVariableNode(Identifiers.Server_ServerStatus_StartTime);        
        DataValue value = node.readValue();

        logger.info( "StartTime=" + value.getValue().getValue());
//
//        List<NodeId> nodeIds = ImmutableList.of(
//            Identifiers.Server_ServerStatus_State,
//            Identifiers.Server_ServerStatus_CurrentTime);
//
//        // synchronous read request
//        List<DataValue> values = client.readValues(0.0, TimestampsToReturn.Both, nodeIds).get();
//        DataValue v0 = values.get(0);
//        DataValue v1 = values.get(1);
//
//        logger.info( "Succeeded in making a connection on a secure channel.");
//        logger.info( "State=" + ServerState.from((Integer) v0.getValue().getValue()));
//        logger.info( "CurrentTime=" + v1.getValue().getValue());
//
//        // create a subscription @ 500ms publishing interval
//        UaSubscription subscription = client.getSubscriptionManager().createSubscription(200.0).get();
//
//        NodeId cosinusSignal = new NodeId( 2, "1002");
//        NodeId sinusSignal = new NodeId( 2, "1001");
//        
//        // subscribe to the Value attribute of the server's CurrentTime node
//        ReadValueId readValueId = 
//                new ReadValueId(
//                    cosinusSignal,
//                    AttributeId.Value.uid(), 
//                    null, 
//                    QualifiedName.NULL_VALUE
//                );
//
//        // important: client handle must be unique per item
//        UInteger clientHandleId = uint(clientHandleIdCounter.getAndIncrement());
//
//        MonitoringParameters parameters = new MonitoringParameters(
//            clientHandleId,
//            0.0,        // sampling interval 0.0 means recieve all reported value changes
//            null,       // filter, null means use default
//            uint(10000),// queue size
//            true        // discard oldest
//        );
//
//        MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
//            readValueId, MonitoringMode.Reporting, parameters);
//
//        // when creating items in MonitoringMode.Reporting this callback is where each item needs to have its
//        // value/event consumer hooked up. The alternative is to create the item in sampling mode, hook up the
//        // consumer after the creation call completes, and then change the mode for all items to reporting.
//        BiConsumer<UaMonitoredItem, Integer> onItemCreated =
//            (item, id) -> item.setValueConsumer(this::onSubscriptionValue);
//
//        LocalDateTime startMonitoring = LocalDateTime.now();
//        
//        List<UaMonitoredItem> items = subscription.createMonitoredItems(
//            TimestampsToReturn.Both,
//            newArrayList( request),
//            onItemCreated
//        ).get();
//
//        for (UaMonitoredItem item : items) {
//            if (item.getStatusCode().isGood()) {
//                logger.info("item created for nodeId={}", item.getReadValueId().getNodeId());
//            } else {
//                logger.warn(
//                    "failed to create item for nodeId={} (status={})",
//                    item.getReadValueId().getNodeId(), item.getStatusCode());
//            }
//        }
//
//        // let the example run for 115 seconds then terminate
//        Thread.sleep(115000);
//        // delete the subscriptions
//        subscription.deleteMonitoredItems( items);
//        // record the time
//        LocalDateTime stopMonitoring = LocalDateTime.now();
//        // calc duration
//        Duration duration = Duration.between( startMonitoring, stopMonitoring);
//        logger.info("Duration of measurement" + duration);
//        // calc samples per second
//        double samplesPerSecond = this.valueUpdatedCounter / (duration.getNano() / 1E9d + duration.getSeconds());
//        logger.info("Recorded {} samples per second", samplesPerSecond);
//        
//        // close client properly
//        future.complete(client);
//        
//        // log the number of item values recieved
//        logger.info("item values received in 15 seconds ={}", valueUpdatedCounter);
    }

    private void browseNode(String indent, OpcUaClient client, NodeId browseRoot) throws InterruptedException, ExecutionException {
        BrowseDescription browse = new BrowseDescription(
            browseRoot,
            BrowseDirection.Forward,
            Identifiers.References,
            true,
            uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
            uint(BrowseResultMask.All.getValue())
        );
        
        BrowseResult browseResult = client.browse(browse).get();

        List<ReferenceDescription> references = toList(browseResult.getReferences());

        for (ReferenceDescription rd : references) {
            try {
                NodeId nodeId = rd.getNodeId().toNodeIdOrThrow(client.getNamespaceTable());
                String nodeIdString = rd.getNodeId().getIdentifier().toString();
                if (nodeId.getNamespaceIndex().intValue()== Identifiers.Server.getNamespaceIndex().intValue() && nodeIdString != null &&
                        (nodeIdString.equals( Identifiers.Server.getIdentifier().toString()) ||
                        nodeIdString.equals(Identifiers.ViewsFolder.getIdentifier().toString()) ||
                        nodeIdString.equals(Identifiers.TypesFolder.getIdentifier().toString()))) {
                    // skip
                    logger.info("Skipping{} Node={}, NodeID={}, Parent={}", indent, rd.getBrowseName().getName(), rd.getNodeId().getIdentifier(), browseRoot.getIdentifier());
                } else {
                    logger.info("{} Node={}, NodeID={}, NodeTypeId={}, Parent={}", indent, rd.getBrowseName().getName(), rd.getNodeId().getIdentifier(), rd.getReferenceTypeId(), browseRoot.getIdentifier());

                    // recursively browse to children
                    browseNode(indent + "  ", client, nodeId);
                }
            } catch (Exception ex) {
                java.util.logging.Logger.getLogger(PlayerTestClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void onSubscriptionValue(UaMonitoredItem item, DataValue value) {
        logger.info(
            "subscription value received: item={}, value={}",
            item.getReadValueId().getNodeId(), value.getValue());
        valueUpdatedCounter++;
    }

    public static void main(String[] argv) throws Exception {
        PlayerTestClient testClient = new PlayerTestClient();
    }

}

