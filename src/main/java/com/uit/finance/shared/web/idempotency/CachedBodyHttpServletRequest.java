package com.uit.finance.shared.web.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Reads the body once (bounded) so it can be fingerprinted and still consumed downstream. */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

  private final byte[] body;

  CachedBodyHttpServletRequest(HttpServletRequest request, int maxBytes) throws IOException {
    super(request);
    this.body = readBounded(request.getInputStream(), maxBytes);
  }

  byte[] body() {
    return body;
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream buffer = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public int read() {
        return buffer.read();
      }

      @Override
      public boolean isFinished() {
        return buffer.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("Async reads are not supported");
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    String enc = getCharacterEncoding();
    Charset charset = enc != null ? Charset.forName(enc) : StandardCharsets.UTF_8;
    return new BufferedReader(new InputStreamReader(getInputStream(), charset));
  }

  private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
    byte[] data = in.readNBytes(maxBytes + 1);
    if (data.length > maxBytes) {
      throw new BodyTooLargeException(maxBytes);
    }
    return data;
  }

  static final class BodyTooLargeException extends IOException {
    BodyTooLargeException(int maxBytes) {
      super("Request body exceeds " + maxBytes + " bytes");
    }
  }
}
