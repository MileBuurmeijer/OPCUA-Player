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
                            .setAccesRight( anAssetConfigurationItem.getAccessRight())
                            .setParentAsset( theAsset)
                            .build();
            // check if measurement point was properly build, its not when incomplete
            if ( aMeasurementPoint != null) {
                theAsset.addMeasurementPoint( aMeasurementPoint); // add measurement point to asset
                if ( aMeasurementPoint.isSimulated()) {
                    this.simulations = true; // make note that there is at least one simulated measurement point in the configured assets
                }
                Logger.getLogger( DataFilePlayerController.class.getName()).log(Level.INFO, "line(" + anAssetConfigurationItem.getLineCounter() + ")=> assetID=" + someAsset.getName());
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
}
