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
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.security.Security;
import java.util.List;
import java.util.UUID;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.application.DefaultCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.util.CryptoRestrictions;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_ANONYMOUS;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_USERNAME;

import name.buurmeijermile.opcuaservices.controllableplayer.measurements.DataBackendController;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.USER_TOKEN_POLICY_X509;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.X509IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.application.DirectoryCertificateValidator;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.slf4j.LoggerFactory;

public class OPCUAPlayerServer {
    
    private static final int PORT = 12000;
    public  static final String APPLICATIONURI = "urn:SmileSoft:OPCUA:playerserver:" + UUID.randomUUID();
    private static final String VERSION = "0.5.1";
    private static final String PRODUCTURI = "urn:SmileSoft:OPCUA:player-server";
    private static final String SERVERNAME = "OPCUA-Player";
    private static final String DATAFILEKEYWORD = "datafile";
    private static final String CONFIGFILEKEYWORD = "configfile";
    private static final String USERNAME = "user";
    private static final String ADMINNAME = "admin";
    private static final String USERPASSWORD = "8h5%32@!~";
    private static final String ADMINPASSWORD = "6g8&fs*()";
    
    private final OpcUaServer server;
    private final DataBackendController dataBackendController;
    private File dataFile; // = new File("/home/mbuurmei/OPC UA/OPC UA Player/PMPDemoData_01.csv");
    private File configFile; // = new File("/home/mbuurmei/OPC UA/OPC UA Player/WisselSensoren.csv");
    private File baseCertificateDir = new File("/home/mbuurmei/OPC UA/OPC UA Player/certificate");

    static {
        CryptoRestrictions.remove();

        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Main method that parses the command line with file arguments, 
     * creates OPC UA Player server and starts this server.
     * @param args command line parameters: "-datafile 'file'" and "-configfile 'file'"
     */
    public static void main(String[] args) {
        try {
            File aDataFile = null;
            File aConfigFile = null;
            
            // parse arguments
            try {
                Options options = new Options();
                Option dataFileOption = Option.builder( DATAFILEKEYWORD)
                        .argName( "file")
                        .desc( "use given file for reading data")
                        .hasArg()
                        .build();
                options.addOption( dataFileOption);
                Option configFileOption = Option.builder( CONFIGFILEKEYWORD)
                        .argName("file")
                        .desc( "use given file for reading configuration")
                        .hasArg()
                        .build();
                options.addOption( configFileOption);
                CommandLineParser parser = new DefaultParser();
                CommandLine cmd = null;
                cmd = parser.parse( options, args);
                if (cmd.hasOption( DATAFILEKEYWORD)) {
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Datafile=" + cmd.getOptionValue(DATAFILEKEYWORD));
                    // create file reference to data file
                    aDataFile = new File( cmd.getOptionValue( DATAFILEKEYWORD));
                } else {
                    // flag missing datafile command line option
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "-datafile argument is missing");
                }
                if (cmd.hasOption( CONFIGFILEKEYWORD)) {
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "Configfile=" + cmd.getOptionValue(CONFIGFILEKEYWORD));
                    // create file reference to data file
                    aConfigFile = new File( cmd.getOptionValue( CONFIGFILEKEYWORD));
                } else {
                    // flag missing datafile command line option
                    Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, "-configfile argument is missing");
                }
            } catch (ParseException ex) {
                Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, null, ex);
            }
            OPCUAPlayerServer playerServer = new OPCUAPlayerServer( aDataFile, aConfigFile);

            playerServer.startup().get();

            final CompletableFuture<Void> future = new CompletableFuture<>();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> future.complete(null)));

            future.get();
        } catch ( Exception ex) {
            Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public OPCUAPlayerServer( File aDataFile, File aConfigFile) throws Exception {
        // reference to data file that is going to be played
        this.dataFile = aDataFile;
        // reference to config file with the opc ua folder and variable node name configuration
        this.configFile = aConfigFile;
        // remove crypto restriction so that certificates can be setup

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
//        Logger.getLogger(OPCUAPlayerServer.class.getName()).log(Level.INFO, "security temp dir: " + securityTempDir.getAbsolutePath());
        // create default cetificate validator
        File pkiDir = securityTempDir.toPath().resolve("pki").toFile();
        DirectoryCertificateValidator certificateValidator = new DirectoryCertificateValidator(pkiDir);
        LoggerFactory.getLogger(getClass()).info("pki dir: {}", pkiDir.getAbsolutePath());
//        DefaultCertificateValidator certificateValidator = new DefaultCertificateValidator(securityTempDir);
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
        
        // The configured application URI must match the one in the certificate(s)
        String applicationUri = certificateManager.getCertificates().stream()
            .findFirst()
            .map(certificate ->
                CertificateUtil.getSubjectAltNameField(certificate, CertificateUtil.SUBJECT_ALT_NAME_URI)
                    .map(Object::toString)
                    .orElseThrow(() -> new RuntimeException("certificate is missing the application URI")))
            .orElse("urn:eclipse:milo:examples:server:" + UUID.randomUUID());

        // prepare the bind address
        List<String> bindAddresses = Lists.newArrayList();
        bindAddresses.add("0.0.0.0");
        // and end points
        List<String> endpointAddresses = Lists.newArrayList();
        endpointAddresses.add(HostnameUtil.getHostname());
        endpointAddresses.addAll(HostnameUtil.getHostnames("0.0.0.0"));
        // create OPC UA server configuration
        OpcUaServerConfig serverConfig = OpcUaServerConfig.builder()
            .setApplicationUri( applicationUri)
            .setApplicationName(LocalizedText.english("Smiles OPC UA player (powered by Eclipse Milo, an open source OPC-UA SDK)"))
            .setBindAddresses( bindAddresses)
            .setEndpointAddresses(endpointAddresses)
            .setBindPort( PORT)
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
            .setCertificateValidator(certificateValidator)
            .setIdentityValidator(new CompositeValidator(identityValidator, x509IdentityValidator))
            .setProductUri(PRODUCTURI)
            .setServerName(SERVERNAME)
            .setSecurityPolicies(
                EnumSet.of(
                    SecurityPolicy.None,
                    SecurityPolicy.Basic128Rsa15,
                    SecurityPolicy.Basic256,
                    SecurityPolicy.Basic256Sha256))
            .setUserTokenPolicies(
                ImmutableList.of(
                    USER_TOKEN_POLICY_ANONYMOUS,
                    USER_TOKEN_POLICY_X509,
                    USER_TOKEN_POLICY_USERNAME))
            .build();
        // create OPC UA server based on this configuraton
        this.server = new OpcUaServer(serverConfig);
        
        // create a data backend controller that serves the OPC UA source data
        this.dataBackendController =  new DataBackendController( this.dataFile, this.configFile);
        
        // register the server name space for the player
        this.server.getNamespaceManager().registerAndAdd(
            PlayerNameSpace.NAMESPACE_URI,
            idx -> new PlayerNameSpace ( this.server, idx, this.dataBackendController)
        );
        // activate the thread of the data backend source
        Thread dataBackendThread = new Thread( this.dataBackendController);
        dataBackendThread.start();
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
