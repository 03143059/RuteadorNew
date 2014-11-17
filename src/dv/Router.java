package dv;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Router implements Runnable {
    protected InetAddress addr; // this neighbor's IP address
    protected int port; // this neighbor's port
    protected HashMap<InetAddress, Integer> dv; // this neighbor's row in the network cost table
    protected HashMap<InetAddress, InetAddress> next; // "next" node in route
    protected ArrayList<NbrCostPair> nbrList;
    protected Neighbor myself;
    protected static final int INFINITY = 10000000;
    protected static Random rng;

    public static DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    protected ServerSocket serverSocket = null;
    protected boolean isStopped = false;
    protected Thread runningThread = null;
    protected ExecutorService threadPool = Executors.newFixedThreadPool(10);

    protected static Router server = null;
    protected boolean isRunning = false;

    // Reflection objects to invoke Ruteador on default package
    static Object ruteador;
    static Method log;

    static {
        try {
            Class<?> Ruteador = Class.forName("Ruteador");
            log = Ruteador.getMethod("println", new Class[] { String.class });
            ruteador = Ruteador.newInstance();
        } catch (Exception e) {

        }
    }

    public static boolean isServerRunning(){
        return server != null && server.isRunning;
    }

    public static void stop() {
        server.stopServer();
        server = null;
    }

    public static void start(InetAddress address, int port,
                             ArrayList<NbrCostPair> nbrList) {
        server = new Router(address, port, nbrList);
        new Thread(server).start();
    }


    /**
     * Constructor
     * @param addr this host's IP address
     * @param port this host's port
     * @param nbrList list of neighbors
     */
    public Router(InetAddress addr, int port,
                  ArrayList<NbrCostPair> nbrList) {
        this.addr = addr;
        this.port = port;
        this.nbrList = nbrList;
        dv = new HashMap<InetAddress, Integer>();
        next = new HashMap<InetAddress, InetAddress>();
        rng = new Random();
    }

    /**
     * Using reflection to print a message
     * @param s string to print
     */
    protected static void println(String s){
        try {
            log.invoke(ruteador, Router.df.format(new Date())+ " " +s);
            System.out.println(Router.df.format(new Date())+ " " + s);
        } catch (Exception e) {

        }
    }

    private void openServerSocket() {
        try {
            this.serverSocket = new ServerSocket(this.port, 0, addr);
            isRunning = true;
        } catch (IOException e) {
            isRunning = false;
            this.serverSocket = null;
            isStopped = true;
            println("[Router.openServerSocket] Router detenido.");

            throw new RuntimeException("No se puede abrir el puerto " + this.port, e);
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            this.runningThread = Thread.currentThread();
        }

        openServerSocket();

        println("[Router.run] Router iniciado en " + addr.getHostAddress() + ":" + port);

        /////////////////////////////////////////////
        // Initialize the router's "distance" and "next router" vectors:
        /////////////////////////////////////////////

        println("[Router.run] Inicializando tabla de vector distancia.");

        // First, initialize distance vector to all "infinity" and
        // next router vector to "none" (I use an invalid number -1 for this):

        // Now modify distance vector with information about immediate neighbors.
        // The "next router" entry is just the neighbor's id.
        for (NbrCostPair ncp : nbrList) {
            println(String.format("[Router.run] Host: %s, Costo: %d, Ruta: %s", ncp.getNbr().getAddr().getHostAddress(), ncp.getCost(), ncp.getNbr().getAddr().getHostAddress()));
            dv.put(ncp.getNbr().getAddr(), ncp.getCost());
            next.put(ncp.getNbr().getAddr(), ncp.getNbr().getAddr());
        }

        /////////////////////////////////////////////
        // For consistency I must create a "Neighbor" object
        // for myself. (See header comments about refactoring code.)
        /////////////////////////////////////////////
        myself = new Neighbor(addr, port, dv);

        println("[Router.run] Enviando tabla de vector distancia a todos los vecinos.");

        // Send distance vector to all neighbors:
        distribute();

        while (!isStopped()) {
            Socket clientSocket = null;
            try {
                clientSocket = this.serverSocket.accept();
            } catch (IOException e) {
                if (isStopped()) {
                    println("[Router.run] Router detenido.");
                    return;
                }
                throw new RuntimeException(
                        "Error aceptando conexion del cliente", e);
            }
            this.threadPool.execute(
                    new RouterWorker(clientSocket, this));
        }
        this.threadPool.shutdown();
        println("[Router.run] Router detenido.");
    }


    private synchronized boolean isStopped() {
        return this.isStopped;
    }

    public synchronized void stopServer() {
        this.isStopped = true;
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException("Error deteniendo el server", e);
        }
    }


    // CONVENIENT UTILITY PROGRAM TO PRINT A DISTANCE VECTOR:
    public void printDv(InetAddress fromId, HashMap<InetAddress, Integer> dv) {
        System.out.println("[Router.run] Desde vecino " + fromId.getHostAddress() + ":");
        for(Map.Entry<InetAddress, Integer> entry : dv.entrySet()) {
            InetAddress host = entry.getKey();
            int costo = entry.getValue();
            Router.println("[Router.printDv] " + host.getHostAddress() + ":" + costo);
        }
    }

    /**
     * Este metodo ejecuta una instancia de broadcaster el cual debe usar conexiones existentes o bien
     * crear una nueva con cada vecino y dejarla abierta
     */
    public void distribute() {
        for (NbrCostPair ncp : nbrList) {
            Broadcaster br = new Broadcaster(myself, ncp.getNbr());
            Thread t = new Thread(br);
            t.start();
        }
    }

} //end-class Router

