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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.utils.Waiter;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

/**
 * OPC UA Data Backend Controller. It provides input for the OPC UA namespace
 * and provides values to the variable nodes 
 * @author mbuurmei
 */
public class DataFilePlayerController implements Runnable, DataControllerInterface {

    public static enum RUNSTATE { Initialized, PlayForward, PlayFastForward, PlayBackward, PlayFastBackward, Paused} 
    public static enum COMMAND { Play, Backward, PlayFast, BackwardFast ,Stop, Pause, Endless}
    
    private Map<Integer, COMMAND> commandMap;
    private Assets theAssets;
    private final DataStreamController dataStreamController;
    private RUNSTATE currentState;
    private RUNSTATE prePausedState;
    private final File inputDataFile;
    private final File assetConfigurationFile;
    private boolean endless = true;
    private UaVariableNode runStateUaVariableNode;
    private List<RunstateEventListener> runstateEventListeners;
    private SimulationController simulationController;
        
    public DataFilePlayerController( File aConfigFile, File aDataFile) {
        this.inputDataFile = aDataFile;
        this.assetConfigurationFile = aConfigFile;
        this.createAssetStructure();
        this.dataStreamController = new DataStreamController( this.inputDataFile, this);
        this.runstateEventListeners = new ArrayList<>();
        this.currentState = RUNSTATE.Initialized;
        this.initializeCommandMap();
    }
        
    private void writeDataLogEntry( String currentTimestamp, String assetID, String channelID, double value, String sourceTimestamp, String duration) {
        String output = "=|" + currentTimestamp + ';' + assetID + ';' + channelID + ';' + value + ';' + sourceTimestamp + ';' + duration;
        Logger.getLogger(this.getClass().getName()).log(Level.INFO, output);
    }
    
    private void createAssetStructure() {
        ConfigurationController configurationController = new ConfigurationController( this.assetConfigurationFile);
        this.theAssets = configurationController.createAssetStructure();
        this.simulationController = configurationController.getSimulationController();
    }

    /**
     * Get the assets read from the configuration file.
     * @return the assets
     */
    public List<Asset> getHierarchicalAssetList() {
        return this.theAssets.getHierachicalAssets();
    }
    
    /**
     * Get the assets read from the configuration file.
     * @return the assets
     */
    public List<Asset> getFlatAssetList() {
        return this.theAssets.getFlattenedAssets();
    }

    public boolean isPlayFast() {
        return this.currentState == RUNSTATE.PlayFastBackward || this.currentState == RUNSTATE.PlayFastForward;
    }
    
    public boolean isPlayForward() {
        return this.currentState == RUNSTATE.PlayForward || this.currentState == RUNSTATE.PlayFastForward;
    }
    
    public boolean isPlayBackward() {
        return this.currentState == RUNSTATE.PlayBackward || this.currentState == RUNSTATE.PlayFastBackward;
    }

    public boolean isPlayState() {
        return this.isPlayBackward() || this.isPlayForward();
    }

    /**
     * OPC UA method for remote controlling the data provisioning (tart, stop, etc).
     * @param controlCommand the remote control command to be executed
     * @return result of executing remote control command.
     */
    public Integer doRemotePlayerControl(Integer controlCommand) {
        boolean commandValid = this.commandMap.containsKey( controlCommand);
        Integer commandResult;
        if ( commandValid) {
            commandResult = this.setCommand( this.commandMap.get( controlCommand));
        } else {
            commandResult = 0;
        }
        return commandResult;
    }
    
    /**
     * Get the current runstate the player is in.
     * @return the currentState
     */
    public String getCurrentState() {
        return currentState.toString();
    }
    
    public void addRunstateEventListener( RunstateEventListener aRunstateEventListener) {
        this.runstateEventListeners.add( aRunstateEventListener);
    }

    /**
     * Set OPC UA RunState variable node. This data backend controller
     * will update the node value when its runstate changes.
     * @param aUaVariableNode the node to pass to this backend controller
     */
    public void setRunStateUANode( UaVariableNode aUaVariableNode) {
        this.runStateUaVariableNode = aUaVariableNode;
        // set initial value to the current runstate of the dataBackendController
        if ( this.runStateUaVariableNode != null) {
            aUaVariableNode.setValue(new DataValue( new Variant(this.getCurrentState())));
        }
    }

