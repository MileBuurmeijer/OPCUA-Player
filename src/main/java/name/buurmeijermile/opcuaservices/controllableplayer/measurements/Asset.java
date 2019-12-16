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
import java.util.List;

/**
 * Asset is the object of interest that is exposed through this OPC UA
 * Player. It has one or more measurement points and a name.
 *
 * @author mbuurmei
 */
public class Asset {
    
    public static String RANDOMID = "###";

    private final List<MeasurementPoint> measurementPoints;
    private final String name;
    private final String shortName;
    private String id;
    private Asset parent;
    private List<Asset> children;

    /**
     * Creates an Asset with the given name.
     * @param aName the name of this asset
     */
    public Asset(String aName, String anId) {
        this.measurementPoints = new ArrayList<>();
        this.name = aName;
        this.id = anId;
        this.children = new ArrayList<>();
        this.shortName = this.deriveShortName();
        this.parent = null;
    }

        
    /**
     * getCopy
     * @param asset the copy of the original asset
     */
    public Asset getCopy( String newName, String assetId) {
        return new Asset( newName, assetId);
    }

    private String deriveShortName() {
        String [] parts = this.name.split( Assets.ASSETSEPERATORTOKEN);
        if (parts.length >= 1) {
            return parts[ parts.length - 1];
        } else {
            return this.name;
        }
        
    }

    /**
     * Gets the configured measurement points for this asset.
     * @return list of measurement points of this asset
     */
    public List<MeasurementPoint> getMeasurementPoints() {
        return this.measurementPoints;
    }

    /**
     * Adds a measurement point to this asset
     * @param aMeasurementPoint 
     */
    public void addMeasurementPoint(MeasurementPoint aMeasurementPoint) {
        this.measurementPoints.add(aMeasurementPoint);
    }

    /**
     * Gets the name of this Asset.
     * @return 
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @return the parent
     */
    public Asset getParent() {
        return parent;
    }

    /**
     * @param parent the parent to set
     */
    public void setParent(Asset parent) {
        this.parent = parent;
    }

    /**
     * @return the children
     */
    public List<Asset> getChildren() {
        return children;
    }
    
    /**
     * set child
     * @param 
     */
    public void addChild( Asset child) {
        // check if it already exists
        if (!this.children.contains( child)) {
            this.children.add( child);
        }
    }

    /**
     * @return the shortName
     */
    public String getShortName() {
        return shortName;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }
}
