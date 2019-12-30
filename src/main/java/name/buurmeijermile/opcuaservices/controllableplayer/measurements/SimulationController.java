/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.measurements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import static name.buurmeijermile.opcuaservices.controllableplayer.measurements.MeasurementPoint.SIMULATIONTOKEN;
import static name.buurmeijermile.opcuaservices.controllableplayer.measurements.MeasurementPoint.VARIABLESPLITTOKEN;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class SimulationController{

    private final List<SimulationWorker> workers = new ArrayList<>();
    private final Assets assets;
    private List<MeasurementPoint> simulatedMeasurementPoints;
    private final List<MeasurementPoint> allMeasurementPoints;
    private Logger logger = Logger.getLogger( this.getClass().getName());

    public SimulationController( Assets someAssets) {
        // initialize the variables
        this.assets = someAssets;
        this.simulatedMeasurementPoints = someAssets.getSimulatedMeasurementPoints();
        this.allMeasurementPoints = someAssets.getAllMeasurementPoints();
        // go through four step approach to initialize the simulation controller
        // step [1]: for each simulated measurement point set the simulation function derived from its name
        for (MeasurementPoint aMeasurementPoint : this.simulatedMeasurementPoints) {
            if (aMeasurementPoint.isSimulated()) {
                // strip name and build the simulation expression
                this.setNameAndSimulationFunction( aMeasurementPoint); 
            }
        }
        // step[2]: for each  measurement point derive the measurement points it depends on
        this.simulatedMeasurementPoints = someAssets.getSimulatedMeasurementPoints();
        for (MeasurementPoint aMeasurementPoint : this.simulatedMeasurementPoints) {
            if (aMeasurementPoint.isSimulated()) {
                // derive for each measurement point is dependend measurement points
                this.setMeasurementPointsItDependsOn( aMeasurementPoint);
            }
        }
        // step [3]: for eacht measurement point check if it has circular dependencies
        this.simulatedMeasurementPoints = this.assets.getSimulatedMeasurementPoints();
        for (MeasurementPoint aMeasurementPoint : this.simulatedMeasurementPoints) {
            if (aMeasurementPoint.isSimulated()) {
                // check the measurement point has circular dependencies
                boolean hasCircularDependency = this.hasCircularDependency(aMeasurementPoint, aMeasurementPoint.getDependingMeasurementPoints()); 
                if (hasCircularDependency) {
                    logger.log(Level.SEVERE, "Measurement point [" + aMeasurementPoint.getName() + "] has circular dependencies");
                    aMeasurementPoint.setSimulated( false);
                }
            }
        }
        // step[4]:  for each remaining simulated measurement point create a simulation worker
        this.simulatedMeasurementPoints = this.assets.getSimulatedMeasurementPoints();
        for (MeasurementPoint aMeasurementPoint : this.simulatedMeasurementPoints) {
            SimulationWorker worker = new SimulationWorker( aMeasurementPoint);
            this.workers.add( worker);
        }
    }
    
    /**
     * This method set the name of the measurement point and 
     * @param aMeasurementPoint 
     */
    private void setNameAndSimulationFunction( MeasurementPoint aMeasurementPoint) {
        String measurementPointName = aMeasurementPoint.getName();
        // check if the name start with the simulation token
        if (measurementPointName.startsWith( SIMULATIONTOKEN)) {
            // handle simulation, but first derive the name
            int parenthesisPosition = measurementPointName.indexOf('(', 1); // start looking after the SIMULATIONTOKEN until parenthesis open
            // check if bigger then 1 becasue the name should at least contain 1 character
            if ( parenthesisPosition > 1) {
                // OK found, strip the real name out of the formula string, example formula string: "#sinus(t)[100]:sin(2t)*2
                String realName = measurementPointName.substring(1, parenthesisPosition); // end index excluded
                aMeasurementPoint.setName( realName); // reset the name from its current scripted simulation formula state to the stripped name
                // then analyze the rest for the functon definition
                String functionDefinition = measurementPointName.substring( parenthesisPosition);
                this.setSimulationFunction( aMeasurementPoint, functionDefinition);
            } else {
                // log error because parenthesis open was not found and therefor the name can not properly be set
                logger.log(Level.SEVERE, "Parenthesis open was not found that should properly ends the name of this measurement point: " + aMeasurementPoint.getName());
                aMeasurementPoint.setSimulated( false);
            }
        }
    }
    
    /**
     * This method sets the simulation function based on the Exp4J syntax used in the part of measurement point name that defines the function.
     * It also sets the simulation update frequency.
     * @param aMeasurementPoint the measurement point of which we set the simulation function
     * @param functionDefinition  the string in Expr4J syntax that defines the function
     */
    private void setSimulationFunction(MeasurementPoint aMeasurementPoint, String functionDefinition) {
        // todo: think aobut moving this method to the simulator controller=> that combines a simulation logic (config & run)
        // function definition example (t,x)[10]:sinus(2t) + 50x
        Set<String> variableSet = new HashSet<>(); // set to hold the variables of the simulation function
        if ( functionDefinition.startsWith( "(")) {
            // OK thats correct, let's find the closing parenthesis
            int closingParenthesis = functionDefinition.indexOf(')');
            if ( closingParenthesis >= 1) {
                // strip the part with variables
                String variableString = functionDefinition.substring(1, closingParenthesis); // end index not included
                String [] variables = variableString.split( VARIABLESPLITTOKEN);
                // add variables to the variable list
                for ( String aVariable: variables) {
                    variableSet.add( aVariable); // add the variables to the list
                }
                // decode the update frequency term
                int openBracketIndex = functionDefinition.indexOf( '[');
                if ( openBracketIndex > 0) {
                    // OK found, lets find the integer between the brackets
                    int closeBracketIndex = functionDefinition.indexOf( ']');
                    if ( closeBracketIndex > openBracketIndex + 1) {
                        String updateFrequencyString = functionDefinition.substring( openBracketIndex + 1, closeBracketIndex); // one further then open bracket index
                        // check if its a number
                        if ( updateFrequencyString.matches("\\d*")) { // matches any number
                            int simulationUpdateFrequency = Integer.parseInt( updateFrequencyString);
                            aMeasurementPoint.setSimulationUpdateFrequency( simulationUpdateFrequency);
                            // OK continue processing the expression of this simulator function
                            // find the semicolon after whcih the expression is defined
                            int semiColumnIndex = functionDefinition.indexOf( ':', closeBracketIndex);
                            if ( semiColumnIndex > 0) {
                                // get the expression part
                                String expressionString = functionDefinition.substring(semiColumnIndex + 1);
                                Expression simulationExpression =
                                        new ExpressionBuilder( expressionString)
                                        .variables( variableSet)
                                        .build();
                                if (simulationExpression != null) {
                                    aMeasurementPoint.setSimulationExpression( simulationExpression);
                                } else {
                                    // simulation expression was not properly build / valid
                                    logger.log(Level.SEVERE, "Simulation expression was not properly build / valid: " + expressionString);
                                }
                            } else {
                                // expression opening bracket is missing
                                logger.log(Level.SEVERE, "Expression opening bracket is missing: " + aMeasurementPoint.getName() + functionDefinition);
                            }
                        } else {
                            // updatefrequency is not a number
                            logger.log(Level.SEVERE, "Updatefrequency is not a number: " + aMeasurementPoint.getName() + functionDefinition);
                        }
                    } else {
                        // update frequency not given => not supported thorugh default yet
                        logger.log(Level.SEVERE, "Update frequency not given: " + aMeasurementPoint.getName() + functionDefinition);
                    }
                } else {
                    // update frequency not present => not supported through default yet
                    logger.log(Level.SEVERE, "Update frequency not present []: " + aMeasurementPoint.getName() + functionDefinition);
                }
            } else {
                // variable list is empty that means a constant function => that's not support because that can easily done through the player data file, a single value for a data point remains that until end of time
                logger.log(Level.SEVERE, "Variable list is empty that means a constant function: " + aMeasurementPoint.getName() + functionDefinition);
            }
        } else {
            // function definition doesn't start with (
            logger.log(Level.SEVERE, "Function definition doesn't start with (: " + aMeasurementPoint.getName() + functionDefinition);
        }
    }

    public void startSimulation() {
        this.workers.stream().forEach( p -> p.startWorker());
        logger.log(Level.INFO, "Simulation started, all workers got the signal");
    }
    
    public void stopSimulation() {
        this.workers.forEach(aWorker -> aWorker.stopWorker());
        logger.log(Level.INFO, "Simulation stopped, all workers got the signal");
    }

    /**
     * This method derives the depending measurement points of a simulated measurement point. 
     * It will not find time t, because that has not a measurement point as source.
     * @param aMeasurementPoint 
     */
    private void setMeasurementPointsItDependsOn(MeasurementPoint aMeasurementPoint) {
        // TODO: decide if this logic should go the measurement point, for now not possible or measurement point should know assets
        // get the expression to get its variables
        Expression expression = aMeasurementPoint.getSimulationExpression();
        // create a map with variables and measurement point for those variables
        Map<String, MeasurementPoint> dependingMeasurementPoints = new HashMap<>();
        if ( expression != null) {
            // get the variables
            Set<String> variables = aMeasurementPoint.getSimulationExpression().getVariableNames();
            // loop throug all measurement points and find point with name same as variable
            for (String variableName: variables) {
                boolean found = false;
                // loop thorugh the measurement points
                for (MeasurementPoint anotherMeasurementPoint : this.allMeasurementPoints) {
                    // check if we are not pointing at the same measurement point as wee started with
                    if (anotherMeasurementPoint != aMeasurementPoint) {
                        // create dotted variable name
                        String dottedVariableName = variableName.replace('_', '.');
                        // check if the name is the same as the variable name we are looking for
                        if ( anotherMeasurementPoint.getFullDottedName().contentEquals( dottedVariableName)) {
                            found = true;
                            // OK found a depending measurement point, add it to the set
                            dependingMeasurementPoints.put( variableName, anotherMeasurementPoint);
                        }
                    }
                }
                if ( !variableName.contentEquals("t")) {
                    logger.log(Level.WARNING, "In 'setMeasurementPointsItDependsOn' variable " + variableName + " not found while processing measurementpoint " + aMeasurementPoint.getFullDottedName());
                }
            }
            // check if there where measurement points found for all the variables the expression had
            if ( dependingMeasurementPoints.size() > 0) {
                // set the measurement points this measurement point depends
                aMeasurementPoint.setDependingMeasurementPoints( dependingMeasurementPoints);
            }
        }
    }
    
    /**
     * This method check if a measurement point has circular dependencies (aka
     * circular reference) and is therefor invalid. This method calls itself recursively. 
     * @param theMeasurementPoint the measurement point of which circular dependency is checked
     * @param dependingMeasurementPoints the measurement points that the original measurement point depends on direct or indirect
     * @return true if is has circular dependencies
     */
    private boolean hasCircularDependency( MeasurementPoint theMeasurementPoint, Collection<MeasurementPoint> dependingMeasurementPoints) {
        // check if he measurement point is in the set with dependencies
        boolean result = dependingMeasurementPoints.stream().anyMatch( mp -> mp.equals( theMeasurementPoint));
        if (result) {
            // OK then there is cicular dependency, return with stil bad result
            // TODO: log proper measurement point that has dependencies on original measurement point
            return result;
        } else {
            // if not check each measurement point from the set of dependend measurement points if dependend on the measurement point we started with
            for (MeasurementPoint anotherMeasurementPoint: dependingMeasurementPoints) {
                // for each depending measurement point get its dependencies
                Collection<MeasurementPoint> anotherSetOfDependencies = anotherMeasurementPoint.getDependingMeasurementPoints();
                // check if this set contains dependencies
                if ( anotherSetOfDependencies.size() > 0) {
                    // if so call this same routine and check this set for dependencies
                    result = this.hasCircularDependency(theMeasurementPoint, anotherSetOfDependencies);
                    if (result) {
                        return result;
                    } else {
                        // do nothing special, because there weher no circular dependencies found for this collection of dependend measurement points
                    }
                } else {
                    // do nothing, because we came at a leaf of the dependency tree, this measurement point has no further depdencies
                }
            }
            return result;
        }
    }

    public void setUAActualSimulationSpeedNode( MeasurementPoint aMeasurementPoint, UaVariableNode aUaVariableNode) {
        // find the corresponding simulation worker
        SimulationWorker simulationWorker = this.workers.stream().filter( mp -> mp.getMeasurementPoint().getFullDottedName().contentEquals( aMeasurementPoint.getFullDottedName())).findAny().get();
        if ( simulationWorker != null) {
            simulationWorker.setUaVariableNode( aUaVariableNode);
        }
    }
}
