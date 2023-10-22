/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.main;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.controllableplayer.client.RecorderClient;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataControllerInterface;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataFilePlayerController;
import name.buurmeijermile.opcuaservices.controllableplayer.server.OPCUAPlayerServer;
import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration.OperationMode;
import name.buurmeijermile.opcuaservices.controllableplayer.server.PlayerNamespace;
import name.buurmeijermile.opcuaservices.utils.Waiter;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
public class MainController implements Runnable {
    private OPCUAPlayerServer playerServer = null;
    private DataControllerInterface theDataControllerInterface = null;
    private Configuration configuration;
    private Logger logger = Logger.getLogger(MainController.class.getName());
    
    public void processCommandlineArguments( String [] args) {
        try {
            // parse command line arguments into a configuration object
            configuration = Configuration.getConfiguration();
            configuration.processCommandLine( args);
            // print version info
            logger.log(Level.INFO, "Version: " + configuration.getAppName() + " | " + configuration.getVersion());
        } catch ( Exception ex) {
            logger.log(Level.SEVERE, "Opc Ua Server exxception thrown", ex);
        }
    }
    
    @Override
    public void run() {
        OperationMode operationMode = configuration.getMode();
        logger.log( Level.INFO, "Running in mode:" + operationMode.name());
        switch (operationMode) {
            case PLAYER: {
                this.startupPlayerServer();
                this.startupPlayerContentServing();
                break;
            } 
            case RECORDER: {
                this.startRecorderClient();
                break;
            }
            default: {
                logger.warning("This default should not have happened");
                System.exit( Configuration.ExitCode.WRONGMODE.ordinal());
                break;
            }
        }
        // stay here forever, TODO: replace for smarter way
        // while (true) {;} // do nothing
    }
    
    
    
    public void startupPlayerServer() {
        try {
            // create the player data back end
            theDataControllerInterface = new DataFilePlayerController( configuration.getConfigFile(), configuration.getDataFile());
            playerServer = new OPCUAPlayerServer( theDataControllerInterface, configuration);
            // check if a player is created
            if (playerServer != null) {
                    // start the OPC UA player server
                    playerServer.startup().get();
                    // let it settle for a while and if auto start apply automaticcaly the start playing command
                    if (configuration.isAutoStart()) {
                        logger.log(Level.INFO, "Autostart: wait before starting");
                        // waitADuration for 10 seconds
                        Waiter.waitADuration(Duration.ofSeconds( 10));
                        logger.log(Level.INFO, "Autostart: giving remote play command");
                        // give the data controller the player start command (=1)
                        theDataControllerInterface.doRemotePlayerControl( 1); // this is done before the data backend is started up!
                    } else {
                        logger.log(Level.INFO, "No autostart");
                    }
            } else {
                logger.log(Level.SEVERE, "OPC UA PlayerServer not initialized");
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    public void startupPlayerContentServing() {
        // create the namespace for this OPCUA player server
        PlayerNamespace playerNamespace = new PlayerNamespace( playerServer.getServer(), theDataControllerInterface, Configuration.getConfiguration());
        playerNamespace.startup();
        // activate the data controller to be ready to serve the player data
        theDataControllerInterface.startUp();
    }
    
    private void startRecorderClient() {
        RecorderClient recorderClient = new RecorderClient();
        recorderClient.start();
    }
    
    public static void main(String[] args) {
        MainController mainController = new MainController();
        // always initialize the configuration with the commandline arguments first
        mainController.processCommandlineArguments( args);
        mainController.run();
        // and add runtime shutdown hook
        final CompletableFuture<Void> future = new CompletableFuture<>();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> future.complete(null)));
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(MainController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
