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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mbuurmei
 */
public class AssetConfigurationItem {
    
    private final String assetID;
    private final String assetName;
    private final String measurementPointID;
    private final String measurementPointName;
    private final String phyisicalQuantity;
    private final String unitOfMeasure;
    private final String prefix;
    private final String accessRight;
    private final int lineCounter;
    
    public AssetConfigurationItem(String anAssetID, String anAssetName, String ameasurementPointID, String ameasurementPointName, String aPhysicalQuantity, String aUnitOfMeasure, String aPrefix, String anAccessRight, int aLineCounter) {
        this.assetID = anAssetID;
        this.assetName = anAssetName;
        this.measurementPointID = ameasurementPointID;
        this.measurementPointName = ameasurementPointName;
        this.phyisicalQuantity = aPhysicalQuantity;
        this.unitOfMeasure = aUnitOfMeasure;
        this.prefix = aPrefix;
        this.accessRight = anAccessRight;
        this.lineCounter = aLineCounter;
    }
    
    /**
     * Static method procesConfigLine interprets the configuration line and returns an asset configuration record.
     * @param aConfigLine
     * @param lineCounter
     * @return an asset configuration
     */
    public static AssetConfigurationItem procesConfigLine( String aConfigLine, int lineCounter) {
        AssetConfigurationItem assetConfiguration = null;
        // asset config file is semi-column seperated
        String[] lineItems = aConfigLine.split(";");
        // and 8 columns wide, so check if this is so
        if ( lineItems.length == 8) {
            // create config record
            assetConfiguration = new AssetConfigurationItem(
                    lineItems[0].trim(), // column 1: asset id
                    lineItems[1].trim(), // column 2: asset name
                    lineItems[2].trim(), // column 3: measurement point id
                    lineItems[3].trim(), // column 4: measurement point name
                    lineItems[4].trim(), // column 5: physical quantity
                    lineItems[5].trim(), // column 6: unit of measure
                    lineItems[6].trim(), // column 7: prefix
                    lineItems[7].trim(), // column 8: access right
                    lineCounter
            );
            // check if valid record
            if ( !assetConfiguration.isValid()) {
                assetConfiguration = null;
                Logger.getLogger(AssetConfigurationItem.class.getName()).log(Level.SEVERE, "Error parsing input line " + lineCounter + ", values not correct");
            }
        } else {
            assetConfiguration = null;
            Logger.getLogger(AssetConfigurationItem.class.getName()).log(Level.SEVERE, "Error parsing input line " + lineCounter + ", missing values");
        }
        return assetConfiguration;
    }
    
    /**
     * @return asset names
     */
    public String getAssetName() {
        return this.assetName;
    }

    /**
     * @return the assetID
     */
    public String getAssetID() {
        return assetID;
    }

    /**
     * @return the sensorID
     */
    public String getmeasurementPointID() {
        return measurementPointID;
    }

    /**
     * @return the measurementPointName
     */
    public String getmeasurementPointName() {
        return measurementPointName;
    }
    
    /**
     * Checks if this assetID configuration is valid.
     * @return
     */
    public boolean isValid() {
        boolean result = 
                this.assetID != null && 
                this.assetName != null &&
                this.measurementPointID != null &&
                this.measurementPointName != null && 
                this.phyisicalQuantity != null &&
                this.unitOfMeasure != null &&
                this.prefix != null;
        return result;
    }

    /**
     * @return the phyisicalQuantity
     */
    public String getPhyisicalQuantity() {
        return phyisicalQuantity;
    }

    /**
     * @return the unitOfMeasure
     */
    public String getUnitOfMeasure() {
        return unitOfMeasure;
    }

    /**
     * @return the prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * @return the accessRight
     */
    public String getAccessRight() {
        return accessRight;
    }

    /**
     * @return the lineCounter
     */
    public int getLineCounter() {
        return lineCounter;
    }
}
