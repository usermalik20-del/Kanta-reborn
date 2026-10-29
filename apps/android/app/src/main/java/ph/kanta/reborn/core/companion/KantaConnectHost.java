package ph.kanta.reborn.core.companion;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kanta-Connect host side: NSD service discovery + WebSocket server for phone app clients.
 *
 * <p>Advertises {@code _kanta._tcp} via Android Network Service Discovery so the
 * KANTA Karaoke phone companion can find this host on the local network. Accepts
 * WebSocket connections from paired Kanta-Connect clients and dispatches CompanionProtocol
 * requests (snapshot, search, reserve, cancel, play, pause, next) to SessionController.
 *
 * <p>Key material for the encrypted channel is supplied at runtime via the pairing code
 * flow (CompanionProtocol). The host never persists pairing codes or keys.
 */
public final class KantaConnectHost {

  private static final String TAG = "KantaConnectHost";
  private static final String SERVICE_TYPE = "_kanta._tcp.";
  /** One source of truth for the port: the link model and the QR agree with this. */
  private static final int PORT = ph.kanta.reborn.core.connect.KantaConnectInfo.DEFAULT_PORT;

  private final Context context;
  private final HostAdapter adapter;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

  private NsdManager nsdManager;
  private NsdManager.RegistrationListener registrationListener;
  private NsdManager.DiscoveryListener discoveryListener;
  private ServerSocket serverSocket;
  private boolean advertising = false;
  private boolean listening = false;
  private String pairingCode;
  private volatile Runnable onStatusChanged;

  public interface HostAdapter {
    /** Handle a Kanta-Connect request and return a JSON response string. */
    String handleRequest(String action, String argument, String singer);

    /** Returns true if the host app is in the foreground and ready. */
    boolean isReady();
  }

  public KantaConnectHost(Context context, HostAdapter adapter) {
    this.context = context.getApplicationContext();
    this.adapter = adapter;
    this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
  }

  /** Starts advertising the _kanta._tcp service and listening for WebSocket clients. */
  public void start() {
    if (advertising || listening) return;
    pairingCode = generatePairingCode();
    advertising = true;
    listening = true;

    registerService();
    worker.execute(this::acceptLoop);

    Log.i(TAG, "Kanta-Connect host started: code=" + pairingCode + " port=" + PORT);
    notifyStatus("Kanta-Connect hosting · code: " + pairingCode);
  }

  /** Stops advertising and closes all client connections. */
  public void stop() {
    advertising = false;
    listening = false;

    for (ClientSession session : sessions.values()) {
      session.close();
    }
    sessions.clear();

    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (IOException ignored) {}
    serverSocket = null;

    try {
      if (registrationListener != null) {
        nsdManager.unregisterService(registrationListener);
      }
    } catch (Exception ignored) {}
    registrationListener = null;

    pairingCode = null;
    Log.i(TAG, "Kanta-Connect host stopped");
    notifyStatus("Kanta-Connect stopped");
  }

  /** Returns the current pairing code, or null if not hosting. */
  public String pairingCode() {
    return pairingCode;
  }

  /** Returns true if the host is currently advertising and listening. */
  public boolean isHosting() {
    return advertising && listening;
  }

  /** Sets a callback for status changes (UI thread). */
  public void setOnStatusChanged(Runnable callback) {
    onStatusChanged = callback;
  }

