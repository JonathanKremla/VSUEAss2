package dslab.monitoring;

import dslab.ComponentFactory;
import dslab.monitoring.udp.UdpListenerThread;
import dslab.shell.IShell;
import dslab.util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.util.List;

public class MonitoringServer implements IMonitoringServer {

  private final InputStream in;
  private final PrintStream out;
  private final Config config;
  DatagramSocket datagramSocket;
  private UdpListenerThread udpListenerThread;

  /**
   * Creates a new server instance.
   *
   * @param componentId the id of the component that corresponds to the Config resource
   * @param config      the component config
   * @param in          the input stream to read console input from
   * @param out         the output stream to write console output to
   */
  public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
    this.in = in;
    this.out = out;
    this.config = config;
  }

  public static void main(String[] args) throws Exception {
    IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
    server.run();
  }

  @Override
  public void run() {
    try {
      // constructs a datagram socket and binds it to the specified port
      datagramSocket = new DatagramSocket(config.getInt("udp.port"));

      // create a new thread to listen for incoming packets
      udpListenerThread = new UdpListenerThread(datagramSocket);
      udpListenerThread.start();
    } catch (IOException e) {
      throw new RuntimeException("Cannot listen on UDP port.", e);
    }


    try {
      IShell shell = ComponentFactory.createMonitoringShell(this, "shell-monitor", in, out);
      shell.run();
    } catch (Exception e) {
      e.printStackTrace();
      shutdown();
    }
    shutdown();
  }

  @Override
  public void addresses() {
    List<String> addresses = UsageStaticsticsStorage.addresses();
    for (String address : addresses) {
      out.println(address);
    }
  }

  @Override
  public void servers() {
    List<String> servers = UsageStaticsticsStorage.servers();
    for (String server : servers) {
      out.println(server);
    }
  }

  @Override
  public void shutdown() {
    if (!datagramSocket.isClosed()) {
      datagramSocket.close();
    }
    udpListenerThread.stopThread();
    UsageStaticsticsStorage.clear();
  }

}
