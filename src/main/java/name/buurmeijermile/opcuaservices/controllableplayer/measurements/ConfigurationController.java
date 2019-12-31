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
        
        Iterator<String> inputIterator =
                this.getDataStream(); // oper config file in normal order
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
        // create a simaltion controller for simulated measurement points
        this.simulationController = new SimulationController( this.assets); // this controller performs a lot in its constructor!!
        // print the created asset structure
        int index;
        Logger.getLogger( this.getClass().getName()).log(Level.INFO, "The created assets and measurementpoints structure is as follows:");
        index = 0;
        this.printAssetStructure( this.assets.getHierachicalAssets(), index, true);
        Logger.getLogger( this.getClass().getName()).log(Level.INFO, "The flat assets list and measurementpoints is as follows:");
        index = 0;
        this.printAssetStructure( this.assets.getFlattenedAssets(), index, false);
        return this.assets;
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
