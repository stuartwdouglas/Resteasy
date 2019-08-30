package org.jboss.resteasy.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BiConsumer;

import org.jboss.resteasy.spi.HttpResponse;

/**
 * @author Stuart Douglas
 */
public class AsyncBufferedOutputStream extends OutputStream
{
   protected ByteArrayOutputStream delegate = new ByteArrayOutputStream();
   private final HttpResponse response;
   private boolean closed;

   public AsyncBufferedOutputStream(final HttpResponse response) {
      this.response = response;
   }

   @Override
   public void write(int i) throws IOException
   {
      delegate.write(i);
   }

   @Override
   public void write(byte[] bytes) throws IOException
   {
      delegate.write(bytes);
   }

   @Override
   public void write(byte[] bytes, int i, int i1) throws IOException
   {
      delegate.write(bytes, i, i1);
   }

   @Override
   public void flush() throws IOException
   {
      //noop
   }

   public void asyncFlush() {

      response.writeAsync(new BiConsumer<HttpResponse, Throwable>() {
         @Override
         public void accept(HttpResponse httpResponse, Throwable throwable) {
         }
      }, delegate.toByteArray());
      delegate.reset();
   }

   @Override
   public void close() throws IOException
   {
      if(closed) {
         return;
      }
      closed = true;
      response.writeAsync(new BiConsumer<HttpResponse, Throwable>() {
         @Override
         public void accept(HttpResponse httpResponse, Throwable throwable) {
            try {
               httpResponse.close();
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      }, delegate.toByteArray());
   }
}
