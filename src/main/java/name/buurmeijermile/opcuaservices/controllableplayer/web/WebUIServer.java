package name.buurmeijermile.opcuaservices.controllableplayer.web;

import static spark.Spark.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import java.io.FileReader;
import com.google.gson.reflect.TypeToken;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.OpcNodeConfig;

import com.google.gson.Gson;

public class WebUIServer {
    private static final Logger logger = Logger.getLogger(WebUIServer.class.getName());
    private final int webPort;
    private static final Gson gson = new Gson();
    
    // In-memory list of active instances
    public static final Map<String, InstanceInfo> instances = new ConcurrentHashMap<>();
    private static int instanceCounter = 0;

    public static class InstanceInfo {
        public String id;
        public String type; // "player" or "recorder"
        public String status; // "Stopped", "Starting", "Running", "Paused", "Recording", "Completed", "Failed"
        public int port;
        public String uri;
        public String namespace;
        public String servicename;
        public String configfile;
        public String datafile;
        public String duration;
        public Double publishingInterval;
        public Double samplingInterval;
        public boolean captureModel;
        public String startNode;
        
        public transient Process process;
        public transient OpcUaClient client;
        public List<String> logs = new CopyOnWriteArrayList<>();
        public Map<String, String> nodeValues = new ConcurrentHashMap<>();
        public List<Map<String, Object>> nodeTree = new CopyOnWriteArrayList<>();
    }

    public WebUIServer(int port) {
        this.webPort = port;
    }

    public void start() {
        logger.log(Level.INFO, "Starting Web UI server on port " + webPort);
        
        port(webPort);
        
        // Setup static files directory: src/main/resources/web
        staticFiles.location("/web");
        
        // Setup WebSocket path
        webSocket("/ws", WebUIWebSocket.class);
        
        // REST API: List files in the files folder
        get("/api/files", (req, res) -> {
            res.type("application/json");
            File filesDir = new File("files");
            if (!filesDir.exists()) {
                filesDir = new File("../files");
            }
            List<String> fileNames = new ArrayList<>();
            if (filesDir.exists() && filesDir.isDirectory()) {
                File[] list = filesDir.listFiles();
                if (list != null) {
                    for (File f : list) {
                        if (f.isFile()) {
                            fileNames.add(f.getName());
                        }
                    }
                }
            }
            fileNames.sort(String.CASE_INSENSITIVE_ORDER);
            return gson.toJson(fileNames);
        });

        // REST API: Get all instances
        get("/api/instances", (req, res) -> {
            res.type("application/json");
            return gson.toJson(instances.values());
        });
        
        init();
    }

    public static synchronized InstanceInfo createInstance(Map<String, Object> params) throws Exception {
        String type = (String) params.get("type"); // "player" or "recorder"
        instanceCounter++;
        String id = "inst_" + instanceCounter;
        
        InstanceInfo inst = new InstanceInfo();
        inst.id = id;
        inst.type = type;
        inst.status = "Stopped";
        inst.configfile = (String) params.get("configfile");
        inst.datafile = (String) params.get("datafile");
        inst.uri = (String) params.get("uri");
        
        if ("player".equals(type)) {
            inst.port = ((Double) params.getOrDefault("port", 12400.0)).intValue();
            inst.namespace = (String) params.getOrDefault("namespace", "urn:SmileSoft:OPC_UA_Player");
            inst.servicename = (String) params.getOrDefault("servicename", "Smiles-OPCUA-Player");
        } else {
            inst.duration = (String) params.get("duration");
            if (params.containsKey("publishingInterval")) {
                inst.publishingInterval = (Double) params.get("publishingInterval");
            }
            if (params.containsKey("samplingInterval")) {
                inst.samplingInterval = (Double) params.get("samplingInterval");
            }
            inst.captureModel = (Boolean) params.getOrDefault("captureModel", false);
            inst.startNode = (String) params.get("startNode");
        }

        instances.put(id, inst);
        return inst;
    }

