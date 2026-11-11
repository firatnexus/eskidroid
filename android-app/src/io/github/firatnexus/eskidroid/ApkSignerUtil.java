package io.github.firatnexus.eskidroid;

import com.android.apksig.ApkSigner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collections;

/**
 * Imzalama anahtari repoya/APK'ya gomulmez: ilk kullanimda cihazda uretilir
 * (self-signed), uygulamanin ozel klasorunde saklanir. Boylece her kurulumun
 * anahtari kendine ozgudur ve APK icinden cekilebilecek bir anahtar yoktur.
 */
public final class ApkSignerUtil {

    /** Anahtarlarin saklanacagi klasor; MainActivity context'i verir. */
    private static File keyDir;

    public static void setKeyDir(File dir) {
        keyDir = dir;
    }

    public static synchronized void sign(File in, File out) throws Exception {
        PrivateKey key;
        X509Certificate cert;
        File kd = keyDir != null ? keyDir : new File(System.getProperty("java.io.tmpdir", "/tmp"), "eskidroid-keys");

        File keyFile = new File(kd, "key.der");
        File certFile = new File(kd, "cert.der");
        if (keyFile.exists() && certFile.exists()) {
            key = loadKey(keyFile);
            cert = loadCert(certFile);
        } else {
            kd.mkdirs();
            KeyPair kp = X509SelfSigned.newKeyPair();
            cert = X509SelfSigned.selfSign(kp);
            try (OutputStream os = new FileOutputStream(keyFile)) {
                os.write(kp.getPrivate().getEncoded());
            }
            try (OutputStream os = new FileOutputStream(certFile)) {
                os.write(cert.getEncoded());
            }
            key = kp.getPrivate();
        }

        new ApkSigner.Builder(Collections.singletonList(
                        new ApkSigner.SignerConfig.Builder("eskidroid", key,
                                Collections.singletonList(cert)).build()))
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setMinSdkVersion(21)
                .setInputApk(in)
                .setOutputApk(out)
                .build()
                .sign();
    }

    private static PrivateKey loadKey(File f) throws Exception {
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(readAll(f)));
    }

    private static X509Certificate loadCert(File f) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new FileInputStream(f));
    }

    private static byte[] readAll(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream is = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) > 0) bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }
}
