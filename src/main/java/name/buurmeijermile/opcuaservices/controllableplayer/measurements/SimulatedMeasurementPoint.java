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

import name.buurmeijermile.opcuaservices.controllableplayer.measurements.simulatorfunctions.SimulatorFunction;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.simulatorfunctions.SinusSimulator;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.simulatorfunctions.CosinusSimulator;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 *
 * @author mbuurmei
 */
public class SimulatedMeasurementPoint extends MeasurementPoint {

   private SimulatorFunction dataFunction;
    
   /**
     * Constructor used by builder
     */
    public SimulatedMeasurementPoint() {
    }
    
    /**
     * Constructor
     * @param anID an identifier for the measurement point
     * @param aName an descriptive name of this measurement point
     * @param aPhyscialQuantity a physical quantity of what is measured at this point
     * @param aBaseUoM a base unit of measurement
     * @param aUnitPrefix  a prefix of the unit of measure
     */
    public SimulatedMeasurementPoint( int anID, String aName, PHYSICAL_QUANTITY aPhyscialQuantity, BASE_UNIT_OF_MEASURE aBaseUoM, UNIT_PREFIX aUnitPrefix) {
        this.setSimulationFunction();
        // set initial value
        this.setInitialValue();
    }

    public void setSimulationFunction() {
        String [] parts = this.getName().split("_");
        if (parts.length == 2) {
            int frequency = 0;
            switch (parts[1]) { // second part contains the frequency of the simulated time based function
                case "5000hz": {
                    frequency = 5000;
                    break;
                }
                case "1000Hz": {
                    frequency = 1000;
                    break;
                }
                case "500Hz": {
                    frequency = 500;
                    break;
                }
                default: {
                    frequency = 1;
                    break;
                }
            }
            switch (parts[0]) { // the first part contains the function type of the simulated function
                case "Sinus": {
                    this.dataFunction = new SinusSimulator( frequency);
                    break;
                } 
                case "Cosinus": {
                    this.dataFunction = new CosinusSimulator( frequency);
                    break;
                } 
                default: {
                    this.dataFunction = new SinusSimulator( frequency);
                    break;
                }
            }
        } else {
            Logger.getLogger(SimulatedMeasurementPoint.class.getName()).log(Level.SEVERE, "Error parsing simulated function name reference: " + this.getName());
        }
    }
    
   @Override
    public boolean isSimulated() {
        return true;
    }

    public void setInitialValue() {
        super.setInitialValue();
        this.createMeasurementSample();
    }
    
    /**
     * Create a measurement sample based on the internal simulation function
     */
    public void createMeasurementSample() {
        LocalDateTime timestamp = LocalDateTime.now(); // timestamp for the measurement value
        double timeFractionInSeconds = timestamp.getNano() / 1E9d; // for simulated frequencies above 1 Hz
        double newSimulatedValue = this.dataFunction.getValue( timeFractionInSeconds); 
        // create OPC UA variant based on type of measurement point
        Variant aSimulatedValue = new Variant ( newSimulatedValue);
        // create measurement sample
        MeasurementSample measurementSample = new MeasurementSample( aSimulatedValue, MeasurementSample.DATAQUALITY.Good, timestamp, this.getZoneOffset());
        // add measurement sample to this measurement point
        this.setMeasurementSample( measurementSample);
    }
}