    public static void startInstance(String id) {
        InstanceInfo inst = instances.get(id);
        if (inst == null || !"Stopped".equals(inst.status) && !"Completed".equals(inst.status) && !"Failed".equals(inst.status)) {
            return;
        }

        inst.logs.clear();
        inst.nodeValues.clear();
        inst.nodeTree.clear();
        inst.status = "Starting";
        WebUIWebSocket.broadcastStatus(id, inst.status);

        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-cp");
        
        File jarWithDeps = new File("target/OPCUA-Controllable-Player-0.8.0-jar-with-dependencies.jar");
        if (!jarWithDeps.exists()) {
            jarWithDeps = new File("../target/OPCUA-Controllable-Player-0.8.0-jar-with-dependencies.jar");
        }
        
        if (jarWithDeps.exists()) {
            cmd.add(jarWithDeps.getAbsolutePath());
        } else {
            cmd.add(System.getProperty("java.class.path"));
        }
        
        cmd.add("name.buurmeijermile.opcuaservices.controllableplayer.main.MainController");
        cmd.add("-mode");
        cmd.add(inst.type);
        
        File filesDir = new File("files");
        if (!filesDir.exists()) {
            filesDir = new File("../files");
        }
        
        cmd.add("-configfile");
        cmd.add(new File(filesDir, inst.configfile).getAbsolutePath());
        
        if (inst.datafile != null && !inst.datafile.trim().isEmpty()) {
            cmd.add("-datafile");
            cmd.add(new File(filesDir, inst.datafile).getAbsolutePath());
        }

        cmd.add("-uri");
        cmd.add(inst.uri);

        if ("player".equals(inst.type)) {
            cmd.add("-port");
            cmd.add(String.valueOf(inst.port));
            cmd.add("-namespace");
            cmd.add(inst.namespace);
            cmd.add("-servicename");
            cmd.add(inst.servicename);
        } else {
            if (inst.duration != null && !inst.duration.trim().isEmpty()) {
                cmd.add("-duration");
                cmd.add(inst.duration);
            }
            if (inst.publishingInterval != null) {
                cmd.add("-publishinginterval");
                cmd.add(String.valueOf(inst.publishingInterval));
            }
            if (inst.samplingInterval != null) {
                cmd.add("-samplinginterval");
                cmd.add(String.valueOf(inst.samplingInterval));
            }
            if (inst.captureModel) {
                cmd.add("-captureinformationmodel");
                if (inst.startNode != null && !inst.startNode.trim().isEmpty()) {
                    cmd.add("-startnode");
                    cmd.add(inst.startNode);
                }
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File("."));
            inst.process = pb.start();
            
            // Read stdout
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inst.process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        inst.logs.add(line);
                        if (inst.logs.size() > 1000) {
                            inst.logs.remove(0);
                        }
                        WebUIWebSocket.broadcastLog(id, line);
                    }
                } catch (IOException e) {
                    // process ended or error
                }
            }).start();

            // Read stderr
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inst.process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        inst.logs.add(line);
                        if (inst.logs.size() > 1000) {
                            inst.logs.remove(0);
                        }
                        WebUIWebSocket.broadcastLog(id, line);
                    }
                } catch (IOException e) {
                    // process ended or error
                }
            }).start();

            // Monitor process exit
            new Thread(() -> {
                try {
                    int exitCode = inst.process.waitFor();
                    OpcUaClient c = inst.client;
                    if (c != null) {
                        try {
                            c.disconnect().get();
                        } catch (Exception e) {
                            // ignore
                        }
                        inst.client = null;
                    }
                    // Only update if it wasn't explicitly stopped by the user
                    if (!"Stopped".equals(inst.status)) {
                        inst.status = exitCode == 0 ? "Completed" : "Failed";
                        WebUIWebSocket.broadcastStatus(id, inst.status);
                    }
                } catch (InterruptedException e) {
                    inst.status = "Stopped";
                    WebUIWebSocket.broadcastStatus(id, inst.status);
                }
            }).start();

            if ("player".equals(inst.type)) {
                // Monitor/Connect OPC UA client
                new Thread(() -> {
                    OpcUaClient miloClient = null;
                    int retries = 15;
                    while (retries > 0 && inst.process.isAlive()) {
                        try {
                            // First, wait a moment to give the process some time
                            Thread.sleep(1000);
                            String connectingMsg = "[SYSTEM] Connecting to OPC UA Server (attempts remaining: " + retries + ")...";
                            inst.logs.add(connectingMsg);
                            WebUIWebSocket.broadcastLog(id, connectingMsg);
                            miloClient = connectToPlayer(inst.port, inst.uri);
                            break;
                        } catch (Exception e) {
                            retries--;
                            if (retries == 0) {
                                String failedMsg = "[SYSTEM] Failed to connect to OPC UA Server after multiple attempts: " + e.getMessage();
                                inst.logs.add(failedMsg);
                                WebUIWebSocket.broadcastLog(id, failedMsg);
                                inst.status = "Failed";
                                WebUIWebSocket.broadcastStatus(id, inst.status);
                            }
                        }
                    }
                    
                    if (miloClient != null && inst.process.isAlive()) {
                        try {
                            inst.client = miloClient;
                            inst.status = "Running";
                            WebUIWebSocket.broadcastStatus(id, inst.status);
                            
                            String connectedMsg = "[SYSTEM] Connected. Browsing node tree...";
                            inst.logs.add(connectedMsg);
                            WebUIWebSocket.broadcastLog(id, connectedMsg);
                            
                            List<Map<String, Object>> tree = new CopyOnWriteArrayList<>();
                            browseNodeTree(miloClient, Identifiers.ObjectsFolder, tree, "");
                            inst.nodeTree = tree;
                            WebUIWebSocket.broadcastNodeTree(id, tree);
                            
                            String browsedMsg = "[SYSTEM] Node tree browsed. Subscribing to variables...";
                            inst.logs.add(browsedMsg);
                            WebUIWebSocket.broadcastLog(id, browsedMsg);
                            
                            subscribeToVariables(id, inst, miloClient, tree);
                        } catch (Exception e) {
                            String errInitMsg = "[SYSTEM] Error initializing client: " + e.getMessage();
                            inst.logs.add(errInitMsg);
                            WebUIWebSocket.broadcastLog(id, errInitMsg);
                            inst.status = "Failed";
                            WebUIWebSocket.broadcastStatus(id, inst.status);
                        }
                    }
                }).start();
            } else {
                inst.status = "Recording";
                WebUIWebSocket.broadcastStatus(id, inst.status);
                
                // Monitor/Connect OPC UA client for Recorder to populate node tree and subscribe to variables
                new Thread(() -> {
                    OpcUaClient miloClient = null;
                    int retries = 15;
                    while (retries > 0 && inst.process.isAlive()) {
                        try {
                            Thread.sleep(1000);
                            String connectingMsg = "[SYSTEM] Connecting to target OPC UA Server (attempts remaining: " + retries + ")...";
                            inst.logs.add(connectingMsg);
                            WebUIWebSocket.broadcastLog(id, connectingMsg);
                            miloClient = connectToTarget(inst.uri);
                            break;
                        } catch (Exception e) {
                            retries--;
                            if (retries == 0) {
                                String failedMsg = "[SYSTEM] Failed to connect to target OPC UA Server: " + e.getMessage();
                                inst.logs.add(failedMsg);
                                WebUIWebSocket.broadcastLog(id, failedMsg);
                            }
                        }
                    }
                    
                    if (miloClient != null && inst.process.isAlive()) {
                        try {
                            inst.client = miloClient;
                            
                            String connectedMsg = "[SYSTEM] Connected to target. Browsing node tree...";
                            inst.logs.add(connectedMsg);
                            WebUIWebSocket.broadcastLog(id, connectedMsg);
                            
                            List<Map<String, Object>> tree = new CopyOnWriteArrayList<>();
                            browseNodeTree(miloClient, Identifiers.ObjectsFolder, tree, "");
                            inst.nodeTree = tree;
                            WebUIWebSocket.broadcastNodeTree(id, tree);
                            
                            String browsedMsg = "[SYSTEM] Node tree browsed. Subscribing to variables...";
                            inst.logs.add(browsedMsg);
                            WebUIWebSocket.broadcastLog(id, browsedMsg);
                            
                            subscribeToVariables(id, inst, miloClient, tree);
                        } catch (Exception e) {
                            String errInitMsg = "[SYSTEM] Error initializing client: " + e.getMessage();
                            inst.logs.add(errInitMsg);
                            WebUIWebSocket.broadcastLog(id, errInitMsg);
                        }
                    }
                }).start();
            }

        } catch (Exception e) {
            inst.status = "Failed";
            inst.logs.add("[SYSTEM] Start failed: " + e.getMessage());
            WebUIWebSocket.broadcastStatus(id, inst.status);
            WebUIWebSocket.broadcastLog(id, "[SYSTEM] Start failed: " + e.getMessage());
        }
    }

    private static void destroyInstanceProcess(InstanceInfo inst) {
        if (inst.client != null) {
            try {
                inst.client.disconnect().get();
            } catch (Exception e) {
                // ignore
            }
            inst.client = null;
        }
        if (inst.process != null && inst.process.isAlive()) {
            inst.process.destroy();
            try {
                Thread.sleep(200);
                if (inst.process.isAlive()) {
                    inst.process.destroyForcibly();
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static void stopInstance(String id) {
        InstanceInfo inst = instances.get(id);
        if (inst == null) {
            return;
        }
        
        try {
            if ("player".equals(inst.type)) {
                if (inst.client != null) {
                    controlPlayer(inst, 6); // 6 is Stop command in command map
                    inst.logs.add("[SYSTEM] Stopped playback via OPC UA.");
                    WebUIWebSocket.broadcastLog(id, "[SYSTEM] Stopped playback via OPC UA.");
                } else {
                    inst.logs.add("[SYSTEM] Cannot stop: Player OPC UA client is not connected.");
                    WebUIWebSocket.broadcastLog(id, "[SYSTEM] Cannot stop: Player OPC UA client is not connected.");
                }
            } else {
                // Recorder: stop by terminating its process
                inst.logs.add("[SYSTEM] Stopping recorder process...");
                WebUIWebSocket.broadcastLog(id, "[SYSTEM] Stopping recorder process...");
                destroyInstanceProcess(inst);
                inst.status = "Stopped";
                WebUIWebSocket.broadcastStatus(id, inst.status);
            }
        } catch (Exception e) {
            inst.logs.add("[SYSTEM] Stop error: " + e.getMessage());
            WebUIWebSocket.broadcastLog(id, "[SYSTEM] Stop error: " + e.getMessage());
        }
    }

    public static void removeInstance(String id) {
        InstanceInfo inst = instances.get(id);
        if (inst == null) {
            return;
        }
        try {
            inst.logs.add("[SYSTEM] Removing instance and terminating process...");
            WebUIWebSocket.broadcastLog(id, "[SYSTEM] Removing instance and terminating process...");
            destroyInstanceProcess(inst);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to clean up instance during removal: " + e.getMessage());
        }
        instances.remove(id);
    }

    public static void controlPlayerInstance(String id, int commandCode) {
        InstanceInfo inst = instances.get(id);
        if (inst == null || inst.client == null) {
            return;
        }
        
        new Thread(() -> {
            try {
                controlPlayer(inst, commandCode);
                if (commandCode == 1) { // Play
                    inst.logs.add("[SYSTEM] Play command sent.");
                    WebUIWebSocket.broadcastLog(id, "[SYSTEM] Play command sent.");
                } else if (commandCode == 5) { // Pause / Resume
                    inst.logs.add("[SYSTEM] Pause / Resume command sent.");
                    WebUIWebSocket.broadcastLog(id, "[SYSTEM] Pause / Resume command sent.");
                } else if (commandCode == 6) { // Stop
                    inst.logs.add("[SYSTEM] Stop command sent.");
                    WebUIWebSocket.broadcastLog(id, "[SYSTEM] Stop command sent.");
                }
            } catch (Exception e) {
                inst.logs.add("[SYSTEM] Control failed: " + e.getMessage());
                WebUIWebSocket.broadcastLog(id, "[SYSTEM] Control failed: " + e.getMessage());
            }
        }).start();
    }

    private static void controlPlayer(InstanceInfo inst, int commandCode) throws Exception {
        if (inst.client == null) {
            throw new Exception("OPC UA Client is not connected");
        }
        
        int nsIndex = 2; // Default
        try {
            org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort index = inst.client.getNamespaceTable().getIndex(inst.namespace);
            if (index != null) {
                nsIndex = index.intValue();
            }
        } catch (Exception e) {
            // fallback
        }
        
        NodeId parentNodeId = new NodeId(nsIndex, "Player-Control");
        NodeId methodNodeId = new NodeId(nsIndex, "Player-Control/remote-control(x)");
        
        CallMethodRequest request = new CallMethodRequest(
            parentNodeId,
            methodNodeId,
            new Variant[]{ new Variant(commandCode) }
        );
        
        CallMethodResult result = inst.client.call(request).get();
        if (result.getStatusCode().isBad()) {
            throw new Exception("Method call failed with status " + result.getStatusCode());
        }
    }

    private static OpcUaClient connectToPlayer(int port, String uri) throws Exception {
        String cleanUri = uri.startsWith("/") ? uri.substring(1) : uri;
        String endpointUrl = "opc.tcp://127.0.0.1:" + port + "/" + cleanUri;
        
        List<EndpointDescription> endpoints = org.eclipse.milo.opcua.stack.client.DiscoveryClient.getEndpoints(endpointUrl).get();
        EndpointDescription endpoint = endpoints.stream()
            .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
            .findFirst()
            .orElse(endpoints.get(0));
            
        OpcUaClientConfig config = OpcUaClientConfig.builder()
            .setApplicationName(LocalizedText.english("WebUI Client"))
            .setApplicationUri("urn:webui:client")
            .setEndpoint(endpoint)
            .setRequestTimeout(Unsigned.uint(5000))
            .build();
            
        OpcUaClient client = OpcUaClient.create(config);
        client.connect().get();
        return client;
    }

    private static void browseNodeTree(OpcUaClient client, NodeId browseRoot, List<Map<String, Object>> treeList, String parentId) {
        try {
            BrowseDescription browse = new BrowseDescription(
                browseRoot,
                BrowseDirection.Forward,
                Identifiers.References,
                true,
                Unsigned.uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
                Unsigned.uint(BrowseResultMask.All.getValue())
            );
            BrowseResult browseResult = client.browse(browse).get();
            ReferenceDescription[] references = browseResult.getReferences();
            if (references == null) return;
            
            for (ReferenceDescription rd : references) {
                try {
                    NodeId nodeId = rd.getNodeId().toNodeIdOrThrow(client.getNamespaceTable());
                    String nodeIdStr = nodeId.toParseableString();
                    String name = rd.getBrowseName().getName();
                    String type = rd.getNodeClass().name();
                    
                    if (nodeId.getNamespaceIndex().intValue() == 0 && ("Server".equals(name) || "Types".equals(name) || "Views".equals(name))) {
                        continue;
                    }
                    
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", nodeIdStr);
                    node.put("name", name);
                    node.put("type", type);
                    node.put("parentId", parentId);
                    treeList.add(node);
                    
                    if (rd.getNodeClass() == NodeClass.Object) {
                        browseNodeTree(client, nodeId, treeList, nodeIdStr);
                    }
                } catch (Exception ex) {
                    // ignore
                }
            }
        } catch (Exception ex) {
            // ignore
        }
    }

    private static void subscribeToVariables(String instanceId, InstanceInfo inst, OpcUaClient client, List<Map<String, Object>> treeList) {
        try {
            List<MonitoredItemCreateRequest> requests = new ArrayList<>();
            int handleCounter = 1;
            
            for (Map<String, Object> node : treeList) {
                if ("Variable".equals(node.get("type"))) {
                    try {
                        NodeId nodeId = NodeId.parse((String) node.get("id"));
                        UInteger clientHandle = Unsigned.uint(handleCounter++);
                        MonitoringParameters params = new MonitoringParameters(
                            clientHandle,
                            0.0, // default sampling
                            null,
                            Unsigned.uint(10),
                            true
                        );
                        ReadValueId readValueId = new ReadValueId(
                            nodeId,
                            AttributeId.Value.uid(),
                            null,
                            QualifiedName.NULL_VALUE
                        );
                        requests.add(new MonitoredItemCreateRequest(readValueId, MonitoringMode.Reporting, params));
                    } catch (Exception e) {
                        // ignore parse error
                    }
                }
            }
            
            if (requests.isEmpty()) return;
            
            UaSubscription subscription = client.getSubscriptionManager().createSubscription(500.0).get();
            subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                requests,
                (item, id) -> item.setValueConsumer((monitoredItem, value) -> {
                    String nodeIdStr = monitoredItem.getReadValueId().getNodeId().toParseableString();
                    String valStr = value.getValue().getValue() != null ? value.getValue().getValue().toString() : "null";
                    inst.nodeValues.put(nodeIdStr, valStr);
                    WebUIWebSocket.broadcastNodeValue(instanceId, nodeIdStr, valStr);
                    
                    // If this is the RunState variable, update the status and broadcast it to the UI!
                    if ("player".equals(inst.type) && nodeIdStr.endsWith("Player-Control/RunState")) {
                        inst.status = valStr;
                        WebUIWebSocket.broadcastStatus(instanceId, valStr);
                    }
                })
            ).get();
        } catch (Exception e) {
            inst.logs.add("[SYSTEM] Subscription failed: " + e.getMessage());
            WebUIWebSocket.broadcastLog(instanceId, "[SYSTEM] Subscription failed: " + e.getMessage());
        }
    }

    private static OpcUaClient connectToTarget(String endpointUrl) throws Exception {
        List<EndpointDescription> endpoints = org.eclipse.milo.opcua.stack.client.DiscoveryClient.getEndpoints(endpointUrl).get();
        EndpointDescription endpoint = endpoints.stream()
            .filter(e -> e.getSecurityPolicyUri().equals(SecurityPolicy.None.getUri()))
            .findFirst()
            .orElse(endpoints.get(0));
            
        OpcUaClientConfig config = OpcUaClientConfig.builder()
            .setApplicationName(LocalizedText.english("WebUI Client"))
            .setApplicationUri("urn:webui:client")
            .setEndpoint(endpoint)
            .setRequestTimeout(Unsigned.uint(5000))
            .build();
            
        OpcUaClient client = OpcUaClient.create(config);
        client.connect().get();
        return client;
    }

    public static List<Map<String, Object>> parseConfigToNodeTree(File configFile) {
        List<Map<String, Object>> tree = new ArrayList<>();
        if (configFile == null || !configFile.exists()) {
            return tree;
        }
        
        if (configFile.getName().endsWith(".json")) {
            try (FileReader fileReader = new FileReader(configFile)) {
                Gson gson = new Gson();
                java.lang.reflect.Type listType = new TypeToken<List<OpcNodeConfig>>(){}.getType();
                List<OpcNodeConfig> configs = gson.fromJson(fileReader, listType);
                if (configs != null) {
                    java.util.Set<String> allNodeIds = new java.util.HashSet<>();
                    for (OpcNodeConfig config : configs) {
                        if (config.nodeId != null) {
                            allNodeIds.add(config.nodeId);
                        }
                    }
                    
                    for (OpcNodeConfig config : configs) {
                        if (config.nodeId == null) continue;
                        
                        Map<String, Object> node = new HashMap<>();
                        node.put("id", config.nodeId);
                        node.put("name", config.displayName != null ? config.displayName : 
                                        (config.browseName != null ? config.browseName.name : config.nodeId));
                        node.put("type", config.nodeClass != null ? config.nodeClass : "Variable");
                        
                        String parentId = "";
                        if (config.references != null) {
                            for (OpcNodeConfig.OpcReference ref : config.references) {
                                if (!ref.isForward && ref.targetNodeId != null && allNodeIds.contains(ref.targetNodeId)) {
                                    parentId = ref.targetNodeId;
                                    break;
                                }
                            }
                        }
                        node.put("parentId", parentId);
                        tree.add(node);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error parsing JSON config to tree", e);
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line = reader.readLine();
                if (line != null) {
                    if (line.contains("nodeId") || line.contains("NodeId")) {
                        // skip header
                    } else {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            Map<String, Object> node = new HashMap<>();
                            node.put("id", line);
                            node.put("name", line);
                            node.put("type", "Variable");
                            node.put("parentId", "");
                            tree.add(node);
                        }
                    }
                }
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        Map<String, Object> node = new HashMap<>();
                        node.put("id", line);
                        node.put("name", line);
                        node.put("type", "Variable");
                        node.put("parentId", "");
                        tree.add(node);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error parsing CSV config to tree", e);
            }
        }
        return tree;
    }

    public static void populateNamespaceFromConfig(String id) {
        InstanceInfo inst = instances.get(id);
        if (inst == null) return;
        
        File filesDir = new File("files");
        if (!filesDir.exists()) {
            filesDir = new File("../files");
        }
        File confFile = new File(filesDir, inst.configfile);
        
        List<Map<String, Object>> tree = parseConfigToNodeTree(confFile);
        inst.nodeTree = tree;
        
        WebUIWebSocket.broadcastNodeTree(id, tree);
        
        String logMsg = "[SYSTEM] Populated namespace with " + tree.size() + " nodes from config file: " + inst.configfile;
        inst.logs.add(logMsg);
        WebUIWebSocket.broadcastLog(id, logMsg);
    }
}