    /**
     * Changes the internal state machine to the new state and updates
     * the corresponding OPC UA runstate variable tot this new runstate
     * @param newRunState the new runstate to transition to
     */
    private void changeRunState( RUNSTATE fromState, RUNSTATE toState) {
        Logger.getLogger(SimulationController.class.getName()).log(Level.INFO, "Chaging to state " + toState);
        RunStateEvent aRunStateEvent = new RunStateEvent( fromState, toState);
        this.currentState = toState;
        this.runstateEventListeners.stream().forEach( p -> p.runStateChanged( aRunStateEvent));
        // set the OPC UA runstate variable node accordingly
        if (this.runStateUaVariableNode != null) {
            this.runStateUaVariableNode.setValue( new DataValue( new Variant( toState)));
        }
    }
    
    /**
     * Initialize command map for remote control of this OPC UA data backend controller
     */
    private void initializeCommandMap() {
        // command codes: 1 = play forward, 2 = play forward double speed, 3 = playbackward, 4 = play backward double speed, 5 = pause, 6 = stop, 7 = toggle endless mode
        commandMap = new HashMap<>();
        commandMap.put(1, COMMAND.Play);
        commandMap.put(2, COMMAND.PlayFast);
        commandMap.put(3, COMMAND.Backward);
        commandMap.put(4, COMMAND.BackwardFast);
        commandMap.put(5, COMMAND.Pause);
        commandMap.put(6, COMMAND.Stop);
        commandMap.put(7, COMMAND.Endless);
    }

    /**
     * State machine sets the next state based on the remote control command 
     * and allowable/possible transitions. It also the things needed when
     * going to this new state.
     * @param aCommand the remote control command to based the next state on
     * @return the resulting next state
     */
    private Integer setCommand( COMMAND aCommand) {
        // runstates:  Initialized, PlayForward, PlayForwardDoubleSpeed, PlayBackward, PlayBackwardDoubleSpeed, Paused, Endless
        Integer commandResult = 1; // 1 is good, 0 is bad, assume good until proven otherwise
        switch (aCommand) {
            case Play: {
                if (this.currentState == RUNSTATE.PlayForward) {
                    this.changeRunState( this.currentState, RUNSTATE.PlayFastForward);
                } else {
                    this.changeRunState( this.currentState, RUNSTATE.PlayForward);
                    if (this.simulationController != null) {
                        this.simulationController.startSimulation();
                    }
                }
                break;
            }
            case PlayFast: {
                this.changeRunState( this.currentState, RUNSTATE.PlayFastForward);
                break;
            }
            case Backward: {
                if (this.currentState == RUNSTATE.PlayBackward) {
                    this.changeRunState( this.currentState, RUNSTATE.PlayFastBackward);
                } else {
                    this.changeRunState( this.currentState, RUNSTATE.PlayBackward);
                }
                break;
            }
            case BackwardFast: {
                this.changeRunState( this.currentState, RUNSTATE.PlayFastBackward);
                break;
            }
            case Pause: {
                // check if we were already in the paused state
                // TODO: think whats need to be done with the timeshift of the read timestamps from input data file
                if (this.currentState == RUNSTATE.Paused) {
                    // if so resume to pre-paused state
                    this.changeRunState( this.currentState, this.prePausedState);
                    this.prePausedState = null;
                } else {
                    // if not in paused state store current state to pre-paused state
                    this.prePausedState = this.currentState;
                    this.changeRunState( this.currentState, RUNSTATE.Paused);
                }
                break;
            }
            case Stop: {
                // check if there are simulation running
                if (this.simulationController != null) {
                    this.simulationController.stopSimulation();
                }
                // reset the time shift
                MeasurementDataRecord.resetTimeShift();
                // reset all measurement points to its initial values
                this.clearMeasurementPointValues();
                // goto initialized runstate
                this.changeRunState( this.currentState, RUNSTATE.Initialized);
                break;
            }
            case Endless: {
                this.endless = !this.endless;
                break;
            }
            default: {
                commandResult = 0; // this should not have happened
                break;
            }
        }
        Logger.getLogger( this.getClass().getName()).log(Level.INFO, String.format("Command %s led to runstate %s \n", aCommand, this.getCurrentState()));
        return commandResult;
    }
    
