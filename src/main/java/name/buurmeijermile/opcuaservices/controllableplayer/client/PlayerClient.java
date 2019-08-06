/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.client;

/**
 *
 * @author Milé Buurmeijer <mbuurmei at netscape.net>
 */
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.common.collect.ImmutableList;
import java.util.Scanner;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.X509IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerClient extends SecureClientExample {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private IdentityProvider identityProvider;
    private X509Certificate cert;
    private KeyPair keyPair;

    public static void main(String[] args) {
        PlayerClient example = new PlayerClient();
        new SecureClientStandaloneRunner(example).run();
    }

    private PlayerClient() {
        /* Get keystore password */
        System.out.println("Enter your keystore password: ");
        Scanner scanner = new Scanner(System.in);
        String keystorePasswordArray = scanner.nextLine();
        System.out.println( keystorePasswordArray);
        System.out.println( "Enter your key password: ");
        String keyPasswordArray = scanner.nextLine();

        try {
            String keystorepath = "opcua.keystore";
            logger.info("Trying to load keyfile from " + keystorepath);
            File file = new File(keystorepath);
            System.out.println("the full file name is: " + file.getAbsolutePath());
            FileInputStream is = new FileInputStream(file);
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(is, keystorePasswordArray.toCharArray());
            is.close();

            /*Get key from keystore */
            PrivateKey key = (PrivateKey) keystore.getKey("opcua", keyPasswordArray.toCharArray());

            /* Get certificate of public key */
            cert = (X509Certificate) keystore.getCertificate("opcua");

            keyPair = new KeyPair(cert.getPublicKey(), key);

            identityProvider = new X509IdentityProvider(cert, key);
        } catch (FileNotFoundException f) {
            logger.error("Keystore file not found.");
            System.exit(1);
        } catch (Exception e) {
            logger.error("Loading from keystore failed.");
            System.exit(1);
        }
    }

    public IdentityProvider getIdentityProvider() {
        return identityProvider;
    }

    @Override
    public X509Certificate getClientCertificate() {
        return cert;
    }

    @Override
    public KeyPair getKeyPair() {
        return keyPair;
    }

    public void run(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception {
        // synchronous connect
        client.connect().get();

        // synchronous read request via VariableNode
        VariableNode node = client.getAddressSpace().createVariableNode(Identifiers.Server_ServerStatus_StartTime);
        DataValue value = node.readValue().get();

        logger.info("StartTime={}", value.getValue().getValue());

        // asynchronous read request
        readServerStateAndTime(client).thenAccept(values -> {
            DataValue v0 = values.get(0);
            DataValue v1 = values.get(1);

            logger.info("Succeeded in making a connection on a secure channel.");
            logger.info("State={}", ServerState.from((Integer) v0.getValue().getValue()));
            logger.info("CurrentTime={}", v1.getValue().getValue());

            future.complete(client);
        });
    }

    private CompletableFuture<List<DataValue>> readServerStateAndTime(OpcUaClient client) {
        List<NodeId> nodeIds = ImmutableList.of(
            Identifiers.Server_ServerStatus_State,
            Identifiers.Server_ServerStatus_CurrentTime);

        return client.readValues(0.0, TimestampsToReturn.Both, nodeIds);
    }
    
}
