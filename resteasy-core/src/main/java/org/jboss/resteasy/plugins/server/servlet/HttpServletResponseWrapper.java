package org.jboss.resteasy.plugins.server.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.util.AsyncBufferedOutputStream;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class HttpServletResponseWrapper implements HttpResponse
{
   protected final HttpServletResponse response;
   protected final HttpServletRequest request;
   protected int status = 200;
   protected MultivaluedMap<String, Object> outputHeaders;
   protected ResteasyProviderFactory factory;
   protected OutputStream outputStream = new DeferredOutputStream();
   protected boolean asyncStarted = false;
   protected byte[] bufferedData;
   protected boolean closeQueued;
   protected volatile BiConsumer<HttpResponse, Throwable> callback;
   boolean asyncDone;
   protected volatile Consumer<Throwable> asyncFlushCallback;


   /**
    * RESTEASY-684 wants to defer access to outputstream until a write happens
    *
    */
   protected class DeferredOutputStream extends OutputStream
   {
      @Override
      public void write(int i) throws IOException
      {
         response.getOutputStream().write(i);
      }

      @Override
      public void write(byte[] bytes) throws IOException
      {
         response.getOutputStream().write(bytes);
      }

      @Override
      public void write(byte[] bytes, int i, int i1) throws IOException
      {
         response.getOutputStream().write(bytes, i, i1);
      }

      @Override
      public void flush() throws IOException
      {
         response.getOutputStream().flush();
      }

      @Override
      public void close() throws IOException
      {
         //NOOP (RESTEASY-1650)
      }
   }

   public HttpServletResponseWrapper(final HttpServletResponse response, final HttpServletRequest request, final ResteasyProviderFactory factory)
   {
      this.response = response;
       this.request = request;
       outputHeaders = new HttpServletResponseHeaders(response, factory);
      this.factory = factory;
   }

   public int getStatus()
   {
      return status;
   }

   public void setStatus(int status)
   {
      this.status = status;
      this.response.setStatus(status);
   }

   public MultivaluedMap<String, Object> getOutputHeaders()
   {
      return outputHeaders;
   }

   public OutputStream getOutputStream() throws IOException
   {
      return outputStream;
   }

   @Override
   public void setOutputStream(OutputStream os)
   {
      this.outputStream = os;
   }

   public void addNewCookie(NewCookie cookie)
   {
      outputHeaders.add(javax.ws.rs.core.HttpHeaders.SET_COOKIE, cookie);
   }

   public void sendError(int status) throws IOException
   {
      response.sendError(status);
   }

   public void sendError(int status, String message) throws IOException
   {
      response.sendError(status, message);
   }

   public boolean isCommitted()
   {
      return response.isCommitted();
   }

   public void reset()
   {
      response.reset();
      outputHeaders = new HttpServletResponseHeaders(response, factory);
   }

   @Override
   public void flushBuffer() throws IOException
   {
      response.flushBuffer();
   }

   @Override
   public void close() throws IOException {
        //noop
   }

   @Override
   public boolean isAsyncIoRequired() {
      return request.isAsyncStarted();
   }

    @Override
    public void startAsyncIO() {
        if (!asyncStarted) {
            asyncStarted = true;
            try {
                response.getOutputStream().setWriteListener(new WriteListener());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean isAsyncIOStarted() {
        return asyncStarted;
    }

    @Override
    public void writeAsync(final BiConsumer<HttpResponse, Throwable> completeFunction, final byte[] data, final int off, final int len) {
        try {
            if (!asyncStarted) {
                throw new IllegalStateException("Must call startAsyncIO first");
            }
            if (response.getOutputStream().isReady()) {
                response.getOutputStream().write(data, off, len);
                completeFunction.accept(this, null);

                if(closeQueued) {
                    response.getOutputStream().close();
                }
            } else {
                bufferedData = new byte[len];
                System.arraycopy(data, off, bufferedData, 0, len);
                callback = completeFunction;
            }
        } catch (IOException e) {
            completeFunction.accept(this, e);
            try {
                close();
            } catch (IOException ex) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void asyncDone() {
       if(outputStream instanceof AsyncBufferedOutputStream) {
           try {
               outputStream.close();
           } catch (IOException e) {
               throw new RuntimeException(e);
           }
       }
        if(bufferedData == null) {
            request.getAsyncContext().complete();
        } else {
            asyncDone = true;
        }
    }

    @Override
    public void flushAsync(Consumer<Throwable> throwableConsumer) {
        if(outputStream instanceof AsyncBufferedOutputStream) {
            ((AsyncBufferedOutputStream) outputStream).asyncFlush();
        }
        if(bufferedData == null) {
            throwableConsumer.accept(null);
        } else {
            asyncFlushCallback = throwableConsumer;
        }

    }

    private class WriteListener implements javax.servlet.WriteListener {

      @Override
      public void onWritePossible() throws IOException {
         if(bufferedData != null) {
             if(!response.getOutputStream().isReady()) {
                return;
             }
             response.getOutputStream().write(bufferedData);
             BiConsumer<HttpResponse, Throwable> callback = HttpServletResponseWrapper.this.callback;
             HttpServletResponseWrapper.this.callback = null;
             HttpServletResponseWrapper.this.bufferedData = null;
             Consumer<Throwable> fc = HttpServletResponseWrapper.this.asyncFlushCallback;
             if(fc != null) {
                 HttpServletResponseWrapper.this.asyncFlushCallback = null;
                 fc.accept(null);

             }

             if(asyncDone) {
                 request.getAsyncContext().complete();
             }
             if(closeQueued) {
                 response.getOutputStream().close();
             }
             if(callback != null) {
                 callback.accept(HttpServletResponseWrapper.this,null);
             }
         }
      }

      @Override
      public void onError(final Throwable t) {
          BiConsumer<HttpResponse, Throwable> callback = HttpServletResponseWrapper.this.callback;
          if(asyncFlushCallback != null) {
              asyncFlushCallback.accept(t);
          }
          HttpServletResponseWrapper.this.callback = null;
          HttpServletResponseWrapper.this.bufferedData = null;
         if(callback != null) {
            callback.accept(HttpServletResponseWrapper.this, t);
         }
         try {
            response.getOutputStream().close();
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }
   }
}
