/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.utils.Waiter;
import net.objecthunter.exp4j.Expression;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class SimulationWorker extends Thread {
    
    private static final long CORRECTIONCONSTANT = 1; // correction constant to improve timing 
    private static final Logger LOGGER = Logger.getLogger(SimulationWorker.class.getClass().getName());;

    private final MeasurementPoint measurementPoint;
    private final int sampleRate; // sample rate in samples per second
    private long delayTime;
    private boolean isRunning = false;
    private long counter = 0;
    private long previousTime = System.nanoTime();
    private long correctionValue = 1;
    private double actualSamplesPerSecond = 0.0;
    private UaVariableNode uaVariableNode;
    
    /**
     * Simulation Worker is a thread class that manages a single simulated measurement point. 
     * It calculates the value according the simulation expression at the update frequency of the specific meausrement point.
     * It update frequency is measured on system time in nano seconds and loops doing nothing to reach new time to calculate
     * the simulation value; The real calculation is done in the measurement point.
     * @param aMeasurementPoint the measurement point that needs to be simulated
     */
    public SimulationWorker(MeasurementPoint aMeasurementPoint) {
        this.measurementPoint = aMeasurementPoint;
        this.sampleRate = this.measurementPoint.getSimulationUpdateFrequency();
        this.delayTime = Math.round( 1E9d / sampleRate) - CORRECTIONCONSTANT; // delay time in nano seconds
        this.isRunning = false; // create worker in non running mode
    }
    
    public void stopWorker() {
        this.isRunning = false;
    }
    
    public void startWorker() {
        //this.logger.log(Level.INFO, "Starting simlation worker: " + this.measurementPoint.getName());
        this.isRunning = true;
        // check if thread is not started yet
        //this.logger.log(Level.INFO, "Simulation worker alive?: " + this.isAlive() + "(" + this.measurementPoint.getName() + ")");
        if (!this.isAlive()) {
            this.start(); // ... and start
            LOGGER.log(Level.INFO, "Simlation worker " + this + " started: " + this.isAlive() + "(" + this.getMeasurementPoint().getName() + ")");
            LOGGER.log(Level.INFO, this + "@delaytime=" + this.delayTime);
        }
    }
    
    private void monitorCounter( MeasurementPoint aMeasurementPoint) {
        Thread monitor = new Thread() {
            public void run() {
                long start = System.nanoTime();
                long lastCounter = 0;
                while (true) {
                    Waiter.wait(Duration.ofSeconds(10)); // wait for short period
                    long deltaCounter = counter > lastCounter ? counter - lastCounter: lastCounter - counter;
                    long now = System.nanoTime();
                    double deltaSeconds = (now - start) / 1E9;
                    actualSamplesPerSecond = deltaCounter / deltaSeconds;
                    if ( uaVariableNode !=  null) {
                        MeasurementSample aMeasurementSample = 
                                new MeasurementSample( 
                                        aMeasurementPoint.createVariant( String.valueOf( actualSamplesPerSecond)),
                                        MeasurementSample.DATAQUALITY.Good, 
                                        LocalDateTime.now(), 
                                        aMeasurementPoint.getZoneOffset()
                                );
                        uaVariableNode.setValue( aMeasurementSample.getUADateValue());
                    }
                    double correction = ((double) sampleRate - actualSamplesPerSecond) / 2.0;
                    delayTime = Math.round( 1E9d / (sampleRate + correction));
//                    LOGGER.log(Level.INFO, "Simulation function " + 
//                            theMeasurementPointToMonitor.getName() + 
//                            ", with an sample rate of " +theMeasurementPointToMonitor.getSimulationUpdateFrequency() +  
//                            " samples / second has achieved a real number of  samples / second of " + actualSamplesPerSecond);
                    lastCounter = counter;
                    start = now;
                }
            }
        };
        monitor.start();
    }
    
    private void delayToSampleFrequency() {
        // this is in fact wasting CPU cycles, there should be better ways to do this
        // TOOD: find ways to improve java sleep capabilities
        long currentTime = System.nanoTime();
        long deltaTime = currentTime - previousTime;
        while ( deltaTime < this.delayTime) {
            currentTime = System.nanoTime();
            deltaTime = currentTime - previousTime;
        }
        previousTime = currentTime;
    }

    @Override
    public void run() {
        this.monitorCounter(this.getMeasurementPoint());
        while (this.isRunning) {
            this.getMeasurementPoint().getSimulatedValue();
            counter++;
            this.delayToSampleFrequency();
        }
    }

    /**
     * @param uaVariableNode the uaVariableNode to set
     */
    public void setUaVariableNode(UaVariableNode uaVariableNode) {
        this.uaVariableNode = uaVariableNode;
    }

    /**
     * @return the measurementPoint
     */
    public MeasurementPoint getMeasurementPoint() {
        return measurementPoint;
    }
}
