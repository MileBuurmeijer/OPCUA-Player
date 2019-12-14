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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mbuurmei
 */
public class MeasurementDataRecord {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER=  DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS");
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.from( ZonedDateTime.now()); // timezone offset of runtime platform
    private static final Duration ONE_MILLISECOND = Duration.ofMillis(1); // constant to add when two smaples have same timestamp
    private static Duration TIME_SHIFT; // the period the input timestamps are shifted towards now
    private static MeasurementDataRecord PREVIOUS_MEASUREMENT = null; // the previous measurement sample

    private final String measurementPointId;
    private final String assetID;
    private final String timeStampString;
    private final String value;
    private LocalDateTime orginalTimeStamp;
    private LocalDateTime todaysTimeStamp;
    private final ZoneOffset zoneOffset; // timezone offset of runtime platform
    private final int sourceLineNumber;

    public MeasurementDataRecord(String anAssetId, String aMeasurementPointId, String aTimestamp, String aValue, Duration aTimeShiftToTodaysStart, ZoneOffset aZoneOffset, int lineNumber) {
        this.assetID = anAssetId;
        this.measurementPointId = aMeasurementPointId;
        this.timeStampString = aTimestamp;
        this.value = aValue;
        this.zoneOffset = aZoneOffset;
        this.parseTimestamp();
        this.transposeTimestamp( aTimeShiftToTodaysStart);
        this.sourceLineNumber = lineNumber;
    }
    
    public static void resetTimeShift() {
        TIME_SHIFT = null;
    }
    
    /**
     * Process an incoming data line: retrieve some fields and create 
     * MeasurementDataRecords from them.
     * @param aDataLine
     * @return measurement data record that holds the read input line from data file
     */
    public static MeasurementDataRecord procesDataLine( String aDataLine, int lineCounter) {
        MeasurementDataRecord measurementDataRecord = null;
        // check if it is the first line that is processed
        if ( lineCounter == 0) {
            // if so do nothing because it contains a header
        } else {
            // input file is semi-column seperated
            String[] lineItems = aDataLine.split(";");
            // and 7 columns wide, so check if we have got 7 parts as strings
            if ( lineItems.length == 4) {
                // calculate timeshift if not already done => only once per inputfile played (at end of inputfile this timeshift duration is reset to null
                if (TIME_SHIFT ==  null) {
                    MeasurementDataRecord.setTimeShiftDuration( lineItems[2]);
                }
                // create measurement record based on the line item fields (not all are used!)
                measurementDataRecord = new MeasurementDataRecord(
                        lineItems[0], // first column: asset id
                        lineItems[1], // second column: measurement point id
                        lineItems[2], // third column: timestamp of measurement
                        lineItems[3].replace(',', '.'), // fourth column: value of measurement, replacement of ',' for '.'
                        TIME_SHIFT,   // the read timestamp are shifted towards the start time of the OPC UA player
                        ZONE_OFFSET,  // the zone offset of the read timestamps
                        lineCounter   // the source line number for back tracking errors in the input file
                );
                if ( !measurementDataRecord.isValid()) {
                    Logger.getLogger( MeasurementDataRecord.class.getName()).log(Level.SEVERE, "Error parsing input line " + lineCounter + ", values not correct");
                    measurementDataRecord = null;
                }
                // check if previous input line was same channel, asset and timestamp
                if ( PREVIOUS_MEASUREMENT != null && measurementDataRecord != null) {
                    if ( PREVIOUS_MEASUREMENT.isSame( measurementDataRecord)) {
                        Logger.getLogger( MeasurementDataRecord.class.getName()).log(Level.INFO, "Records are equal, adding one milliseconds to new one");
                        measurementDataRecord.shiftDuration( ONE_MILLISECOND);
                    }
                }
                PREVIOUS_MEASUREMENT = measurementDataRecord;
            } else {
                Logger.getLogger( MeasurementDataRecord.class.getName()).log(Level.SEVERE, "Error parsing input line " + lineCounter + ", missing values");
            }
        }
        return measurementDataRecord;
    }

