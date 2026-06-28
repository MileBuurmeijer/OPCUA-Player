package name.buurmeijermile.opcuaservices.controllableplayer.web;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@WebSocket
public class WebUIWebSocket {
    private static final Logger logger = Logger.getLogger(WebUIWebSocket.class.getName());
    private static final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private static final Gson gson = new Gson();

    @OnWebSocketConnect
    public void onConnect(Session session) {
        sessions.add(session);
        logger.log(Level.INFO, "Client connected. Active sessions: " + sessions.size());
        
        // Push current state of all instances to the new client
        try {
            Map<String, Object> initMsg = new HashMap<>();
            initMsg.put("type", "init");
            initMsg.put("instances", WebUIServer.instances.values());
            synchronized (session) {
                session.getRemote().sendString(gson.toJson(initMsg));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to send init state to client", e);
        }
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
        logger.log(Level.INFO, "Client disconnected. Active sessions: " + sessions.size());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        try {
            Map<String, Object> msg = gson.fromJson(message, new TypeToken<Map<String, Object>>(){}.getType());
            String action = (String) msg.get("action");
            
            if ("create".equals(action)) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) msg.get("params");
                    WebUIServer.InstanceInfo inst = WebUIServer.createInstance(params);
                    
                    // Broadcast new instance to all clients
                    Map<String, Object> reply = new HashMap<>();
                    reply.put("type", "created");
                    reply.put("instance", inst);
                    broadcast(gson.toJson(reply));
                } catch (Exception e) {
                    sendError(session, "Creation failed: " + e.getMessage());
                }
            } else if ("start".equals(action)) {
                String id = (String) msg.get("id");
                WebUIServer.startInstance(id);
            } else if ("stop".equals(action)) {
                String id = (String) msg.get("id");
                WebUIServer.stopInstance(id);
            } else if ("control".equals(action)) {
                String id = (String) msg.get("id");
                int commandCode = ((Double) msg.get("command")).intValue();
                WebUIServer.controlPlayerInstance(id, commandCode);
            } else if ("remove".equals(action)) {
                String id = (String) msg.get("id");
                WebUIServer.removeInstance(id);
                
                // Broadcast removal to all clients so they remove it from their UI
                Map<String, Object> reply = new HashMap<>();
                reply.put("type", "removed");
                reply.put("id", id);
                broadcast(gson.toJson(reply));
            } else if ("populate_namespace".equals(action)) {
                String id = (String) msg.get("id");
                WebUIServer.populateNamespaceFromConfig(id);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing WebSocket message", e);
        }
    }

    private void sendError(Session session, String errorMsg) {
        try {
            Map<String, String> reply = new HashMap<>();
            reply.put("type", "error");
            reply.put("message", errorMsg);
            synchronized (session) {
                session.getRemote().sendString(gson.toJson(reply));
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private static void broadcast(String text) {
        for (Session session : sessions) {
            if (session.isOpen()) {
                synchronized (session) {
                    try {
                        session.getRemote().sendString(text);
                    } catch (IOException e) {
                        sessions.remove(session);
                    }
                }
            }
        }
    }

    public static void broadcastStatus(String id, String status) {
        Map<String, String> msg = new HashMap<>();
        msg.put("type", "status");
        msg.put("id", id);
        msg.put("status", status);
        broadcast(gson.toJson(msg));
    }

    public static void broadcastLog(String id, String line) {
        Map<String, String> msg = new HashMap<>();
        msg.put("type", "log");
        msg.put("id", id);
        msg.put("line", line);
        broadcast(gson.toJson(msg));
    }

    public static void broadcastNodeTree(String id, Object tree) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "node_tree");
        msg.put("id", id);
        msg.put("tree", tree);
        broadcast(gson.toJson(msg));
    }

    public static void broadcastNodeValue(String id, String nodeId, String value) {
        Map<String, String> msg = new HashMap<>();
        msg.put("type", "node_value");
        msg.put("id", id);
        msg.put("nodeId", nodeId);
        msg.put("value", value);
        broadcast(gson.toJson(msg));
    }
}
