/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class MeasurementPointBuilder {

    private final MeasurementPoint aMeasurementPoint;

    public MeasurementPointBuilder( boolean isSimulated) {
        if ( isSimulated) {
            this.aMeasurementPoint = new SimulatedMeasurementPoint();
        } else {
            this.aMeasurementPoint = new MeasurementPoint();
        }
    }

    public MeasurementPointBuilder setId(String anId) {
        try {
            int id = Integer.parseInt(anId);
            this.aMeasurementPoint.setId(id);
        } catch (NumberFormatException nfe) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert String " + anId + " to integer", nfe);
        }
        return this;
    }

    public MeasurementPointBuilder setName(String aName) {
        this.aMeasurementPoint.setName(aName);
        return this;
    }

    public MeasurementPointBuilder setPhysicalQuantity(String aPhysicalQuantity) {
        try {
            PointInTime.PHYSICAL_QUANTITY physicalQuantity = PointInTime.PHYSICAL_QUANTITY.valueOf(aPhysicalQuantity);
            this.aMeasurementPoint.setPhysicalQuantity(physicalQuantity);
        } catch (IllegalArgumentException iae) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert physical quantity " + aPhysicalQuantity + " to a PHYSICAL_QUANTITY", iae);
        }
        return this;
    }

    public MeasurementPointBuilder setUnitOfMeasure(String aBaseUnitOfMeasure) {
        try {
            PointInTime.BASE_UNIT_OF_MEASURE unitOfMeasure = PointInTime.BASE_UNIT_OF_MEASURE.valueOf(aBaseUnitOfMeasure);
            this.aMeasurementPoint.setBaseUnitOfMeasure(unitOfMeasure);
        } catch (IllegalArgumentException iae) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert base unit of measure " + aBaseUnitOfMeasure + " to a BASE_UNIT_OF_MEASURE", iae);
        }
        return this;
    }

    public MeasurementPointBuilder setUnitPrefix(String aUnitPrefix) {
        try {
            PointInTime.UNIT_PREFIX unitPrefix = PointInTime.UNIT_PREFIX.valueOf(aUnitPrefix);
            this.aMeasurementPoint.setTheUnitPrefix(unitPrefix);
        } catch (IllegalArgumentException iae) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert unit prefix " + aUnitPrefix + " to a UNIT_PREFIX", iae);
        }
        return this;
    }

    public MeasurementPointBuilder setAccesRight(String anAccesRight) {
        try {
            PointInTime.ACCESS_RIGHT accessRight = PointInTime.ACCESS_RIGHT.valueOf(anAccesRight);
            this.aMeasurementPoint.setAccessRight(accessRight);
        } catch (IllegalArgumentException iae) {
            Logger.getLogger(MeasurementPointBuilder.class.getName()).log(Level.SEVERE, "Cannot convert acces right " + anAccesRight + " to a ACCESS_RIGHT", iae);
        }
        return this;
    }

    /**
     * Builds the measurement point after setting the parameters
     *
     * @return
     */
    public MeasurementPoint build() {
        // let's build it, only if valid
        if (this.aMeasurementPoint.isValid()) {
            if (this.aMeasurementPoint.isSimulated()) {
                SimulatedMeasurementPoint aSimulatedMeasurementPoint = (SimulatedMeasurementPoint) this.aMeasurementPoint;
                // set simulated function
                aSimulatedMeasurementPoint.setSimulationFunction();
            }
            this.aMeasurementPoint.setInitialValue();
            return this.aMeasurementPoint;
        } else {
            return null;
        }
    }
}
