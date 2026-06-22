package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.util.List;
import com.google.gson.JsonElement;

public class OpcNodeConfig {
    public String nodeId;
    public String nodeClass; // "Object", "Variable", "Method", etc.
    public OpcBrowseName browseName;
    public String displayName;
    public String description;
    public String typeDefinition;
    public String dataType;
    public Integer accessLevel;
    public Integer userAccessLevel;
    public JsonElement value;
    public List<OpcReference> references;

    public static class OpcBrowseName {
        public int namespaceIndex;
        public String name;
    }

    public static class OpcReference {
        public String referenceTypeId;
        public boolean isForward;
        public String targetNodeId;
    }
}
