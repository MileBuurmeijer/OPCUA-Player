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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;

/**
 *
 * @author mbuurmei
 */
public abstract class PointInTime {

    public static final Map<String, NodeId> SUPPORTED_DATATYPES = Stream.of(new Object[][]{{"Boolean", Identifiers.Boolean}, {"SByte", Identifiers.SByte}, {"Byte", Identifiers.Byte}, {"Integer", Identifiers.Integer}, {"Int16", Identifiers.Int16}, {"UInt16", Identifiers.UInt16}, {"Int32", Identifiers.Int32}, {"UInt32", Identifiers.UInt32}, {"Int64", Identifiers.Int64}, {"UInt64", Identifiers.UInt64}, {"Float", Identifiers.Float}, {"Double", Identifiers.Double}, {"String", Identifiers.String}, {"DateTime", Identifiers.DateTime}, {"Guid", Identifiers.Guid}, {"ByteString", Identifiers.ByteString}, {"XmlElement", Identifiers.XmlElement}}
        ).collect(Collectors.toMap((data) -> (String) data[0], (data) -> (NodeId) data[1]));
    public static final List<NodeId> DISCRETENODEITEMS = Arrays.asList(new NodeId[]{Identifiers.Byte, Identifiers.SByte, Identifiers.Integer, Identifiers.Int16, Identifiers.Int32, Identifiers.Int64, Identifiers.UInteger, Identifiers.UInt16, Identifiers.UInt32, Identifiers.UInt64});
    public static final List<NodeId> ANALOGNODEITEMS = Arrays.asList(new NodeId[]{Identifiers.Float, Identifiers.Double});
    public static final List<NodeId> SPECIALNODEITEMS = Arrays.asList(new NodeId[]{Identifiers.String, Identifiers.DateTime, Identifiers.Guid, Identifiers.ByteString, Identifiers.XmlElement, Identifiers.LocalizedText, Identifiers.QualifiedName, Identifiers.NodeId, Identifiers.BaseDataType, Identifiers.Duration, Identifiers.UtcTime});
    // implement all Milo OPC UA SDK supported OPC UA data types

    public static enum ACCESS_RIGHT { Read, Write, Both}
    public static enum BASE_UNIT_OF_MEASURE { Ampere, Voltage, Gram, Meter, Newton, NoUoM }
    public static enum PHYSICAL_QUANTITY { Current, Power, Mass, Length, Force, NoQuantity }
    public static enum UNIT_PREFIX { Giga, Mega, Kilo, NoPrefix, Milli, Micro, Nano }
    
    public static Double MIN_SAMPLING_INTERVAL = Double.valueOf(10.0f); // the minimum sampling rate in ms. I.e. this would mean the client can retrieve up a maximum of 100 samples per second with this setting.
    
    private String name;
    private int id;
    private BASE_UNIT_OF_MEASURE baseUnitOfMeasure;
    private PHYSICAL_QUANTITY physicalQuantity;
    private UNIT_PREFIX unitPrefix;
    private ACCESS_RIGHT accessRight = ACCESS_RIGHT.Read; // default value
    private Double minumumSamplingInterval = MIN_SAMPLING_INTERVAL;
    private NodeId dataType;
    
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
    public PointInTime( int anID, String aName, PHYSICAL_QUANTITY aPhyscialQuantity, BASE_UNIT_OF_MEASURE aBaseUoM, UNIT_PREFIX aUnitPrefix, NodeId aDataType) {
        this.id = anID;
        this.name = aName;
        this.physicalQuantity = aPhyscialQuantity;
        this.baseUnitOfMeasure = aBaseUoM;
        this.unitPrefix = aUnitPrefix;
        this.dataType = aDataType;
    }
    
    public static Class<?> getBackingClass(NodeId aNodeId) {
        switch ( aNodeId.hashCode()) {
            case 1:  return Boolean.class;  // Boolean
            case 2:  return Byte.class;     // SByte
            case 3:  return Byte.class;     // Byte
            case 4:  return Short.class;    // Int16
            case 5:  return UShort.class;   // UInt16
            case 6:  return Integer.class;  // Int32
            case 7:  return UInteger.class; // UInt32
            case 8:  return Long.class;     // Int64
            case 9:  return ULong.class;    // UInt64
            case 10: return Float.class;    // Float
            case 11: return Double.class;   // Double
            case 12: return String.class;   // String
            case 13: return DateTime.class; // DateTime
            case 14: return UUID.class;     // Guid
            case 15: return ByteString.class; // ByteString
            case 16: return XmlElement.class; // XmlElement
            default: return null;
        }
    }

    public static String getDefaultValue(NodeId aNodeId) {
        switch ( aNodeId.hashCode()) {
            case 1:  return Boolean.FALSE.toString(); // boolean
            case 2:  return "0"; // SByte
            case 3:  return "0"; // Byte
            case 4:  return "0"; // Int16
            case 5:  return "0"; // UInt16
            case 6:  return "0"; // Int32
            case 7:  return "0"; // UInt32
            case 8:  return "0"; // Int64
            case 9:  return "0"; // UInt64
            case 10: return "0.0"; // Float
            case 11: return "0.0"; // Double
            case 12: return "";    // String
            case 13: return null;  // DateTime
            case 14: return "0";   // Guid
            case 15: return "";    // ByteString
            case 16: return "";    // XmlElement
            default: return null;
        }
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
    public BASE_UNIT_OF_MEASURE getTheBaseUnitOfMeasure() {
        return baseUnitOfMeasure;
    }

    /**
     * @return the physicalQuantity
     */
    public PHYSICAL_QUANTITY getThePhysicalQuantity() {
        return physicalQuantity;
    }

    /**
     * @return the unitPrefix
     */
    public UNIT_PREFIX getTheUnitPrefix() {
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
                this.unitPrefix != null &&
                this.dataType != null;
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
    
    public NodeId getDataType() {
        return this.dataType;
    }
    
    public void setDataType( NodeId aDataType) {
        this.dataType = aDataType;
    }
}
