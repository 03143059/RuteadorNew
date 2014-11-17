package dv; /**
 * Neighbor.java -- encapsulates the information about a router.
 * Strictly speaking, this class should not have been necessary, since it
 * duplicates a lot of the information in class "Router".
 *
 * However, due to time constraints this is how the program evolved; a
 * nice project would be to refactor the code for this simulation.
 */

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class Neighbor {
    private InetAddress addr; // this neighbor's IP address
    private int port; // this neighbor's port
    private HashMap<InetAddress, Integer> dv; // this neighbor's row in the network cost table
    private HashMap<InetAddress, InetAddress> next; // "next" node in route
    public static final int INFINITY = 10000000;

    /////////////////////////////////////////////////////////////
    // Constructor:
    /////////////////////////////////////////////////////////////
    public Neighbor(InetAddress addr, int port, HashMap<InetAddress, Integer> dv) {
        this.addr = addr;
        this.port = port;
        this.dv = dv;

        next = new HashMap<InetAddress, InetAddress>();

        for(Map.Entry<InetAddress, Integer> entry : dv.entrySet()) {
            InetAddress host = entry.getKey();
            int costo = entry.getValue();
            if (costo > 0 && costo < INFINITY)
                next.put(host, host);
            else
                next.put(host, null);
        }
    }

    public InetAddress getAddr() {
        return addr;
    }

    public int getPort() {
        return port;
    }

    public HashMap<InetAddress, Integer> getDv() {
        return dv;
    }

    public void setDv(HashMap<InetAddress, Integer> dv) {
        this.dv.clear();
        for(Map.Entry<InetAddress, Integer> entry : dv.entrySet()) {
            InetAddress host = entry.getKey();
            int costo = entry.getValue();
            this.dv.put(host, costo);
        }
    }

    @Override
    public String toString(){
        return addr.getHostAddress();
    }
}
