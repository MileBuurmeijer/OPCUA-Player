/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.utils.Waiter;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class SimulationWorker extends Thread {
    
    private static final long CORRECTIONFACTOR = 1;

    private final SimulatedMeasurementPoint simulatedMeasurementPoint;
    private final int sampleRate;
    private final long delayTime;
    private boolean isRunning = false;
    private long counter = 0;
    private long previousTime = System.currentTimeMillis();
    
    public SimulationWorker(SimulatedMeasurementPoint aSimulatedMeasurementPoint, int aSampleRate) {
        this.simulatedMeasurementPoint = aSimulatedMeasurementPoint;
        this.sampleRate = aSampleRate;
        this.delayTime = Math.round( 1E9d / sampleRate) - CORRECTIONFACTOR; // delay time in nano seconds
        this.isRunning = false; // create worker in non running mode
        //Logger.getLogger(SimulationWorker.class.getName()).log(Level.INFO, "SimulationWorker delay set to " + delayTime);
    }
    
    public void stopWorker() {
        this.isRunning = false;
    }
    
    public void startWorker() {
        //Logger.getLogger(SimulationWorker.class.getName()).log(Level.INFO, "Starting simlation worker: " + this.simulatedMeasurementPoint.getName());
        this.isRunning = true;
        // check if thread is not started yet
        //Logger.getLogger(SimulationWorker.class.getName()).log(Level.INFO, "Simlation worker alive?: " + this.isAlive() + "(" + this.simulatedMeasurementPoint.getName() + ")");
        if (!this.isAlive()) {
            this.start(); // ... and start
            //Logger.getLogger(SimulationWorker.class.getName()).log(Level.INFO, "Simlation worker started: " + this.isAlive() + "(" + this.simulatedMeasurementPoint.getName() + ")");
        }
    }
    
    private void monitorCounter() {
        Thread monitor = new Thread() {
            public void run() {
                long start = System.nanoTime();
                long lastCounter = 0;
                while (true) {
                    Waiter.wait(Duration.ofSeconds(10)); // wait for short period
                    long deltaCounter = counter - lastCounter;
                    long now = System.nanoTime();
                    double deltaSeconds = (now - start) / 1E9;
                    System.out.println("Counter = " + counter);
                    System.out.println("Samples per second = " + deltaCounter / deltaSeconds );
                    lastCounter = counter;
                    start = now;
                }
            }
        };
        monitor.start();
    }
    
    private void delayToSampleFrequency() {
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
        System.out.println("delaytime=" + this.delayTime);
        this.monitorCounter();
        while (this.isRunning) {
            this.simulatedMeasurementPoint.createMeasurementSample();
            counter++;
            this.delayToSampleFrequency();
        }
    }

}
