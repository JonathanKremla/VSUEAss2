package dslab.transfer;

import dslab.ComponentFactory;
import dslab.shell.IShell;
import dslab.transfer.dmtp.DmtpListenerThread;
import dslab.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;

public class TransferServer implements ITransferServer, Runnable {

  private final int tcpDmtpPort;
  private final InputStream in;
  private final PrintStream out;
  private final Config transferConfig;
  private final Log LOG = LogFactory.getLog(TransferServer.class);
  private ServerSocket dmtpSocket;
  private DmtpListenerThread dmtpListenerThread;

  /**
   * Creates a new server instance.
   *
   * @param componentId the id of the component that corresponds to the Config resource
   * @param config      the component config
   * @param in          the input stream to read console input from
   * @param out         the output stream to write console output to
   */
  public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
    this.in = in;
    this.out = out;
    this.transferConfig = config;
    tcpDmtpPort = transferConfig.getInt("tcp.port");
  }

  public static void main(String[] args) throws Exception {
    ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
    server.run();
  }

  @Override
  public void run() {
    createDmtpListenerThread();
    LOG.info("Server is up!");

    try {
      IShell shell = ComponentFactory.createBasicShell("shell-transfer", in, out);
      shell.run();
    } catch (Exception e) {
      e.printStackTrace();
      shutdown();
    }
    shutdown();
  }

  @Override
  public void shutdown() {
    try {
      dmtpSocket.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    dmtpListenerThread.stopThread();
  }

  public void createDmtpListenerThread() {
    try {
      dmtpSocket = new ServerSocket(tcpDmtpPort);
      dmtpListenerThread = new DmtpListenerThread(dmtpSocket, transferConfig);
      dmtpListenerThread.start();
    } catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }

}
