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

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;

/**
 *
 * @author mbuurmei
 */
public abstract class PointInTime {

    public static enum ACCESS_RIGHT { Read, Write, Both}
    public static enum BASE_UNIT_OF_MEASURE { Ampere, Voltage, Gram, Meter, Newton, NoUoM }
    public static enum PHYSICAL_QUANTITY { Current, Power, Mass, Length, Force, NoQuantity }
    public static enum UNIT_PREFIX { Giga, Mega, Kilo, NoPrefix, Milli, Micro, Nano }
    
    public static Double MIN_SAMPLING_INTERVAL = new Double (10.0f); // the minimum sampling rate in ms. I.e. this would mean the client can retrieve up a maximum of 100 samples per second with this setting.
    
    private String name;
    private int id;
    private BASE_UNIT_OF_MEASURE baseUnitOfMeasure;
    private PHYSICAL_QUANTITY physicalQuantity;
    private UNIT_PREFIX unitPrefix;
    private ACCESS_RIGHT accessRight = ACCESS_RIGHT.Read; // default value
    private Double minumumSamplingInterval = MIN_SAMPLING_INTERVAL;
    
    /**
     * Base physical quantities:
     * 
     * Physical Quantity           | Canonical unit
     * --------------------------------------------
     * Electric Current            | ampere (A)
     * Length                      | meter (m)
     * Mass                        | kilogram (kg)
     * Moles (amount of substance) | mole (mol)
     * Plane Angle                 | radian (rad)
     * Quantity                    | count
     * Ratio                       | percent (%)
     * Temperature                 | kelvin (K)
     * Time                        | second (s)
     */
    
    /**
     * For builder pattern.
     */
    public PointInTime() {
    }
    
    /**
     * 
     * @param anID
     * @param aName
     * @param aPhyscialQuantity
     * @param aBaseUoM
     * @param aUnitPrefix
     */
    public PointInTime( int anID, String aName, PHYSICAL_QUANTITY aPhyscialQuantity, BASE_UNIT_OF_MEASURE aBaseUoM, UNIT_PREFIX aUnitPrefix) {
        this.id = anID;
        this.name = aName;
        this.physicalQuantity = aPhyscialQuantity;
        this.baseUnitOfMeasure = aBaseUoM;
        this.unitPrefix = aUnitPrefix;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the baseUnitOfMeasure
     */
    public MeasurementPoint.BASE_UNIT_OF_MEASURE getTheBaseUnitOfMeasure() {
        return baseUnitOfMeasure;
    }

    /**
     * @return the physicalQuantity
     */
    public MeasurementPoint.PHYSICAL_QUANTITY getThePhysicalQuantity() {
        return physicalQuantity;
    }

    /**
     * @return the unitPrefix
     */
    public MeasurementPoint.UNIT_PREFIX getTheUnitPrefix() {
        return unitPrefix;
    }

    /**
     * @return the accessRight
     */
    public ACCESS_RIGHT getReadWriteCapability() {
        return getAccessRight();
    }
    
    public abstract DataValue getValue();
    
    public abstract double getDoubleValue();
    
    public boolean isValid() {
        boolean valid = 
                this.accessRight != null &&
                this.name != null &&
                this.baseUnitOfMeasure != null &&
                this.physicalQuantity != null &&
                this.unitPrefix != null;
        return valid;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
    }
    
    public boolean isSimulated() {
        return false;
    }

    /**
     * @param theBaseUnitOfMeasure the baseUnitOfMeasure to set
     */
    public void setBaseUnitOfMeasure(BASE_UNIT_OF_MEASURE theBaseUnitOfMeasure) {
        this.baseUnitOfMeasure = theBaseUnitOfMeasure;
    }

    /**
     * @param thePhysicalQuantity the physicalQuantity to set
     */
    public void setPhysicalQuantity(PHYSICAL_QUANTITY thePhysicalQuantity) {
        this.physicalQuantity = thePhysicalQuantity;
    }

    /**
     * @param theUnitPrefix the unitPrefix to set
     */
    public void setTheUnitPrefix(UNIT_PREFIX theUnitPrefix) {
        this.unitPrefix = theUnitPrefix;
    }

    /**
     * @return the accessRight
     */
    public ACCESS_RIGHT getAccessRight() {
        return accessRight;
    }

    /**
     * @param accessRight the accessRight to set
     */
    public void setAccessRight(ACCESS_RIGHT accessRight) {
        this.accessRight = accessRight;
    }

    /**
     * @return the minumumSamplingInterval
     */
    public Double getMinimumSamplingInterval() {
        return minumumSamplingInterval;
    }

    /**
     * @param minumumSamplingInterval the minumumSamplingInterval to set
     */
    public void setMinumumSamplingInterval(Double minumumSamplingInterval) {
        this.minumumSamplingInterval = minumumSamplingInterval;
    }
}
