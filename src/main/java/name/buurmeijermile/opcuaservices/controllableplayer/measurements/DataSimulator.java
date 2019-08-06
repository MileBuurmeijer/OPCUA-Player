/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class DataSimulator implements DataControllerInterface{
    
    public static enum RUNSTATE { Idle, Starting, Stopping, Running}
    public static enum COMMAND { Start, Stop} 

    private final int sampleRate = 7000; // seven thousend samples per second

    private final Assets assets = new Assets();
    private final List<SimulationWorker> workers = new ArrayList<>();
    private RUNSTATE runState = RUNSTATE.Idle;
    private final List<RunstateEventListener> runstateEventListeners = new ArrayList<>();
    private UaVariableNode runStateUaVariableNode;

    public DataSimulator() {
        // create simulatiuon objects
        this.createSimulationObjects();
        // create workers for each asset
        this.createWorkers();
    }

    public void createSimulationObjects() {
        AssetConfigurationItem assetConfigurationItem;
        assetConfigurationItem = // create an asset configuration item
                new AssetConfigurationItem(
                        "SimSet_1", // asset ID
                        "SimulatedValues_500Hz", // asset name
                        "1001", // measurementPointID
                        "Sinus_500Hz", // measurementPointName
                        "Current", // physicalQuantity, 
                        "Ampere", // unitOfMeasure
                        "Milli", // prefix
                        "Read", // accessRight
                        0 // lineCounter, not relevant for simulated measurement points
                );
        this.assets.addSimulatedAsset( assetConfigurationItem); // add this to the assets
        assetConfigurationItem = // create an asset configuration item
                new AssetConfigurationItem(
                        "SimSet_2", // asset ID
                        "SimulatedValues_1000Hz", // asset name
                        "1002", // measurementPointID
                        "Cosinus_1000Hz", // measurementPointName
                        "Current", // physicalQuantity, 
                        "Voltage", // unitOfMeasure
                        "Milli", // prefix
                        "Read", // accessRight
                        0 // lineCounter, not relevant for simulated measurement points
                );
        this.assets.addSimulatedAsset( assetConfigurationItem); // add this to the assets
    }
    

    @Override
    public void startUp() {
        // this.setRunState( COMMAND.Start); // do nothing for the moment
    }

    /**
     * Changes the internal state machine to the new state and updates
     * the corresponding OPC UA runstate variable tot this new runstate
     * @param from the old runstate to transition away from
     * @param to the new runstate to transition towards to
     */
    private void changeRunState( RUNSTATE from, RUNSTATE to) {
        Logger.getLogger(DataSimulator.class.getName()).log(Level.INFO, "Chaging to state " + to);
        RunStateEvent aRunStateEvent = new RunStateEvent( from, to);
        this.runState = to;
        this.runstateEventListeners.stream().forEach( p -> p.runStateChanged( aRunStateEvent));
        if (this.runStateUaVariableNode != null) {
            this.runStateUaVariableNode.setValue( new DataValue( new Variant( this.runState.toString())));
        }
    }

    @Override
    public Integer doRemotePlayerControl(Integer command) {
        switch( command) {
            case 1: {
                Logger.getLogger(DataSimulator.class.getName()).log(Level.INFO, "Start command received");
                this.setRunState(COMMAND.Start);
                break;
            }
            case 2: {
                Logger.getLogger(DataSimulator.class.getName()).log(Level.INFO, "Stop command received");
                this.setRunState(COMMAND.Stop);
                break;
            }
        }
        return 1;
    }

    public void setRunState( COMMAND aCommand) {
        switch (aCommand) {
            case Start: {
                switch (this.runState) {
                    case Idle: {
                        changeRunState( this.runState, RUNSTATE.Starting);
                        this.workers.stream().forEach( p -> p.startWorker());
                        changeRunState( this.runState, RUNSTATE.Running);
                        break;
                    }
                    case Starting: {
                        // do nothing
                        break;
                    }
                    case Stopping: {
                        // do nothing, let it stop first before starting up
                        break;
                    }
                    case Running: {
                        // do nothing it is already started
                        break;
                    }
                    default: {
                        // do nothing
                        break;
                    }
                }
                break;
            }
            case Stop: {
                switch (this.runState) {
                    case Idle: {
                        // do nothing, it was not even running
                        break;
                    }
                    case Starting: {
                        // do nothing, let it start first before stopping it
                        break;
                    }
                    case Stopping: {
                        // do nothing additionallity because it is already stopping
                        break;
                    }
                    case Running: {
                        // change to runstate stopping
                        changeRunState (this.runState, RUNSTATE.Stopping);
                        this.workers.forEach(aWorker -> aWorker.stopWorker());
                        changeRunState( this.runState, RUNSTATE.Idle);
                        break;
                    }
                    default: {
                        // do nothing
                        break;
                    }
                }
                break;
            }
        }
    }

    private void createWorkers() {
        // for each asset do
        for (Asset anAsset : this.assets.getAssets()) {
            // for each simulated measurement point of this asset
            for (MeasurementPoint aMeasurementPoint : anAsset.getMeasurementPoints()) {
                if (aMeasurementPoint.isSimulated()) {
                    SimulatedMeasurementPoint simulatedMeasurementPoint = (SimulatedMeasurementPoint) aMeasurementPoint;
                    this.createSimulationWorker( simulatedMeasurementPoint, this.sampleRate);
                }
            }
        }
    }

    private void createSimulationWorker(SimulatedMeasurementPoint simulatedMeasurementPoint, int aSampleRate) {
        SimulationWorker worker = new SimulationWorker( simulatedMeasurementPoint, aSampleRate);
        this.workers.add( worker);
    }
    
    /**
     * Set OPC UA RunState variable node. This data simulator
     * will update the node value when its runstate changes.
     * @param aUaVariableNode the OPC UA node to pass to this data simulator
     */
    public void setRunStateUANode( UaVariableNode aUaVariableNode) {
        this.runStateUaVariableNode = aUaVariableNode;
        // set initial value to the current runstate of the dataBackendController
        if ( this.runStateUaVariableNode != null) {
            aUaVariableNode.setValue(new DataValue( new Variant(this.runState.toString())));
        }
    }

    public List<Asset> getAssets() {
        return this.assets.getAssets();
    }
    
    public RUNSTATE getRunstate() {
        return this.runState;
    }
    
    public void addRunstateEventListener( RunstateEventListener aRunstateEventListener) {
        this.runstateEventListeners.add( aRunstateEventListener);
    }
}
