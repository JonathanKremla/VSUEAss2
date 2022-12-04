package dslab.mailbox.dmap;

import dslab.mailbox.ClientCommunicator;

import java.util.List;
import java.util.Objects;

/**
 * The Thread which handles the DMAP Communication between this Mailbox Server and a
 * connected Client. This Thread is only executed vie {@link java.util.concurrent.ExecutorService}
 * therefore it only implements {@link Runnable} not {@link Thread}
 * <p>
 * This is a short lived Thread, only handling the Communication between one Connected Client and
 * then terminated.
 */
public class DmapCommunicationThread implements Runnable {

  private ClientCommunicator communicator;
  private String users;

  public DmapCommunicationThread(ClientCommunicator communicator, String users) {
    this.communicator = communicator;
    this.users = users;
  }

  public void run() {
    DmapRequestHandler dmapRequestHandler = new DmapRequestHandler(users);
    String request;
    communicator.println("ok DMAP");
    communicator.flush();
    // read client requests
    while ((request = communicator.readLine()) != null && !Objects.equals(request, "quit")) {
      List<String> responses = dmapRequestHandler.handle(request);
      for (String response : responses) {
        communicator.println(response);
      }
      communicator.flush();
    }
    communicator.println("ok bye");
    communicator.flush();
    communicator.close();
  }
}
