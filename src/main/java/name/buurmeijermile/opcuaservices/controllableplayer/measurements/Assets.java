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
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class Assets {
    
    public static String ASSETNAMESSEPARATORREGEX = "\\" + MeasurementPoint.ASSETNAMESEPERATORTOKEN; // the dot is the asset name seperator token
    
    private List<Asset> flatAssetList; // linear list holding all assets in flat hierarchy structure
    private final List<Asset> hierarchicalAssetList; // short list of only the top level flatAssetList in the hierarchy
    private boolean simulations = false;
    
    public Assets() {
        this.flatAssetList = new ArrayList<>();
        this.hierarchicalAssetList = new ArrayList<>();
    }
    
    public void addAsset( AssetConfigurationItem anAssetConfigurationItem) {
        // check if real assetconfiguration item was supplied
        if ( anAssetConfigurationItem != null) {
            // create an asset to hold this supplied configuration
            Asset someAsset = new Asset( anAssetConfigurationItem.getAssetName(), anAssetConfigurationItem.getAssetID());
            // place asset in hierarchy
            Asset theAsset = this.findOrPlaceAssetInHierarchy( someAsset);
            // check if the asset was found
            if ( theAsset == null) {
                Logger.getLogger( this.getClass().getName()).log(Level.SEVERE, "line(" + anAssetConfigurationItem.getLineCounter() + ")=> assetID=" + someAsset.getName() + " was not found/placed in existing hierarchy");
                return;
            }
            // create a measurement point that can be linked to this asset
            MeasurementPoint aMeasurementPoint = 
                    new MeasurementPointBuilder()
                            .setName( anAssetConfigurationItem.getmeasurementPointName())
                            .setId( anAssetConfigurationItem.getmeasurementPointID())
                            .setPhysicalQuantity( anAssetConfigurationItem.getPhyisicalQuantity())
                            .setUnitOfMeasure( anAssetConfigurationItem.getUnitOfMeasure())
                            .setUnitPrefix( anAssetConfigurationItem.getPrefix())
                            .setAccessRight( anAssetConfigurationItem.getAccessRight())
                            .setDataType( anAssetConfigurationItem.getDataType())
                            .setParentAsset( theAsset)
                            .build();
            // check if measurement point was properly build, its not when incomplete
            if ( aMeasurementPoint != null) {
                theAsset.addMeasurementPoint( aMeasurementPoint); // add measurement point to asset
                if ( aMeasurementPoint.isSimulated()) {
                    this.simulations = true; // make note that there is at least one simulated measurement point in the configured assets
                }
            } else {
                Logger.getLogger( DataFilePlayerController.class.getName()).log(Level.SEVERE, "line(" + anAssetConfigurationItem.getLineCounter() + ")=> assetID=" + someAsset.getName() + " has an invalid measurement point configuration");
            }
        }
    }
    
    /**
     * Finds the assets in the asset hierarchy or adds them to the existing hierarchy if they are not found.
     * The name of the asset may contain separators, normally dots in OPC UA, to reflect an hierarchy.
     * @param anAsset
     * @return 
     */
    private Asset findOrPlaceAssetInHierarchy( Asset anAsset) {
        // tokenize the assetname
        String [] nameParts = anAsset.getName().split(ASSETNAMESSEPARATORREGEX);
        List<String> namePartsList = Arrays.asList(nameParts);
        int index = 0; // the top level in the hierarchy we are searching matching flatAssetList
        if (namePartsList.size() > 0) {
            // iterate over the parts to check what part of this hierarchy is present or needs to be created
            return this.findOrPlaceAssetInHierarchy( null, namePartsList, index, anAsset);
        } else {
            return null; // this is an faulty situation
        }
    }
    
    private String gerRightNamePart( List<String> namePartsList, int index) {
        return namePartsList.get( index);
    }
    
    private Asset findOrPlaceAssetInHierarchy( Asset parentAsset, final List<String> namePartsList, final int index, Asset anAsset) {
        if ( namePartsList == null || index >= namePartsList.size()) {
            // TODO: log error
            return null;
        }
        Asset resultAsset = null;
        List<Asset> theCurrentAssetList;
        // check for initial situation where it start finding flatAssetList
        if (parentAsset == null) {
            // OK we are now at the top level, so the main asset list
            theCurrentAssetList = this.hierarchicalAssetList;
        } else {
            theCurrentAssetList = parentAsset.getChildren();
        }
        // find asset on this level in the hierarchy      
        Asset theAsset = theCurrentAssetList.stream().filter( asset -> asset.getShortName().contentEquals( namePartsList.get(index))).findFirst().orElse( null);
        // check if asset was found
        if ( theAsset !=  null) {
            // OK it existed, check if the ID is correct
            if ( namePartsList.size() - 1 == index) {
                // OK we are processing a leaf, lets reset its name to the ID we just read => complex issue in hierarchy creation when they are in randowm order in the config file
                theAsset.setId( anAsset.getId());
            }
            resultAsset = theAsset;
        } else {
            // OK not found, so prepare asset in hierarchy
            String assetName = gerRightNamePart( namePartsList, index); // only use the part name we are processing now
            // create randomID for assets that are not the lead asset for the current namePartsList we are processing
            String assetId;
            if (namePartsList.size() - 1 > index) {
                assetId = Asset.RANDOMID;
            } else {
                assetId = anAsset.getId();
            }
            Asset newAsset = anAsset.getCopy( assetName, assetId);
            newAsset.setParent( parentAsset);
            // lets add anAsset to this level
            if (parentAsset == null) {
                theCurrentAssetList.add( newAsset);
            } else {
                parentAsset.addChild(newAsset);
            }
            resultAsset = newAsset;
        }
        int nextIndex = index + 1; // move to the next level in the hierarchy
        // check if the asset that we work on has more hierarchy details to process
        if (nextIndex < namePartsList.size()) { // this is the end check of the recursive calls
            // make the recursive call
            resultAsset = this.findOrPlaceAssetInHierarchy( resultAsset, namePartsList, nextIndex, anAsset);
        }
        // return the deepest asset found in the hierarchy
        return resultAsset;
    }
    
    public List<Asset> getFlattenedAssets() {
        List<Asset> tempFlatAssetList = new ArrayList<>();
        if (this.flatAssetList == null || this.flatAssetList.size() == 0) {
            this.flatAssetList = this.getFlattendAssets( this.hierarchicalAssetList, 0, tempFlatAssetList);
        }
        return this.flatAssetList;
    }
    
    public List<Asset> getFlattendAssets( List<Asset> startList, int level, List<Asset> destinationList) {
        startList.forEach( asset -> {
            if ( !destinationList.contains( asset)) {
                destinationList.add( asset);
            }
            int newLevel = level + 1;
            List<Asset> children = asset.getChildren();
            if ( children != null && children.size() > 0) {
                List<Asset> flatChildList = getFlattendAssets( children, newLevel, destinationList);
                flatChildList.forEach( flatChild -> {
                    if ( !destinationList.contains( flatChild)) {
                        destinationList.add( flatChild);
                    }
                });
           }
        });
        return destinationList;
    }
        
    public List<Asset> getHierachicalAssets() {
        return this.hierarchicalAssetList;
    }
    
    public List<MeasurementPoint> getSimulatedMeasurementPoints(){
        return this.getMeasurementPoints( true);
    }
    
    public List<MeasurementPoint> getAllMeasurementPoints(){
        return this.getMeasurementPoints( false);
    }

    public List<MeasurementPoint> getMeasurementPoints( boolean onlySimulated){
        List<MeasurementPoint> resultList = new ArrayList<>();
        this.flatAssetList = this.getFlattenedAssets(); // make sure we have got the latest list
        // iterate over the assets and their measurement points
        for (Asset anAsset: this.flatAssetList) {
            List<MeasurementPoint> measurementPointList = anAsset.getMeasurementPoints();
            for (MeasurementPoint aMeasurementPoint: measurementPointList) {
                if (onlySimulated) {
                    if ( aMeasurementPoint.isSimulated()) { 
                        resultList.add(aMeasurementPoint);
                    }
                } else {
                    resultList.add(aMeasurementPoint);
                }
            }
        }
//        this.flatAssetList.forEach( asset -> 
//                asset.getMeasurementPoints()
//                        .stream()
//                        .filter( measurementPoint -> onlySimulated ? measurementPoint.isSimulated() : true)
//                        .forEach( measurementPoint -> resultList.add( measurementPoint))
//        );
        return resultList;
    }
    /**
     * @return the simulations
     */
    public boolean containsSimulations() {
        return simulations;
    }

    public void addRecordedNode(NodeId nodeId, String dataTypeStr) {
        String identifierStr = nodeId.getIdentifier().toString();
        String[] parts = identifierStr.split("\\.");
        
        Asset targetAsset = null;
        String measurementPointName;
        if (parts.length > 1) {
            // build hierarchy
            StringBuilder assetNameBuilder = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) {
                    assetNameBuilder.append(".");
                }
                assetNameBuilder.append(parts[i]);
            }
            String assetName = assetNameBuilder.toString();
            Asset someAsset = new Asset(assetName, assetName);
            targetAsset = this.findOrPlaceAssetInHierarchy(someAsset);
            measurementPointName = parts[parts.length - 1];
        } else {
            // no dots, put under default top-level asset "RecordedNodes"
            Asset defaultAsset = new Asset("RecordedNodes", "RecordedNodes");
            targetAsset = this.findOrPlaceAssetInHierarchy(defaultAsset);
            measurementPointName = identifierStr;
        }
        
        // build measurement point
        MeasurementPoint aMeasurementPoint = new MeasurementPointBuilder()
                .setName(measurementPointName)
                .setId("0")
                .setPhysicalQuantity("NoQuantity")
                .setUnitOfMeasure("NoUoM")
                .setUnitPrefix("NoPrefix")
                .setAccessRight("Read")
                .setDataType(dataTypeStr)
                .setParentAsset(targetAsset)
                .build();
                 
        if (aMeasurementPoint != null) {
            aMeasurementPoint.setCustomNodeId(nodeId);
            targetAsset.addMeasurementPoint(aMeasurementPoint);
        }
    }

    private List<OpcNodeConfig> opcNodeConfigs = null;

    public void setOpcNodeConfigs(List<OpcNodeConfig> configs) {
        this.opcNodeConfigs = configs;
    }

    public List<OpcNodeConfig> getOpcNodeConfigs() {
        return this.opcNodeConfigs;
    }

    public boolean isJsonConfig() {
        return this.opcNodeConfigs != null;
    }

    public void addJsonNodes(List<OpcNodeConfig> configs) {
        for (OpcNodeConfig config : configs) {
            if (config.nodeClass != null && config.nodeClass.equalsIgnoreCase("Variable")) {
                // Parse the NodeId to get the identifier
                NodeId nodeId = NodeId.parse(config.nodeId);
                String identifierStr = nodeId.getIdentifier().toString();
                
                // Only add main variables (skip properties / sub-nodes)
                if (identifierStr.contains("/")) {
                    continue;
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
                if (isProperty) {
                    continue;
                }
                
                String[] parts = identifierStr.split("\\.");
                Asset targetAsset = null;
                String measurementPointName;
                
                if (parts.length > 1) {
                    StringBuilder assetNameBuilder = new StringBuilder();
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (i > 0) {
                            assetNameBuilder.append(".");
                        }
                        assetNameBuilder.append(parts[i]);
                    }
                    String assetName = assetNameBuilder.toString();
                    Asset someAsset = new Asset(assetName, assetName);
                    targetAsset = this.findOrPlaceAssetInHierarchy(someAsset);
                    measurementPointName = parts[parts.length - 1];
                } else {
                    Asset defaultAsset = new Asset("RecordedNodes", "RecordedNodes");
                    targetAsset = this.findOrPlaceAssetInHierarchy(defaultAsset);
                    measurementPointName = identifierStr;
                }
                
                String dataTypeStr = getDataTypeString(config.dataType);
                
                MeasurementPoint aMeasurementPoint = new MeasurementPointBuilder()
                        .setName(measurementPointName)
                        .setId("0")
                        .setPhysicalQuantity("NoQuantity")
                        .setUnitOfMeasure("NoUoM")
                        .setUnitPrefix("NoPrefix")
                        .setAccessRight("Read")
                        .setDataType(dataTypeStr)
                        .setParentAsset(targetAsset)
                        .build();
                
                if (aMeasurementPoint != null) {
                    aMeasurementPoint.setCustomNodeId(nodeId);
                    targetAsset.addMeasurementPoint(aMeasurementPoint);
                }
            }
        }
    }

    private String getDataTypeString(String dataTypeNodeId) {
        if (dataTypeNodeId == null) return "Float";
        if (dataTypeNodeId.endsWith("i=1")) return "Boolean";
        if (dataTypeNodeId.endsWith("i=2")) return "SByte";
        if (dataTypeNodeId.endsWith("i=3")) return "Byte";
        if (dataTypeNodeId.endsWith("i=4")) return "Int16";
        if (dataTypeNodeId.endsWith("i=5")) return "UInt16";
        if (dataTypeNodeId.endsWith("i=6")) return "Int32";
        if (dataTypeNodeId.endsWith("i=7")) return "UInt32";
        if (dataTypeNodeId.endsWith("i=8")) return "Int64";
        if (dataTypeNodeId.endsWith("i=9")) return "UInt64";
        if (dataTypeNodeId.endsWith("i=10")) return "Float";
        if (dataTypeNodeId.endsWith("i=11")) return "Double";
        if (dataTypeNodeId.endsWith("i=12")) return "String";
        if (dataTypeNodeId.endsWith("i=13")) return "DateTime";
        return "String";
    }
}
