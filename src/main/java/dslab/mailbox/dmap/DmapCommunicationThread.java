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
    private final String componentId;
    private boolean startSecureError = false;

    public DmapCommunicationThread(ClientCommunicator communicator, String users, String componentId) {
        this.communicator = communicator;
        this.users = users;
        this.componentId = componentId;
    }

    public void run() {
        DmapRequestHandler dmapRequestHandler = new DmapRequestHandler(users, componentId);
        String request;
        communicator.println("ok DMAP2.0");
        communicator.flush();
        // read client requests
        while (!startSecureError && (request = communicator.readLine()) != null && !Objects.equals(request, "quit")) {
            List<String> responses = dmapRequestHandler.handle(request);
            for (String response : responses) {
                if (response.equals("error during startsecure")) {
                    System.err.println("Error during startSecure, terminating connection");
                    startSecureError = true;
                    continue;
                }
                if (!response.equals("startsecure finished")) {
                    communicator.println(response);
                }
            }
            communicator.flush();
        }
        if (!startSecureError) {
            communicator.println("ok bye");
            communicator.flush();
        }
        communicator.close();
    }
}
