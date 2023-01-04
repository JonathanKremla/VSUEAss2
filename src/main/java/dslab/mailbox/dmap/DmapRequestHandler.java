package dslab.mailbox.dmap;

import dslab.mailbox.MessageStorage;
import dslab.util.Config;
import dslab.util.Keys;
import dslab.util.datastructures.Email;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.*;
import java.util.ArrayList;
import java.util.Base64;
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

    private final String componentId;
    private int startSecureStep;

    private Cipher aesEncCipher;
    private Cipher aesDecCipher;
    private final List<String> startSecureError = List.of("error during startsecure");

    public DmapRequestHandler(String userConfig, String componentId) {
        this.config = new Config(userConfig);
        this.componentId = componentId;
        this.startSecureStep = 0;

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
        if (startSecureStep > 1) {
            request = decrypt(request);
            System.out.println("handle " + request);
        }
        if (responseMap.containsKey(request)) {
            var answer = responseMap.get(request);
            handleLogicalRequests(request);
            if (startSecureStep > 1) {
                answer = encrypt(answer);
            }
            return answer;
        }
        if (startSecureStep == 1) {
            List<String> response = startSecure(request);
            if(response.equals(startSecureError)){
                return response;
            }
            return encrypt(response);
        }
        if (startSecureStep == 2) {
            if (request.equals("ok")) {
                startSecureStep++;
                return List.of("startsecure finished");
            } else {
                return startSecureError;
            }
        }
        List<String> invalidRequest = new ArrayList<>();
        invalidRequest.add("error");
        return invalidRequest;
    }

    private List<String> encrypt(List<String> answer) {
        List<String> response = new ArrayList<>();
        for (String s0 : answer) {
            for (String s : s0.split("\n")) {
                byte[] bytes = s.getBytes();
                byte[] encryptedBytes = aesEncCipher.update(bytes);
                response.add(encode(encryptedBytes));
            }
        }
        return response;
    }

    public String decrypt(String encryptedMessage) {
        byte[] encryptedBytes = decode(encryptedMessage);
        byte[] decryptedMessage = aesDecCipher.update(encryptedBytes);
        String response = new String(decryptedMessage);
        if (response.endsWith("\n")) {
            response = response.substring(0, response.length() - 1);
        }
        return response;
    }

    private String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    private byte[] decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    private void handleLogicalRequests(String key) {
        if (startSecureStep == 0 && key.equals("startsecure")) {
            startSecureStep++;
        }
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
        startSecureResponse();
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

    private void startSecureResponse() {
        if (startSecureStep == 0) {
            List<String> responseList = new ArrayList<>();
            responseList.add("ok " + componentId);
            responseMap.put("startsecure", responseList);
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
                        "hash " + message.getHash() + "\n" +
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

    private List<String> startSecure(String request) {
        Cipher rsaCipher;
        try {
            rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            String privateKeyFileName = "keys/server/" + componentId + ".der";

            PrivateKey myPrivKey = Keys.readPrivateKey(new File(privateKeyFileName));

            rsaCipher.init(Cipher.PRIVATE_KEY, myPrivKey);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | IOException | InvalidKeyException e) {
            return startSecureError;
        }

        List<String> responseList = new ArrayList<>();
        String response = "ok ";
        try {
            byte[] requestBytes = decode(request);
            byte[] decryptedMessage = rsaCipher.doFinal(requestBytes);

            String decryptedString = new String(decryptedMessage);
            byte[] decryptedChallenge = decode(decryptedString.substring(3, 47));

            byte[] decryptedCipher = decode(decryptedString.substring(48, 92));
            Key key = new SecretKeySpec(decryptedCipher, "AES");

            byte[] decryptedVector = decode(decryptedString.substring(93));
            IvParameterSpec iv = new IvParameterSpec(decryptedVector);

            this.aesEncCipher = Cipher.getInstance("AES/CTR/NoPadding");
            this.aesDecCipher = Cipher.getInstance("AES/CTR/NoPadding");
            aesEncCipher.init(Cipher.ENCRYPT_MODE, key, iv);
            aesDecCipher.init(Cipher.DECRYPT_MODE, key, iv);

            response += encode(decryptedChallenge);
            startSecureStep++;

        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException | NoSuchPaddingException |
                 BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            return startSecureError;
        }
        responseList.add(response);
        return responseList;
    }
}
