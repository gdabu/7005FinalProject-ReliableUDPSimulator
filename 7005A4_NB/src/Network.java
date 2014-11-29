import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

/**
 *
 * @author geoffdabu
 */
class PacketRelayer implements Runnable {

    private InetAddress remoteReceiverAddress;
    private int remoteReceiverPort;

    private DatagramSocket localReceiveSocket;
    private DatagramSocket localTransmitSocket;

    private int dropPercentage = 0;

    private static int totalDropped = 0;

    public PacketRelayer(DatagramSocket localReceiveSocket,
            DatagramSocket localTransmitSocket,
            InetAddress remoteReceiverAddress,
            int remoteReceiverPort,
            int dropPercentage) {
        this.localReceiveSocket = localReceiveSocket;
        this.localTransmitSocket = localTransmitSocket;
        this.remoteReceiverAddress = remoteReceiverAddress;
        this.remoteReceiverPort = remoteReceiverPort;
        this.dropPercentage = dropPercentage;
    }

    public void run() {
        try {
            Random rand = new Random();
            byte[] transmitByteArray;
            int randomNumber = 0;

            while (true) {
                transmitByteArray = new byte[1024];

                DatagramPacket transmitPacket = new DatagramPacket(transmitByteArray, transmitByteArray.length);

                // Wait for packet from sender
                localReceiveSocket.receive(transmitPacket);
                System.out.println("> Receiving from: " + transmitPacket.getAddress() + "/" + transmitPacket.getPort());

                // DROP STATE
                // Packets are dropped if the random generated number is less than or equal to
                // the user specified drop rate
                randomNumber = rand.nextInt(100) + 1;

                if (randomNumber <= dropPercentage) {
                    ReliableUDPHeader droppedPacket = (ReliableUDPHeader) ReliableUDPHelper.extractObjectFromPacket(transmitPacket);
                    System.out.println("\n> Packet Dropped: Type=" + droppedPacket.getPacketType() + ", SeqNum="
                            + droppedPacket.getSeqNum() + ", AckNum=" + droppedPacket.getAckNum());
                    System.out.println(" > totalPacketsDropped=" + ++totalDropped + "\n");
                    continue;
                }
                
                Thread.sleep(500);
                
                
                // SEND STATE
                // change packet address from receiver to destination address
                transmitPacket.setAddress(remoteReceiverAddress);
                transmitPacket.setPort(remoteReceiverPort);

                // Send packet to receiver
                localTransmitSocket.send(transmitPacket);
                System.out.println(">Sending to: " + transmitPacket.getAddress() + "/" + transmitPacket.getPort());
                
            }
        } catch (Exception e) {

        }
    }
}

public class Network {

    private static InetAddress clientAddress;
    private static InetAddress serverAddress;
    private static final int clientRemotePort = 7004;
    private static final int serverRemotePort = 7007;
    private static final int clientPort = 7005;
    private static final int serverPort = 7006;

    private static DatagramSocket clientSocket;
    private static DatagramSocket serverSocket;

    private static int packetDropPercentage = 0;

    public static void main(String args[]) throws Exception {

        packetDropPercentage = Integer.parseInt(args[0]);

        //initialize (remote) client and server addresses 
        clientAddress = InetAddress.getByName("localhost");
        serverAddress = InetAddress.getByName("localhost");

        //initialize local sockets for client and server transmission
        clientSocket = new DatagramSocket(clientPort);
        serverSocket = new DatagramSocket(serverPort);

        System.out.println("> Initialize Network");
        System.out.println("> Approximate Drop Rate: " + packetDropPercentage + "%\n");

        //start client to server relay thread
        PacketRelayer sendToServer = new PacketRelayer(clientSocket, serverSocket, serverAddress, serverRemotePort, packetDropPercentage);
        Thread sendToServerRelayThread = new Thread(sendToServer);
        sendToServerRelayThread.start();

        //start server to client relay thread
        PacketRelayer sendToClient = new PacketRelayer(serverSocket, clientSocket, clientAddress, clientRemotePort, packetDropPercentage);
        Thread sendToClientRelayThread = new Thread(sendToClient);
        sendToClientRelayThread.start();

        //Wait for both  relay threads to end
        sendToServerRelayThread.join();
        sendToClientRelayThread.join();

        System.out.print("> Ending Network");
    }
}
