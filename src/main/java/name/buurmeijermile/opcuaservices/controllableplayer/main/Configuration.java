/* 
 * The MIT License
 *
 * Copyright 2019 Milé Buurmeijer <mbuurmei at netscape.net>.
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
package name.buurmeijermile.opcuaservices.controllableplayer.main;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Enumeration;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration.ExitCode;
import static name.buurmeijermile.opcuaservices.controllableplayer.main.Configuration.OperationMode.PLAYER;
import name.buurmeijermile.opcuaservices.controllableplayer.server.OPCUAPlayerServer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 *
 * @author mbuurmei
 */
public class Configuration {

    public static enum ExitCode { NORMAL, CONFIGFILEERROR, DATAFILEERROR, WRONGMODE, 
        WRONGDURATION, WRONGSAMPLINGINTERVAL, WRONGPUBLISHINGINTERVAL, TMPDIRFAILS, CONFIGFILEEXISTS, CONNECTIONFAILED};
    public static enum OperationMode { 
        PLAYER("player"), 
        RECORDER("recorder");
        private final String modeName;
        private OperationMode( String aName) {
            modeName = aName;
        }
        @Override
        public String toString() {
            return modeName;
        }

        public static OperationMode matchValueOf(String modeString) {
            if (modeString.contentEquals(PLAYER.toString())) {
                return PLAYER;
            } else {
                if (modeString.contentEquals( RECORDER.toString())) {
                    return RECORDER;
                } else {
                    return null;
                }
            }
        }
    };

    private static final String PLAINUSER = "user";
    private static final String ADMINUSER = "admin";
    private static final String USERPASSWORD = "8h5%32@!~"; // default user password
    private static final String ADMINPASSWORD = "6g8&fs*()"; // default admin password

    private static final String DATAFILEKEYWORD = "datafile";
    private static final String CONFIGFILEKEYWORD = "configfile";
    private static final String AUTOSTARTKEYWORD = "autostart";
    private static final String PORTKEYWORD = "port";
    private static final String NAMESPACEKEYWORD = "namespace";
    private static final String URIKEYWORD = "uri";
    private static final String SERVICENAMEKEYWORD = "servicename";
    private static final String MODEKEYWORD = "mode";
    private static final String DURATIONKEYWORD = "duration";
    private static final String SAMPLINGINTERVALKEYWORD = "samplinginterval";
    private static final String PUBLISHINGINTERVALKEYWORD = "publishinginterval";
    private static final String CAPTUREINFOMODELKEYWORD = "captureinformationmodel";
    private static final String STARTNODEKEYWORD = "startnode";
    
    private static final String SECURITYFOLDERNAME = "opcua-player-recorder-security";
    
    private static final int    PORT = 12000; // default port
    

    private static Configuration PLAYERCONFIGURATIONSINGLETON;

    private String userPassword;
    private String plainUser;
    private String adminUser;
    private String adminPassword;
    private boolean autoStart;
    private File dataFile;
    private File configFile;
    private String configFileName;
    private int port;
    private boolean captureInformationModel = false; // default value is false
    private String startNode = "ns=0;i=85"; //default is "Object" folder for browsing the information model
    private Duration recordingDuration;
    private Double samplingInterval = 250.0; // default value of 250 mS
    private Double publishingInterval = 500.0; // default value of 500mS
    private String version = "0.0.0 - not run from jar file"; // default version nummer logged at startup
    private String appName = "noname - not run from jar file"; // default app name logged at startup
    private String namespace = "urn:SmileSoft:OPC_UA_Player"; // default namespace for the data that the player serves
    private String uri = "OPCUA-Player"; // default uri that the player use to serve its data, <hostanme>:<port>/<uri>
    private String serviceName = "Smiles-OPCUA-Player"; // default service name that is providde when discovering the service the player provides
    private final String securityFolderName = SECURITYFOLDERNAME; // default name of temporary security folder for certicates and the like
    private final Logger logger;
    private OperationMode mode = OperationMode.PLAYER; // default value
    private Options options = new Options();
    private CommandLineParser parser = new DefaultParser();

