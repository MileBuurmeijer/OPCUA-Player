/*
 * Copyright (c) 2019 the Eclipse Milo Authors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package name.buurmeijermile.opcuaservices.controllableplayer.client;
        
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;

public class KeystoreLoader {

    private static final Pattern IP_ADDR_PATTERN = Pattern.compile(
        "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    private static final String CLIENT_ALIAS = "client-ai";
    private static final char[] PASSWORD = "password".toCharArray();

    private final Logger logger = Logger.getLogger(KeystoreLoader.class.getName());

    private X509Certificate clientCertificate;
    private KeyPair clientKeyPair;

    public KeystoreLoader load(Path baseDir) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            
            Path serverKeyStore = baseDir.resolve("example-client.pfx");
            
            logger.log(Level.INFO, "Loading KeyStore at " + serverKeyStore);
            
            if (!Files.exists(serverKeyStore)) {
                try {
                    keyStore.load(null, PASSWORD);
                    
                    KeyPair keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
                    
                    SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                            .setCommonName("Eclipse Milo Example Client")
                            .setOrganization("digitalpetri")
                            .setOrganizationalUnit("dev")
                            .setLocalityName("Folsom")
                            .setStateName("CA")
                            .setCountryCode("US")
                            .setApplicationUri("urn:eclipse:milo:examples:client")
                            .addDnsName("localhost")
                            .addIpAddress("127.0.0.1");
                    
                    // Get as many hostnames and IP addresses as we can listed in the certificate.
                    for (String hostname : HostnameUtil.getHostnames("0.0.0.0")) {
                        if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
                            builder.addIpAddress(hostname);
                        } else {
                            builder.addDnsName(hostname);
                        }
                    }
                    
                    X509Certificate certificate = builder.build();
                    
                    keyStore.setKeyEntry(CLIENT_ALIAS, keyPair.getPrivate(), PASSWORD, new X509Certificate[]{certificate});
                    try (OutputStream out = Files.newOutputStream(serverKeyStore)) {
                        keyStore.store(out, PASSWORD);
                    }
                }   catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
                    java.util.logging.Logger.getLogger(KeystoreLoader.class.getName()).log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    Logger.getLogger(KeystoreLoader.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                try (InputStream in = Files.newInputStream(serverKeyStore)) {
                    keyStore.load(in, PASSWORD);
                } catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
                    java.util.logging.Logger.getLogger(KeystoreLoader.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            Key serverPrivateKey = keyStore.getKey(CLIENT_ALIAS, PASSWORD);
            if (serverPrivateKey instanceof PrivateKey) {
                clientCertificate = (X509Certificate) keyStore.getCertificate(CLIENT_ALIAS);
                PublicKey serverPublicKey = clientCertificate.getPublicKey();
                clientKeyPair = new KeyPair(serverPublicKey, (PrivateKey) serverPrivateKey);
            }
            
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException ex) {
            java.util.logging.Logger.getLogger(KeystoreLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
        return this;
    }

    X509Certificate getClientCertificate() {
        return clientCertificate;
    }

    KeyPair getClientKeyPair() {
        return clientKeyPair;
    }
}
