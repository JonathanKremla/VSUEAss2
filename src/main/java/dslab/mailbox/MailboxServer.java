package dslab.mailbox;

import dslab.ComponentFactory;
import dslab.mailbox.dmap.DmapListenerThread;
import dslab.mailbox.dmtp.DmtpListenerThread;
import dslab.nameserver.AlreadyRegisteredException;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.shell.IShell;
import dslab.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class MailboxServer implements IMailboxServer, Runnable {

  private static final Log LOG = LogFactory.getLog(MailboxServer.class);
  private final InputStream in;
  private final PrintStream out;
  private final String domain;
  private final int tcpDmapPort;
  private final int tcpDmtpPort;
  private final String users;
  private final String registryHost;
  private final String registryPort;
  private final String rootId;
  private DmapListenerThread dmapListenerThread;
  private DmtpListenerThread dmtpListenerThread;

  /**
   * Creates a new server instance.
   *
   * @param componentId the id of the component that corresponds to the Config resource
   * @param config      the component config
   * @param in          the input stream to read console input from
   * @param out         the output stream to write console output to
   */
  public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
    this.in = in;
    this.out = out;
    domain = config.getString("domain");
    users = config.getString("users.config");
    tcpDmapPort = config.getInt("dmap.tcp.port");
    tcpDmtpPort = config.getInt("dmtp.tcp.port");
    registryHost = config.getString("registry.host");
    registryPort = config.getString("registry.port");
    rootId = config.getString("root_id");
  }

  public static void main(String[] args) throws Exception {
    IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
    server.run();
  }

  @Override
  public void run() {
    MessageStorage.loadUsers(new Config(users));
    createDmapListenerThread();
    createDmtpListenerThread();
    LOG.info("Server is up!");

    try {
      IShell shell = ComponentFactory.createBasicShell("shell-mailbox", in, out);
      shell.run();
    } catch (Exception e) {
      e.printStackTrace();
      shutdown();
    }
    shutdown();
  }

  private void register() {
    try {
      Registry registry = LocateRegistry.getRegistry(registryHost, Integer.parseInt(registryPort));
      INameserverRemote remote = (INameserverRemote) registry.lookup(rootId);
      remote.registerMailboxServer(domain, "localhost:" + tcpDmtpPort);
    } catch (RemoteException | NotBoundException | AlreadyRegisteredException | InvalidDomainException e) {
      e.printStackTrace();
    }

  }

  @Override
  public void shutdown() {
    dmapListenerThread.stopThread();
    dmtpListenerThread.stopThread();
  }

  private void createDmapListenerThread() {
    try {
      ServerSocket dmapSocket = new ServerSocket(tcpDmapPort);
      dmapListenerThread = new DmapListenerThread(dmapSocket, users);
      dmapListenerThread.start();
    } catch (IOException e) {
      LOG.error(e.getMessage());
      shutdown();
    }
  }

  private void createDmtpListenerThread() {
    try {
      ServerSocket dmtpSocket = new ServerSocket(tcpDmtpPort);
      LOG.info(dmtpSocket.getLocalSocketAddress());
      register();
      dmtpListenerThread = new DmtpListenerThread(dmtpSocket, domain, users);
      dmtpListenerThread.start();
    } catch (IOException e) {
      LOG.error(e.getMessage());
      shutdown();
    }
  }
}
