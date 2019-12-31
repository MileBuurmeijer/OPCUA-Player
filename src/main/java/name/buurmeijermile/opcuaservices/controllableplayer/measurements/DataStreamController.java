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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import name.buurmeijermile.opcuaservices.utils.Waiter;

/**
 * The data stream controller reads in time-based measurement samples from a 
 * file, processes its lines, create measurement samples from it and update
 * the appropriate measurement points with these samples. The samples are 
 * handled in real time with appropriate waiting time in between the read samples.
 * The read timestamps are transposed to the current time based on the difference
 * between the current time and the time of the first timestamp read.
 * The measurement input data file should contain: asset id, measurement point id, 
 * timestamp (formatted "uuuu-MM-dd HH:mm:ss.SSS"), value (format floating point 
 * with ',' as decimal separator). These samples should be chronological order 
 * with the oldest sample first.
 * @author Milé Buurmeijer
 */
public class DataStreamController {
    
    public static final String TIMESTAMPFORMATTER = "uuuu-MM-dd HH:mm:ss.SSS";
    public static final int FAST_FORWARD_FACTOR = 2; // twice the speed of a normal play, also used for playing backwards faster
    
    private final File dataSourceFile; // the soruce file for the measurement data stream
    private final DataFilePlayerController dataBackendController; // the overarching data backend controller that maintains the players state machine
    private int lineCounter = 0; // used to be able to print out the line number when some error arises
    private Duration timeShiftDuration; // the period the input timestamps are shifted towards now
    private final ZonedDateTime timezoneDateTime = ZonedDateTime.now(); // only used to retrieve platform timezone
    private final ZoneOffset zoneOffset = ZoneOffset.from(this.timezoneDateTime); // timezone offset of runtime platform
    private MeasurementDataRecord previousMeasurement = null; // the previous measurement sample
    private final Duration aMilliSecond = Duration.ofMillis(1); // constant to add when two smaples have same timestamp
    private Iterator<String> iterator;
    private int dataLineCounter;
    
    /**
     * Constructor for this controller. After constructing nothing happens yet. 
     * First get a data stream and then request to process input data.
     * @param aDataSourceFile
     * @param theDataBackendController
     */
    public DataStreamController( File aDataSourceFile, DataFilePlayerController theDataBackendController) {
        this.dataSourceFile = aDataSourceFile;
        this.dataBackendController = theDataBackendController;
    }
    
    private void procesInputData(MeasurementDataRecord readData) {
        if ( readData != null) {
            LocalDateTime now = LocalDateTime.now();
            // calculate duration between the read timestamp and the current time
            Duration duration = Duration.between( now, readData.getTimestamp());
            //this.writeDataLogEntry( now.toString(), readData.getAssetID(), readData.getMeasurementPointID(), readData.getValue(), readData.getTimestamp().toString(), duration.toString());
            // check if we are currently playing (could be switched to other state while processing
            if ( this.dataBackendController.isPlayState()) {
                // check if we play the source file backward
                if (this.dataBackendController.isPlayBackward()) {
                    // negate the duration so that it can be interpreted as numral duration for a delay
                    duration = duration.negated();
                }
                // check if we are in fast forward or fast backward mode
                if (this.dataBackendController.isPlayFast()) {
                    // apply the fast forward factor
                    duration = duration.dividedBy(FAST_FORWARD_FACTOR);
                }
            }
            // find the measurement point this record refers to
            MeasurementPoint measurementPoint = this.getMeasurementPoint( readData.getAssetID(), readData.getMeasurementPointID());
            // check if we need to waitADuration for this time stamp to happen any time soon now
            if ( !duration.isNegative() && !duration.isZero()) {
                // typically the read timestamp is newer than the current time, 
                // so we have to waitADuration until the read timestamp reaches the current time
                Waiter.waitADuration( duration);
            } // if not just go ahead, because the read timestamp is already in the past
            // and add measurement sample to measurement point
            if ( measurementPoint != null) {
                measurementPoint.setMeasurementSample( readData.getValueString(), MeasurementSample.DATAQUALITY.Good, readData.getTimestamp(), readData.getZoneOffset());
            }
        } else {
            Logger.getLogger( this.getClass().getName()).log(Level.SEVERE, "Error received readData object is null");
        }
    }