class RouterWorker implements Runnable {

    protected Socket connect = null;
    Router router; // parent router instance

    public RouterWorker(Socket clientSocket, Router router) {
        this.connect = clientSocket;
        Router.println("[RouterWorker] Conexion iniciada desde: " +
                connect.getInetAddress().getHostAddress());
        this.router = router;
    }
    /////////////////////////////////////////////
    // In lieu of a "main" method, I called this "run".
    // However, this class does not implement "Runnable",
    // so method "run" is invoked just like any other method.
    /////////////////////////////////////////////
    public void run() {

        //// Socket is open, read the distance vector
        //// sender is "fromId" and its distance vector is "fromdv"
        BufferedReader in = null;

        try {
           connect.setSoTimeout(0); // infinite timeout = keep alive
            //get character input stream from client
            in = new BufferedReader(new InputStreamReader(connect.getInputStream()));

            //get From:<Name Router>
            String line = in.readLine();
            //tokenizer From
            StringTokenizer st = new StringTokenizer(line,":");
            //ignore "From"
            st.nextToken();
            //get name of Router
            String routerName = st.nextToken();

            //get "Type:<type>"
            line = in.readLine();
            //tokenizer Type
            st = new StringTokenizer(line,":");
            //ignore "Type"
            st.nextToken();
            //get type of message
            String msgType = st.nextToken();

            if(msgType.equals("HELLO")){
                Router.println("[RouterWorker.run] HELLO from " + routerName);
                String message = "From:" + router.addr.getHostAddress() + "\nType:WELCOME\n";
                DataOutputStream outToClient = new DataOutputStream(connect.getOutputStream());
                outToClient.writeBytes(message + '\n');
                outToClient.flush();
               // outToClient.close();
            } else {
                throw new Exception("Tipo de mensaje invalido");
            }

            while (true){
                // recibir más rutas
                InetAddress fromId = this.connect.getInetAddress();
                HashMap<InetAddress, Integer> fromdv = new HashMap<InetAddress, Integer>();

                //get From:<Name Router>
                line = in.readLine();
                //tokenizer From
                st = new StringTokenizer(line,":");
                //ignore "From"
                st.nextToken();
                //get name of Router
                routerName = st.nextToken();

                //get "Type:<type>"
                line = in.readLine();
                //tokenizer Type
                st = new StringTokenizer(line,":");
                //ignore "Type"
                st.nextToken();
                //get type of message
                msgType = st.nextToken();

                if (!msgType.equals("DV"))
                    throw new Exception("Tipo de mensaje invalido");

                //get "Len:<leb>"
                line = in.readLine();
                //tokenizer Type
                st = new StringTokenizer(line,":");
                //ignore "Len"
                st.nextToken();
                //get type of message
                int len = Integer.parseInt(st.nextToken());
                //for to save distanceVectorTable
                for (int i = 1; i <= len; i++) {
                    //get first line of request from client
                    String input = in.readLine();
                    if (input == null) throw new Exception("Solicitud invalida");
                    StringTokenizer parse = new StringTokenizer(input, ":");
                    String peerName = parse.nextToken();
                    int dv = Integer.parseInt(parse.nextToken());

                    //update table
                    fromdv.put(InetAddress.getByName(peerName), dv);

                }

                // FOR DEBUGGING:
                router.printDv(fromId, fromdv);

                boolean change = false;

                ////// Update my own distance vector and routing table:
                for(Map.Entry<InetAddress, Integer> entry : fromdv.entrySet()) {
                    InetAddress host = entry.getKey();
                    int costo = entry.getValue();
                    if (!router.dv.containsKey(host)){
                        change = true;
                        router.dv.put(host, costo);
                    } else {
                        int costoActual = router.dv.get(host);
                        if  (costoActual != costo){
                            change = true;
                            router.dv.replace(host, costoActual);
                        }
                    }
                }

                if (change) {
                    ///// DISTRIBUTE THE UPDATED VECTOR TO ALL NEIGHBORS
                    router.distribute();
                }

            } // end-while


        } catch (SocketTimeoutException ste) {
            router.println("[RouterWorker.run] Tiempo de espera de conexion agotado");
        } catch (Exception ioe) {
            router.println("[RouterWorker.run] Error de servidor: " + ioe);
            ioe.printStackTrace();
        } finally {
            try {
                in.close(); //close character input stream
                connect.close(); //close socket connection
            } catch (Exception e) {
                router.println("[RouterWorker.run] Error cerrando conexion: " + e);
            }
            router.println("[RouterWorker.run] Conexion cerrada.");
        }

    }

}
