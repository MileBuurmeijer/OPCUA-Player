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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ValidationResult;
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
    
    public static final String SIMULATIONTOKEN = "#";
    public static final String VARIABLESPLITTOKEN = ",";
    public static final String ASSETNAMESEPERATORTOKEN = "."; // the dot is the asset name seperator token    
    
    private MeasurementSample theCurrentMeasurementSample = null;
    private UaVariableNode uaVariableNode;
    private ZoneOffset zoneOffset;
    private boolean simulated = false;
    private Expression simulationExpression;
    private int simulationUpdateFrequency = 1000; // samples per second, 1000 = default value, TODO move to Configuration   
    private Map<String, MeasurementPoint> dependingMeasurementPoints = new HashMap<>(); // all the measurement point is depend on for simulation
    private Asset parentAsset; // the asset this measurement point belongs to
    private String fullDottedName; // <name of the parent asset>.<name of measurement point>
   
    /**
     * No argument constructor used by builder
     */
    public MeasurementPoint() {
    }
    
    public void setInitialValue() {
        // TODO: check if this is OK for simulated values
        ZonedDateTime timezoneDateTime = ZonedDateTime.now(); // only used to retrieve platform timezone
        this.zoneOffset = ZoneOffset.from( timezoneDateTime); // timezone offset of runtime platform
        if (this.getThePhysicalQuantity().equals( PHYSICAL_QUANTITY.NoQuantity)) {
            this.setMeasurementSample("0", MeasurementSample.DATAQUALITY.Good, LocalDateTime.now(), this.getZoneOffset());
        } else {
            this.setMeasurementSample("0.0", MeasurementSample.DATAQUALITY.Good, LocalDateTime.now(), this.getZoneOffset());
        }
    }
    
    @Override
    public void setName( String aName) {
        super.setName( aName);
        if (aName.startsWith( SIMULATIONTOKEN)) {
            this.setSimulated( true);
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

    public Variant createVariant(String aValueString) {
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
    
    /**
     * Gets the simulation value by setting the variables and evaluating the expression.
     * @return the simulated value
     */
    public double getSimulatedValue() {
        double result;
        LocalDateTime timestamp = LocalDateTime.now(); // timestamp for the measurement value
        double timeFractionInSeconds = timestamp.getNano() / 1E9d; // for simulated frequencies above 1 Hz, i.e. more then 1 samples/sec
        if (this.simulationExpression!=null) {
            // first set the time variable of the expression
            this.simulationExpression.setVariable( "t", timeFractionInSeconds);
            // then set the other variables according to values of the corresponding measurement points
            for ( String aVariable : this.dependingMeasurementPoints.keySet()) {
                // based on the Exp4J variable names with underscores
                MeasurementPoint dependingMeasurementPoint = this.dependingMeasurementPoints.get( aVariable);
                double value = dependingMeasurementPoint.getSimulatedValue();
                this.simulationExpression.setVariable( aVariable, value);
            }
            // end last but not least evaluate the expression to get the resulting value
            result = this.simulationExpression.evaluate();
        } else {
            // get value from measurement sample
            if (this.theCurrentMeasurementSample == null) {
                this.setInitialValue();
            }
            result = this.theCurrentMeasurementSample.getValue();
        }
        // add measurement sample based on the calculated value to this measurement point
        this.setMeasurementSample( String.valueOf( result), MeasurementSample.DATAQUALITY.Good, timestamp, this.zoneOffset);
        return result;
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

    /**
     * @return the simulated
     */
    public boolean isSimulated() {
        return simulated;
    }

    /**
     * @param simulated the simulated to set
     */
    public void setSimulated(boolean simulated) {
        this.simulated = simulated;
        // also delete the simulation expresssion since it is not valid
        this.simulationExpression = null;
    }
    
    public boolean isValid() {
        boolean result = super.isValid(); // first check if it is a valid point in time
        if ( this.simulated) {
            if (this.simulationExpression != null) {
                ValidationResult validationResult = this.simulationExpression.validate( true);
                if ( !validationResult.isValid()) {
                    // TODO: log errors
                }
                result = result && validationResult.isValid();
            } else {
                // OK it is simulated but no simulationExpression set yet
            }
        }
        return result;
    }
    
    public String getFullDottedName() {
        if (this.fullDottedName == null) {
            // check if it still contains a simulation configuration
            String name = this.getName();
            if (name.startsWith( SIMULATIONTOKEN)) {
                int indexOfParenthesis = name.indexOf( "(");
                if ( indexOfParenthesis > 0) {
                    name = name.substring( 1, indexOfParenthesis);
                } else {
                    name = name.substring(1);
                }
            }
            this.fullDottedName = this.parentAsset.getFullDottedName() + ASSETNAMESEPERATORTOKEN + name;
        }
        return this.fullDottedName;
    }

    /**
     * @return the simulationExpression
     */
    public Expression getSimulationExpression() {
        return simulationExpression;
    }

    /**
     * @return the simulationUpdateFrequency
     */
    public int getSimulationUpdateFrequency() {
        return simulationUpdateFrequency;
    }

    /**
     * @param simulationUpdateFrequency the simulationUpdateFrequency to set
     */
    public void setSimulationUpdateFrequency(int simulationUpdateFrequency) {
        this.simulationUpdateFrequency = simulationUpdateFrequency;
    }

    /**
     * @param simulationExpression the simulationExpression to set
     */
    public void setSimulationExpression(Expression simulationExpression) {
        this.simulationExpression = simulationExpression;
    }

    /**
     * @return the dependingMeasurementPoints
     */
    public Map<String, MeasurementPoint> getDependingMeasurementPointMap() {
        return dependingMeasurementPoints;
    }

    /**
     * @return the dependingMeasurementPoints
     */
    public Collection<MeasurementPoint> getDependingMeasurementPoints() {
        return dependingMeasurementPoints.values();
    }
    
    public boolean isTimeBased() {
        if ( this.simulated) {
            if ( this.simulationExpression != null && this.dependingMeasurementPoints.keySet().contains("t")) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    /**
     * @param theDependendMeasurementPoints the dependingMeasurementPoints to set
     */
    public void setDependingMeasurementPoints(Map<String, MeasurementPoint> theDependendMeasurementPoints) {
        this.dependingMeasurementPoints = theDependendMeasurementPoints;
    }

    /**
     * @return the parentAsset
     */
    public Asset getParentAsset() {
        return parentAsset;
    }

    /**
     * @param parentAsset the parentAsset to set
     */
    public void setParentAsset(Asset parentAsset) {
        this.parentAsset = parentAsset;
    }
}