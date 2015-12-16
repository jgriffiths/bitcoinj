/*
 * Copyright 2013 Google Inc.
 * Copyright 2014-2016 the libsecp256k1 contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoin;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.math.BigInteger;
import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import static org.bitcoin.NativeSecp256k1Util.*;

/**
 * <p>This class holds native methods to handle ECDSA verification.</p>
 *
 * <p>You can find an example library that can be used for this at https://github.com/bitcoin/secp256k1</p>
 *
 * <p>To build secp256k1 for use with bitcoinj, run
 * `./configure --enable-jni --enable-experimental --enable-module-schnorr --enable-module-ecdh`
 * and `make` then copy `.libs/libsecp256k1.so` to your system library path
 * or point the JVM to the folder containing it with -Djava.library.path
 * </p>
 */
public class NativeSecp256k1 {

    private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private static final Lock r = rwl.readLock();
    private static final Lock w = rwl.writeLock();
    private static ThreadLocal<ByteBuffer> nativeECDSABuffer = new ThreadLocal<ByteBuffer>();

    /**
     * Verifies the given secp256k1 signature in native code. Calling when enabled == false is undefined (probably
     * library not loaded)
     *
     * @param data The data which was signed, must be exactly 32 bytes
     * @param signature The signature
     * @param pub The public key which did the signing
     */
    public static boolean verify(byte[] data, byte[] signature, byte[] pub) throws AssertFailException {
        Preconditions.checkArgument(data.length == 32 && signature.length <= 520 && pub.length <= 520);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < 520) {
            byteBuff = ByteBuffer.allocateDirect(520);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(data);
        byteBuff.put(signature);
        byteBuff.put(pub);

        r.lock();
        try {
            return secp256k1_ecdsa_verify(byteBuff, Secp256k1Context.getContext(), signature.length, pub.length) == 1;
        } finally {
            r.unlock();
        }
    }

