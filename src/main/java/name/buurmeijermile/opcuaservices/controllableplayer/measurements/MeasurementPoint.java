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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 *
 * @author mbuurmei
 */
public class MeasurementPoint extends PointInTime {
    public static final int MAXSAMPLES = 1000;
    
    private MeasurementSample theCurrentMeasurementSample = null;
    private UaVariableNode uaVariableNode;
    private ZoneOffset zoneOffset;
    
    /**
     * Constructor used by builder
     */
    public MeasurementPoint() {
    }
    
    /**
     * Constructor
     * @param anID an identifier for the measurement point
     * @param aName an descriptive name of this measurement point
     * @param aPhyscialQuantity a physical quantity of what is measured at this point
     * @param aBaseUoM a base unit of measurement
     * @param aUnitPrefix  a prefix of the unit of measure
     */
    public MeasurementPoint( int anID, String aName, PHYSICAL_QUANTITY aPhyscialQuantity, BASE_UNIT_OF_MEASURE aBaseUoM, UNIT_PREFIX aUnitPrefix) {
        super( anID, aName, aPhyscialQuantity, aBaseUoM, aUnitPrefix);
        // set initial value
        this.setInitialValue();
    }

    public void setInitialValue() {
        ZonedDateTime timezoneDateTime = ZonedDateTime.now(); // only used to retrieve platform timezone
        this.zoneOffset = ZoneOffset.from( timezoneDateTime); // timezone offset of runtime platform
        if (this.getThePhysicalQuantity().equals( PHYSICAL_QUANTITY.NoQuantity)) {
            this.setMeasurementSample("0", MeasurementSample.DATAQUALITY.Good, LocalDateTime.now(), this.zoneOffset);
        } else {
            this.setMeasurementSample("0.0", MeasurementSample.DATAQUALITY.Good, LocalDateTime.now(), this.zoneOffset);
        }
    }
    
    /**
     * Set a measurement sample based on the simple parameters
     * @param aValueString the value to set
     * @param theDataQuality the datat quality of the sample
     * @param aTimeStamp the timestamp of the sample
     * @param aZoneOffset the zone offset of the sample
     */
    public void setMeasurementSample( String aValueString, MeasurementSample.DATAQUALITY theDataQuality, LocalDateTime aTimeStamp, ZoneOffset aZoneOffset) {
        // create OPC UA variant based on type of measurement point
        Variant aValue = this.createVariant( aValueString);
        // create measurement sample
        MeasurementSample measurementSample = new MeasurementSample( aValue, theDataQuality, aTimeStamp, aZoneOffset);
        // add measurement sample to this measurement point
        this.setMeasurementSample( measurementSample);
    }

    private Variant createVariant(String aValueString) {
        // base variant parsing on physical quantity type:
        // - if anything then NoQuantity => Double
        // - in NoQuantity => Boolean
        // TODO: implement more data types
        if ( !this.getThePhysicalQuantity().equals( PHYSICAL_QUANTITY.NoQuantity)) {
            // OK this measurement point has a real physical quantity expressed in double values
            return new Variant ( Double.parseDouble( aValueString));
        } else {
            // no physical quantity, so assume it is a boolean with values '0' or '1'
            switch ( aValueString) {
                case "1": {
                    return new Variant( Boolean.TRUE);
                }
                case "0": {
                    return new Variant( Boolean.FALSE);
                }
                default: {
                    return null;
                }
            }
        }
    }
    
    public void setMeasurementSample( MeasurementSample aMeasurementSample) {
        this.theCurrentMeasurementSample = aMeasurementSample;
        if (this.uaVariableNode != null) {
            this.uaVariableNode.setValue( this.theCurrentMeasurementSample.getUADateValue());
        }
    }
    
    @Override
    public DataValue getValue() {
        return new DataValue( 
                new Variant( null), 
                StatusCode.GOOD, 
                new DateTime()
        );
    }
    
    @Override
    public double getDoubleValue() {
        return 0.0f;
    }
    
    public ZoneOffset getZoneOffset() {
        return this.zoneOffset;
    }
    
    /**
     * Add reference to the corresponding OPC UA variable node. This 
     * referenced node is updated whenever the measurement point gets a new
     * measurement sample.
     * @param aUaVariableNode 
     */
    public void setUaVariableNode( UaVariableNode aUaVariableNode) {
        this.uaVariableNode = aUaVariableNode;
        if (this.theCurrentMeasurementSample != null) {
            this.uaVariableNode.setValue( this.theCurrentMeasurementSample.getUADateValue()); // initial value 
        }
    }

    /**
     * @return the theUaVariableNode
     */
    public UaVariableNode getUaVariableNode() {
        return uaVariableNode;
    }
    
    public MeasurementSample getCurrentMeasurementSample() {
        return this.theCurrentMeasurementSample;
    }

    public void clearValue() {
        this.setInitialValue(); // reset to initial value
    }
}