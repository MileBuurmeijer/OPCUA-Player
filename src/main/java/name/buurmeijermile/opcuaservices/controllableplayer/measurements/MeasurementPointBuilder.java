/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class MeasurementPointBuilder {
    // implement all Milo OPC UA SDK supported OPC UA data types    
    private static final Map<String, NodeId> SUPPORTED_DATATYPES = 
            Stream.of(new Object[][] {
        {"Boolean", Identifiers.Boolean},
        {"Byte", Identifiers.Byte},
        {"SByte", Identifiers.SByte},
        {"Integer", Identifiers.Integer},
        {"Int16", Identifiers.Int16},
        {"Int32", Identifiers.Int32},
        {"Int64", Identifiers.Int64},
        {"UInteger", Identifiers.UInteger},
        {"UInt16", Identifiers.UInt16},
        {"UInt32", Identifiers.UInt32},
        {"UInt64", Identifiers.UInt64},
        {"Float", Identifiers.Float},
        {"Double", Identifiers.Double},
        {"String", Identifiers.String},
        {"DateTime", Identifiers.DateTime},
        {"Guid", Identifiers.Guid},
        {"ByteString", Identifiers.ByteString},
        {"XmlElement", Identifiers.XmlElement},
        {"LocalizedText", Identifiers.LocalizedText},
        {"QualifiedName", Identifiers.QualifiedName},
        {"NodeId", Identifiers.NodeId},
        {"Variant", Identifiers.BaseDataType},
        {"Duration", Identifiers.Duration},
        {"UtcTime", Identifiers.UtcTime},
    }).collect(Collectors.toMap(data -> (String) data[0], data -> (NodeId) data[1]));
    
    private final MeasurementPoint measurementPoint;

    public MeasurementPointBuilder() {
        this.measurementPoint = new MeasurementPoint();
    }

    public MeasurementPointBuilder setId(String anId) {
        try {
            int id = Integer.parseInt(anId);
            this.measurementPoint.setId(id);
        } catch (NumberFormatException nfe) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert String " + anId + " to integer", nfe);
        }
        return this;
    }

    public MeasurementPointBuilder setName(String aName) {
        this.measurementPoint.setName(aName);
        return this;
    }

    public MeasurementPointBuilder setPhysicalQuantity(String aPhysicalQuantity) {
        try {
            PointInTime.PHYSICAL_QUANTITY physicalQuantity = PointInTime.PHYSICAL_QUANTITY.valueOf(aPhysicalQuantity);
            this.measurementPoint.setPhysicalQuantity(physicalQuantity);
        } catch (IllegalArgumentException iae) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert physical quantity " + aPhysicalQuantity + " to a PHYSICAL_QUANTITY", iae);
        }
        return this;
    }

    public MeasurementPointBuilder setUnitOfMeasure(String aBaseUnitOfMeasure) {
        try {
            PointInTime.BASE_UNIT_OF_MEASURE unitOfMeasure = PointInTime.BASE_UNIT_OF_MEASURE.valueOf(aBaseUnitOfMeasure);
            this.measurementPoint.setBaseUnitOfMeasure(unitOfMeasure);
        } catch (IllegalArgumentException iae) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert base unit of measure " + aBaseUnitOfMeasure + " to a BASE_UNIT_OF_MEASURE", iae);
        }
        return this;
    }

    public MeasurementPointBuilder setUnitPrefix(String aUnitPrefix) {
        try {
            PointInTime.UNIT_PREFIX unitPrefix = PointInTime.UNIT_PREFIX.valueOf(aUnitPrefix);
            this.measurementPoint.setTheUnitPrefix(unitPrefix);
        } catch (IllegalArgumentException iae) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert unit prefix " + aUnitPrefix + " to a UNIT_PREFIX", iae);
        }
        return this;
    }

    public MeasurementPointBuilder setAccessRight(String anAccesRight) {
        try {
            PointInTime.ACCESS_RIGHT accessRight = PointInTime.ACCESS_RIGHT.valueOf(anAccesRight);
            this.measurementPoint.setAccessRight(accessRight);
        } catch (IllegalArgumentException iae) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert acces right " + anAccesRight + " to a ACCESS_RIGHT", iae);
        }
        return this;
    }
    
    public MeasurementPointBuilder setParentAsset( Asset aParentAsset) {
        this.measurementPoint.setParentAsset( aParentAsset);
        return this;
    }
    
    public MeasurementPointBuilder setDataType( String aDataType) {
        NodeId dataType = SUPPORTED_DATATYPES.get( aDataType);
        this.measurementPoint.setDataType( dataType);
        return this;
    }

    /**
     * Builds the measurement point after setting the parameters
     *
     * @return
     */
    public MeasurementPoint build() {
        // let's build it, only if valid
        if (this.measurementPoint.isValid()) {
            this.measurementPoint.setInitialValue();
            return this.measurementPoint;
        } else {
            return null;
        }
    }
}
