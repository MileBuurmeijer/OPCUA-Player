/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.IdType;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class NodeListFileController {

    private File configFile;
    private Logger logger = Logger.getLogger(NodeListFileController.class.getName());

    public NodeListFileController() {
        boolean captureNodeList = Configuration.getConfiguration().isCaptureInformationModel();
        configFile = Configuration.getConfiguration().getConfigFile();
        if (captureNodeList) {
            try {
                if (configFile.exists()) {
                    boolean deleted = configFile.delete();
                    if (!deleted) {
                        logger.log(Level.WARNING, "The existing config file coould not be deleted prior to creating a new one");
                    }
                }
                boolean isCreated = configFile.createNewFile();
                if (!isCreated) {
                    configFile = null;
                    logger.log(Level.SEVERE, "Can not create the configuration file");
                    System.exit(Configuration.ExitCode.CONFIGFILEEXISTS.ordinal());
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "IO error occured when working with the config file", ex);
            }
        }
    }

    public void writeNodeIdConfigFile(List<NodeId> nodeIdList) {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter( this.configFile);
            this.writeHeader( fileWriter);
            for (NodeId aNodeId : nodeIdList) {
                String fullNodeName = this.getFullNodeName( aNodeId);
                if (fullNodeName != null) {
                    this.writeNodeName( fileWriter, fullNodeName);
                }
            }
            logger.log(Level.INFO, "Ready with writing the configuration file");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error in writing to resulting configuration file", ex);
        } finally {
            try {
                fileWriter.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error in closing the resulting configuration file", ex);
            }
        }
    }
    
    public List<NodeId> readNodeIdConfigFile() {
        FileReader fileReader = null;
        List<NodeId> nodeIdList = new ArrayList<>();
        try {
            fileReader = new FileReader( this.configFile);
            BufferedReader bufferedReader = new BufferedReader( fileReader);
            this.readHeader( bufferedReader);
            bufferedReader.lines().forEach( line -> {
                NodeId aNodeId = NodeId.parse(line);
                nodeIdList.add(aNodeId);
            });
        } catch (Exception ex) {
            logger.log( Level.SEVERE, "Error reading from the config file", ex);
        }
        return nodeIdList;
    } 
    
    private void writeNodeName( FileWriter theFileWriter, String fullNodeName) {
        try {
            theFileWriter.append(fullNodeName + "\n");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error appending to the configuration file", ex);
        }
    }
    
    private String getFullNodeName( NodeId aNodeId) {
        UShort namespaceIndex = aNodeId.getNamespaceIndex();
        IdType idType = aNodeId.getType();
        String aNodeIDName = aNodeId.getIdentifier().toString();
        String idTypeToken = null;
        switch (idType) {
            case Numeric: {
                idTypeToken = "i";
                break;
            }
            case String: {
                idTypeToken = "s";
                break;
            }
            default: {
                logger.log(Level.WARNING, "not implemented NodeID type occured");
                break;
            }
        }
        if (idTypeToken != null) {
            String fullNodeName = "ns=" + namespaceIndex.toString() + ";" + idTypeToken + "=" + aNodeIDName;
            return fullNodeName;
        } else {
            return null;
        }
    }

    private void writeHeader( FileWriter theFileWriter) {
        try {
            theFileWriter.append("FullNodeName\n");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error appending to the configuration file", ex);
        }
    }

    private void readHeader( BufferedReader bufferedReader) {
        try {
            String header = bufferedReader.readLine();
            logger.log(Level.INFO, "Header read from configfile:" + header);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Exception occured when reading the header of the config file", ex);
        }
    }
}