    private void clearMeasurementPointValues() {
        // reset all measurement points to their initial values, use flattend asset list for this
        this.theAssets.getFlattenedAssets().stream().forEach( asset -> asset.getMeasurementPoints().stream().forEach( mp -> mp.clearValue()));
    }
    
    public void startUp() {
        Thread thread = new Thread( this);
        thread.start();
    }

    /**
     * Main loop of the data backend controller. Based on the set run state 
     * it processes the read data or waits for subsequent commands.
     */
    @Override
    public void run() {
        this.changeRunState( this.currentState, RUNSTATE.Initialized);
        // loop for ever in the following state machine
        while ( true) {
            // only do something when not in initialized state
            if (this.currentState != RUNSTATE.Initialized) {
                boolean streamOpen = false;
                // start with getting the datastream in
                if ( this.currentState == RUNSTATE.PlayBackward || this.currentState == RUNSTATE.PlayFastBackward) {
                    streamOpen = this.dataStreamController.getDataStream( false);
                } else {
                    streamOpen = this.dataStreamController.getDataStream( true);
                }
                if (!streamOpen) {
                    Logger.getLogger( this.getClass().getName()).log(Level.WARNING, "Could not open data stream");
                }
                // loop through data until the end or runstate became initialized again after a stop command
                while (streamOpen && this.dataStreamController.hasNext() && this.currentState != RUNSTATE.Initialized) {
                    switch (this.currentState) {
                        case PlayFastForward:
                        case PlayForward: {
                            this.dataStreamController.processSample();
                            break;
                        }
                        case PlayFastBackward:
                        case PlayBackward: {
                            // TODO: implement, no idea yet how to do this
                            break;
                        }
                        case Paused: {
                            Waiter.wait( Duration.ofSeconds( 1)); // wait for 1 seconds for something to happen
                            break;
                        }
                        case Initialized: {
                            // do nothing and jump out of this while loop
                            break;
                        }
                        default: {
                            // this should not happen
                            break;
                        }
                    }
                }
                // check if we maintain in non initialized state
                if ( this.currentState != RUNSTATE.Initialized) {
                    // check if there was no longer input to proces
                    if ( !this.dataStreamController.hasNext()) {
                        // reset the time shift
                        MeasurementDataRecord.resetTimeShift();
                        // reset all measurement points to its initial values
                        this.clearMeasurementPointValues();
                        // so end of input data file
                        if (this.endless) {
                            Waiter.wait( Duration.ofSeconds( 1)); // wait for 1 seconds to show the resetted measurement points
                            Logger.getLogger( this.getClass().getName()).log(Level.INFO, "Reached end of file & endless, so resetting all node values to zero");
                        } else {
                            // if not endless mode return to the initialized state
                            this.changeRunState( this.currentState, RUNSTATE.Initialized);
                            Logger.getLogger( this.getClass().getName()).log(Level.INFO, "Reached end of file & !endless, so goto state initialized");
                        }
                    } else {
                        // there was stil input to process and not in init state => this can not happen
                        // the while loop above can only exit when there is no input 
                        // or when in initialized state!
                        Logger.getLogger( this.getClass().getName()).log(Level.WARNING, "There was stil input to process and not in initialized state");
                    }
                } else {
                    // what to do if we are in initialized state? => stopped situation
                    // that situation was already handled in stop command processing in set command
                    Logger.getLogger( this.getClass().getName()).log(Level.INFO, "Stop state reached in while loop");
                }
            } else {
                // still in runstate initialized, let's wait a while
                Waiter.wait( Duration.ofSeconds( 1)); // wait for 1 seconds for something to happen
            }
        }
    }
}