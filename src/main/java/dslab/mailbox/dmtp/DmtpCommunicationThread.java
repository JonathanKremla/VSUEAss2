package dslab.mailbox.dmtp;

import dslab.mailbox.ClientCommunicator;

import java.util.Objects;

/**
 * The Thread which handles the DMTP Communication between this Mailbox Server and a
 * connected Client. This Thread is only executed vie {@link java.util.concurrent.ExecutorService}
 * therefore it only implements {@link Runnable} not {@link Thread}
 * <p>
 * This is a short lived Thread, only handling the Communication between one Connected Client and
 * then terminated.
 */
public class DmtpCommunicationThread implements Runnable {

  private final ClientCommunicator communicator;
  private final String users;
  private final String domain;

  public DmtpCommunicationThread(ClientCommunicator communicator, String users, String domain) {
    this.communicator = communicator;
    this.users = users;
    this.domain = domain;
  }

  public void run() {

    DmtpRequestHandler requestHandler = new DmtpRequestHandler(domain, users);
    String request;
    communicator.println("ok DMTP");
    communicator.flush();
    // read client requests
    while ((request = communicator.readLine()) != null && !Objects.equals(request, "quit")) {
      String response = requestHandler.handleRequest(request);
      communicator.println(response);
      communicator.flush();
    }
    communicator.println("ok bye");
    communicator.flush();
    communicator.close();
  }
}
