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
package name.buurmeijermile.opcuaservices.controllableplayer.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
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
public class PlayerConfiguration {

    private static final String USERPASSWORD = "8h5%32@!~";
    private static final String ADMINPASSWORD = "6g8&fs*()";
    private static final int PORT = 12000;

    private static final String DATAFILEKEYWORD = "datafile";
    private static final String CONFIGFILEKEYWORD = "configfile";
    private static final String SIMULATIONKEYWORD = "simulation";
    private static final String AUTOSTARTKEYWORD = "autostart";
    private static final String PORTKEYWORD = "port";
    private static final String NAMESPACEKEYWORD = "namespace";
    private static final String URIKEYWORD = "uri";
    private static final String SERVICENAMEKEYWORD = "servicename";

    private static PlayerConfiguration THECONFIGURATION;

    private String userPassword;
    private String adminPassword;
    private boolean simulation;
    private boolean autoStart;
    private File dataFile;
    private File configFile;
    private int port;
    private String version = "0.0.0 - not run from jar file"; // default version numer logged at startup
    private String appName = "noname - not run from jar file"; // default app name logged at startup
    private String namespace = "urn:SmileSoft:OPC_UA_Player"; // default namespace for the data that the player serves
    private String uri = "OPCUA-Player"; // default uri that the player use to serve its data, <hostanme>:<port>/<uri>
    private String serviceName = "Smiles-OPCUA-Player"; // default service name that is providde when discovering the service the player provides

    private PlayerConfiguration() {
        this.port = PORT;
        this.userPassword = USERPASSWORD;
        this.adminPassword = ADMINPASSWORD;
    }

    public static PlayerConfiguration getConfiguration() {
        if (THECONFIGURATION == null) {
            THECONFIGURATION = new PlayerConfiguration();
        }
        return THECONFIGURATION;
    }

    public void processCommandLine(String[] args) {
        // print version info
        this.getManifestInfo();
        // parse arguments
        try {
            Options options = new Options();
            // add data file command line option
            Option dataFileOption = Option.builder(DATAFILEKEYWORD)
                    .argName("file")
                    .required(false)
                    .desc("use given file for reading data")
                    .hasArg()
                    .build();
            options.addOption(dataFileOption);
            // add config file command line option
            Option configFileOption = Option.builder(CONFIGFILEKEYWORD)
                    .argName("file")
                    .required(false)
                    .desc("use given file for reading configuration")
                    .hasArg()
                    .build();
            options.addOption(configFileOption);
            // add port command line option
            Option portOption = Option.builder(PORTKEYWORD)
                    .required(false)
                    .hasArg(true)
                    .desc("use to set the port on which the OPC UA Player provides its services")
                    .build();
            options.addOption(portOption);
            // add namespace command line option
            Option namespaceOption = Option.builder(NAMESPACEKEYWORD)
                    .required(false)
                    .hasArg(true)
                    .desc("use to set the namespace for the data the OPC UA Player serves")
                    .build();
            options.addOption(namespaceOption);
            // add url command line option
            Option uriOption = Option.builder(URIKEYWORD)
                    .required(false)
                    .hasArg(true)
                    .desc("use to set the uri part after the / on which the OPC UA Player provides its services")
                    .build();
            options.addOption(uriOption);
            // add servicename command line option
            Option serviceNameOption = Option.builder(SERVICENAMEKEYWORD)
                    .required(false)
                    .hasArg(true)
                    .desc("use to set the service name for the services OPC UA Player provides")
                    .build();
            options.addOption(serviceNameOption);
            // add simulation command line option
            Option simulationOption = Option.builder(SIMULATIONKEYWORD)
                    .required(false)
                    .desc("use to simulate values from some fixed functions (sinus, cosine")
                    .hasArg(false)
                    .build();
            options.addOption(simulationOption);
            // add autostart command line option
            Option autostartOption = Option.builder(AUTOSTARTKEYWORD)
                    .required(false)
                    .desc("use to start immediatly serving values without the use of the remote control functionality")
                    .hasArg(false)
                    .build();
            options.addOption(autostartOption);
            // parse command line
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = null;
            cmd = parser.parse(options, args);
            // check if any arguments are given
            if (args.length == 0) {
                // print out instructions
                System.out.println("Welcome to the OPC UA Player that serves timeseries data from a file in real time");
                System.out.println("Argument options are listed below:");
                for (Option anOption : options.getOptions()) {
                    System.out.println( "- " + anOption.getOpt() + " => " + anOption.getDescription());
                }
                // and exit the VM
                System.exit(0);
            } else {
                // list all arguments
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "The command line arguments are listed below");
                cmd.iterator().forEachRemaining( a -> {
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "-" + a.getOpt() + " " + a.getValue());
                });
            }
            // check if autostart command is present
            if (cmd.hasOption(AUTOSTARTKEYWORD)) {
                autoStart = true;
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Autostart=" + autoStart);
            }
            // check if port number was assigned
            if (cmd.hasOption(PORTKEYWORD)) {
                String portString = cmd.getOptionValue(PORTKEYWORD);
                port = Integer.parseInt(portString, 10);
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Port=" + port);
            }
            // check if namespace was assigned
            if (cmd.hasOption(NAMESPACEKEYWORD)) {
                this.namespace = cmd.getOptionValue(NAMESPACEKEYWORD);
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Namespace=" + this.namespace);
            }
            // check if uri was assigned
            if (cmd.hasOption(URIKEYWORD)) {
                this.uri = cmd.getOptionValue(URIKEYWORD);
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Uri=" + this.uri);
            }
            // check if serviceName was assigned
            if (cmd.hasOption(SERVICENAMEKEYWORD)) {
                this.serviceName = cmd.getOptionValue(SERVICENAMEKEYWORD);
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Servicename=" + this.serviceName);
            }
            // check if simulation keyword is present
            if (cmd.hasOption(SIMULATIONKEYWORD)) {
                simulation = true;
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Simulation=" + true);
            } else {
                if (cmd.hasOption(DATAFILEKEYWORD)) {
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Datafile=" + cmd.getOptionValue(DATAFILEKEYWORD));
                    // create file reference to data file
                    dataFile = new File(cmd.getOptionValue(DATAFILEKEYWORD));
                    if (getDataFile().exists() && getDataFile().canRead()) {
                        if (cmd.hasOption(CONFIGFILEKEYWORD)) {
                            Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Configfile=" + cmd.getOptionValue(CONFIGFILEKEYWORD));
                            // create file reference to data file
                            configFile = new File(cmd.getOptionValue(CONFIGFILEKEYWORD));
                            if (!getConfigFile().exists() || !getConfigFile().canRead()) {
                                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "Config file %s can't be read or does not exist", configFile.getName());
                            }
                        } else {
                            // flag missing -configfile command line option
                            Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "-configfile argument is missing");
                        }
                    } else {
                        Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "Data file %s can't be read or does not exist", getDataFile());
                    }
                } else {
                    // flag missing -datafile command line option
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "-datafile argument is missing");
                }
            }
        } catch (ParseException ex) {
            Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "Error parsing command line", ex);
        }
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
                } catch (Exception ex) {
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
     * @return the simulation
     */
    public boolean isSimulation() {
        return simulation;
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
}