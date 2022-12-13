package dslab.mailbox.dmap;

import dslab.mailbox.MessageStorage;
import dslab.util.Config;
import dslab.util.datastructures.Email;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * handles all DMAP requests, a request being one command sent from a Client,
 * for Mailbox Server and provides the appropriate response(s)
 */
public class DmapRequestHandler {
  //map containing the responses(can be multiple or single) for a specific request
  private final HashMap<String, List<String>> responseMap = new HashMap<>();
  private final Config config;
  private String currentUser;

  public DmapRequestHandler(String userConfig) {
    this.config = new Config(userConfig);
    fillResponseMap();
  }

  /**
   * handles a specific request
   *
   * @param request request to handle
   * @return List of responses to be sent to the client
   */
  public List<String> handle(String request) {
    fillResponseMap();
    if (responseMap.containsKey(request)) {
      var answer = responseMap.get(request);
      handleLogicalRequests(request);
      return answer;
    }
    List<String> invalidRequest = new ArrayList<>();
    invalidRequest.add("error");
    return invalidRequest;
  }

  private void handleLogicalRequests(String key) {
    if (currentUser == null) {
      if (key.startsWith("login")) {
        currentUser = key.split(" ")[1];
        responseMap.remove("login " + currentUser + " " + config.getString(currentUser));
      }
    } else {
      if (key.startsWith("delete")) {
        deleteMessage(Integer.parseInt(key.split(" ")[1]));
      }
      if (key.startsWith("logout")) {
        logoutUser();
      }
    }

  }

  private void fillResponseMap() {
    if (currentUser == null) {
      loginResponse();
    }
    if (currentUser != null) {
      listRespone();
      deleteResponse();
      showResponse();
      logoutResponse();
    }
  }

  private void loginResponse() {
    List<String> responseList = new ArrayList<>();
    responseList.add("ok");
    var keys = config.listKeys();
    for (String key : keys) {
      responseMap.put("login" + " " + key + " " + config.getString(key), responseList);
    }
  }

  private void listRespone() {
    List<String> responseList = new ArrayList<>();
    for (int i = 1; i <= MessageStorage.getIndex(currentUser); i++) {
      Email message = MessageStorage.get(currentUser, i);
      if (message != null) {
        responseList.add(i + " " + message.getFrom() + " " + message.getSubject());
      }
    }
    responseList.add("ok");
    responseMap.put("list", responseList);
  }

  private void showResponse() {
    int maxAmountOfMessages = MessageStorage.getIndex(currentUser);
    for (int i = 1; i <= maxAmountOfMessages; i++) {
      Email message = MessageStorage.get(currentUser, i);
      if (message != null) {
        List<String> responseList = new ArrayList<>();
        responseList.add("from " + message.getFrom() + "\n" +
                "to " + message.getTo() + "\n" +
                "subject " + message.getSubject() + "\n" +
                "data " + message.getData() + "\n" +
                "ok");
        responseMap.put("show " + i, responseList);
      }
    }
  }

  private void deleteResponse() {
    List<String> responseList = new ArrayList<>();
    responseList.add("ok");
    for (int i = 1; i <= MessageStorage.getIndex(currentUser); i++) {
      Email message = MessageStorage.get(currentUser, i);
      if (message != null) {
        responseMap.put("delete " + i, responseList);
      }
    }
  }

  private void logoutResponse() {
    List<String> responseList = new ArrayList<>();
    responseList.add("ok");
    responseMap.put("logout", responseList);
  }

  private void deleteMessage(int id) {
    responseMap.remove("list");
    responseMap.remove("show " + id);
    responseMap.remove("delete " + id);
    MessageStorage.remove(currentUser, id);
  }

  private void logoutUser() {
    currentUser = null;
    responseMap.clear();
    fillResponseMap();
  }

}
