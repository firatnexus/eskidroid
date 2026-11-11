package io.github.firatnexus.eskidroid;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

/**
 * Repoda/APK'da imzalama anahtari tasimamak icin: anahtar ilk calistirmada
 * cihazda uretilir (self-signed X.509). Anahtar APK icinde degil,
 * uygulamanin ozel klasorunde saklanir.
 */
public final class X509SelfSigned {

    public static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    /** CN=EskiDroid, 30 yil gecerli self-signed sertifikasi uretir. */
    public static X509Certificate selfSign(KeyPair kp) throws Exception {
        byte[] tbs = tbsCertificate(kp);
        byte[] algId = derSequence(derOid("1.2.840.113549.1.1.11"), derNull());

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbs);
        byte[] sigVal = derBitString(sig.sign());

        byte[] cert = derSequence(tbs, algId, sigVal);

        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(cert));
    }

    // -------------------------------------------------- DER yardimcilari

    private static byte[] derSequence(byte[]... parts) throws Exception {
        return derTag(0x30, concat(parts));
    }

    private static byte[] derSet(byte[]... parts) throws Exception {
        return derTag(0x31, concat(parts));
    }

    private static byte[] derInteger(BigInteger v) throws Exception {
        byte[] b = v.toByteArray();
        return derTag(0x02, b);
    }

    private static byte[] derOid(String oid) throws Exception {
        String[] p = oid.split("\\.");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(Integer.parseInt(p[0]) * 40 + Integer.parseInt(p[1]));
        for (int i = 2; i < p.length; i++) {
            long v = Long.parseLong(p[i]);
            ByteArrayOutputStream t = new ByteArrayOutputStream();
            t.write((int) (v & 0x7F));
            v >>>= 7;
            while (v != 0) {
                t.write((int) ((v & 0x7F) | 0x80));
                v >>>= 7;
            }
            byte[] tb = t.toByteArray();
            for (int j = tb.length - 1; j >= 0; j--) bos.write(tb[j]);
        }
        return derTag(0x06, bos.toByteArray());
    }

    private static byte[] derNull() throws Exception {
        return new byte[]{0x05, 0x00};
    }

    private static byte[] derUtf8(String s) throws Exception {
        return derTag(0x0C, s.getBytes("UTF-8"));
    }

    private static byte[] derBitString(byte[] data) throws Exception {
        byte[] out = new byte[data.length + 1];
        out[0] = 0; // kullanimilmayan bit yok
        System.arraycopy(data, 0, out, 1, data.length);
        return derTag(0x03, out);
    }

    private static byte[] derTag(int tag, byte[] content) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(tag);
        int len = content.length;
        if (len < 128) {
            bos.write(len);
        } else {
            ByteArrayOutputStream lb = new ByteArrayOutputStream();
            while (len > 0) {
                lb.write(len & 0xFF);
                len >>>= 8;
            }
            byte[] l = lb.toByteArray();
            bos.write(0x80 | l.length);
            for (int j = l.length - 1; j >= 0; j--) bos.write(l[j]);
        }
        bos.write(content);
        return bos.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    // -------------------------------------------------- TBSCertificate

    private static byte[] tbsCertificate(KeyPair kp) throws Exception {
        Calendar c = Calendar.getInstance();
        Date now = c.getTime();
        c.add(Calendar.YEAR, 30);
        Date until = c.getTime();

        byte[] version = derTag(0xA0, derInteger(BigInteger.valueOf(2)));
        byte[] serial = derInteger(BigInteger.valueOf(System.currentTimeMillis()));
        byte[] sigAlg = derSequence(derOid("1.2.840.113549.1.1.11"), derNull());
        byte[] name = derSequence(derSet(derSequence(
                derOid("2.5.4.3"), derUtf8("EskiDroid"))));
        byte[] validity = derSequence(derUtcTime(now), derUtcTime(until));
        byte[] spki = ((RSAPublicKey) kp.getPublic()).getEncoded();

        return derSequence(version, serial, sigAlg, name, validity, name, spki);
    }

    private static byte[] derUtcTime(Date d) throws Exception {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return derTag(0x17, String.format("%02d%02d%02d%02d%02d%02dZ",
                c.get(Calendar.YEAR) % 100,
                c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
                c.get(Calendar.SECOND)).getBytes("ASCII"));
    }
}
