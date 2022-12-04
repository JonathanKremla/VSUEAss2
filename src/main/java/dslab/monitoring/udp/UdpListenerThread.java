package dslab.monitoring.udp;

import dslab.monitoring.UsageStaticsticsStorage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * The Thread which handles the UDP Packages between this Monitoring Server and a
 * Client (Transfer Server).
 * <p>
 * This Threads Lifespan is as long as the Applications Lifespan
 */
public class UdpListenerThread extends Thread {
  private final DatagramSocket datagramSocket;
  private final Log LOG = LogFactory.getLog(UdpListenerThread.class);
  private boolean stopped = false;

  public UdpListenerThread(DatagramSocket datagramSocket) {
    this.datagramSocket = datagramSocket;
  }

  public void run() {

    byte[] buffer;
    DatagramPacket packet;
    try {
      while (!stopped) {
        buffer = new byte[1024];
        // create a datagram packet of specified length (buffer.length)
        /*
         * Keep in mind that, in UDP, packet delivery is not guaranteed,
         * and the order of the delivery/processing is also not guaranteed.
         */
        packet = new DatagramPacket(buffer, buffer.length);

        // wait for incoming packets from client
        datagramSocket.receive(packet);
        // get the data from the packet
        String request = new String(packet.getData()).trim();

        LOG.info("Received request-packet from client: " + request);

        if (!UsageStaticsticsStorage.add(request)) {
          LOG.info("Nothing saved request is invalid format");
        }
      }

    } catch (SocketException e) {
      // when the socket is closed, the send or receive methods of the DatagramSocket will throw a SocketException
      LOG.info("SocketException while waiting for/handling packets: " + e.getMessage());
      return;
    } catch (IOException e) {
      // other exceptions should be handled correctly in your implementation
      throw new UncheckedIOException(e);
    } finally {
      if (datagramSocket != null && !datagramSocket.isClosed()) {
        datagramSocket.close();
      }
    }

  }


  public void stopThread() {
    this.stopped = true;
    if (!datagramSocket.isClosed()) {
      datagramSocket.close();
    }
  }
}
