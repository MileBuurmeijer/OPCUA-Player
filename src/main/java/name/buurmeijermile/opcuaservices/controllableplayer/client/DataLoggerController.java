/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.client;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration;
import name.buurmeijermile.opcuaservices.utils.Waiter;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class DataLoggerController {
    private BufferedWriter bufferedWriter = null;
    private final File outputFile;
    private final Logger logger = Logger.getLogger(DataLoggerController.class.getName());
    private final Deque<Sample> sampleList;
    private boolean continueWriting = true;
    private boolean setup = false;
    private long counter = 0;
    
    public DataLoggerController( Deque<Sample> aSampleList) {
        this.outputFile = Configuration.getConfiguration().getDataFile();
        this.sampleList = aSampleList;
    }
    
    public void startWriting() {
        try {
            logger.log(Level.INFO, "Starting up output file writing");
            this.continueWriting = true;
            this.bufferedWriter = new BufferedWriter( new FileWriter( outputFile));
            this.writeHeader( bufferedWriter);
            DataLoggerController thisController  = this;
            Thread writingThread = new Thread() {
                @Override
                public void run() {
                    thisController.continueWriting();
                }
            };
            writingThread.start();
            this.setup = true;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Can't create the output file", ex);
        }
    }
    
    private void writeHeader( BufferedWriter bufferedWriter) throws IOException {
        String header = "Timestamp, Tag, Value\n";
        bufferedWriter.append(header);
    }
    
    public void continueWriting() {
        logger.log( Level.INFO, "Entering continue writing method");
        while (setup && continueWriting) {
            if ( !this.sampleList.isEmpty()) {
                counter++;
                Sample aSample = this.sampleList.peek(); // peeks at the head of the queue
                this.writeSample( aSample);
                // remove the just written sample from the head of the queue
                this.sampleList.remove(); 
                if (counter%100==0) {
                    int queueSize = this.sampleList.size();
                    logger.log( Level.INFO, "The current queue size = " +  queueSize);
                }
            } else {
                Waiter.waitMilliseconds(10); // wait for 10 mS till there are more samples in the queue
            }
        }
    }
    
    public void stopWriting() {
        try {
            logger.log(Level.INFO, "Stopping with output file writing");
            this.continueWriting = false;
            LocalDateTime stopWritingCommandTimestamp = LocalDateTime.now();
            while (!this.sampleList.isEmpty() && !Waiter.hasTimePassed(stopWritingCommandTimestamp, Duration.ofSeconds( 10))) {
                Waiter.waitMilliseconds( 500); // do nothing && wait 500mS
            }
            this.bufferedWriter.close();
            logger.log(Level.INFO, "Output file closed");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Exception occured during clsing the output file", ex);
        }
    }

    private void writeSample(Sample aSample) {
        try {
            NodeId nodeId = aSample.getNodeId();
            DataValue value = aSample.getValue();
            DateTime timestamp = value.getServerTime();
            String timestampString;
            if (timestamp != null) {
                timestampString = timestamp.getJavaInstant().toString();
            } else {
                timestampString = "null";
            }
            String outputFormat = value.getServerTime().getJavaInstant() + ", " + nodeId.toParseableString() + ", " + value.getValue().getValue();
            this.bufferedWriter.write( outputFormat + "\n");
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}
