/*
 * The MIT License
 *
 * Copyright 2018 Milé Buurmeijer <mbuurmei at netscape.net>.
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
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class ConfigurationController {
    
    private final File configurationFile;
    private final Assets assets = new Assets();
    private SimulationController simulationController;
    
    public ConfigurationController( File anConfigurationFile) {
        this.configurationFile = anConfigurationFile;
    }
    
    public Assets createAssetStructure() {
        int lineCounter = 0;
        boolean firstTime = true;
        
        if (this.configurationFile != null && this.configurationFile.getName().endsWith(".json")) {
            try (java.io.FileReader reader = new java.io.FileReader(this.configurationFile)) {
                Gson gson = new Gson();
                java.lang.reflect.Type listType = new TypeToken<List<OpcNodeConfig>>(){}.getType();
                List<OpcNodeConfig> configs = gson.fromJson(reader, listType);
                this.assets.setOpcNodeConfigs(configs);
                this.assets.addJsonNodes(configs);
            } catch (Exception ex) {
                Logger.getLogger(ConfigurationController.class.getName()).log(Level.SEVERE, "Error loading JSON config file", ex);
            }
        } else if (Configuration.getConfiguration().isRecordedFormat()) {
            File dataFile = Configuration.getConfiguration().getDataFile();
            Map<String, String> tagToDataType = inferDataTypesFromDataFile(dataFile);
            Iterator<String> inputIterator = this.getDataStream();
            while (inputIterator.hasNext()) {
                String aConfigLine = inputIterator.next();
                lineCounter++;
                if (!firstTime) {
                    if (aConfigLine.trim().isEmpty() || aConfigLine.startsWith("#")) {
                        continue;
                    }
                    try {
                        NodeId nodeId = NodeId.parse(aConfigLine.trim());
                        String tagStr = nodeId.toParseableString();
                        String dataType = tagToDataType.getOrDefault(tagStr, "Float");
                        this.assets.addRecordedNode(nodeId, dataType);
                    } catch (Exception ex) {
                        Logger.getLogger(ConfigurationController.class.getName()).log(Level.SEVERE, "Error parsing recorded NodeId config line " + lineCounter, ex);
                    }
                } else {
                    firstTime = false;
                }
            }
        } else {
            Iterator<String> inputIterator =
                    this.getDataStream(); // open config file in normal order
            while ( inputIterator.hasNext()) {
                String aConfigLine = inputIterator.next();
                lineCounter++;
                // check if  we need to skip the header of the file
                if (!firstTime) {
                    // past header so lets proces these lines into assets and companing measurement point
                    AssetConfigurationItem assetConfiguration = AssetConfigurationItem.procesConfigLine( aConfigLine, lineCounter);
                    this.assets.addAsset( assetConfiguration);
                } else {
                    // OK header skipped
                    firstTime = false;
                }
            }
        }
        // create a simaltion controller for simulated measurement points
        this.simulationController = new SimulationController( this.assets); // this controller performs a lot in its constructor!!

        return this.assets;
    }

    private Map<String, String> inferDataTypesFromDataFile(File dataFile) {
        Map<String, String> tagToDataType = new HashMap<>();
        if (dataFile == null || !dataFile.exists()) {
            return tagToDataType;
        }
        try (BufferedReader reader = Files.newBufferedReader(dataFile.toPath())) {
            String header = reader.readLine();
            if (header == null) {
                return tagToDataType;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // recorded format is comma-separated: Timestamp, Tag, Value
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String tag = parts[1].trim();
                    String valStr = parts[2].trim();
                    if (!tagToDataType.containsKey(tag)) {
                        String dataType = inferDataType(valStr);
                        tagToDataType.put(tag, dataType);
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(ConfigurationController.class.getName()).log(Level.WARNING, "Error scanning data file to infer types", ex);
        }
        return tagToDataType;
    }

    private String inferDataType(String valStr) {
        if (valStr.equalsIgnoreCase("true") || valStr.equalsIgnoreCase("false")) {
            return "Boolean";
        }
        try {
            Integer.parseInt(valStr);
            return "Int32";
        } catch (NumberFormatException e) {
            // not an integer
        }
        try {
            Double.parseDouble(valStr);
            return "Float";
        } catch (NumberFormatException e) {
            // not a double
        }
        return "String";
    }
    
    /**
     * getDatastream open the data input file and returns an iterator to its content.
     * @return iterator to the data lines in the data input file
     */
    private Iterator<String> getDataStream() {
        // check if source file is set
        if ( this.configurationFile != null) {
            // check if source file exists
            if (this.configurationFile.exists()) {
                // check if file can be read
                if (this.configurationFile.canRead()) {
                    return this.openFile( this.configurationFile.toPath());
                } else {
                    // error data file can not be read
                    Logger.getLogger(DataStreamController.class.getName()).log(Level.SEVERE, "Error configuration file can not be read");
                }
            } else {
                // error data file does not exist
                Logger.getLogger(DataStreamController.class.getName()).log(Level.SEVERE, "Error configuration file does not exist");
            }
        } else {
            // error data file is zero
            Logger.getLogger(DataStreamController.class.getName()).log(Level.SEVERE, "Error configuration file is null");
        }
        return null;
    }
    
    private Iterator<String> openFile( Path aPath) {
        // try to open it
        try {
            // open file and get stream of lines
            Stream<String> lines = Files.lines( aPath);
            // check if open was successful
            if (lines != null) {
                Logger.getLogger(DataStreamController.class.getName()).log(Level.INFO, "File " + aPath.getFileName() + " opened");
                // return the iterator
                return lines.iterator();
            } else {
                // flag that no data line stream was returned by java nio
                Logger.getLogger(DataStreamController.class.getName()).log(Level.SEVERE, "No data line stream retrieved from configuration file");
            }
        } catch (IOException ex) {
            // flag that io exceptio was reaised by java nio
            Logger.getLogger(DataStreamController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    } 

    private void printAssetStructure(List<Asset> assets, final int index, boolean includeChilds) {
        assets.forEach( asset -> {
            // print out the asset itself including the measurement points
            Logger.getLogger( this.getClass().getName()).log(Level.INFO, this.printIndent(index) + "Asset(" + asset.getId() + "):" + asset.getName());
            asset.getMeasurementPoints().forEach( mp -> {
                this.printIndent( index);
                System.out.println( this.printIndent(index) + "\tMeasurementpoint(" + mp.getId() + "): " + mp.getName());
            });
            if ( includeChilds) {
                // and prints its children recursevily
                int newIndex = index + 1;
                printAssetStructure( asset.getChildren(), newIndex, includeChilds);
            }
        });
    }

    private String printIndent(int index) {
        String result = "";
        for (int i = 0; i < index; i++) {
            result += "\t";
        }
        return result;
    }   
    
    public void setUAActualSimulationSpeed( MeasurementPoint aMeasurementPoint, UaVariableNode aUaVariableNode) {
        this.getSimulationController().setUAActualSimulationSpeedNode(aMeasurementPoint, aUaVariableNode);
    }

    /**
     * @return the simulationController
     */
    public SimulationController getSimulationController() {
        return simulationController;
    }
}
