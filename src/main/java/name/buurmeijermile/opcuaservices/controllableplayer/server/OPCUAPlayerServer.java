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
package name.buurmeijermile.opcuaservices.controllableplayer.server;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.security.Security;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
//import org.eclipse.milo.opcua.stack.core.util.CryptoRestrictions;

import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataFilePlayerController;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataControllerInterface;
import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataSimulator;
import name.buurmeijermile.opcuaservices.utils.Waiter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.X509IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedHttpsCertificateBuilder;
import org.slf4j.LoggerFactory;

public class OPCUAPlayerServer {
    
    public  static final String APPLICATIONURI = "urn:SmileSoft:OPCUA:playerserver";
    private static final String VERSION = "0.5.5";
    private static final String PRODUCTURI = "urn:SmileSoft:OPCUA:player-server";
    private static final String SERVERNAME = "OPCUA-Player";
    private static final String DATAFILEKEYWORD = "datafile";
    private static final String CONFIGFILEKEYWORD = "configfile";
    private static final String SIMULATIONKEYWORD = "simulation";
    private static final String AUTOSTARTKEYWORD = "autostart";
    public static final String USERNAME = "user";
    public static final String ADMINNAME = "admin";
    private static final String USERPASSWORD = "8h5%32@!~";
    private static final String ADMINPASSWORD = "6g8&fs*()";
    private static int TCP_BIND_PORT = 12000;
    private static int HTTPS_BIND_PORT = 12080;
    
    private OpcUaServer server;
    private DataFilePlayerController dataBackendController;
    private File baseCertificateDir = new File("/home/mbuurmei/OPC UA/OPC UA Player/certificate");