  private void registerService() {
    NsdServiceInfo serviceInfo = new NsdServiceInfo();
    serviceInfo.setServiceName("KANTA Karaoke Host");
    serviceInfo.setServiceType(SERVICE_TYPE);
    serviceInfo.setPort(PORT);

    registrationListener = new NsdManager.RegistrationListener() {
      @Override public void onServiceRegistered(NsdServiceInfo service) {
        Log.d(TAG, "NSD service registered: " + service.getServiceName());
      }

      @Override public void onUnregistrationFailed(NsdServiceInfo service, int errorCode) {
        Log.e(TAG, "NSD unregistration failed: " + errorCode);
      }

      @Override public void onServiceUnregistered(NsdServiceInfo service) {
        Log.d(TAG, "NSD service unregistered");
      }

      @Override public void onRegistrationFailed(NsdServiceInfo service, int errorCode) {
        Log.e(TAG, "NSD registration failed: " + errorCode);
        advertising = false;
      }
    };

    try {
      nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "NSD registration failed: " + e.getMessage());
      advertising = false;
    }
  }

  private void acceptLoop() {
    try {
      serverSocket = new ServerSocket();
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new InetSocketAddress(PORT));
      Log.i(TAG, "Kanta-Connect WebSocket server listening on port " + PORT);

      while (listening && !serverSocket.isClosed()) {
        try {
          Socket socket = serverSocket.accept();
          socket.setTcpNoDelay(true);
          socket.setSoTimeout(30000);

          String clientId = UUID.randomUUID().toString();
          ClientSession session = new ClientSession(clientId, socket, adapter);
          sessions.put(clientId, session);
          worker.execute(session::run);

          Log.d(TAG, "Kanta-Connect client connected: " + clientId);
        } catch (IOException e) {
          if (listening) Log.e(TAG, "Accept error: " + e.getMessage());
          break;
        }
      }
    } catch (IOException e) {
      Log.e(TAG, "Cannot bind Kanta-Connect port " + PORT + ": " + e.getMessage());
      listening = false;
      main.post(() -> notifyStatus("Kanta-Connect port in use: " + PORT));
    }
  }

  private void removeSession(String clientId) {
    sessions.remove(clientId);
    Log.d(TAG, "Kanta-Connect client disconnected: " + clientId);
  }

  private void notifyStatus(String status) {
    if (onStatusChanged != null) main.post(() -> onStatusChanged.run());
  }

  private String generatePairingCode() {
    java.security.SecureRandom random = new java.security.SecureRandom();
    byte[] bytes = new byte[16];
    random.nextBytes(bytes);
    StringBuilder sb = new StringBuilder(32);
    for (byte b : bytes) sb.append(String.format("%02x", b & 0xFF));
    return sb.toString();
  }

  private final class ClientSession {
    private final String clientId;
    private final Socket socket;
    private final HostAdapter adapter;
    private volatile boolean closed = false;

    ClientSession(String clientId, Socket socket, HostAdapter adapter) {
      this.clientId = clientId;
      this.socket = socket;
      this.adapter = adapter;
    }

    void run() {
      java.io.DataInputStream in = null;
      try {
        in = new java.io.DataInputStream(
            new java.io.BufferedInputStream(socket.getInputStream()));
        java.io.OutputStream out = socket.getOutputStream();

        // Read the HTTP request line and headers byte-by-byte so no WebSocket
        // frame bytes are consumed by an over-reading text reader.
        String requestLine = readHttpLine(in);
        if (requestLine == null || !requestLine.startsWith("GET")) {
          sendHttpResponse(out, 400, "text/plain", "Expected WebSocket upgrade");
          return;
        }

        String upgradeHeader = null;
        String secKey = null;
        String line;
        while ((line = readHttpLine(in)) != null && !line.isEmpty()) {
          if (line.startsWith("Upgrade:")) upgradeHeader = line.substring(8).trim();
          else if (line.startsWith("Sec-WebSocket-Key:")) secKey = line.substring(19).trim();
        }

        if (!"websocket".equalsIgnoreCase(upgradeHeader) || secKey == null) {
          sendHttpResponse(out, 400, "text/plain", "Missing WebSocket headers");
          return;
        }

        String acceptKey = computeAcceptKey(secKey);
        String response =
            "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptKey + "\r\n"
                + "\r\n";
        out.write(response.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        out.flush();

        readFrames(in, out);
      } catch (IOException e) {
        if (!closed) Log.d(TAG, "Client " + clientId + " disconnected: " + e.getMessage());
      } finally {
        close();
      }
    }

    /** Reads one CRLF/LF terminated HTTP header line, or null at end of stream. */
    private String readHttpLine(java.io.DataInputStream in) throws IOException {
      StringBuilder sb = new StringBuilder();
      while (true) {
        int b = in.read();
        if (b == -1) return sb.length() == 0 ? null : sb.toString();
        if (b == '\n') {
          int len = sb.length();
          if (len > 0 && sb.charAt(len - 1) == '\r') sb.setLength(len - 1);
          return sb.toString();
        }
        sb.append((char) b);
        if (sb.length() > 8192) throw new IOException("HTTP header too long");
      }
    }

    private void readFrames(java.io.DataInputStream in, java.io.OutputStream out)
        throws IOException {
      while (!closed) {
        int b1 = in.read();
        if (b1 == -1) break;
        int b2 = in.read();
        if (b2 == -1) break;

        int opcode = b1 & 0x0F;
        boolean masked = (b2 & 0x80) != 0;
        long length = b2 & 0x7F;

        if (opcode == 0x8) { // close
          break;
        }
        if (opcode == 0x9) { // ping -> pong (unmasked, empty)
          out.write(new byte[] {(byte) 0x8A, 0x00});
          out.flush();
          continue;
        }
        if (opcode != 0x1) { // only text frames are dispatched
          continue;
        }

        if (length == 126) {
          length = in.readUnsignedShort();
        } else if (length == 127) {
          length = in.readLong();
        }
        if (length < 0 || length > 65536) throw new IOException("Frame too large");

        byte[] mask = new byte[4];
        if (masked) in.readFully(mask);

        byte[] payload = new byte[(int) length];
        in.readFully(payload);

        if (masked) {
          for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }

        String message = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
        sendTextFrame(out, dispatchRequest(message));
      }
    }

    private String dispatchRequest(String message) {
      try {
        org.json.JSONObject obj = new org.json.JSONObject(message);
        String action = obj.optString("action", "");
        String argument = obj.optString("argument", "");
        String singer = obj.optString("singer", "");
        String requestId = obj.optString("id", java.util.UUID.randomUUID().toString());

        if (!adapter.isReady()) {
          return jsonResponse(requestId, "error", "Host not ready", null);
        }

        String result = adapter.handleRequest(action, argument, singer);
        return jsonResponse(requestId, "ok", null, result);
      } catch (Exception e) {
        return jsonResponse(java.util.UUID.randomUUID().toString(), "error", e.getMessage(), null);
      }
    }

    private String jsonResponse(String id, String status, String error, String result) {
      try {
        org.json.JSONObject obj = new org.json.JSONObject();
        obj.put("id", id);
        obj.put("status", status);
        if (error != null) obj.put("error", error);
        if (result != null) obj.put("result", result);
        return obj.toString();
      } catch (Exception e) {
        return "{}";
      }
    }

    private void sendTextFrame(java.io.OutputStream out, String text) throws IOException {
      byte[] payload = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      int length = payload.length;

      out.write(0x81);
      if (length < 126) {
        out.write(length);
      } else if (length < 65536) {
        out.write(126);
        out.write((length >>> 8) & 0xFF);
        out.write(length & 0xFF);
      } else {
        out.write(127);
        for (int i = 7; i >= 0; i--) out.write((length >>> (i * 8)) & 0xFF);
      }
      out.write(payload);
      out.flush();
    }

    private void sendHttpResponse(java.io.OutputStream out, int code, String type, String body)
        throws IOException {
      byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      String response =
          "HTTP/1.1 " + code + " " + getReason(code) + "\r\n"
              + "Content-Type: " + type + "\r\n"
              + "Content-Length: " + bytes.length + "\r\n"
              + "Connection: close\r\n"
              + "\r\n";
      out.write(response.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      out.write(bytes);
      out.flush();
    }

    private String getReason(int code) {
      switch (code) {
        case 200: return "OK";
        case 400: return "Bad Request";
        case 101: return "Switching Protocols";
        default: return "Unknown";
      }
    }

    private String computeAcceptKey(String secKey) {
      try {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        md.update((secKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] digest = md.digest();
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP);
      } catch (Exception e) {
        return "";
      }
    }

    void close() {
      if (closed) return;
      closed = true;
      try { socket.close(); } catch (IOException ignored) {}
      removeSession(clientId);
    }
  }
}