    private Configuration() {
        this.port = PORT;
        this.userPassword = USERPASSWORD;
        this.adminPassword = ADMINPASSWORD;
        this.plainUser = PLAINUSER;
        this.adminUser = ADMINUSER;
        this.logger = Logger.getLogger(OPCUAPlayerServer.class.getName());
        this.createOptions();
    }

    public static Configuration getConfiguration() {
        if (PLAYERCONFIGURATIONSINGLETON == null) {
            PLAYERCONFIGURATIONSINGLETON = new Configuration();
        }
        return PLAYERCONFIGURATIONSINGLETON;
    }
    
    private void createOptions() {
        options = new Options();
        // add config file command line option
        Option option = Option.builder(CONFIGFILEKEYWORD)
                .argName("file")
                .required(true) // config file is only mandatory command line argument
                .desc("use given file for reading configuration of this OPC UA Player")
                .hasArg(true)
                .build();
        options.addOption(option);
        // add data file command line option
        option = Option.builder(DATAFILEKEYWORD)
                .argName("file")
                .required(false)
                .desc("use given file for reading data that will be played back to this OPC UA Player")
                .hasArg(true)
                .build();
        options.addOption(option);
        // add port command line option
        option = Option.builder(PORTKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("use to set the port on which the OPC UA Player provides its services")
                .build();
        options.addOption(option);
        // add namespace command line option
        option = Option.builder(NAMESPACEKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("use to set the namespace for the data the OPC UA Player serves")
                .build();
        options.addOption(option);
        // add url command line option
        option = Option.builder(URIKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("use to set the uri part after the / on which the OPC UA Player provides its services")
                .build();
        options.addOption(option);
        // add servicename command line option
        option = Option.builder(SERVICENAMEKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("use to set the service name for the services OPC UA Player provides")
                .build();
        options.addOption(option);
        // add autostart command line option
        option = Option.builder(AUTOSTARTKEYWORD)
                .required(false)
                .desc("use to start serving values immediatly without waiting for a remote control command")
                .hasArg(false)
                .build();
        options.addOption(option);
        // add mode command line option
        option = Option.builder(MODEKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("to set if it needs to run in player or recorder mode")
                .build();
        options.addOption(option);
        // add cature information model command line option
        option = Option.builder(CAPTUREINFOMODELKEYWORD)
                .required(false)
                .hasArg(false)
                .desc("flags that information model of OPCUA server needs to be captured in a usable config file")
                .build();
        options.addOption(option);
        // add duration command line option
        option = Option.builder(DURATIONKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("set how long the recording must be performed in 'hh:mm:ss' format")
                .build();
        options.addOption(option);
        // add sampling interval command line option
        option = Option.builder(SAMPLINGINTERVALKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("set the sampling interval in milliseconds of the monitored variable node items")
                .build();
        options.addOption(option);
        // add publishing interval command line option
        option = Option.builder(PUBLISHINGINTERVALKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("set publishing interval in milliseconds for the subscription")
                .build();
        options.addOption(option);
        // add publishing interval command line option
        option = Option.builder(STARTNODEKEYWORD)
                .required(false)
                .hasArg(true)
                .desc("set from which tag downwards the information model of target servers needs to be captured")
                .build();
        options.addOption(option);
    }

    public void processCommandLine(String[] args) {
        // print version info
        this.getManifestInfo();
        // parse arguments
        try {
            // parse command line
            CommandLine cmd = parser.parse(options, args);
            // check if any arguments are given
            if (args.length != 0) {
                // list all arguments
                logger.log(Level.INFO, "The command line arguments received are listed below:");
                cmd.iterator().forEachRemaining( 
                        optionObject -> { 
                            if (optionObject.hasArg()) {
                                logger.log(Level.INFO, "[" + optionObject.getOpt() + "]=" + optionObject.getValue());
                            } else {
                                logger.log(Level.INFO, "[" + optionObject.getOpt() + "]" + optionObject.getValue());
                            }
                        }
                );
            } else {
                // print out instructions
                logger.info("Welcome to the OPC UA Player that serves timeseries data from a file in real time");
                logger.info("Argument options are listed below:");
                for (Option anOption : options.getOptions()) {
                    logger.info( "- " + anOption.getOpt() + " => " + anOption.getDescription());
                }
                // and exit the VM
                System.exit( ExitCode.NORMAL.ordinal());
            }
            logger.log(Level.INFO, "Now interpreting the given command line arguments:");
            // check if mode was set, this must the first thing to check so that the mode 
            // can be used to interpret the other command line arguments
            if (cmd.hasOption( MODEKEYWORD)) {
                String optionString = cmd.getOptionValue(MODEKEYWORD);
                switch (OperationMode.matchValueOf( optionString)) {
                    case PLAYER: {
                        mode = OperationMode.PLAYER;
                        break;
                    }
                    case RECORDER: {
                        mode = OperationMode.RECORDER;
                        break;
                    }
                    default: { 
                        logger.log( Level.SEVERE, "Wrong mode, use '-mode player' or '-mode recorder' as mode commandline option");
                        System.exit( ExitCode.WRONGMODE.ordinal()); // exit application with proper exit code
                    }
                }
            } // no else needed becaause its an optional argument and the default mode is Player
            logger.log( Level.INFO, "Mode: " + this.mode.name());
            // ===> generic commands for both player as well as recorder mode <===
            // check if autostart command is present
            if (cmd.hasOption(AUTOSTARTKEYWORD)) {
                autoStart = true;
                logger.log(Level.INFO, "Autostart=" + autoStart);
            }
            // check if data file was present
            if (cmd.hasOption(DATAFILEKEYWORD)) { // optional
                String dataFileString = cmd.getOptionValue(DATAFILEKEYWORD);
                logger.log(Level.INFO, "Datafile=" + dataFileString);
                // create file reference to data file
                dataFile = new File( dataFileString);
                if ( mode == OperationMode.PLAYER) {
                    if (!dataFile.exists() || !dataFile.canRead() || !dataFile.isFile()) {
                        logger.log(Level.SEVERE, "Data file " + dataFileString + " can't be read or does not exist");
                        System.exit( ExitCode.DATAFILEERROR.ordinal()); // exit application with proper exit code
                    }
                } else { // OK. RECORDER mode
                    if (dataFile.exists() || dataFile.isDirectory()) {
                        logger.log(Level.SEVERE, "Data file " + dataFileString + " exists or is a directory");
                        System.exit( ExitCode.DATAFILEERROR.ordinal()); // exit application with proper exit code
                    }
                }
            }
            if (cmd.hasOption(CONFIGFILEKEYWORD)) {
                configFileName = cmd.getOptionValue(CONFIGFILEKEYWORD);
                logger.log(Level.INFO, "Configfile=" + getConfigFileName());
                // create file reference to data file
                configFile = new File( getConfigFileName());
                if ( mode ==  OperationMode.PLAYER) {
                    if (!configFile.exists() || !configFile.canRead()) {
                        logger.log(Level.SEVERE, "Config file %s can't be read or does not exist", getConfigFileName());
                        System.exit( ExitCode.CONFIGFILEERROR.ordinal()); // exit application with proper exit code
                    }
                } else { // so in RECORDER mode
                    if (cmd.hasOption(CAPTUREINFOMODELKEYWORD)) {
                        if (configFile.exists() || configFile.isDirectory()) {
                            logger.log(Level.SEVERE, "Config file exists or is directory", getConfigFileName());
                            System.exit( ExitCode.CONFIGFILEERROR.ordinal()); // exit application with proper exit code
                        }
                    } else {
                        if ( !configFile.exists() || configFile.isDirectory()) {
                            logger.log(Level.SEVERE, "Config file does not exist or is directory", getConfigFileName());
                            System.exit( ExitCode.CONFIGFILEERROR.ordinal()); // exit application with proper exit code
                        }
                    }
                }
            } else {
                // flag missing -configfile command line option
                logger.log(Level.SEVERE, "-configfile argument is missing");
            }
            // check if uri was assigned
            if (cmd.hasOption(URIKEYWORD)) {
                this.uri = cmd.getOptionValue(URIKEYWORD);
                logger.log(Level.INFO, "Uri=" + this.uri);
            } else {
                // flag missing -uri command line option
                logger.log(Level.SEVERE, "uri argument is missing");
            }
            // handle 'mode' specific commandline arguments
            if (mode.equals(OperationMode.PLAYER)) {
                // ===> player mode specific additional commands <===
                // check if port number was assigned
                if (cmd.hasOption(PORTKEYWORD)) {
                    String portString = cmd.getOptionValue(PORTKEYWORD);
                    port = Integer.parseInt(portString, 10);
                    logger.log(Level.INFO, "Port=" + port);
                }
                // check if namespace was assigned
                if (cmd.hasOption(NAMESPACEKEYWORD)) {
                    this.namespace = cmd.getOptionValue(NAMESPACEKEYWORD);
                    logger.log(Level.INFO, "Namespace=" + this.namespace);
                }
                // check if serviceName was assigned
                if (cmd.hasOption(SERVICENAMEKEYWORD)) {
                    this.serviceName = cmd.getOptionValue(SERVICENAMEKEYWORD);
                    logger.log(Level.INFO, "Servicename=" + this.serviceName);
                }
            } else {
                if (mode.equals(OperationMode.RECORDER)) {
                    // ===> recorder mode specific additional commands <===
                    // check if capture info model was requested
                    if (cmd.hasOption(CAPTUREINFOMODELKEYWORD)) {
                        // OK we just need to capture the information model
                        this.captureInformationModel = true;
                        // check if start node was set
                        if (cmd.hasOption(STARTNODEKEYWORD)) {
                            this.startNode = cmd.getOptionValue(STARTNODEKEYWORD);
                            logger.log(Level.INFO, "StartNode=" + this.startNode);
                        }
                    } else {
                        // check other command line arguments that are relevant for recording
                        // check if duration of the recording was assigned
                        if (cmd.hasOption(DURATIONKEYWORD)) {
                            String durationString = cmd.getOptionValue(DURATIONKEYWORD);
                            recordingDuration = this.parseDuration( durationString);
                            logger.log(Level.INFO, "Duration=" + getRecordingDuration());
                            if (getRecordingDuration() == null) {
                                System.exit( ExitCode.WRONGDURATION.ordinal()); // exit application with proper exit code
                            }
                        }
                        // check if sampling interval of the monitored items was set
                        if (cmd.hasOption(SAMPLINGINTERVALKEYWORD)) {
                            String samplingIntervalString = cmd.getOptionValue(SAMPLINGINTERVALKEYWORD);
                            try {
                                samplingInterval = Double.parseDouble(samplingIntervalString);
                            } catch (NumberFormatException nfe) {
                                logger.log(Level.SEVERE, "Sampling interval can not be parsed as double");
                            }
                            if (samplingInterval >= 0.0) { //TODO: do we need to check maximum value?
                                logger.log(Level.INFO, "Sampling interval=" + getSamplingInterval());
                            } else {
                                logger.log(Level.SEVERE, "Sampling interval can not be negative");
                                System.exit( ExitCode.WRONGSAMPLINGINTERVAL.ordinal()); // exit application with proper exit code
                            }
                        }
                        // check if duration of the recording was assigned
                        if (cmd.hasOption(PUBLISHINGINTERVALKEYWORD)) {
                            String publishingIntervalString = cmd.getOptionValue(PUBLISHINGINTERVALKEYWORD);
                            try {
                                publishingInterval = Double.parseDouble(publishingIntervalString);
                            } catch (NumberFormatException nfe) {
                                logger.log(Level.SEVERE, "Publshing interval can not be parsed as double");
                            }
                            if (getPublishingInterval() >= 0) {
                                logger.log(Level.INFO, "Publishing interval=" + publishingInterval);
                            } else {
                                logger.log(Level.SEVERE, "Publshing interval can not be negative");
                                System.exit( ExitCode.WRONGPUBLISHINGINTERVAL.ordinal()); // exit application with proper exit code
                            }
                        }
                    }
                }
            }
        } catch (ParseException ex) {
            logger.log(Level.SEVERE, "Error parsing command line", ex);
        }
    }
    
    private void checkDataFile() {
        
    }

    private Duration parseDuration(String durationString) {
        try {
            LocalTime localTime = LocalTime.parse( durationString);
            Duration duration = Duration.ofHours( localTime.getHour());
            duration = duration.plusMinutes( localTime.getMinute());
            duration = duration.plusSeconds( localTime.getSecond());
            return duration;
        } catch( DateTimeParseException dtfe) {
            logger.log( Level.SEVERE, "wrong duration format given, should be 'hh:mm:ss", dtfe);
        }
        return null;
    }

    private void getManifestInfo() {
        // String appManifestFileName = this.getClass().getProtectionDomain().getCodeSource().getLocation().toString() + "/" + JarFile.MANIFEST_NAME;
        // Logger.getLogger(Configuration.class.getName()).log(Level.INFO, "appName:" + appManifestFileName);
        Enumeration resourceEnumeration;
        try {
            // Logger.getLogger(Configuration.class.getName()).log(Level.INFO, "JarFile.MANIFEST_NAME: " + JarFile.MANIFEST_NAME);
            resourceEnumeration = Thread.currentThread().getContextClassLoader().getResources(JarFile.MANIFEST_NAME);
            while (resourceEnumeration.hasMoreElements()) {
                try {
                    URL url = (URL) resourceEnumeration.nextElement();
                    InputStream inputStream = url.openStream();
                    if (inputStream != null) {
                        Manifest manifest = new Manifest(inputStream);
                        Attributes mainAttribs = manifest.getMainAttributes();
                        String title = mainAttribs.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
                        if (title != null) {
                            // Logger.getLogger(Configuration.class.getName()).log(Level.INFO, "Application-name: " + title);
                            appName = title;
                        } else {
                            // Logger.getLogger(Configuration.class.getName()).log(Level.WARNING, Attributes.Name.IMPLEMENTATION_TITLE + " not found");
                        }
                        String versionString = mainAttribs.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                        if (versionString != null) {
                            // Logger.getLogger(Configuration.class.getName()).log(Level.INFO, "Version: " + versionString);
                            version = versionString;
                        } else {
                            // Logger.getLogger(Configuration.class.getName()).log(Level.WARNING, Attributes.Name.IMPLEMENTATION_VERSION + " not found");
                        }
                    }
                } catch (IOException ex) {
                    // Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, "Manifest not found", ex);
                }
            }
        } catch (IOException iex) {
            // Logger.getLogger(Configuration.class.getName()).log(Level.SEVERE, "Get resources failed", iex);
        }
    }

    /**
     * @return the userPassword
     */
    public String getUserPassword() {
        return userPassword;
    }

    /**
     * @param userPassword the userPassword to set
     */
    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    /**
     * @return the adminPassword
     */
    public String getAdminPassword() {
        return adminPassword;
    }

    /**
     * @param adminPassword the adminPassword to set
     */
    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    /**
     * @return the autoStart
     */
    public boolean isAutoStart() {
        return autoStart;
    }

    /**
     * @return the aDataFile
     */
    public File getDataFile() {
        return dataFile;
    }

    /**
     * @return the aConfigFile
     */
    public File getConfigFile() {
        return configFile;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return the appName
     */
    public String getAppName() {
        return appName;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @return the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @return the url
     */
    public String getUri() {
        return uri;
    }

    /**
     * @return the serviceName
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * @return the recordingDuration
     */
    public Duration getRecordingDuration() {
        return recordingDuration;
    }

    /**
     * @return the samplingInterval
     */
    public Double getSamplingInterval() {
        return samplingInterval;
    }

    /**
     * @return the publishingInterval
     */
    public Double getPublishingInterval() {
        return publishingInterval;
    }

    /**
     * @return the captureInformationModel
     */
    public boolean isCaptureInformationModel() {
        return captureInformationModel;
    }

    /**
     * @return the mode
     */
    public OperationMode getMode() {
        return mode;
    }

    /**
     * @return the securityFolderName
     */
    public String getSecurityFolderName() {
        return securityFolderName;
    }

    /**
     * @return the startNode
     */
    public String getStartNode() {
        return startNode;
    }

    /**
     * @return the configFileName
     */
    public String getConfigFileName() {
        return configFileName;
    }

    /**
     * @return the plainUser
     */
    public String getPlainUser() {
        return plainUser;
    }

    /**
     * @return the adminUser
     */
    public String getAdminUser() {
        return adminUser;
    }
}