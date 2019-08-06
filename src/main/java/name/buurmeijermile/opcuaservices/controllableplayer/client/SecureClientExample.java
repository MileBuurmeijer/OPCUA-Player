/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package name.buurmeijermile.opcuaservices.controllableplayer.client;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SecureClientExample {

    private Logger logger = LoggerFactory.getLogger(SecureClientExample.class);

    protected KeyPair keyPair;
    protected X509Certificate certificate;

    String getDiscoveryEndpointUrl() {
        return "opc.tcp://127.0.0.1:12000/milo/discovery";
    }

    SecurityPolicy getSecurityPolicy() {
        return SecurityPolicy.None;
    }

    MessageSecurityMode getMessageSecurityMode() {
        return MessageSecurityMode.None;
    }

    IdentityProvider getIdentityProvider() {
        return new AnonymousProvider();
    }

    abstract void run(OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception;

    X509Certificate getClientCertificate() {
        if (certificate == null) {
            generateSelfSignedCertificate();
        }
        return certificate;
    }

    KeyPair getKeyPair() {
        if (keyPair == null) {
            generateSelfSignedCertificate();
        }
        return keyPair;
    }

    protected void generateSelfSignedCertificate() {
        //Generate self-signed certificate
        try {
            keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
        } catch (NoSuchAlgorithmException n) {
            logger.error("Could not generate RSA Key Pair.", n);
            System.exit(1);
        }

        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName("Eclipse Milo Example Client")
                .setOrganization("digitalpetri")
                .setOrganizationalUnit("dev")
                .setLocalityName("Folsom")
                .setStateName("CA")
                .setCountryCode("US")
                .setApplicationUri("urn:eclipse:milo:examples:client");

        try {
            certificate = builder.build();
        } catch (Exception e) {
            logger.error("Could not build certificate.", e);
            System.exit(1);
        }
    }
}
