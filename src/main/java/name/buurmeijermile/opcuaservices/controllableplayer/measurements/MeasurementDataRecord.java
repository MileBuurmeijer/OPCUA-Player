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
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration;

/**
 *
 * @author mbuurmei
 */
public class MeasurementDataRecord {

    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.from( ZonedDateTime.now()); // timezone offset of runtime platform
    private static final Duration ONE_MILLISECOND = Duration.ofMillis(1); // constant to add when two smaples have same timestamp
    private static Duration TIME_SHIFT; // the period the input timestamps are shifted towards now
    private static MeasurementDataRecord PREVIOUS_MEASUREMENT = null; // the previous measurement sample
    private static String COMMENTTOKEN = "#";

    private final String measurementPointId;
    private final String assetID;
    private final String tag;
    private final String timeStampString;
    private final String value;
    private LocalDateTime orginalTimeStamp;
    private LocalDateTime todaysTimeStamp;
    private final ZoneOffset zoneOffset; // timezone offset of runtime platform
    private final int sourceLineNumber;

    public MeasurementDataRecord(String anAssetId, String aMeasurementPointId, String aTag, String aTimestamp, String aValue, Duration aTimeShiftToTodaysStart, ZoneOffset aZoneOffset, int lineNumber) {
        this.assetID = anAssetId;
        this.measurementPointId = aMeasurementPointId;
        this.tag = aTag;
        this.timeStampString = aTimestamp;
        this.value = aValue;
        this.zoneOffset = aZoneOffset;
        this.parseTimestamp();
        this.transposeTimestamp( aTimeShiftToTodaysStart);
        this.sourceLineNumber = lineNumber;
    }

    public MeasurementDataRecord(String anAssetId, String aMeasurementPointId, String aTimestamp, String aValue, Duration aTimeShiftToTodaysStart, ZoneOffset aZoneOffset, int lineNumber) {
        this(anAssetId, aMeasurementPointId, null, aTimestamp, aValue, aTimeShiftToTodaysStart, aZoneOffset, lineNumber);
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
            // check if data line starts with comment token
            if (!aDataLine.startsWith( COMMENTTOKEN)) {
                if (Configuration.getConfiguration().isRecordedFormat()) {
                    // recorded format is comma-separated: Timestamp, Tag, Value
                    String[] lineItems = aDataLine.split(",");
                    if (lineItems.length >= 3) {
                        String timestamp = lineItems[0].trim();
                        String tag = lineItems[1].trim();
                        String value = lineItems[2].trim();
                        if (TIME_SHIFT == null) {
                            MeasurementDataRecord.setTimeShiftDuration(timestamp);
                        }
                        measurementDataRecord = new MeasurementDataRecord(
                                null, // asset id
                                null, // measurement point id
                                tag,
                                timestamp,
                                value.replace(',', '.'),
                                TIME_SHIFT,
                                ZONE_OFFSET,
                                lineCounter
                        );
                        if (!measurementDataRecord.isValid()) {
                            Logger.getLogger(MeasurementDataRecord.class.getName()).log(Level.SEVERE, "Error parsing input line " + lineCounter + ", values not correct");
                            measurementDataRecord = null;
                        }
                        if (PREVIOUS_MEASUREMENT != null && measurementDataRecord != null) {
                            if (PREVIOUS_MEASUREMENT.isSame(measurementDataRecord)) {
                                Logger.getLogger(MeasurementDataRecord.class.getName()).log(Level.INFO, "Records contain same timestamp, adding one milliseconds to new one");
                                measurementDataRecord.shiftDuration(ONE_MILLISECOND);
                            }
                        }
                        PREVIOUS_MEASUREMENT = measurementDataRecord;
                    } else {
                        if (lineItems.length != 0) {
                            Logger.getLogger(MeasurementDataRecord.class.getName()).log(Level.SEVERE, "Error parsing input line " + lineCounter + ", missing values");
                        }
                    }
                } else {
                    // input file is semi-column separated
                    String[] lineItems = aDataLine.split(";");
                    // and 4 columns wide, so check if we have got 4 string parts
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
                                Logger.getLogger( MeasurementDataRecord.class.getName()).log(Level.INFO, "Records contain same timestamp, adding one milliseconds to new one");
                                measurementDataRecord.shiftDuration( ONE_MILLISECOND);
                            }
                        }
                        PREVIOUS_MEASUREMENT = measurementDataRecord;
                    } else {
                        if (lineItems.length != 0) { // empty line in file, skip
                            Logger.getLogger( MeasurementDataRecord.class.getName()).log(Level.SEVERE, "Error parsing input line " + lineCounter + ", missing values");
                        }
                    }
                }
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
        try { 
            LocalDateTime startupTimestamp = LocalDateTime.now();
            LocalDateTime startSourceDataTimestamp;
            if (Configuration.getConfiguration().isRecordedFormat()) {
                startSourceDataTimestamp = LocalDateTime.ofInstant(java.time.Instant.parse(firstTimestampRead), java.time.ZoneId.systemDefault());
            } else {
                startSourceDataTimestamp = LocalDateTime.parse( firstTimestampRead, MeasurementPoint.TIMESTAMP_FORMATTER);
            }
            TIME_SHIFT = Duration.between( startSourceDataTimestamp, startupTimestamp);
        } catch ( Exception dtpe) {
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

    public String getTag() {
        return tag;
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
        if (Configuration.getConfiguration().isRecordedFormat()) {
            return this.tag != null && this.orginalTimeStamp != null && this.value != null;
        } else {
            return this.assetID != null && this.measurementPointId != null && this.orginalTimeStamp != null && this.value != null;
        }
    }

    private void parseTimestamp() {
        LocalDateTime result = null;
        try {
            if (Configuration.getConfiguration().isRecordedFormat()) {
                result = LocalDateTime.ofInstant(java.time.Instant.parse(this.timeStampString), java.time.ZoneId.systemDefault());
            } else {
                result = LocalDateTime.parse(this.timeStampString, MeasurementPoint.TIMESTAMP_FORMATTER);
            }
        } catch (Exception dtpe) {
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
        if (Configuration.getConfiguration().isRecordedFormat()) {
            return timestampEqual && this.tag.equals(otherMeasurementDataRecord.getTag());
        } else {
            boolean assetEqual = this.assetID.equals( otherMeasurementDataRecord.getAssetID());
            boolean channelID = this.measurementPointId.equals(otherMeasurementDataRecord.getMeasurementPointID());
            return timestampEqual && assetEqual && channelID;
        }
    }

    /**
     * @return the ZONE_OFFSET
     */
    public ZoneOffset getZoneOffset() {
        return ZONE_OFFSET;
    }
    
    public void shiftDuration( Duration aDuration) {
        if ( aDuration != null && this.todaysTimeStamp != null) {
            this.todaysTimeStamp = this.todaysTimeStamp.plus(aDuration);
        }
    }
}
