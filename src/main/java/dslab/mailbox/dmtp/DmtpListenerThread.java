package dslab.mailbox.dmtp;

import dslab.mailbox.ClientCommunicator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of the Thread which listens for new DMTP connections,
 * establishes new Connections and then passes the Communication to the {@link DmtpCommunicationThread}
 * and resumes listening.
 * <p>
 * This Threads Lifespan is as long as the Applications Lifespan
 */
public class DmtpListenerThread extends Thread {
  private final ServerSocket serverSocket;
  private final String users;
  private final String domain;
  private final Log LOG = LogFactory.getLog(DmtpListenerThread.class);
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private boolean stopped = false;

  public DmtpListenerThread(ServerSocket serverSocket, String domain, String userConfig) {
    this.serverSocket = serverSocket;
    this.users = userConfig;
    this.domain = domain;
    Thread.currentThread().setName("Listener Thread");
  }

  public void run() {
    while (!stopped) {
      ClientCommunicator communicator = new ClientCommunicator(serverSocket);
      if (!communicator.establishConnection()) {
        break;
      }
      executor.execute(new DmtpCommunicationThread(communicator, users, domain));
    }
    executor.shutdownNow();
  }

  public void stopThread() {
    close();
    this.stopped = true;
    executor.shutdownNow();
  }


  public void close() {
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (IOException e) {
        LOG.error("Error while closing server socket: " + e.getMessage());
      }
    }
  }
}
