/** SocketJS.java
 *  @author Michael Fu
 *  @date   2013-08-14
 */
 
import java.applet.*;
import netscape.javascript.*;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.net.*;
import java.io.*;
import java.nio.charset.Charset;
import java.lang.*;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.concurrent.SynchronousQueue;

public class SocketJS extends Applet {
  JSObject window;
  JSObject namespace;
  Hashtable<Integer,SocketHandler> sockets;
  SynchronousQueue<SocketTransactionPackage> inbound;
  SynchronousQueue<SocketTransactionPackage> outbound;
  Thread privilegedThread;
  int nextId;
  
  public void init() {
    window = JSObject.getWindow(this);
    namespace = (JSObject)window.getMember("SocketJS");
    sockets = new Hashtable<Integer,SocketHandler>();
    inbound = new SynchronousQueue<SocketTransactionPackage>();
    outbound = new SynchronousQueue<SocketTransactionPackage>();
    privilegedThread = new Thread(new PrivilegedSocketProducer());
    privilegedThread.start();
    nextId = 1;
    try {
      namespace.call("_init", null);
    } catch (JSException e) {}
  }
  
/*   public void start() {
    String debug = "123";
    System.out.println(debug);
    log(debug);
    try {
      window.call("runtest", null);
      namespace.call("test", null);
    } catch (JSException e) { e.printStackTrace(); }
  } */
  
  public void destroy() {
    privilegedThread.interrupt();
    Enumeration<SocketHandler> it = sockets.elements();
    while (it.hasMoreElements()) {
      SocketHandler h = it.nextElement();
      h.close();
    }
    try {
      privilegedThread.join();
    } catch (InterruptedException e) {}
  }
  
  public int connect(String host, int port, boolean ssl) {
    final int id = nextId;
    ++nextId;
    try {
      inbound.put(new SocketTransactionPackage(id, host, port, ssl)); // Hand to elevated thread
    } catch (InterruptedException e) { return -1;}
    new Thread(new Runnable() {
      public void run() {
        boolean success = false;
        Socket s = null;
        try {
          s = outbound.take().disarm(); // Retrieve
        } catch (InterruptedException e) {}
        if (s != null) {
          try {
            SocketHandler h = new SocketHandler(id, s);
            sockets.put(Integer.valueOf(id), h);
            success = true;
          } catch (IOException e) { log(e.getMessage()); }
        }
        try {
          namespace.call("_conn", new Object[] {Integer.valueOf(id), Boolean.valueOf(success)});
        } catch (JSException e) {}
      }
    }).start();
    return id;
  }
  
  public int connect(String host, int port) {
    return connect(host,port,false);
  }
  
  public void disconnect(int id) {
    SocketHandler h = sockets.remove(Integer.valueOf(id));
    if (h != null) {
      h.close();
    }
  }
  
  public void send(int id, String data) {
    SocketHandler h = sockets.get(Integer.valueOf(id));
    if (h != null) {
      h.e_send(data);
    }
  }
  
  public void i_dispatch(int id, String line) {
    try {
      namespace.call("_recv", new Object[] {Integer.valueOf(id), line});
    } catch (JSException e) {}
  }
  
  public void i_notifyClose(int id) {
    try {
      namespace.call("_closed", new Object[] {Integer.valueOf(id)});
    } catch (JSException e) {}
  }
  
  public void log(String message) {
    try {
      namespace.call("_log", new Object[] {message});
    } catch (JSException e) {}
  }
  
  private class SocketTransactionPackage {
    public int id;
    public String host;
    public int port;
    public boolean ssl;
    Socket s;
    
    public SocketTransactionPackage(int id, String host, int port, boolean ssl) {
      this.id = id;
      this.host = host;
      this.port = port;
      this.ssl = ssl;
      s = null;
    }
    
    public void arm(Socket s) {
      this.s = s;
    }
    
    public Socket disarm() {
      Socket p = s;
      s = null;
      return p;
    }
  }
  
  private class PrivilegedSocketProducer implements Runnable {
    SocketFactory factory;
    SocketFactory sslfactory;
    
    public PrivilegedSocketProducer() {
      factory = (SocketFactory) SocketFactory.getDefault();
      sslfactory = (SocketFactory) SSLSocketFactory.getDefault();
    }
    
    public void run() {
      try {
        while(true) {
          SocketTransactionPackage thePackage = inbound.take(); // Get unarmed package
          try {
            SocketFactory selected = thePackage.ssl ? sslfactory : factory;
            Socket s = selected.createSocket(thePackage.host, thePackage.port);
            thePackage.arm(s);
          } catch (Exception e) {
            log(e.getMessage());
          }
          outbound.put(thePackage); // Smuggle armed package back out
        }
      } catch (InterruptedException e) {}
    }
  }
  
  private class SocketHandler {
    int id;
    Socket s;
    BufferedWriter out;
    Thread listener;
    boolean open;
    
    public SocketHandler(int id, Socket s) throws IOException {
      this.id = id;
      this.s = s;
      out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF8"));
      listener = new Thread(new SocketListener(s, this));
      listener.start();
      open = true;
    }
    
    public int getId() {
      return id;
    }
    
    public void e_send(String data) {
      try {
        out.write(data);
        out.flush();
      } catch (IOException e) { log(e.getMessage());}
    }
    
    public void i_recv(String line) {
      i_dispatch(id, line);
    }
    
    public void close() {
      if (open) {
        open = false;
        try {
          out.close();
          s.close();
        } catch (IOException e) { log(e.getMessage());}
        i_notifyClose(id);
        sockets.remove(Integer.valueOf(id));
        try {
          listener.join();
        } catch (InterruptedException e) {}
      }
    }
    
    private class SocketListener implements Runnable {
      Socket s;
      ReadLineInputStream in;
      SocketHandler handler;
      
      public SocketListener(Socket s, SocketHandler handler) throws IOException {
        this.s = s;
        this.handler = handler;
        in = new ReadLineInputStream(new BufferedInputStream(s.getInputStream()), "UTF8");
      }
      
      public void run() {
        String line;
        try {
          while((line = in.readLine()) != null) {
            handler.i_recv(line);
          }
        } catch (IOException e) { log(e.getMessage());}
        try {
          in.close();
        } catch (IOException e) { log(e.getMessage());}
        handler.close();
      }
    }
  }
  
  /**
   * Attempt at smashing together DataInputStream and InputStreamReader
   * Wishlist: InputStreamReader except possible to reset() to just after last character read() to read([], int, int) non character data
  */
  private class ReadLineInputStream extends FilterInputStream {
    private final String charsetName;
    
    public ReadLineInputStream(InputStream in, String charsetName) {
      super(in);
      Charset.isSupported(charsetName);
      this.charsetName = new String(charsetName);
    }
    
    public String readLine() throws IOException {
      ByteArrayOutputStream s = new ByteArrayOutputStream();
      boolean eof = false;
      int c;
      loop:
      for (;;) {
        /* Pretty sure comparing bytes to 10 and 13 only works in unicode */
        switch (c = in.read()) {
          case -1:
            eof = true;
            /* fallthrough */
          case '\n':
            break loop;
          case '\r':
            break;
          default:
            s.write(c);
            break;
        }
      }
      if (eof && s.size() == 0) {
        return null;
      }
      return s.toString(charsetName);
    }
  }
}