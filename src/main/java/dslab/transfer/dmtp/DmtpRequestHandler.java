package dslab.transfer.dmtp;

import dslab.transfer.MessageDistributer;
import dslab.util.datastructures.Email;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * handles all DMTP requests, a request being one command sent from a Client,
 * for Transfer Server and provides the appropriate response(s)
 * <p>
 * It also forwards the received messages to the {@link MessageDistributer  }
 */
public class DmtpRequestHandler extends Thread {

  private final MessageDistributer messageDistributer;
  private final Log LOG = LogFactory.getLog(DmtpRequestHandler.class);
  private Email receivedEmail = new Email();
  private boolean transferBegan = false;

  public DmtpRequestHandler(MessageDistributer messageDistributer) {
    this.messageDistributer = messageDistributer;
    Thread.currentThread().setName("DmtpRequestHandlerThread");
  }

  public String handleRequest(String request) {
    switch (request.split(" ")[0]) {
      case "begin":
        return parseBegin(request);
      case "to":
        return parseTo(request);
      case "from":
        return parseFrom(request);
      case "subject":
        return parseSubject(request);
      case "data":
        return parseData(request);
      case "hash":
        return parseHash(request);
      case "send":
        return parseSend();
      default:
        return "error invalid Request";
    }
  }

  private String parseHash(String request) {
    if (!transferBegan) {
      return "error invalid request (1)";
    }

    String[] args = request.split(" ");
    if (args.length != 2) {
      return "error invalid request (2)";
    }
    String hash = args[1];
    byte[] bytes = hash.getBytes();

    // "The generated hash is a 32 byte value"
    if (bytes.length != 32) {
      return "error invalid request (3)";
    }

    receivedEmail.setHash(hash);
    return "ok";
  }

  private String parseTo(String request) {
    if (!transferBegan) {
      return "error invalid request";
    }
    //remove "to"
    request = request.substring(2);
    var recipients = Arrays.stream(request.split(","))
            .map(String::trim)
            .collect(Collectors.toList());

    receivedEmail.setTo(recipients.stream()
            .reduce("", (emails, email) -> emails
                    + (Objects.equals(emails, "") ? "" : " , ")
                    + email));

    List<String> domainList = new ArrayList<>();
    for (String recipient : recipients) {
      domainList.add(recipient.split("@")[1]);
    }
    receivedEmail.setDomains(domainList.stream().distinct().collect(Collectors.toList()));
    return "ok " + domainList.size();
  }

  private String parseFrom(String request) {
    if (!transferBegan) {
      return "error invalid request";
    }
    var splitRequest = request.split(" ");
    if (splitRequest.length > 2) {
      return "error only one sender possible";
    }
    var email = splitRequest[1];
    if (!email.matches("(.*)@(.*)")) {
      return "error invalid Email";
    }
    receivedEmail.setFrom(email);
    return "ok";

  }

  private String parseSubject(String request) {
    if (!transferBegan) {
      return "error invalid request";
    }
    receivedEmail.setSubject(request.substring(7).trim());
    return "ok";
  }

  private String parseData(String request) {
    if (!transferBegan) {
      return "error invalid request";
    }
    receivedEmail.setData(request.substring(4).trim());
    return "ok";

  }

  private String parseSend() {
    LOG.info("parseSend");
    if (!transferBegan) {
      return "error";
    }
    if (!allEmailAttributesSet()) {
      return "error";
    }
    try {
      LOG.info("call MessageDistributer: " + receivedEmail.toString());
      messageDistributer.distribute(receivedEmail);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    this.transferBegan = false;
    this.receivedEmail = new Email();
    return "ok";
  }

  private boolean allEmailAttributesSet() {
    return receivedEmail.getFrom() != null &&
            receivedEmail.getTo() != null &&
            receivedEmail.getSubject() != null &&
            receivedEmail.getData() != null;
  }

  private String parseBegin(String request) {
    if (request.split(" ").length > 1) {
      return "error invalid request";
    }
    if (transferBegan) {
      return "error invalid request";
    }
    transferBegan = true;
    return "ok";
  }

}



