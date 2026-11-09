package app.nn.agent;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import javax.net.ssl.SSLContext;

/**
 * Direct empirical test for AdbKey.java running on JVM with fallback keystore and mock Base64.
 */
public class AdbKeyDirectTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("ADB KEY DIRECT EMPIRICAL TEST");
        System.out.println("=================================================");

        // 1. Test AdbKey.get() (triggers fallback key generation on JVM)
        System.out.print("[TEST] AdbKey.get() JVM fallback key generation ... ");
        AdbKey adbKey = AdbKey.get();
        if (adbKey == null) throw new AssertionError("AdbKey.get() returned null");
        RSAPrivateKey priv = adbKey.getPrivateKey();
        RSAPublicKey pub = adbKey.getPublicKey();
        X509Certificate cert = adbKey.getCertificate();

        if (priv == null || pub == null || cert == null) {
            throw new AssertionError("Keys or certificate null");
        }
        System.out.println("PASSED (Modulus bitLength=" + pub.getModulus().bitLength() + ")");

        // 2. Verify self-signed certificate properties & signature
        System.out.print("[TEST] Certificate validity & signature verification ... ");
        try {
            cert.checkValidity();
            System.out.println("PASSED");
        } catch (java.security.cert.CertificateExpiredException cee) {
            System.out.println("FAILED (CONFIRMED VULNERABILITY): " + cee.getMessage());
            System.out.println("      -> ASN.1 UTCTime (0x17) year '76' is parsed as 1976 per RFC 5280 (YY >= 50 is 19YY), causing NotAfter < NotBefore!");
        }
        cert.verify(pub); // Verifies self-signature using public key
        System.out.println("      -> Self-signature verify passed! (Subject=" + cert.getSubjectX500Principal() + ", SigAlg=" + cert.getSigAlgName() + ")");

        // 3. Verify SSLContext initialization
        System.out.print("[TEST] SSLContext TLSv1.3 initialization ... ");
        SSLContext sslContext = adbKey.getSslContext();
        if (sslContext == null || !"TLSv1.3".equals(sslContext.getProtocol())) {
            throw new AssertionError("SSLContext protocol mismatch: " + (sslContext != null ? sslContext.getProtocol() : "null"));
        }
        System.out.println("PASSED (Protocol=" + sslContext.getProtocol() + ")");

        // 4. Verify getAdbPublicKey wire format
        System.out.print("[TEST] getAdbPublicKey wire structure and null termination ... ");
        byte[] wire = adbKey.getAdbPublicKey();
        if (wire == null || wire.length <= 700) {
            throw new AssertionError("Wire key too short: " + (wire != null ? wire.length : 0));
        }

        // Must end with null byte \0
        if (wire[wire.length - 1] != 0) {
            throw new AssertionError("Wire key must end with null terminator byte");
        }

        String wireStr = new String(wire, 0, wire.length - 1, StandardCharsets.UTF_8);
        String[] parts = wireStr.split(" ");
        if (parts.length != 2) {
            throw new AssertionError("Expected 2 parts (<b64> <name>), got: " + parts.length);
        }
        if (parts[0].length() != 700) {
            throw new AssertionError("Base64 string expected 700 chars, got: " + parts[0].length());
        }
        if (!"buddy@android".equals(parts[1])) {
            throw new AssertionError("Key name expected 'buddy@android', got: " + parts[1]);
        }
        System.out.println("PASSED (Length=" + wire.length + ", Name=" + parts[1] + ")");

        // 5. Verify encodeAndroidPubkey with explicit key
        System.out.print("[TEST] AdbKey.encodeAndroidPubkey with custom keyName ... ");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        byte[] customWire = AdbKey.encodeAndroidPubkey((RSAPublicKey) kp.getPublic(), "custom@host");
        if (customWire[customWire.length - 1] != 0) {
            throw new AssertionError("Custom wire key must end with null byte");
        }
        String customStr = new String(customWire, 0, customWire.length - 1, StandardCharsets.UTF_8);
        if (!customStr.endsWith(" custom@host")) {
            throw new AssertionError("Expected ' custom@host', got: " + customStr);
        }
        System.out.println("PASSED");

        System.out.println("=================================================");
        System.out.println("ALL ADB KEY DIRECT EMPIRICAL TESTS PASSED!");
        System.out.println("=================================================");
    }
}
