package org.jboss.resteasy.spi;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

/**
 * Bridge interface between the base Resteasy JAX-RS implementation and the actual HTTP transport (i.e. a servlet container)
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public interface HttpResponse extends Closeable
{
   int getStatus();

   void setStatus(int status);

   MultivaluedMap<String, Object> getOutputHeaders();

   OutputStream getOutputStream() throws IOException;
   void setOutputStream(OutputStream os);

   void addNewCookie(NewCookie cookie);

   void sendError(int status) throws IOException;

   void sendError(int status, String message) throws IOException;

   boolean isCommitted();

   /**
    * reset status and headers.  Will fail if response is committed
    */
   void reset();

   default void close() throws IOException {
      // RESTEASY-1650
      getOutputStream().close();
   }

   void flushBuffer() throws IOException;

   /**
    * If this returns false then blocking IO is not currently allowed, and async IO must be used instead.
    *
    * Async IO can only be used if this returns true, as not all implementations may support it
    *
    */
   default boolean isAsyncIoRequired() {
      return false;
   }

   default boolean isAsyncIOStarted() {
      return false;
   }

   default void startAsyncIO() {
      throw new IllegalStateException("This implementation does not support async IO");
   }

   default void writeAsync(BiConsumer<HttpResponse, Throwable> completeFunction, byte[] data) {
      writeAsync(completeFunction, data, 0, data.length);
   }

   default void writeAsync(BiConsumer<HttpResponse, Throwable> completeFunction, byte[] data, int off, int len) {
      throw new IllegalStateException("This implementation does not support async IO");
   }

   default void asyncDone() {

   }

   default void flushAsync(Consumer<Throwable> throwableConsumer){
      throwableConsumer.accept(null);
   }
}
