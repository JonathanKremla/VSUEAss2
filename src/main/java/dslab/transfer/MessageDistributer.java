package dslab.transfer;

import dslab.util.Config;
import dslab.util.datastructures.DataQueue;
import dslab.util.datastructures.Email;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.MissingResourceException;

/**
 * This Class implements the Producer-Consumer Class receiving Messages from the {@link dslab.transfer.dmtp.DmtpRequestHandler}
 * and passing them forward to the appropriate Mailbox Server and Monitoring Server.
 * <p>
 * The Producer Class {@link dslab.transfer.dmtp.DmtpRequestHandler} calls the distribute() function after finishing
 * producing, while the Sender thread in {@link dslab.transfer.dmtp.DmtpCommunicationThread} calls the forward() function
 * which then puts the Sender thread in a loop, connecting to the mailbox Servers and Monitoring Server and sending
 * the Message.
 * distribute() puts the message in the {@link DataQueue} queue while it is not full, forward() extracts messages out of the
 * queue while it is not empty. If the Queue is full/empty respectively the Thread blocks and waits for the queue to
 * be the desired state (not full, not empty)
 * </p>
 */
public class MessageDistributer {
  private final DataQueue queue = new DataQueue(10);
  private final Config domainConfig = new Config("domains");
  private final Log LOG = LogFactory.getLog(MessageDistributer.class);
  private Socket mailboxSocket;
  private PrintWriter mailboxOut;
  private BufferedReader mailboxIn;
  private Config transferConfig;

  public void setTransferConfig(Config transferConfig) {
    this.transferConfig = transferConfig;
  }

  /**
   * Is called by the Producer Class {@link dslab.transfer.dmtp.DmtpRequestHandler} with a freshly produced message.
   * If the Queue is not full the message is saved to the Queue and the producer Thread can return to producing Messages.
   * If the Queue is full the producer Thread is blocked until the Queue is not full any more (see forward method)
   *
   * @param email message to send
   * @throws InterruptedException if the Thread gets interrupted during wait
   */
  public void distribute(Email email) throws InterruptedException {
    while (queue.isFull()) {
      try {
        queue.waitOnFull();
      } catch (InterruptedException e) {
        break;
      }
    }
    queue.add(email);
    queue.notifyAllForEmpty();
  }

  /**
   * Is called by the Sender Thread in {@link dslab.transfer.dmtp.DmtpCommunicationThread}
   * It loops endlessly(until thread is terminated) to process the Queue {@link DataQueue}
   * If the Queue is empty the Thread waits for new Messages to be produced (see distribute method)
   * If the Queue is not empty it establishes Connections to the required Servers and sends the Message
   * to the appropriate recipients
   */
  public void forward() {
    while (true) {
      if (queue.isEmpty()) {
        try {
          queue.waitOnEmpty();
        } catch (InterruptedException e) {
          break;
        }
      }
      Email toSend = queue.poll();
      queue.notifyAllForFull();
      for (String domain : toSend.getDomains()) {
        if (!establishClientConnection(domain)) {
          sendFailureMail(toSend.getFrom());
        } else {
          sendStatistics(toSend);
          sendMail(toSend);
        }
      }
    }
  }

  private boolean establishClientConnection(String domain) {
    int ip;
    try {
      if (mailboxSocket != null &&
              mailboxSocket.isConnected() &&
              mailboxSocket.getPort() == Integer.parseInt(domainConfig.getString(domain).split(":")[1])) {
        return true;
      }
      ip = Integer.parseInt(domainConfig.getString(domain).split(":")[1]);
    } catch (MissingResourceException e) {
      return false;
    }
    try {
      LOG.info("establishConnection: " + domain);
      if (mailboxOut != null) {
        mailboxOut.close();
      }
      if (mailboxIn != null) {
        mailboxIn.close();
      }
      mailboxSocket = new Socket("localhost", ip);
      mailboxOut = new PrintWriter(mailboxSocket.getOutputStream());
      mailboxIn = new BufferedReader(new InputStreamReader(mailboxSocket.getInputStream()));
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private void sendMail(Email email) {
    mailboxOut.println("begin");
    mailboxOut.println("to " + email.getTo());
    mailboxOut.println("from " + email.getFrom());
    mailboxOut.println("subject " + email.getSubject());
    mailboxOut.println("data " + email.getData());
    mailboxOut.println("send");
    mailboxOut.flush();
    LOG.info("Finished sending email");
  }

  private void sendStatistics(Email toSend) {
    LOG.info("sendStatistics: " + toSend.toString());

    DatagramSocket socket = null;
    byte[] message = (mailboxSocket.getInetAddress().getHostAddress() + ":"
            + transferConfig.getString("tcp.port") + " " + toSend.getFrom() + "\n").getBytes();
    try {
      socket = new DatagramSocket();
      DatagramPacket packet = new DatagramPacket(message, message.length
              , InetAddress.getByName(transferConfig.getString("monitoring.host"))
              , transferConfig.getInt("monitoring.port"));
      socket.send(packet);
    } catch (IOException e) {
      LOG.error(e);
    } finally {
      if (socket != null && !socket.isClosed()) {
        socket.close();
      }
    }

  }

  private void sendFailureMail(String from) {
    String domain = from.split("@")[1];
    if (establishClientConnection(domain)) {
      sendMail(new Email("mailer@[" + mailboxSocket.getInetAddress().getHostAddress() + "]",
              from, "Failed to send Email", "Failed to send Email"));
    }

  }


}
