package org.witness.proofmode.crypto;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Security;

import org.spongycastle.bcpg.ArmoredOutputStream;
import org.spongycastle.bcpg.BCPGOutputStream;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.openpgp.PGPCompressedData;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPObjectFactory;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRingCollection;
import org.spongycastle.openpgp.PGPSecretKey;
import org.spongycastle.openpgp.PGPSignature;
import org.spongycastle.openpgp.PGPSignatureGenerator;
import org.spongycastle.openpgp.PGPSignatureList;
import org.spongycastle.openpgp.PGPUtil;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

/**
 * A simple utility class that creates seperate signatures for files and verifies them.
 * <p>
 * To sign a file: DetachedSignatureProcessor -s [-a] fileName secretKey passPhrase.<br>
 * If -a is specified the output file will be "ascii-armored".
 * <p>
 * To decrypt: DetachedSignatureProcessor -v  fileName signatureFile publicKeyFile.
 * <p>
 * Note: this example will silently overwrite files.
 * It also expects that a single pass phrase
 * will have been used.
 */
public class DetachedSignatureProcessor
{
    public static boolean verifySignature(
            String fileName,
            String inputFileName,
            String keyFileName)
            throws GeneralSecurityException, IOException, PGPException
    {
        InputStream in = new BufferedInputStream(new FileInputStream(inputFileName));
        InputStream keyIn = new BufferedInputStream(new FileInputStream(keyFileName));

        boolean result = verifySignature(fileName, in, keyIn);

        keyIn.close();
        in.close();

        return result;
    }

    /*
     * verify the signature in in against the file fileName.
     */
    public static boolean verifySignature(
            String          fileName,
            InputStream     in,
            InputStream     keyIn)
            throws GeneralSecurityException, IOException, PGPException
    {
        in = PGPUtil.getDecoderStream(in);

        PGPObjectFactory    pgpFact = new PGPObjectFactory(in);
        PGPSignatureList    p3;

        Object    o = pgpFact.nextObject();
        if (o instanceof PGPCompressedData)
        {
            PGPCompressedData             c1 = (PGPCompressedData)o;

            pgpFact = new PGPObjectFactory(c1.getDataStream());

            p3 = (PGPSignatureList)pgpFact.nextObject();
        }
        else
        {
            p3 = (PGPSignatureList)o;
        }

        PGPPublicKeyRingCollection  pgpPubRingCollection = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(keyIn));


        InputStream                 dIn = new BufferedInputStream(new FileInputStream(fileName));

        PGPSignature                sig = p3.get(0);
        PGPPublicKey                key = pgpPubRingCollection.getPublicKey(sig.getKeyID());

        sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("SC"), key);

        int ch;
        while ((ch = dIn.read()) >= 0)
        {
            sig.update((byte)ch);
        }

        dIn.close();

        if (sig.verify())
        {
            System.out.println("signature verified.");
            return true;
        }
        else
        {
            System.out.println("signature verification failed.");
            return false;
        }
    }

    public static void createSignature(
            PGPSecretKey             pgpSec,
            InputStream          in,
            OutputStream    out,
            char[]          pass,
            boolean         armor)
            throws GeneralSecurityException, IOException, PGPException
    {
        if (armor)
        {
            out = new ArmoredOutputStream(out);
        }

        PGPPrivateKey            pgpPrivKey = pgpSec.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("SC").build(pass));
        PGPSignatureGenerator    sGen = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(pgpSec.getPublicKey().getAlgorithm(), PGPUtil.SHA1).setProvider("SC"));

        sGen.init(PGPSignature.BINARY_DOCUMENT, pgpPrivKey);

        BCPGOutputStream         bOut = new BCPGOutputStream(out);
        InputStream              fIn = new BufferedInputStream(in);

        int ch;
        while ((ch = fIn.read()) >= 0)
        {
            sGen.update((byte)ch);
        }

        fIn.close();

        sGen.generate().encode(bOut);

        if (armor)
        {
            out.close();
        }
    }


}