    /**
     * libsecp256k1 Create an ECDSA signature.
     *
     * @param data Message hash, 32 bytes
     * @param key Secret key, 32 bytes
     * @return sig byte array of signature
     */
    public static byte[] sign(byte[] data, byte[] sec) throws AssertFailException {
        Preconditions.checkArgument(data.length == 32 && sec.length <= 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < 32 + 32) {
            byteBuff = ByteBuffer.allocateDirect(32 + 32);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(data);
        byteBuff.put(sec);

        byte[][] retByteArray;

        r.lock();
        try {
            retByteArray = secp256k1_ecdsa_sign(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] sigArr = retByteArray[0];
        int sigLen = new BigInteger(new byte[] { retByteArray[1][0] }).intValue();
        int retVal = new BigInteger(new byte[] { retByteArray[1][1] }).intValue();

        assertEquals(sigArr.length, sigLen, "Got bad signature length.");

        return retVal == 0 ? new byte[0] : sigArr;
    }

    public static byte[] signSchnorr(byte[] data, byte[] sec) throws AssertFailException {
        while (sec.length > 32 && sec[0] == 0) {
            sec = Arrays.copyOfRange(sec, 1, sec.length);
        }
        Preconditions.checkArgument(data.length == 32 && sec.length <= 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null) {
            byteBuff = ByteBuffer.allocateDirect(32 + 32);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(data);
        byteBuff.put(sec);

        byte[][] retByteArray;

        r.lock();
        try {
          retByteArray = secp256k1_schnorr_sign(byteBuff, Secp256k1Context.getContext());
        } finally {
          r.unlock();
        }

        byte[] sigArr = retByteArray[0];
        int sigLen = new BigInteger(new byte[] { retByteArray[1][0] }).intValue();
        int retVal = new BigInteger(new byte[] { retByteArray[1][1] }).intValue();

        assertEquals(sigArr.length,sigLen, "Got bad signature length." );

        if( retVal == 0 ) sigArr = new byte[0];

        return sigArr;
    }

    /**
     * libsecp256k1 Seckey Verify - returns 1 if valid, 0 if invalid
     *
     * @param seckey ECDSA Secret key, 32 bytes
     */
    public static boolean secKeyVerify(byte[] seckey) {
        Preconditions.checkArgument(seckey.length == 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < seckey.length) {
            byteBuff = ByteBuffer.allocateDirect(seckey.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(seckey);

        r.lock();
        try {
            return secp256k1_ec_seckey_verify(byteBuff, Secp256k1Context.getContext()) == 1;
        } finally {
            r.unlock();
        }
    }

    /**
     * libsecp256k1 Compute Pubkey - computes public key from secret key
     *
     * @param seckey ECDSA Secret key, 32 bytes
     * @return pubkey ECDSA Public key, 33 or 65 bytes
     */
    // TODO add a 'compressed' arg
    public static byte[] computePubkey(byte[] seckey) throws AssertFailException {
        Preconditions.checkArgument(seckey.length == 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < seckey.length) {
            byteBuff = ByteBuffer.allocateDirect(seckey.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(seckey);

        byte[][] retByteArray;

        r.lock();
        try {
            retByteArray = secp256k1_ec_pubkey_create(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] pubArr = retByteArray[0];
        int pubLen = new BigInteger(new byte[] { retByteArray[1][0] }).intValue();
        int retVal = new BigInteger(new byte[] { retByteArray[1][1] }).intValue();

        assertEquals(pubArr.length, pubLen, "Got bad pubkey length.");

        return retVal == 0 ? new byte[0] : pubArr;
    }

    /**
     * libsecp256k1 Cleanup - This destroys the secp256k1 context object This should be called at the end of the program
     * for proper cleanup of the context.
     */
    public static synchronized void cleanup() {
        w.lock();
        try {
            secp256k1_destroy_context(Secp256k1Context.getContext());
        } finally {
            w.unlock();
        }
    }

    public static long cloneContext() {
        r.lock();
        try {
            return secp256k1_ctx_clone(Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }
    }

    /**
     * libsecp256k1 PrivKey Tweak-Mul - Tweak privkey by multiplying to it
     *
     * @param tweak some bytes to tweak with
     * @param seckey 32-byte seckey
     */
    public static byte[] privKeyTweakMul(byte[] privkey, byte[] tweak) throws AssertFailException {
        Preconditions.checkArgument(privkey.length == 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < privkey.length + tweak.length) {
            byteBuff = ByteBuffer.allocateDirect(privkey.length + tweak.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(privkey);
        byteBuff.put(tweak);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_privkey_tweak_mul(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] privArr = retByteArray[0];

        int privLen = (byte) new BigInteger(new byte[] { retByteArray[1][0] }).intValue() & 0xFF;
        int retVal = new BigInteger(new byte[] { retByteArray[1][1] }).intValue();

        assertEquals(privArr.length, privLen, "Got bad pubkey length.");

        assertEquals(retVal, 1, "Failed return value check.");

        return privArr;
    }

    /**
     * libsecp256k1 PrivKey Tweak-Add - Tweak privkey by adding to it
     *
     * @param tweak some bytes to tweak with
     * @param seckey 32-byte seckey
     */
    public static byte[] privKeyTweakAdd(byte[] privkey, byte[] tweak) throws AssertFailException {
        Preconditions.checkArgument(privkey.length == 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < privkey.length + tweak.length) {
            byteBuff = ByteBuffer.allocateDirect(privkey.length + tweak.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(privkey);
        byteBuff.put(tweak);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_privkey_tweak_add(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] privArr = retByteArray[0];

        int privLen = (byte) new BigInteger(new byte[] { retByteArray[1][0] }).intValue() & 0xFF;
        int retVal = new BigInteger(new byte[] { retByteArray[1][1] }).intValue();

        assertEquals(privArr.length, privLen, "Got bad pubkey length.");

        assertEquals(retVal, 1, "Failed return value check.");

        return privArr;
    }

    /**
     * libsecp256k1 PubKey Tweak-Add - Tweak pubkey by adding to it
     *
     * @param tweak some bytes to tweak with
     * @param pubkey 32-byte seckey
     */
    public static byte[] pubKeyTweakAdd(byte[] pubkey, byte[] tweak) throws AssertFailException {
        Preconditions.checkArgument(pubkey.length == 33 || pubkey.length == 65);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < pubkey.length + tweak.length) {
            byteBuff = ByteBuffer.allocateDirect(pubkey.length + tweak.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(pubkey);
        byteBuff.put(tweak);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_pubkey_tweak_add(byteBuff, Secp256k1Context.getContext(), pubkey.length);
        } finally {
            r.unlock();
        }

        byte[] pubArr = retByteArray[0];

        int pubLen = (byte) new BigInteger(new byte[] { retByteArray[1][0] }).intValue() & 0xFF;
        int retVal = new BigInteger(new byte[] { retByteArray[1][1] }).intValue();

        assertEquals(pubArr.length, pubLen, "Got bad pubkey length.");

        assertEquals(retVal, 1, "Failed return value check.");

        return pubArr;
    }

    /**
     * libsecp256k1 PubKey Tweak-Mul - Tweak pubkey by multiplying to it
     *
     * @param tweak some bytes to tweak with
     * @param pubkey 32-byte seckey
     */
    public static byte[] pubKeyTweakMul(byte[] pubkey, byte[] tweak) throws AssertFailException {
        Preconditions.checkArgument(pubkey.length == 33 || pubkey.length == 65);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < pubkey.length + tweak.length) {
            byteBuff = ByteBuffer.allocateDirect(pubkey.length + tweak.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(pubkey);
        byteBuff.put(tweak);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_pubkey_tweak_mul(byteBuff, Secp256k1Context.getContext(), pubkey.length);
        } finally {
            r.unlock();
        }

        byte[] pubArr = retByteArray[0];

        int pubLen = (byte) new BigInteger(new byte[] { retByteArray[1][0] }).intValue() & 0xFF;
        int retVal = new BigInteger(new byte[] { retByteArray[1][1] }).intValue();

        assertEquals(pubArr.length, pubLen, "Got bad pubkey length.");

        assertEquals(retVal, 1, "Failed return value check.");

        return pubArr;
    }

    /**
     * libsecp256k1 create ECDH secret - constant time ECDH calculation
     *
     * @param seckey byte array of secret key used in exponentiaion
     * @param pubkey byte array of public key used in exponentiaion
     */
    public static byte[] createECDHSecret(byte[] seckey, byte[] pubkey) throws AssertFailException {
        while (seckey.length > 32 && seckey[0] == 0) {
            seckey = Arrays.copyOfRange(seckey, 1, seckey.length);
        }
        Preconditions.checkArgument(seckey.length <= 32 && pubkey.length <= 65);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < 32+33) {
            byteBuff = ByteBuffer.allocateDirect(32+33);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(seckey);
        byteBuff.put(pubkey);

        byte[][] retByteArray;
        r.lock();
        try {
            retByteArray = secp256k1_ecdh(byteBuff, Secp256k1Context.getContext(), pubkey.length);
        } finally {
            r.unlock();
        }

        byte[] resArr = retByteArray[0];
        int retVal = new BigInteger(new byte[] { retByteArray[1][0] }).intValue();

        assertEquals(resArr.length, 32, "Got bad result length.");
        assertEquals(retVal, 1, "Failed return value check.");

        return resArr;
    }

    /**
     * libsecp256k1 randomize - updates the context randomization
     *
     * @param seed 32-byte random seed
     */
    public static synchronized boolean randomize(byte[] seed) throws AssertFailException {
        Preconditions.checkArgument(seed.length == 32 || seed == null);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null || byteBuff.capacity() < seed.length) {
            byteBuff = ByteBuffer.allocateDirect(seed.length);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(seed);

        w.lock();
        try {
            return secp256k1_context_randomize(byteBuff, Secp256k1Context.getContext()) == 1;
        } finally {
            w.unlock();
        }
    }

    public static byte[] schnorrSign(byte[] data, byte[] sec) throws AssertFailException {
        Preconditions.checkArgument(data.length == 32 && sec.length <= 32);

        ByteBuffer byteBuff = nativeECDSABuffer.get();
        if (byteBuff == null) {
            byteBuff = ByteBuffer.allocateDirect(32 + 32);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }
        byteBuff.rewind();
        byteBuff.put(data);
        byteBuff.put(sec);

        byte[][] retByteArray;

        r.lock();
        try {
            retByteArray = secp256k1_schnorr_sign(byteBuff, Secp256k1Context.getContext());
        } finally {
            r.unlock();
        }

        byte[] sigArr = retByteArray[0];
        int retVal = new BigInteger(new byte[] { retByteArray[1][0] }).intValue();

        assertEquals(sigArr.length, 64, "Got bad signature length.");

        return retVal == 0 ? new byte[0] : sigArr;
    }

    public static RewindResult rangeProofRewind(byte[] nonce, byte[] commitment, byte[] rangeProof) throws AssertFailException {
        Preconditions.checkArgument(nonce.length == 32 && commitment.length == 33 && rangeProof.length < 10000);
        ByteBuffer byteBuff = nativeECDSABuffer.get();
        int reqLen = nonce.length + commitment.length + rangeProof.length;
        if (byteBuff == null || byteBuff.capacity() < reqLen) {
            byteBuff = ByteBuffer.allocateDirect(reqLen);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }

        byteBuff.rewind();
        byteBuff.put(nonce);
        byteBuff.put(commitment);
        byteBuff.put(rangeProof);

        byte[][] retByteArray;
        retByteArray = secp256k1_rangeproof_rewind(
                byteBuff,
                Secp256k1Context.getContext(),
                rangeProof.length
        );

        // int msgLen = new BigInteger(new byte[] { retByteArray[2][0] }).intValue();
        int retVal = new BigInteger(new byte[] { retByteArray[2][1] }).intValue();

        assertEquals(retVal, 1, "Failed return value check.");

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            // BigInteger needs big-endian
            for (int i = 0; i < retByteArray[1].length/2; ++i) {
                byte t = retByteArray[1][i];
                retByteArray[1][i] = retByteArray[1][retByteArray[1].length - i - 1];
                retByteArray[1][retByteArray[1].length - i - 1] = t;
            }
        }

        return new RewindResult(retByteArray[0],
                                new BigInteger(retByteArray[1]).longValue());
    }

    public static byte[] pedersenCommit(long value, byte[] blindingFactor) throws AssertFailException {
        ByteBuffer byteBuff = nativeECDSABuffer.get();
        int reqLen = blindingFactor.length;
        if (byteBuff == null || byteBuff.capacity() < reqLen) {
            byteBuff = ByteBuffer.allocateDirect(reqLen);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }

        byteBuff.rewind();
        byteBuff.put(blindingFactor);

        byte[][] retByteArray = secp256k1_pedersen_commit(
                byteBuff, Secp256k1Context.getContext(), value
        );

        int retVal = new BigInteger(new byte[] { retByteArray[1][0] }).intValue();
        assertEquals(retVal, 1, "Failed return value check.");
        byte[] resArr = retByteArray[0];
        assertEquals(resArr.length, 33, "Got bad result length." );

        return resArr;
    }

    public static byte[] pedersenBlindSum(byte[][] blindedValues, int numInputs) throws AssertFailException {
        ByteBuffer byteBuff = nativeECDSABuffer.get();
        int reqLen = 32 * blindedValues.length;
        if (byteBuff == null || byteBuff.capacity() < reqLen) {
            byteBuff = ByteBuffer.allocateDirect(reqLen);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }

        byteBuff.rewind();
        for (int i = 0; i < blindedValues.length; ++i) {
            assertEquals(blindedValues[i].length, 32, "Got bad input length.");
            byteBuff.put(blindedValues[i]);
        }

        byte[][] retByteArray = secp256k1_pedersen_blind_sum(
                byteBuff,
                Secp256k1Context.getContext(),
                blindedValues.length,
                numInputs
        );

        int retVal = new BigInteger(new byte[] { retByteArray[1][0] }).intValue();
        assertEquals(retVal, 1, "Failed return value check.");
        byte[] resArr = retByteArray[0];
        assertEquals(resArr.length, 32, "Got bad result length." );

        return resArr;
    }

    public static byte[] rangeProofSign(byte[] commitment, byte[] blindingFactor, byte[] nonce, long value) throws AssertFailException {
        ByteBuffer byteBuff = nativeECDSABuffer.get();
        int reqLen = commitment.length + blindingFactor.length + nonce.length;
        if (byteBuff == null || byteBuff.capacity() < reqLen) {
            byteBuff = ByteBuffer.allocateDirect(reqLen);
            byteBuff.order(ByteOrder.nativeOrder());
            nativeECDSABuffer.set(byteBuff);
        }

        byteBuff.rewind();
        byteBuff.put(commitment);
        byteBuff.put(blindingFactor);
        byteBuff.put(nonce);

        byte[][] retByteArray = secp256k1_rangeproof_sign(
                byteBuff,
                Secp256k1Context.getContext(),
                value
        );

        int retVal = new BigInteger(new byte[] { retByteArray[1][0] }).intValue();
        assertEquals(retVal, 1, "Failed return value check.");

        return retByteArray[0];
    }

    private static native long secp256k1_ctx_clone(long context);

    private static native int secp256k1_context_randomize(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_privkey_tweak_add(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_privkey_tweak_mul(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_pubkey_tweak_add(ByteBuffer byteBuff, long context, int pubLen);

    private static native byte[][] secp256k1_pubkey_tweak_mul(ByteBuffer byteBuff, long context, int pubLen);

    private static native void secp256k1_destroy_context(long context);

    private static native int secp256k1_ecdsa_verify(ByteBuffer byteBuff, long context, int sigLen, int pubLen);

    private static native byte[][] secp256k1_ecdsa_sign(ByteBuffer byteBuff, long context);

    private static native int secp256k1_ec_seckey_verify(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_ec_pubkey_create(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_ec_pubkey_parse(ByteBuffer byteBuff, long context, int inputLen);

    private static native byte[][] secp256k1_schnorr_sign(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_ecdh(ByteBuffer byteBuff, long context, int inputLen);

    private static native byte[][] secp256k1_ecdh(ByteBuffer byteBuff, long context);

    private static native byte[][] secp256k1_rangeproof_rewind(ByteBuffer byteBuff, long context, int rangeProofLen);

    private static native byte[][] secp256k1_pedersen_commit(ByteBuffer byteBuff, long context, long value);

    private static native byte[][] secp256k1_pedersen_blind_sum(ByteBuffer byteBuff, long context, int numFactors, int numInputs);

    private static native byte[][] secp256k1_rangeproof_sign(ByteBuffer byteBuff, long context, long value);
}
