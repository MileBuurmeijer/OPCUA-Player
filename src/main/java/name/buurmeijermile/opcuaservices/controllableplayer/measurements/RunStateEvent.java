/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataFilePlayerController.RUNSTATE;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class RunStateEvent {
    private RUNSTATE from;
    private RUNSTATE to;
    
    public RunStateEvent( RUNSTATE aFromState, RUNSTATE aToState) {
        from = aFromState;
        to = aToState;
    }

    /**
     * @return the from
     */
    public RUNSTATE getFromRunState() {
        return from;
    }

    /**
     * @return the to
     */
    public RUNSTATE getToRunState() {
        return to;
    }
}