    static { // needed for adding the bouncy castle security provider
        // CryptoRestrictions.remove();
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Main method that parses the command line with file arguments, 
     * creates OPC UA Player server and starts this server.
     * @param args command line parameters: "-datafile 'file'" and "-configfile 'file'" or -simulation
     */
    public static void main(String[] args) {
        try {
            File aDataFile = null;
            File aConfigFile = null;
            OPCUAPlayerServer playerServer = null;
            boolean autoStart = false;
            DataControllerInterface theDataControllerInterface = null;
            
            // parse arguments
            try {
                Options options = new Options();
                // add data file command line option
                Option dataFileOption = Option.builder( DATAFILEKEYWORD)
                        .argName( "file")
                        .required( false)
                        .desc( "use given file for reading data")
                        .hasArg()
                        .build();
                options.addOption( dataFileOption);
                // add config file command line option
                Option configFileOption = Option.builder( CONFIGFILEKEYWORD)
                        .argName("file")
                        .required( false)
                        .desc( "use given file for reading configuration")
                        .hasArg()
                        .build();
                options.addOption( configFileOption);
                // add simulation command line option
                Option simulationOption = Option.builder( SIMULATIONKEYWORD)
                        .required( false)
                        .hasArg( false)
                        .build();
                options.addOption( simulationOption);
                // add autostart command line option
                Option autostartOption = Option.builder( AUTOSTARTKEYWORD)
                        .required( false)
                        .hasArg( false)
                        .build();
                options.addOption( autostartOption);
                // parse command line
                CommandLineParser parser = new DefaultParser();
                CommandLine cmd = null;
                // check if autostart command is present
                cmd = parser.parse( options, args);
                if (cmd.hasOption( AUTOSTARTKEYWORD)) {
                    autoStart = true;
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Autostart=" + autoStart);
                }
                // check if simulation keyword is present
                if (cmd.hasOption( SIMULATIONKEYWORD)) {
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Simulation=" + true);
                    theDataControllerInterface = new DataSimulator();
                    playerServer = new OPCUAPlayerServer( theDataControllerInterface);
                } else {
                    if (cmd.hasOption( DATAFILEKEYWORD)) {
                        Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Datafile=" + cmd.getOptionValue(DATAFILEKEYWORD));
                        // create file reference to data file
                        aDataFile = new File( cmd.getOptionValue( DATAFILEKEYWORD));
                        if ( aDataFile.exists() && aDataFile.canRead()) {
                            if (cmd.hasOption( CONFIGFILEKEYWORD)) {
                                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Configfile=" + cmd.getOptionValue(CONFIGFILEKEYWORD));
                                // create file reference to data file
                                aConfigFile = new File( cmd.getOptionValue( CONFIGFILEKEYWORD));
                                if (aConfigFile.exists() && aConfigFile.canRead()) {
                                    theDataControllerInterface = new DataFilePlayerController( aDataFile, aConfigFile);
                                    playerServer = new OPCUAPlayerServer( theDataControllerInterface);
                                } else {
                                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "Config file %s can't be read or does not exist", aConfigFile);
                                }
                            } else {
                                // flag missing -configfile command line option
                                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "-configfile argument is missing");
                            }
                        } else {
                            Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "Data file %s can't be read or does not exist", aDataFile); 
                        }
                    } else {
                        // flag missing -datafile command line option
                        Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "-datafile argument is missing");
                    }
                }
            } catch (ParseException ex) {
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "Error parsing command line", ex);
            }
            // check if a player is instantiated
            if (playerServer != null) {
                // start the OPC UA player server
                playerServer.startup().get(); 
                // let it settle for a while and if auto start apply automaticcaly the start playing command
                if (autoStart) {
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Autostart: wait before starting");
                    // wait for 10 seconds
                    Waiter.wait(Duration.ofSeconds( 10));
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Autostart: giving remote play command");
                    // give the data controller the player start command (=1)
                    theDataControllerInterface.doRemotePlayerControl( 1);
                } else {
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "No autostart");
                }
                // and add runtime shutdown hook
                final CompletableFuture<Void> future = new CompletableFuture<>();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> future.complete(null)));
                future.get();
            } else {
                 Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "OPC UA PlayerServer not initialized");
            }
        } catch ( Exception ex) {
            Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "Opc Ua Server exxception thrown", ex);
        }
    }
    
    /**
     * Constructor for the OPC UA server.
     * @throws Exception 
     */
    public OPCUAPlayerServer( DataControllerInterface aDataController) throws Exception {
        this.OPCUAPlayerServerInit();
        // create the namespace for this OPCUA player server
        PlayerNamespace playerNamespace = new PlayerNamespace( server, aDataController);
        playerNamespace.startup();
        // activate the data controller
        aDataController.startUp();
    }
    
    private void OPCUAPlayerServerInit() throws Exception {
        File securityTempDir = new File(System.getProperty("java.io.tmpdir"), "security");
        if (!securityTempDir.exists() && !securityTempDir.mkdirs()) {
            throw new Exception("unable to create security temp dir: " + securityTempDir);
        }
        // setup the logger
        LoggerFactory.getLogger(getClass()).info("security temp dir: {}", securityTempDir.getAbsolutePath());
        // create keystore loader
        KeyStoreLoader loader = new KeyStoreLoader().load( securityTempDir);
        // setup default certificate manager
        DefaultCertificateManager certificateManager = new DefaultCertificateManager(
            loader.getServerKeyPair(),
            loader.getServerCertificate()
        );
        // create file link to temp directory for security temporary files
        // Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "security temp dir: " + securityTempDir.getAbsolutePath());

        // create default cetificate validator
        File pkiDir = securityTempDir.toPath().resolve("pki").toFile();
        DefaultTrustListManager trustListManager = new DefaultTrustListManager(pkiDir);
        LoggerFactory.getLogger(getClass()).info("pki dir: {}", pkiDir.getAbsolutePath());
        DefaultCertificateValidator certificateValidator = new DefaultCertificateValidator(trustListManager);
        KeyPair httpsKeyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        SelfSignedHttpsCertificateBuilder httpsCertificateBuilder = new SelfSignedHttpsCertificateBuilder(httpsKeyPair);
        httpsCertificateBuilder.setCommonName(HostnameUtil.getHostname());
        HostnameUtil.getHostnames("0.0.0.0").forEach(httpsCertificateBuilder::addDnsName);
        X509Certificate httpsCertificate = httpsCertificateBuilder.build();
   
        // create identity validator
        UsernameIdentityValidator identityValidator = new UsernameIdentityValidator(
            true,
            authChallenge -> {
                String username = authChallenge.getUsername();
                String password = authChallenge.getPassword();

                boolean userOk = USERNAME.equals(username) && USERPASSWORD.equals(password);
                boolean adminOk = ADMINNAME.equals(username) && ADMINPASSWORD.equals(password);
            
                return userOk || adminOk;
            }
        );
        // create x509 certificate identity validator
        X509IdentityValidator x509IdentityValidator = new X509IdentityValidator(c -> true);

        // If you need to use multiple certificates you'll have to be smarter than this.
        X509Certificate certificate = certificateManager.getCertificates()
            .stream()
            .findFirst()
            .orElseThrow(() -> new UaRuntimeException(StatusCodes.Bad_ConfigurationError, "no certificate found"));

        // The configured application URI must match the one in the certificate(s)
        String applicationUri = CertificateUtil
            .getSanUri(certificate)
            .orElseThrow(() -> new UaRuntimeException(
                StatusCodes.Bad_ConfigurationError,
                "certificate is missing the application URI"));

        Set<EndpointConfiguration> endpointConfigurations = createEndpointConfigurations(certificate);
        
        // The configured application URI must match the one in the certificate(s)
//        String applicationUri = certificateManager.getCertificates().stream()
//            .findFirst()
//            .map(certificate ->
//                CertificateUtil.getSubjectAltNameField(certificate, CertificateUtil.SUBJECT_ALT_NAME_URI)
//                    .map(Object::toString)
//                    .orElseThrow(() -> new RuntimeException("certificate is missing the application URI")))
//            .orElse("urn:eclipse:milo:examples:server:" + UUID.randomUUID());

        // prepare the bind address
//        List<String> bindAddresses = Lists.newArrayList();
//        bindAddresses.add("0.0.0.0");
//        // and end points
//        List<String> endpointAddresses = Lists.newArrayList();
//        endpointAddresses.add(HostnameUtil.getHostname());
//        endpointAddresses.addAll(HostnameUtil.getHostnames("0.0.0.0"));
        // create OPC UA server configuration
        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationUri( applicationUri)
            .setApplicationName(LocalizedText.english("Smiles OPC UA player (powered by Eclipse Milo, an open source OPC-UA SDK)"))
//            .setBindAddresses( bindAddresses)
            .setEndpoints(endpointConfigurations)
//            .setEndpointAddresses(endpointAddresses)
//            .setBindPort( PORT)
            .setBuildInfo(
                new BuildInfo(
                    PRODUCTURI,
                    "SmileSoft",
                    "OPC UA Player",
                    OpcUaServer.SDK_VERSION,
                    VERSION, 
                    DateTime.now()
                )
            )
            .setCertificateManager(certificateManager)
            .setTrustListManager( trustListManager)
            .setCertificateValidator( certificateValidator)
            .setHttpsKeyPair( httpsKeyPair)
            .setHttpsCertificate( httpsCertificate)
            .setIdentityValidator(new CompositeValidator(identityValidator, x509IdentityValidator))
            .setProductUri(PRODUCTURI)
            .build();
        // create OPC UA server based on this configuraton
        this.server = new OpcUaServer(serverConfig);
    }
    
    private Set<EndpointConfiguration> createEndpointConfigurations(X509Certificate certificate) {
        Set<EndpointConfiguration> endpointConfigurations = new LinkedHashSet<>();

        List<String> bindAddresses = newArrayList();
        bindAddresses.add("0.0.0.0");

        Set<String> hostnames = new LinkedHashSet<>();
        hostnames.add(HostnameUtil.getHostname());
        hostnames.addAll(HostnameUtil.getHostnames("0.0.0.0"));

        for (String bindAddress : bindAddresses) {
            for (String hostname : hostnames) {
                EndpointConfiguration.Builder builder = EndpointConfiguration.newBuilder()
                    .setBindAddress(bindAddress)
                    .setHostname(hostname)
                    .setPath( SERVERNAME)
                    .setCertificate(certificate)
                    .addTokenPolicies(
                        OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS,
                        OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME,
                        OpcUaServerConfig.USER_TOKEN_POLICY_X509);


                EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add( buildTcpEndpoint( noSecurityBuilder));
                endpointConfigurations.add( buildHttpsEndpoint( noSecurityBuilder));

                // TCP Basic256Sha256 / SignAndEncrypt
                endpointConfigurations.add(buildTcpEndpoint(
                    builder.copy()
                        .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                        .setSecurityMode(MessageSecurityMode.SignAndEncrypt))
                );

                // HTTPS Basic256Sha256 / Sign (SignAndEncrypt not allowed for HTTPS)
                endpointConfigurations.add(buildHttpsEndpoint(
                    builder.copy()
                        .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                        .setSecurityMode(MessageSecurityMode.Sign))
                );

                /*
                 * It's good practice to provide a discovery-specific endpoint with no security.
                 * It's required practice if all regular endpoints have security configured.
                 *
                 * Usage of the  "/discovery" suffix is defined by OPC UA Part 6:
                 *
                 * Each OPC UA Server Application implements the Discovery Service Set. If the OPC UA Server requires a
                 * different address for this Endpoint it shall create the address by appending the path "/discovery" to
                 * its base address.
                 */

                EndpointConfiguration.Builder discoveryBuilder = builder.copy()
                    .setPath("/milo/discovery")
                    .setSecurityPolicy(SecurityPolicy.None)
                    .setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add( buildTcpEndpoint(discoveryBuilder));
                endpointConfigurations.add( buildHttpsEndpoint(discoveryBuilder));
            }
        }

        return endpointConfigurations;
    }

    private static EndpointConfiguration buildTcpEndpoint(EndpointConfiguration.Builder base) {
        return base.copy()
            .setTransportProfile( TransportProfile.TCP_UASC_UABINARY)
            .setBindPort( TCP_BIND_PORT)
            .build();
    }

    private static EndpointConfiguration buildHttpsEndpoint(EndpointConfiguration.Builder base) {
        return base.copy()
            .setTransportProfile( TransportProfile.HTTPS_UABINARY)
            .setBindPort( HTTPS_BIND_PORT)
            .build();
    }

    public OpcUaServer getServer() {
        return this.server;
    }

    public CompletableFuture<OpcUaServer> startup() {
        return this.server.startup();
    }

    public CompletableFuture<OpcUaServer> shutdown() {
        return this.server.shutdown();
    }
}
