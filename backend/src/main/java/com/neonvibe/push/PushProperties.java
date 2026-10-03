package com.neonvibe.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to the {@code neonvibe.push.*} configuration keys. When the key pair is
 * empty, native notifications are disabled and the API reports
 * {@code configured: false}.
 */
@ConfigurationProperties(prefix = "neonvibe.push")
public class PushProperties {

    /** VAPID public key (base64url). */
    private String publicKey = "";

    /** VAPID private key (base64url). */
    private String privateKey = "";

    /** Contact subject for VAPID (a mailto: or https: URL). */
    private String subject = "mailto:admin@neonvibe.local";

    public boolean isConfigured() {
        return !publicKey.isBlank() && !privateKey.isBlank();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
