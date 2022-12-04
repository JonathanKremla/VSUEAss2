package dslab.transfer.dmtp;

import dslab.mailbox.ClientCommunicator;
import dslab.transfer.MessageDistributer;
import dslab.util.Config;

import java.util.Objects;

/**
 * The Thread which handles the DMTP Communication between this Transfer Server and a
 * connected Client. This Thread is only executed vie {@link java.util.concurrent.ExecutorService}
 * therefore it only implements {@link Runnable} not {@link Thread}
 * <p>
 * This is a short-lived Thread, only handling the Communication between one Connected Client and
 * then terminates.
 */
public class DmtpCommunicationThread implements Runnable {

  private final ClientCommunicator communicator;
  private final MessageDistributer messageDistributer = new MessageDistributer();

  //Consumer
  Thread sender = new Thread(() -> {
    Thread.currentThread().setName("senderThread");
    messageDistributer.forward();
  });

  public DmtpCommunicationThread(ClientCommunicator communicator, Config transferConfig) {
    this.communicator = communicator;
    Thread.currentThread().setName("DmtpCommunicationThread");
    messageDistributer.setTransferConfig(transferConfig);
  }

  public void run() {
    //Producer
    DmtpRequestHandler requestHandler = new DmtpRequestHandler(messageDistributer);
    requestHandler.start();
    //Consumer
    sender.start();
    String request;
    communicator.println("ok DMTP");
    communicator.flush();
    // read client requests
    boolean stopped = false;
    while (!stopped && (request = communicator.readLine()) != null && !Objects.equals(request, "quit")) {
      String response;
      response = requestHandler.handleRequest(request);
      communicator.println(response);
      communicator.flush();
    }
    communicator.println("ok bye");
    communicator.flush();
    communicator.close();
  }


}

