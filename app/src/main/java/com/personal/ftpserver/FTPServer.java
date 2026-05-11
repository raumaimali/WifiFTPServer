package com.personal.ftpserver;

import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Personal WiFi FTP Server
 * Features: Multi-threading, Resume Transfer, Speed Monitor, Password Protection
 */
public class FTPServer {

    private static final String TAG = "FTPServer";
    public static final int DEFAULT_PORT = 2121;
    private static final int BUFFER_SIZE = 65536; // 64KB buffer for speed

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean isRunning = false;

    private String username;
    private String password;
    private String rootPath;
    private int port;

    // Stats
    private AtomicLong totalBytesTransferred = new AtomicLong(0);
    private AtomicInteger activeConnections = new AtomicInteger(0);
    private long startTime;

    // Callback interface
    public interface ServerCallback {
        void onClientConnected(String clientIP);
        void onClientDisconnected(String clientIP);
        void onFileTransfer(String fileName, long bytes, boolean isUpload);
        void onError(String error);
        void onSpeedUpdate(double speedMBps);
    }

    private ServerCallback callback;

    public FTPServer(String rootPath, String username, String password, int port) {
        this.rootPath = rootPath;
        this.username = username;
        this.password = password;
        this.port = port;
        // Thread pool: up to 10 simultaneous connections
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    public void setCallback(ServerCallback callback) {
        this.callback = callback;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        isRunning = true;
        startTime = System.currentTimeMillis();
        totalBytesTransferred.set(0);

        Log.d(TAG, "FTP Server started on port " + port);

        // Accept connections in background
        new Thread(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setSendBufferSize(BUFFER_SIZE);
                    clientSocket.setReceiveBufferSize(BUFFER_SIZE);
                    clientSocket.setTcpNoDelay(true);

                    String clientIP = clientSocket.getInetAddress().getHostAddress();
                    Log.d(TAG, "Client connected: " + clientIP);

                    if (callback != null) callback.onClientConnected(clientIP);
                    activeConnections.incrementAndGet();

                    // Handle each client in separate thread
                    threadPool.submit(new ClientHandler(clientSocket, clientIP));

                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "Accept error: " + e.getMessage());
                        if (callback != null) callback.onError(e.getMessage());
                    }
                }
            }
        }, "FTP-Accept-Thread").start();
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error stopping server: " + e.getMessage());
        }
        threadPool.shutdown();
        Log.d(TAG, "FTP Server stopped");
    }

    public boolean isRunning() { return isRunning; }
    public long getTotalBytes() { return totalBytesTransferred.get(); }
    public int getActiveConnections() { return activeConnections.get(); }

    public double getAverageSpeedMBps() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        if (elapsed == 0) return 0;
        return (totalBytesTransferred.get() / 1024.0 / 1024.0) / elapsed;
    }

    // ─────────────────────────────────────────────
    //  CLIENT HANDLER — handles one FTP connection
    // ─────────────────────────────────────────────
    private class ClientHandler implements Runnable {

        private Socket controlSocket;
        private String clientIP;
        private BufferedReader reader;
        private PrintWriter writer;
        private boolean authenticated = false;
        private String currentDir = "/";
        private String pendingUser = null;
        private ServerSocket pasvSocket;
        private String dataHost;
        private int dataPort;
        private boolean passiveMode = false;
        private long restartOffset = 0; // For resume transfer (REST command)

        ClientHandler(Socket socket, String clientIP) {
            this.controlSocket = socket;
            this.clientIP = clientIP;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
                writer = new PrintWriter(new OutputStreamWriter(controlSocket.getOutputStream()), true);

                sendResponse("220 Personal FTP Server Ready - Welcome!");

                String line;
                while ((line = reader.readLine()) != null && !controlSocket.isClosed()) {
                    handleCommand(line.trim());
                }

            } catch (IOException e) {
                Log.d(TAG, "Client disconnected: " + clientIP);
            } finally {
                cleanup();
            }
        }

        private void handleCommand(String line) {
            if (line.isEmpty()) return;

            String[] parts = line.split(" ", 2);
            String cmd = parts[0].toUpperCase();
            String arg = parts.length > 1 ? parts[1] : "";

            Log.d(TAG, "CMD [" + clientIP + "]: " + cmd + " " + (cmd.equals("PASS") ? "***" : arg));

            switch (cmd) {
                case "USER": handleUSER(arg); break;
                case "PASS": handlePASS(arg); break;
                case "SYST": sendResponse("215 UNIX Type: L8"); break;
                case "FEAT": handleFEAT(); break;
                case "PWD":  handlePWD(); break;
                case "CWD":  handleCWD(arg); break;
                case "CDUP": handleCWD(".."); break;
                case "LIST": handleLIST(arg); break;
                case "NLST": handleNLST(arg); break;
                case "RETR": handleRETR(arg); break;
                case "STOR": handleSTOR(arg); break;
                case "APPE": handleSTOR(arg); break;
                case "DELE": handleDELE(arg); break;
                case "MKD":  handleMKD(arg); break;
                case "RMD":  handleRMD(arg); break;
                case "RNFR": handleRNFR(arg); break;
                case "RNTO": handleRNTO(arg); break;
                case "SIZE": handleSIZE(arg); break;
                case "MDTM": handleMDTM(arg); break;
                case "REST": handleREST(arg); break;
                case "TYPE": sendResponse("200 Type set"); break;
                case "MODE": sendResponse("200 Mode set"); break;
                case "STRU": sendResponse("200 Structure set"); break;
                case "PASV": handlePASV(); break;
                case "PORT": handlePORT(arg); break;
                case "NOOP": sendResponse("200 OK"); break;
                case "QUIT": sendResponse("221 Goodbye!"); cleanup(); break;
                default:     sendResponse("502 Command not implemented: " + cmd);
            }
        }

        // ── Authentication ──
        private void handleUSER(String user) {
            pendingUser = user;
            if (username.isEmpty()) {
                authenticated = true;
                sendResponse("230 Anonymous login OK");
            } else {
                sendResponse("331 Password required");
            }
        }

        private void handlePASS(String pass) {
            if (username.isEmpty() || (pendingUser != null &&
                    pendingUser.equals(username) && pass.equals(password))) {
                authenticated = true;
                sendResponse("230 Login successful");
            } else {
                sendResponse("530 Login incorrect");
            }
        }

        private void checkAuth() {
            if (!authenticated) sendResponse("530 Not logged in");
        }

        // ── Features list ──
        private void handleFEAT() {
            writer.println("211-Features:");
            writer.println(" REST STREAM");
            writer.println(" SIZE");
            writer.println(" MDTM");
            writer.println(" PASV");
            writer.println(" UTF8");
            writer.println("211 End");
        }

        // ── Directory commands ──
        private void handlePWD() {
            if (!authenticated) { checkAuth(); return; }
            sendResponse("257 \"" + currentDir + "\" is current directory");
        }

        private void handleCWD(String path) {
            if (!authenticated) { checkAuth(); return; }
            File dir = resolvePath(path);
            if (dir.exists() && dir.isDirectory()) {
                currentDir = getRelativePath(dir);
                sendResponse("250 Directory changed to " + currentDir);
            } else {
                sendResponse("550 No such directory");
            }
        }

        // ── LIST — directory listing ──
        private void handleLIST(String arg) {
            if (!authenticated) { checkAuth(); return; }
            sendResponse("150 Opening data connection");
            Socket dataSocket = openDataConnection();
            if (dataSocket == null) { sendResponse("425 Can't open data connection"); return; }

            try {
                PrintWriter dataWriter = new PrintWriter(dataSocket.getOutputStream(), true);
                File dir = resolvePath(arg.isEmpty() ? currentDir : arg);
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        Arrays.sort(files, (a, b) -> {
                            if (a.isDirectory() != b.isDirectory())
                                return a.isDirectory() ? -1 : 1;
                            return a.getName().compareToIgnoreCase(b.getName());
                        });
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", Locale.US);
                        for (File f : files) {
                            String perms = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                            String size  = String.format("%12d", f.length());
                            String date  = sdf.format(new Date(f.lastModified()));
                            dataWriter.println(perms + " 1 user group " + size + " " + date + " " + f.getName());
                        }
                    }
                }
                sendResponse("226 Transfer complete");
            } catch (IOException e) {
                sendResponse("550 List failed");
            } finally {
                try { dataSocket.close(); } catch (IOException ignored) {}
                closePasvSocket();
            }
        }

        private void handleNLST(String arg) {
            if (!authenticated) { checkAuth(); return; }
            sendResponse("150 Opening data connection");
            Socket dataSocket = openDataConnection();
            if (dataSocket == null) { sendResponse("425 Can't open data connection"); return; }
            try {
                PrintWriter dw = new PrintWriter(dataSocket.getOutputStream(), true);
                File dir = resolvePath(arg.isEmpty() ? currentDir : arg);
                File[] files = dir.listFiles();
                if (files != null) for (File f : files) dw.println(f.getName());
                sendResponse("226 Transfer complete");
            } catch (IOException e) {
                sendResponse("550 Failed");
            } finally {
                try { dataSocket.close(); } catch (IOException ignored) {}
                closePasvSocket();
            }
        }

        // ── RETR — Download file (with resume) ──
        private void handleRETR(String path) {
            if (!authenticated) { checkAuth(); return; }
            File file = resolvePath(path);
            if (!file.exists() || !file.isFile()) {
                sendResponse("550 File not found");
                return;
            }

            sendResponse("150 Opening data connection for " + file.getName() +
                    " (" + file.length() + " bytes)");
            Socket dataSocket = openDataConnection();
            if (dataSocket == null) { sendResponse("425 Can't open data connection"); return; }

            long bytesSent = 0;
            long speedStart = System.currentTimeMillis();

            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream out = new BufferedOutputStream(dataSocket.getOutputStream(), BUFFER_SIZE)) {

                // Resume support: seek to REST offset
                if (restartOffset > 0) {
                    raf.seek(restartOffset);
                    bytesSent = restartOffset;
                }
                restartOffset = 0;

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = raf.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    bytesSent += bytesRead;
                    totalBytesTransferred.addAndGet(bytesRead);
                }
                out.flush();

                long elapsed = System.currentTimeMillis() - speedStart;
                double speedMBps = elapsed > 0 ? (bytesSent / 1024.0 / 1024.0) / (elapsed / 1000.0) : 0;

                if (callback != null) {
                    callback.onFileTransfer(file.getName(), bytesSent, false);
                    callback.onSpeedUpdate(speedMBps);
                }

                sendResponse("226 Transfer complete - " + String.format("%.1f", speedMBps) + " MB/s");

            } catch (IOException e) {
                sendResponse("426 Transfer aborted: " + e.getMessage());
            } finally {
                try { dataSocket.close(); } catch (IOException ignored) {}
                closePasvSocket();
            }
        }

        // ── STOR — Upload file (with resume/append) ──
        private void handleSTOR(String path) {
            if (!authenticated) { checkAuth(); return; }
            File file = resolvePath(path);

            sendResponse("150 Ready to receive " + file.getName());
            Socket dataSocket = openDataConnection();
            if (dataSocket == null) { sendResponse("425 Can't open data connection"); return; }

            long bytesReceived = 0;
            long speedStart = System.currentTimeMillis();

            try (InputStream in = new BufferedInputStream(dataSocket.getInputStream(), BUFFER_SIZE);
                 RandomAccessFile raf = new RandomAccessFile(file, "rw")) {

                // Resume: append from REST offset
                if (restartOffset > 0) {
                    raf.seek(restartOffset);
                }
                restartOffset = 0;

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    raf.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                    totalBytesTransferred.addAndGet(bytesRead);
                }

                long elapsed = System.currentTimeMillis() - speedStart;
                double speedMBps = elapsed > 0 ? (bytesReceived / 1024.0 / 1024.0) / (elapsed / 1000.0) : 0;

                if (callback != null) {
                    callback.onFileTransfer(file.getName(), bytesReceived, true);
                    callback.onSpeedUpdate(speedMBps);
                }

                sendResponse("226 Transfer complete - " + String.format("%.1f", speedMBps) + " MB/s");

            } catch (IOException e) {
                sendResponse("426 Transfer aborted");
            } finally {
                try { dataSocket.close(); } catch (IOException ignored) {}
                closePasvSocket();
            }
        }

        // ── File operations ──
        private void handleDELE(String path) {
            if (!authenticated) { checkAuth(); return; }
            File file = resolvePath(path);
            if (file.delete()) sendResponse("250 File deleted");
            else sendResponse("550 Delete failed");
        }

        private void handleMKD(String path) {
            if (!authenticated) { checkAuth(); return; }
            File dir = resolvePath(path);
            if (dir.mkdirs()) sendResponse("257 \"" + path + "\" created");
            else sendResponse("550 Create failed");
        }

        private void handleRMD(String path) {
            if (!authenticated) { checkAuth(); return; }
            File dir = resolvePath(path);
            if (deleteRecursive(dir)) sendResponse("250 Directory removed");
            else sendResponse("550 Remove failed");
        }

        private String rnfrPath = null;

        private void handleRNFR(String path) {
            if (!authenticated) { checkAuth(); return; }
            rnfrPath = path;
            sendResponse("350 Ready for RNTO");
        }

        private void handleRNTO(String path) {
            if (!authenticated) { checkAuth(); return; }
            if (rnfrPath == null) { sendResponse("503 RNFR required first"); return; }
            File from = resolvePath(rnfrPath);
            File to   = resolvePath(path);
            if (from.renameTo(to)) sendResponse("250 Renamed successfully");
            else sendResponse("550 Rename failed");
            rnfrPath = null;
        }

        private void handleSIZE(String path) {
            if (!authenticated) { checkAuth(); return; }
            File file = resolvePath(path);
            if (file.exists()) sendResponse("213 " + file.length());
            else sendResponse("550 File not found");
        }

        private void handleMDTM(String path) {
            if (!authenticated) { checkAuth(); return; }
            File file = resolvePath(path);
            if (file.exists()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                sendResponse("213 " + sdf.format(new Date(file.lastModified())));
            } else {
                sendResponse("550 File not found");
            }
        }

        // ── REST — Resume offset ──
        private void handleREST(String arg) {
            try {
                restartOffset = Long.parseLong(arg.trim());
                sendResponse("350 Restarting at " + restartOffset);
            } catch (NumberFormatException e) {
                sendResponse("501 Bad argument");
            }
        }

        // ── PASV — Passive mode ──
        private void handlePASV() {
            if (!authenticated) { checkAuth(); return; }
            try {
                closePasvSocket();
                pasvSocket = new ServerSocket(0);
                pasvSocket.setSoTimeout(30000);

                InetAddress localAddr = controlSocket.getLocalAddress();
                String ip = localAddr.getHostAddress().replace('.', ',');
                int pasvPort = pasvSocket.getLocalPort();
                int p1 = pasvPort / 256;
                int p2 = pasvPort % 256;

                passiveMode = true;
                sendResponse("227 Entering Passive Mode (" + ip + "," + p1 + "," + p2 + ")");

            } catch (IOException e) {
                sendResponse("425 Can't enter passive mode");
            }
        }

        // ── PORT — Active mode ──
        private void handlePORT(String arg) {
            if (!authenticated) { checkAuth(); return; }
            try {
                String[] parts = arg.split(",");
                dataHost = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
                dataPort = Integer.parseInt(parts[4]) * 256 + Integer.parseInt(parts[5]);
                passiveMode = false;
                sendResponse("200 PORT command successful");
            } catch (Exception e) {
                sendResponse("501 Bad PORT argument");
            }
        }

        // ── Data connection ──
        private Socket openDataConnection() {
            try {
                if (passiveMode && pasvSocket != null) {
                    Socket s = pasvSocket.accept();
                    s.setSendBufferSize(BUFFER_SIZE);
                    s.setReceiveBufferSize(BUFFER_SIZE);
                    s.setTcpNoDelay(true);
                    return s;
                } else {
                    Socket s = new Socket(dataHost, dataPort);
                    s.setSendBufferSize(BUFFER_SIZE);
                    s.setReceiveBufferSize(BUFFER_SIZE);
                    s.setTcpNoDelay(true);
                    return s;
                }
            } catch (IOException e) {
                Log.e(TAG, "Data connection failed: " + e.getMessage());
                return null;
            }
        }

        // ── Utilities ──
        private File resolvePath(String path) {
            if (path == null || path.isEmpty()) path = currentDir;
            File base = new File(rootPath);
            File resolved;
            if (path.startsWith("/")) {
                resolved = new File(base, path.substring(1));
            } else {
                resolved = new File(new File(base, currentDir.substring(1)), path);
            }
            try {
                // Security: prevent directory traversal
                String canonical = resolved.getCanonicalPath();
                String rootCanonical = base.getCanonicalPath();
                if (!canonical.startsWith(rootCanonical)) {
                    return base;
                }
                return resolved;
            } catch (IOException e) {
                return base;
            }
        }

        private String getRelativePath(File file) {
            try {
                String rootCanon = new File(rootPath).getCanonicalPath();
                String fileCanon = file.getCanonicalPath();
                if (fileCanon.equals(rootCanon)) return "/";
                String rel = fileCanon.substring(rootCanon.length());
                return rel.replace('\\', '/');
            } catch (IOException e) {
                return "/";
            }
        }

        private boolean deleteRecursive(File file) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) for (File f : files) deleteRecursive(f);
            }
            return file.delete();
        }

        private void sendResponse(String response) {
            if (writer != null) writer.println(response);
        }

        private void closePasvSocket() {
            if (pasvSocket != null && !pasvSocket.isClosed()) {
                try { pasvSocket.close(); } catch (IOException ignored) {}
            }
            pasvSocket = null;
        }

        private void cleanup() {
            closePasvSocket();
            activeConnections.decrementAndGet();
            if (callback != null) callback.onClientDisconnected(clientIP);
            try { controlSocket.close(); } catch (IOException ignored) {}
        }
    }
}
