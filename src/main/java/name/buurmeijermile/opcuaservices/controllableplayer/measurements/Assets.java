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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class Assets {
    
    private final List<Asset> assets;
    
    public Assets() {
        this.assets = new ArrayList<>();
    }
    
    public void addAsset( AssetConfigurationItem anAssetConfigurationItem) {
        if ( anAssetConfigurationItem != null) {
            final Asset someAsset = new Asset( anAssetConfigurationItem.getAssetName(), anAssetConfigurationItem.getAssetID());
            Asset existingAsset = this.assets.stream().filter( 
                    asset -> asset.getName().contentEquals( someAsset.getName()) && asset.getId().contentEquals( someAsset.getId())
            ).findFirst().orElse( null);
            Asset resultingAsset; // variable to hold the new or filtered asset from the assets list
            if ( existingAsset == null) {
                this.assets.add(someAsset);
                resultingAsset = someAsset;
            } else {
                resultingAsset = existingAsset;
            }
            MeasurementPoint aMeasurementPoint = 
                    new MeasurementPoint.MeasurementPointBuilder()
                            .setName( anAssetConfigurationItem.getmeasurementPointName())
                            .setId( anAssetConfigurationItem.getmeasurementPointID())
                            .setPhysicalQuantity( anAssetConfigurationItem.getPhyisicalQuantity())
                            .setUnitOfMeasure( anAssetConfigurationItem.getUnitOfMeasure())
                            .setUnitPrefix( anAssetConfigurationItem.getPrefix())
                            .setAccesRight( anAssetConfigurationItem.getAccessRight())
                            .build();
            if ( aMeasurementPoint != null) {
                resultingAsset.addMeasurementPoint( aMeasurementPoint);
                Logger.getLogger( DataBackendController.class.getName()).log(Level.INFO, "line(" + anAssetConfigurationItem.getLineCounter() + ")=> assetID=" + someAsset.getName());
            } else {
                Logger.getLogger( DataBackendController.class.getName()).log(Level.SEVERE, "line(" + anAssetConfigurationItem.getLineCounter() + ")=> assetID=" + someAsset.getName() + " has an invalid measurement point configuration");
            }
        }
    }
    
    public List<Asset> getAssets() {
        return this.assets;
    }
}