    /**
     * Find measurement point object based on assetID and channelID.
     * @param assetId
     * @param measurementPointId
     * @return 
     */
    private MeasurementPoint getMeasurementPoint(String assetId, String measurementPointId) {
        // find asset in flat asset list based on its asset ID
        List<Asset> assetList = this.dataBackendController.getFlatAssetList();
        Asset anAsset = assetList.stream().filter( p -> p.getId().equalsIgnoreCase(assetId)).findFirst().orElse( null);
        if (anAsset != null) {
            // find measurement in this assets measurement point based on channel ID (aka measurement point ID)
            MeasurementPoint measurementPoint = anAsset.getMeasurementPoints().stream().filter( p -> p.getId() == Integer.parseInt(measurementPointId)).findFirst().orElse( null);
            if (measurementPoint == null) {
                Logger.getLogger( this.getClass().getName()).log(Level.WARNING, "Error asset/measurementpoint combination not found with asset ID=" + assetId + " and measurementpointID=" +  measurementPointId);
            }
            return measurementPoint;
        } else {
            Logger.getLogger( this.getClass().getName()).log(Level.WARNING, "Error asset not found with ID=" + assetId);
            return null;
        }
    }
    
    
    /**
     * getDatastream open the data input file and returns an iterator to its content.
     * @param isForwardOrder boolean to flag if the data stream should be opened in forward or backward order
     * @return iterator to the data lines in the data input file
     */
    public boolean getDataStream( boolean isForwardOrder) {
        boolean result = false;
        // check if source file is set
        if ( this.dataSourceFile != null) {
            // check if source file exists
            if (this.dataSourceFile.exists()) {
                // check if file can be read
                if (this.dataSourceFile.canRead()) {
                    return this.openFile( this.dataSourceFile.toPath(), isForwardOrder);
                } else {
                    // error data file can not be read
                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error data file can not be read");
                }
            } else {
                // error data file does not exist
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error data file does not exist");
            }
        } else {
            // error data file is zero
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error data file is zero");
        }
        return result;
    }
    
    private boolean openFile( Path aPath, boolean isForwardOrder) {
        boolean result = false;
        // try to open it
        try {
            // check if need to open in ascending chronological order
            if ( isForwardOrder) {
                // open file and get stream of lines
                Stream<String> lines = Files.lines( aPath);
                // check if open was successful
                if (lines != null) {
                    Logger.getLogger(this.getClass().getName()).log(Level.INFO, "File " + aPath.getFileName() + " opened");
                    // reset line counter
                    this.dataLineCounter = 0;
                    // reset time shift duration
                    this.timeShiftDuration = null;
                    // set the iterator
                    this.iterator = lines.iterator();
                    result = true;
                    // return the result
                    return result;
                } else {
                    // flag that no data line stream was returned by java nio
                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "No data line stream retrieved from input file");
                }
            } else { // so its decending chronological order
                try {
                    // this might take a while
                    List<String> lines = Files.readAllLines( aPath); // read the file in to a list
                    String header = lines.get(0); // store the header
                    lines.remove(0); // remove the header line from the list
                    lines.sort( new LineComparator()); // sort according the comparator
                    lines.add(0, header); // add the header again as first item
                    // set the iterator
                    this.iterator = lines.iterator();
                    result = true;
                    // return the result
                    return result;
                } catch (IOException ex) {
                    Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
                }
            }
        } catch (IOException ex) {
            // flag that io exceptio was reaised by java nio
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    } 
    
    public boolean hasNext() {
        return this.iterator.hasNext();
    }
    
    public void processSample() {
        // in play forward state so read some data
        String aDataLine = iterator.next();
        MeasurementDataRecord readData = 
                MeasurementDataRecord.procesDataLine( aDataLine, this.dataLineCounter);
        this.dataLineCounter++;
        if (readData != null) {
            this.procesInputData( readData);
        }
    }
    
    /**
     * Calculate the time shift between first timestamp in data file and 
     * the current time.
     * @param firstTimestampRead
     * @return duration between first timestamp in data file and now
     */
    private Duration calculateTimeShiftDuration( String firstTimestampRead) {
        // timeshift duration is difference between current time and original timestamp of first date line in input file
        Duration timeShift = null;
        try { 
            // get timestamp of now
            LocalDateTime startupTimestamp = LocalDateTime.now();
            // parse input argument
            LocalDateTime startSourceDataTimestamp = 
                    LocalDateTime.parse( 
                            firstTimestampRead, 
                            DateTimeFormatter.ofPattern( TIMESTAMPFORMATTER)
                    );
            // calculate difference
            timeShift = Duration.between( startSourceDataTimestamp, startupTimestamp);
        } catch ( DateTimeParseException dtpe) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Timestamp format error on first data line in datafile", dtpe);
        }
        // return difference if calculated
        return timeShift;
    }
}