    /**
     * Calculate the time shift between first timestamp in data file and 
     * the current time.
     * @param firstTimestampRead
     * @return duration between first timestamp in data file and now
     */
    private static void setTimeShiftDuration( String firstTimestampRead) {
        // timeshift duration is difference between current time and original timestamp of first timestamp read from the data input file
        try { 
            // get timestamp of now
            LocalDateTime startupTimestamp = LocalDateTime.now();
            // parse input argument
            LocalDateTime startSourceDataTimestamp = 
                    LocalDateTime.parse( 
                            firstTimestampRead, 
                            TIMESTAMP_FORMATTER
                    );
            // calculate difference
            TIME_SHIFT = Duration.between( startSourceDataTimestamp, startupTimestamp);
        } catch ( DateTimeParseException dtpe) {
            Logger.getLogger(MeasurementDataRecord.class.getName()).log(Level.SEVERE, "Timestamp format error on first data line in datafile", dtpe);
        }
    }

    /**
     * @return the sensorID
     */
    public String getMeasurementPointID() {
        return measurementPointId;
    }

    /**
     * @return the assetID
     */
    public String getAssetID() {
        return assetID;
    }

    /**
     * @return the timeStampString
     */
    public LocalDateTime getOriginalTimeStamp() {
        return orginalTimeStamp;
    }

    public LocalDateTime getTimestamp() {
        return this.todaysTimeStamp;
    }

    /**
     * @return the value
     */
    public double getValue() {
        double result = 0.0;
        try {
            result = Double.parseDouble(this.value);
        } catch (NumberFormatException nfe) {
            Logger.getLogger(MeasurementDataRecord.class.getName()).log(Level.SEVERE, "Unable to parse " + this.value + "input to double value", nfe);
        }
        return result;
    }
    
    public String getValueString() {
        return this.value;
    }

    /**
     * Check if this MeasurementDataRecord is valid.
     *
     * @return true if valid else false
     */
    public boolean isValid() {
        return this.assetID != null && this.measurementPointId != null && this.orginalTimeStamp != null && this.value != null;
    }

    private void parseTimestamp() {
        LocalDateTime result = null;
        try {
            // transform input string into ISO date format with 'T' in the middle
            result = LocalDateTime.parse(this.timeStampString, TIMESTAMP_FORMATTER);
        } catch (DateTimeParseException dtpe) {
            Logger.getLogger(MeasurementDataRecord.class.getName()).log(Level.SEVERE, "Timestamp format error on line " + this.sourceLineNumber, dtpe);
        }
        this.orginalTimeStamp = result;
    }

    private void transposeTimestamp(Duration timeShiftDuration) {
        // move original timestamp to shifted time
        if (this.orginalTimeStamp != null) {
            this.todaysTimeStamp = this.orginalTimeStamp.plus(timeShiftDuration);
        } else {
            this.todaysTimeStamp = null;
        }
    }

    /**
     * Checks if this record is same as the other record. Same is defined as
 same assetID and same sensorID and same timestamp.
     * @param otherMeasurementDataRecord the measurement data record to compare this instance with
     * @retunr result of comparison
     */
    public boolean isSame(MeasurementDataRecord otherMeasurementDataRecord) {
        boolean timestampEqual = this.todaysTimeStamp.equals(otherMeasurementDataRecord.getTimestamp());
        boolean assetEqual = this.assetID.equals( otherMeasurementDataRecord.getAssetID());
        boolean channelID = this.measurementPointId.equals(otherMeasurementDataRecord.getMeasurementPointID());
        return timestampEqual && assetEqual && channelID;
    }

    /**
     * @return the ZONE_OFFSET
     */
    public ZoneOffset getZoneOffset() {
        return ZONE_OFFSET;
    }
    
    public void shiftDuration( Duration aDuration) {
        if ( aDuration != null) {
            this.todaysTimeStamp.plus(aDuration);
        }
    }
}
