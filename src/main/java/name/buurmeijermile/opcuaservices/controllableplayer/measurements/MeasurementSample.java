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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 *
 * @author mbuurmei
 */
public class MeasurementSample {

    public enum DATAQUALITY { Good, Bad, BadSensor, BadLine, BadRange};
    
    private final Variant variant;
    private final DataValue aDataValue;
    private final LocalDateTime timestamp;
    private final DATAQUALITY dataQuality;
    private final ZoneOffset zoneOffset;
        
    public MeasurementSample( Variant aVariant, DATAQUALITY theDataQuality, LocalDateTime aTimeStamp, ZoneOffset aZoneOffset) {
        this.variant = aVariant;
        this.dataQuality = theDataQuality;
        this.timestamp = aTimeStamp;
        this.zoneOffset = aZoneOffset;
        this.aDataValue = 
            new DataValue( 
                this.variant, 
                this.mapToUAStatus(), 
                this.getUaDateTime(),
                this.getUaDateTime()
        );
    }


    /**
     * @return the timestamp in nano seconds 
     */
    public LocalDateTime getTimestamp() {
        return this.timestamp;
    }

    /**
     * @return the dataQuality
     */
    public DATAQUALITY getDataQuality() {
        return dataQuality;
    }
    
    private StatusCode mapToUAStatus() {
        switch (this.dataQuality) {
            case Good:  {
                return StatusCode.GOOD;
            }
            case Bad: {
                return StatusCode.BAD;
            }
            case BadSensor: {
                return StatusCode.BAD;
            }
            case BadLine: {
                return StatusCode.BAD;
            }
            case BadRange: {
                return StatusCode.BAD;
            }
            default: {
                return StatusCode.BAD;
            }
        }
    }

    private DateTime getUaDateTime() {
        // converts the todays LocalDateTime timestamp to an OPC UA DateTime timestamp (tedious steps) with millisecond precision
        // first get UTC seconds
        long javaUtcMilliSeconds = this.timestamp.toEpochSecond( this.zoneOffset);
        // then multiply by 1000 to get number of milliseconds since epoch
        javaUtcMilliSeconds = javaUtcMilliSeconds * 1000;
        // then calculate milliseconds based on internal nano seconds
        long javaMillisFraction = this.timestamp.getNano() / 1000_000;
        // the add these values to get the utc in milliseconds
        long javaUtcMilli = javaUtcMilliSeconds + javaMillisFraction;
        // create java Date out of this utc in millseconds since epoch
        Date javaDate = new Date(javaUtcMilli);
        // create java date out of that
        DateTime dateTime = new DateTime(javaDate);
        return dateTime;
    }
    
    public DataValue getNullUADataValue() {
        return new DataValue( 
                Variant.NULL_VALUE, 
                this.mapToUAStatus(), 
                this.getUaDateTime(),
                this.getUaDateTime()
        );
    }

    public DataValue getUADateValue() {
        return this.aDataValue;
    }
    
    public double getValue() {
        double result=0.0d;
        switch ( this.variant.getValue().getClass().getName()) {
            case "Double": {
                result = ((Double) this.variant.getValue()).doubleValue();
                break;
            }
            case "Boolean": {
                boolean value = ((Boolean) this.variant.getValue()).booleanValue();
                if (value) {
                    result = 1.0;
                } else {
                    result = 0.0;
                }
                break;
            }
        }
        return result;
    }
}
