// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  Mohammad Yaseen Barlas
//  240000309
//  Yaseen.Barlas@city.ac.uk


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

interface NodeInterface {

    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */
    
    // Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;

    // Open a UDP port for sending and receiving messages.
    public void openPort(int portNumber) throws Exception;


    /*
     * These methods query and change how the network is used.
     */

    // Handle all incoming messages.
    // If you wait for more than delay miliseconds and
    // there are no new incoming messages return.
    // If delay is zero then wait for an unlimited amount of time.
    public void handleIncomingMessages(int delay) throws Exception;
    
    // Determines if a node can be contacted and is responding correctly.
    // Handles any messages that have arrived.
    public boolean isActive(String nodeName) throws Exception;

    // You need to keep a stack of nodes that are used to relay messages.
    // The base of the stack is the first node to be used as a relay.
    // The first node must relay to the second node and so on.
    
    // Adds a node name to a stack of nodes used to relay all future messages.
    public void pushRelay(String nodeName) throws Exception;

    // Pops the top entry from the stack of nodes used for relaying.
    // No effect if the stack is empty
    public void popRelay() throws Exception;
    

    /*
     * These methods provide access to the basic functionality of
     * CRN-25 network.
     */

    // Checks if there is an entry in the network with the given key.
    // Handles any messages that have arrived.
    public boolean exists(String key) throws Exception;
    
    // Reads the entry stored in the network for key.
    // If there is a value, return it.
    // If there isn't a value, return null.
    // Handles any messages that have arrived.
    public String read(String key) throws Exception;

    // Sets key to be value.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean write(String key, String value) throws Exception;

    // If key is set to currentValue change it to newValue.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean CAS(String key, String currentValue, String newValue) throws Exception;

}
// DO NOT EDIT ends

// Complete this!
public class Node implements NodeInterface {
    
    private String nodeName;
    private byte[] hashID;
    private DatagramSocket socket;
    private int port;
    // Store address pairs: nodeName -> "ip:port"
    private Map<String, String> addressStore = new HashMap<>();
    // Store data pairs: dataKey -> value  
    private Map<String, String> dataStore = new HashMap<>();


    //Need this for pending requests
    private Map<String, String> pendingResponses = new ConcurrentHashMap<>();
    private Random txRandom = new Random();


    private Deque<String> relayStack = new ArrayDeque<>();


    public void setNodeName(String nodeName) throws Exception {
        this.nodeName = nodeName;
        this.hashID = HashID.computeHashID(nodeName);
        //Basically hash the nodename using the HashID class
    }

    public void openPort(int portNumber) throws Exception {
        this.port = portNumber;
        this.socket = new DatagramSocket(portNumber);
        //Store it as a pair 
        String myIP = InetAddress.getLocalHost().getHostAddress();
        addressStore.put(nodeName, myIP + ":" + portNumber);

    }

    //For RFC 2119 Terminology we need to encode and decode strings
    // "Hello World" -> "1 Hello World "
    public static String encodeCRNString(String s) {
        int spaces = 0;
        for (char c : s.toCharArray()) {
            if (c == ' ') spaces++;
        }
        return spaces + " " + s + " ";
    }

    // "1 Hello World " -> "Hello World"
    public static String decodeCRNString(String encoded) {
        if (encoded == null || encoded.length() < 2) return "";
        int firstSpace = encoded.indexOf(' ');
        if (firstSpace < 0 || firstSpace + 1 >= encoded.length()) return "";
        return encoded.substring(firstSpace + 1, encoded.length() - 1);
    }

