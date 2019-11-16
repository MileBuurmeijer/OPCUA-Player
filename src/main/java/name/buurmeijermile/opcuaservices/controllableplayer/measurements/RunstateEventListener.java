/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataSimulator.RUNSTATE;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public interface RunstateEventListener {
    
    public void runStateChanged( RunStateEvent aRunStateEvent);
}
