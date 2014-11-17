/**
 * Created by Werner on 9/10/2014.
 * http://ipsit.bu.edu/sc546/sc546Fall2002/RIP2/RIP/
 */

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForwardingService implements Runnable {
    static DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    protected int serverPort;
    protected ServerSocket serverSocket = null;
    protected boolean isStopped = false;
    protected Thread runningThread = null;
    protected ExecutorService threadPool = Executors.newFixedThreadPool(10);
    protected InetAddress address;

    //region Static methods

    static boolean SendMessage(ForwarderMessage message){
        JOptionPane.showMessageDialog(null, "El mensaje ha sido enviado a " + message.to);
        return false;
    }

    static ForwardingService server = null;

    public static boolean isServerRunning(){
        return server != null && server.isRunning;
    }

    public static void stop() {
        server.stopServer();
        server = null;
    }

    public static void start(InetAddress address, int port) {
        Ruteador.println("Forwarder iniciado en " + address.getHostAddress() + ":" + port);
        server = new ForwardingService(address, port);
        new Thread(server).start();
    }

    //endregion // static methods

    //region instance methods

    public ForwardingService(InetAddress address, int port) {
        this.serverPort = port;
        this.address = address;
    }

    protected boolean isRunning = false;

    private void openServerSocket() {
        try {
            this.serverSocket = new ServerSocket(this.serverPort, 0, address);
            isRunning = true;
        } catch (IOException e) {
            isRunning = false;
            this.serverSocket = null;
            isStopped = true;
            Ruteador.println("[ForwardingService.openServerSocket] Forwarder detenido.");
            throw new RuntimeException("No se puede abrir el puerto " + serverPort, e);
        }
    }

    public void run() {
        synchronized (this) {
            this.runningThread = Thread.currentThread();
        }
        openServerSocket();
        while (!isStopped()) {
            Socket clientSocket = null;
            try {
                clientSocket = this.serverSocket.accept();
            } catch (IOException e) {
                if (isStopped()) {
                    Ruteador.println("[ForwardingService.run] Forwarder detenido.");
                    return;
                }
                throw new RuntimeException(
                        "Error aceptando conexion del cliente", e);
            }
            this.threadPool.execute(
                    new ForwarderWorker(clientSocket,
                            "Thread Pooled Server"));
        }
        this.threadPool.shutdown();
        Ruteador.println("[ForwardingService.run] Forwarder detenido.");
    }


    private synchronized boolean isStopped() {
        return this.isStopped;
    }

    public synchronized void stopServer() {
        this.isStopped = true;
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException("Error deteniendo el servidor", e);
        }
    }

    //endregion // instance methods

    class ForwarderWorker implements Runnable {

        protected Socket clientSocket = null;
        protected String serverText = null;

        public ForwarderWorker(Socket clientSocket, String serverText) {
            this.clientSocket = clientSocket;
            this.serverText = serverText;
            Ruteador.println("[ForwardingService] Conexion abierta desde: " +
                    clientSocket.getRemoteSocketAddress());
        }

        public void run() {
            try {
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();
                long time = System.currentTimeMillis();
                output.write(("HTTP/1.1 200 OK\n\nWorkerRunnable: " +
                        this.serverText + " - " +
                        time +
                        "").getBytes());
                output.close();
                input.close();
                Ruteador.println("[ForwarderWorker.run]: " + time);
            } catch (IOException e) {
                //report exception somewhere.
                e.printStackTrace();
            }
        }
    }
}
