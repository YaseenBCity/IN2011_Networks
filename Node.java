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
    private Map<String, String> addressStore = new HashMap<>();
    private Map<String, String> dataStore = new HashMap<>();
    private Map<String, String> pendingResponses = new ConcurrentHashMap<>();
    private Random txRandom = new Random();
    private Deque<String> relayStack = new ArrayDeque<>();

    public void setNodeName(String nodeName) throws Exception {
        this.nodeName = nodeName;
        this.hashID = HashID.computeHashID(nodeName);
    }

    public void openPort(int portNumber) throws Exception {
        this.port = portNumber;
        this.socket = new DatagramSocket(portNumber);
        String myIP = InetAddress.getLocalHost().getHostAddress();
        addressStore.put(nodeName, myIP + ":" + portNumber);
    }

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

    // Parse one CRN-encoded string from the start of input, return the value
    // and advance position by the full encoded field length
    private static int[] parseCRNStringLen(String input) {
        // Returns [valueStart, valueEnd, totalFieldLength]
        int firstSpace = input.indexOf(' ');
        if (firstSpace < 0) return null;
        int spaceCount = Integer.parseInt(input.substring(0, firstSpace));
        int start = firstSpace + 1;
        // Find spaceCount+1 more spaces to locate the trailing space
        int spacesFound = 0;
        int pos = start;
        while (pos < input.length()) {
            if (input.charAt(pos) == ' ') {
                spacesFound++;
                if (spacesFound == spaceCount + 1) {
                    // pos is the trailing space
                    return new int[]{start, pos, pos + 1};
                }
            }
            pos++;
        }
        return null;
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
                return;
            }
            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
            handleMessage(message, packet.getAddress(), packet.getPort());
        }
    }

    private void handleMessage(String message, InetAddress senderAddress, int senderPort) throws Exception {
        if (message.length() < 3) return;
        String txID = message.substring(0, 2);
        if (message.charAt(2) != ' ') return;
        String body = message.substring(3);
        if (body.isEmpty()) return;

        char type = body.charAt(0);

        // Responses go into pendingResponses for sendRequest to pick up
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
            case 'I': break;
            default: break;
        }
    }

    private void sendMessage(String message, InetAddress addr, int port) throws Exception {
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        socket.send(packet);
    }

    public static int computeDistance(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int xor = (a[i] & 0xFF) ^ (b[i] & 0xFF);
            if (xor != 0) {
                int leadingZeros = Integer.numberOfLeadingZeros(xor) - 24;
                return (a.length - i) * 8 - leadingZeros;
            }
        }
        return 0;
    }

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

    private byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private void handleName(String txID, InetAddress addr, int port) throws Exception {
        String response = txID + " H " + encodeCRNString(nodeName);
        sendMessage(response, addr, port);
    }

    private void handleNearest(String txID, String body, InetAddress addr, int port) throws Exception {
        String targetHashIDHex = body.substring(2, 66);
        byte[] targetHash = hexToBytes(targetHashIDHex);

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
        // body = "W <encoded key><encoded value>"
        String rest = body.substring(2); // strip "W "

        // Parse key using space-counting approach
        int[] keyBounds = parseCRNStringLen(rest);
        if (keyBounds == null) return;
        String key = rest.substring(keyBounds[0], keyBounds[1]);
        String valueEncoded = rest.substring(keyBounds[2]);
        String value = decodeCRNString(valueEncoded);

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
        // body = "C <encoded key><encoded currentValue><encoded newValue>"
        String rest = body.substring(2);

        int[] keyBounds = parseCRNStringLen(rest);
        if (keyBounds == null) return;
        String key = rest.substring(keyBounds[0], keyBounds[1]);
        rest = rest.substring(keyBounds[2]);

        int[] curBounds = parseCRNStringLen(rest);
        if (curBounds == null) return;
        String currentValue = rest.substring(curBounds[0], curBounds[1]);
        rest = rest.substring(curBounds[2]);

        String newValue = decodeCRNString(rest);

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

    private String generateTxID() {
        byte[] tx = new byte[2];
        do {
            txRandom.nextBytes(tx);
        } while (tx[0] == 0x20 || tx[1] == 0x20);
        return new String(tx, StandardCharsets.ISO_8859_1);
    }

    private String sendRequest(String message, InetAddress addr, int port) throws Exception {
        String txID = message.substring(0, 2);

        if (!relayStack.isEmpty()) {
            String[] relayNodes = relayStack.toArray(new String[0]);
            for (int i = relayNodes.length - 1; i >= 0; i--) {
                message = txID + " V " + encodeCRNString(relayNodes[i]) + message.substring(3);
            }
        }

        int attempts = 0;
        while (attempts < 3) {
            sendMessage(message, addr, port);

            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline) {
                int remaining = (int)(deadline - System.currentTimeMillis());
                if (remaining <= 0) break;
                socket.setSoTimeout(remaining);
                byte[] buf = new byte[65536];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    String incoming = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    handleMessage(incoming, packet.getAddress(), packet.getPort());
                } catch (SocketTimeoutException e) {
                    break;
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
        return null;
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
        return response.startsWith("H");
    }

    private void handleRelay(String txID, String body, InetAddress addr, int port) throws Exception {
        // body = "V <encoded target name><embedded message>"
        String rest = body.substring(2);
        int[] nameBounds = parseCRNStringLen(rest);
        if (nameBounds == null) return;
        String targetName = rest.substring(nameBounds[0], nameBounds[1]);
        String embeddedMessage = rest.substring(nameBounds[2]);

        String targetAddr = addressStore.get(targetName);
        if (targetAddr == null) return;
        String[] parts = targetAddr.split(":");
        InetAddress targetIP = InetAddress.getByName(parts[0]);
        int targetPort = Integer.parseInt(parts[1]);

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
                return decodeCRNString(response.substring(4));
            }
            if (response.startsWith("S N")) return null;
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
                anySuccess = true;
            }
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
            if (response.startsWith("D R")) return true;
            if (response.startsWith("D N")) return false;
        }
        return false;
    }
}
