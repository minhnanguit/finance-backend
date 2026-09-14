package com.mosaicglobal.finance.shared.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Minimal PEM → RSA key parsing without third-party dependencies. */
final class PemKeys {

  private PemKeys() {}

  static RSAPrivateKey privateKey(String pem) {
    try {
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decode(pem)));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Invalid RSA private key PEM", e);
    }
  }

  static RSAPublicKey publicKey(String pem) {
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decode(pem)));
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("Invalid RSA public key PEM", e);
    }
  }

  private static byte[] decode(String pem) {
    String body = pem.replaceAll("-----(BEGIN|END)[A-Z ]+-----", "").replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }
}