    public void handleIncomingMessages(int delay) throws Exception {
        if (delay != 0) {
            socket.setSoTimeout(delay);
        }
        
        byte[] buf = new byte[65536];
        
        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                // Timeout expired so then return 
                return;
            }
            
            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            handleMessage(message, packet.getAddress(), packet.getPort());
        }
    }

    private void handleMessage(String message, InetAddress senderAddress, int senderPort) throws Exception {
        if (message.length() < 3) return; // Too short to be valid
        
        // First two bytes are transaction ID, then a space
        String txID = message.substring(0, 2);
        if (message.charAt(2) != ' ') return; // Malformed
        
        String body = message.substring(3); // Everything after "XX "
        
        // If this is a response (H, O, F, S, X, D), store it for the waiting sender
        char type = body.charAt(0);
        if ("HOFSXD".indexOf(type) >= 0) {
            pendingResponses.put(txID, body);
            return;
        }

        switch (type) {
            case 'G': handleName(txID, senderAddress, senderPort); break;
            case 'N': handleNearest(txID, body, senderAddress, senderPort); break;
            case 'E': handleExists(txID, body, senderAddress, senderPort); break;
            case 'R': handleRead(txID, body, senderAddress, senderPort); break;
            case 'W': handleWrite(txID, body, senderAddress, senderPort); break;
            case 'C': handleCAS(txID, body, senderAddress, senderPort); break;
            case 'V': handleRelay(txID, body, senderAddress, senderPort); break;
            case 'I': break; // Info messages, can discard
            // Responses (H, O, F, S, X, D) are handled via pendingRequests map
            default: break; // Unknown type, ignore safely
        }
    }


    //Helper needed to send the actual message
    private void sendMessage(String message, InetAddress addr, int port) throws Exception {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        socket.send(packet);
    }
    
    //Distance Calculation
    public static int computeDistance(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int xor = (a[i] & 0xFF) ^ (b[i] & 0xFF);
            if (xor != 0) {
                int leadingZeros = Integer.numberOfLeadingZeros(xor) - 24;
                return (a.length - i) * 8 - leadingZeros;
            }
        }
        return 0; // Identical
    }


    //One of 3 closest
    private boolean isOneOfThreeClosest(String key) throws Exception {
        byte[] keyHash = HashID.computeHashID(key);
        int myDist = computeDistance(this.hashID, keyHash);
        int closerCount = 0;
        for (String name : addressStore.keySet()) {
            byte[] otherHash = HashID.computeHashID(name);
            if (computeDistance(otherHash, keyHash) < myDist) {
                closerCount++;
                if (closerCount >= 3) return false;
            }
        }
        return true;
    }

    //Hex to byte
    private byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i*2, i*2+2), 16);
        }
        return result;
    }


    
    //All differrent message types
    private void handleName(String txID, InetAddress addr, int port) throws Exception {
        String response = txID + " H " + encodeCRNString(nodeName);
        sendMessage(response, addr, port);
    }

    private void handleNearest(String txID, String body, InetAddress addr, int port) throws Exception {
        // Body is "N " + 64 hex char hashID
        String targetHashIDHex = body.substring(2, 66);
        byte[] targetHash = hexToBytes(targetHashIDHex);
        
        // Sort your known addresses by distance to targetHash, take top 3
        List<Map.Entry<String, String>> sorted = addressStore.entrySet().stream()
            .sorted((a, b) -> {
                try {
                    int distA = computeDistance(HashID.computeHashID(a.getKey()), targetHash);
                    int distB = computeDistance(HashID.computeHashID(b.getKey()), targetHash);
                    return Integer.compare(distA, distB);
                } catch (Exception e) { return 0; }
            })
            .limit(3)
            .collect(Collectors.toList());
        
        StringBuilder response = new StringBuilder(txID + " O ");
        for (Map.Entry<String, String> entry : sorted) {
            response.append(encodeCRNString(entry.getKey()))
                    .append(encodeCRNString(entry.getValue()));
        }
        sendMessage(response.toString(), addr, port);
    }

    private void handleExists(String txID, String body, InetAddress addr, int port) throws Exception {
        String key = decodeCRNString(body.substring(2));
        boolean hasKey = dataStore.containsKey(key) || addressStore.containsKey(key);
        boolean isClose = isOneOfThreeClosest(key);
        
        String code;
        if (hasKey) code = "Y";
        else if (isClose) code = "N";
        else code = "?";
        
        sendMessage(txID + " F " + code, addr, port);
    }

    private void handleRead(String txID, String body, InetAddress addr, int port) throws Exception {
        String key = decodeCRNString(body.substring(2));
        boolean isClose = isOneOfThreeClosest(key);
        
        String value = dataStore.containsKey(key) ? dataStore.get(key) 
                    : addressStore.containsKey(key) ? addressStore.get(key) 
                    : null;
        
        String response;
        if (value != null) response = txID + " S Y " + encodeCRNString(value);
        else if (isClose) response = txID + " S N";
        else response = txID + " S ?";
        
        sendMessage(response, addr, port);
    }

    private void handleWrite(String txID, String body, InetAddress addr, int port) throws Exception {
        String rest = body.substring(2);
        String key = decodeCRNString(rest);
        int keyFieldLen = encodeCRNString(key).length();
        String value = decodeCRNString(rest.substring(keyFieldLen));
        
        boolean hasKey = dataStore.containsKey(key);
        boolean isClose = isOneOfThreeClosest(key);
        
        String response;
        if (hasKey) {
            dataStore.put(key, value);
            response = txID + " X R";
        } else if (isClose) {
            dataStore.put(key, value);
            response = txID + " X A";
        } else {
            response = txID + " X X";
        }
        sendMessage(response, addr, port);
    }

    private synchronized void handleCAS(String txID, String body, InetAddress addr, int port) throws Exception {
        // Parse key, currentValue, newValue
        String rest = body.substring(2);
        String key = decodeCRNString(rest);
        int keyLen = encodeCRNString(key).length();
        rest = rest.substring(keyLen);
        String currentValue = decodeCRNString(rest);
        int curLen = encodeCRNString(currentValue).length();
        String newValue = decodeCRNString(rest.substring(curLen));
        
        boolean hasKey = dataStore.containsKey(key);
        boolean isClose = isOneOfThreeClosest(key);
        
        String response;
        if (hasKey) {
            if (dataStore.get(key).equals(currentValue)) {
                dataStore.put(key, newValue);
                response = txID + " D R";
            } else {
                response = txID + " D N";
            }
        } else if (isClose) {
            response = txID + " D A";
        } else {
            response = txID + " D X";
        }
        sendMessage(response, addr, port);
    }

    //Transaction ID
    private String generateTxID() {
        byte[] tx = new byte[2];
        do {
            txRandom.nextBytes(tx);
        } while (tx[0] == 0x20 || tx[1] == 0x20); // No spaces
        return new String(tx, StandardCharsets.ISO_8859_1);
    }

    private String sendRequest(String message, InetAddress addr, int port) throws Exception {
        String txID = message.substring(0, 2);

        if (!relayStack.isEmpty()) {
            // Wrap the message in a relay for each node in the stack (bottom to top)
            String[] relayNodes = relayStack.toArray(new String[0]);
            // Bottom of stack is first relay, so reverse order
            for (int i = relayNodes.length - 1; i >= 0; i--) {
                message = txID + " V " + encodeCRNString(relayNodes[i]) + message.substring(3);
            }
        }
        
        int attempts = 0;
        while (attempts < 3) {
            sendMessage(message, addr, port);
            
            // Wait up to 5 seconds for response, handling other messages while waiting
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                socket.setSoTimeout((int)(deadline - System.currentTimeMillis()));
                byte[] buf = new byte[65536];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    String incoming = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleMessage(incoming, packet.getAddress(), packet.getPort());
                } catch (SocketTimeoutException e) {
                    break; // Inner timeout, try checking pendingResponses
                }
                if (pendingResponses.containsKey(txID)) {
                    return pendingResponses.remove(txID);
                }
            }
            
            if (pendingResponses.containsKey(txID)) {
                return pendingResponses.remove(txID);
            }
            attempts++;
        }
        return null; // No response after 3 attempts
    }

    private InetAddress[] resolveNode(String nodeName) throws Exception {
        String addr = addressStore.get(nodeName);
        if (addr == null) return null;
        String[] parts = addr.split(":");
        return new InetAddress[]{InetAddress.getByName(parts[0]), 
                                null}; // port handled separately
    }

    private int resolvePort(String nodeName) {
        String addr = addressStore.get(nodeName);
        if (addr == null) return -1;
        return Integer.parseInt(addr.split(":")[1]);
    }


    private List<String> getClosestNodes(byte[] targetHash, int count) throws Exception {
        return addressStore.keySet().stream()
            .sorted((a, b) -> {
                try {
                    int dA = computeDistance(HashID.computeHashID(a), targetHash);
                    int dB = computeDistance(HashID.computeHashID(b), targetHash);
                    return Integer.compare(dA, dB);
                } catch (Exception e) { return 0; }
            })
            .limit(count)
            .collect(Collectors.toList());
    }

    public boolean isActive(String nodeName) throws Exception {
        String addr = addressStore.get(nodeName);
        if (addr == null) return false;
        String[] parts = addr.split(":");
        InetAddress ip = InetAddress.getByName(parts[0]);
        int port = Integer.parseInt(parts[1]);
        
        String txID = generateTxID();
        String request = txID + " G";
        String response = sendRequest(request, ip, port);
        
        if (response == null) return false;
        // Response body should be "H <encoded node name>"
        return response.startsWith("H");
    }
    
    private void handleRelay(String txID, String body, InetAddress addr, int port) throws Exception {
        // body is "V <encoded node name><embedded message>"
        String rest = body.substring(2);
        String targetName = decodeCRNString(rest);
        int nameFieldLen = encodeCRNString(targetName).length();
        String embeddedMessage = rest.substring(nameFieldLen);

        String targetAddr = addressStore.get(targetName);
        if (targetAddr == null) return;
        String[] parts = targetAddr.split(":");
        InetAddress targetIP = InetAddress.getByName(parts[0]);
        int targetPort = Integer.parseInt(parts[1]);

        // Forward embedded message, get response, return to original sender
        String response = sendRequest(txID + " " + embeddedMessage, targetIP, targetPort);
        if (response != null) {
            sendMessage(txID + " " + response, addr, port);
        }
    }

    public void pushRelay(String nodeName) throws Exception {
        relayStack.push(nodeName);
    }

    public void popRelay() throws Exception {
        if (!relayStack.isEmpty()) {
            relayStack.pop();
        }
    }

    public boolean exists(String key) throws Exception {
        byte[] keyHash = HashID.computeHashID(key);
        List<String> closest = getClosestNodes(keyHash, 3);
        
        for (String node : closest) {
            String addr = addressStore.get(node);
            if (addr == null) continue;
            String[] parts = addr.split(":");
            InetAddress ip = InetAddress.getByName(parts[0]);
            int port = Integer.parseInt(parts[1]);
            
            String txID = generateTxID();
            String request = txID + " E " + encodeCRNString(key);
            String response = sendRequest(request, ip, port);
            
            if (response == null) continue;
            if (response.startsWith("F Y")) return true;
            if (response.startsWith("F N")) return false;
            // "F ?" means this node isn't close enough, try next
        }
        return false;
    }
    
    public String read(String key) throws Exception {
        byte[] keyHash = HashID.computeHashID(key);
        List<String> closest = getClosestNodes(keyHash, 3);
        
        for (String node : closest) {
            String addr = addressStore.get(node);
            if (addr == null) continue;
            String[] parts = addr.split(":");
            InetAddress ip = InetAddress.getByName(parts[0]);
            int port = Integer.parseInt(parts[1]);
            
            String txID = generateTxID();
            String request = txID + " R " + encodeCRNString(key);
            String response = sendRequest(request, ip, port);
            
            if (response == null) continue;
            if (response.startsWith("S Y ")) {
                // Value follows after "S Y "
                return decodeCRNString(response.substring(4));
            }
            if (response.startsWith("S N")) return null;
            // "S ?" try next node
        }
        return null;
    }

    public boolean write(String key, String value) throws Exception {
        byte[] keyHash = HashID.computeHashID(key);
        List<String> closest = getClosestNodes(keyHash, 3);
        
        boolean anySuccess = false;
        for (String node : closest) {
            String addr = addressStore.get(node);
            if (addr == null) continue;
            String[] parts = addr.split(":");
            InetAddress ip = InetAddress.getByName(parts[0]);
            int port = Integer.parseInt(parts[1]);
            
            String txID = generateTxID();
            String request = txID + " W " + encodeCRNString(key) + encodeCRNString(value);
            String response = sendRequest(request, ip, port);
            
            if (response == null) continue;
            if (response.startsWith("X R") || response.startsWith("X A")) {
                anySuccess = true; // Accepted (replace or add)
            }
            // "X X" means rejected, try next
        }
        return anySuccess;
    }

    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        byte[] keyHash = HashID.computeHashID(key);
        List<String> closest = getClosestNodes(keyHash, 3);
        
        for (String node : closest) {
            String addr = addressStore.get(node);
            if (addr == null) continue;
            String[] parts = addr.split(":");
            InetAddress ip = InetAddress.getByName(parts[0]);
            int port = Integer.parseInt(parts[1]);
            
            String txID = generateTxID();
            String request = txID + " C " + encodeCRNString(key) 
                        + encodeCRNString(currentValue) 
                        + encodeCRNString(newValue);
            String response = sendRequest(request, ip, port);
            
            if (response == null) continue;
            if (response.startsWith("D R")) return true;  // Swapped
            if (response.startsWith("D N")) return false; // Compare failed
            // "D A" or "D X" try next
        }
        return false;
    }
}
