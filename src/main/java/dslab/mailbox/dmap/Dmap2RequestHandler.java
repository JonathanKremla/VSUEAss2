package dslab.mailbox.dmap;

import dslab.mailbox.MessageStorage;
import dslab.util.Config;
import dslab.util.datastructures.Email;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/**
 * handles all DMAP requests, a request being one command sent from a Client,
 * for Mailbox Server and provides the appropriate response(s)
 */
public class Dmap2RequestHandler {
    //map containing the responses(can be multiple or single) for a specific request
    private final HashMap<String, List<String>> responseMap = new HashMap<>();
    private final Config config;
    private String currentUser;

    private final String componentId;
    private int startSecureStep;
    private Cipher rsaCipher;

    private Cipher aesEncCipher;
    private Cipher aesDecCipher;

    public Dmap2RequestHandler(String userConfig, String componentId) {
        this.config = new Config(userConfig);
        this.componentId = componentId;
        this.startSecureStep = 0;
        try {
            this.rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            String privateKeyFileName = "keys/server/" + componentId + ".der";

            //SecretKeySpec keySpec = Keys.readSecretKey(new File(privateKeyFileName));
            Path path = Paths.get(privateKeyFileName);
            byte[] privKeyByteArray = Files.readAllBytes(path);

            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privKeyByteArray);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            PrivateKey myPrivKey = keyFactory.generatePrivate(keySpec);

            this.rsaCipher.init(Cipher.PRIVATE_KEY, myPrivKey);
        } catch (Exception e) {
            System.out.println("ALARM");
            System.out.println(e.getMessage());
            System.out.println(e.getClass());
        }

        fillResponseMap();
    }

    /**
     * handles a specific request
     *
     * @param request request to handle
     * @return List of responses to be sent to the client
     */
    public List<String> handle(String request) {
        System.out.println("handle " + request);
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
            List<String> temp = encrypt(startSecure(request));
            System.out.println("response: " + temp);
            return temp;
        }
        List<String> invalidRequest = new ArrayList<>();
        invalidRequest.add("error");
        return invalidRequest;
    }

    private List<String> encrypt(List<String> answer) {
        List<String> response = new ArrayList<>();
        StringBuilder answerString = new StringBuilder();
        for (String s : answer) {
            answerString.append(s).append("\n");
        }
        if (answerString.charAt(answerString.length() - 1) == '\n') {
            answerString.deleteCharAt(answerString.length() - 1);
        }
        byte[] messageToBytes = answerString.toString().getBytes();
        try {
            byte[] encryptedBytes = aesEncCipher.doFinal(messageToBytes);
            response.add(encode(encryptedBytes));
        } catch (Exception e) {
            System.out.println("whoopsie");
            e.printStackTrace();
        }
        return response;
    }

    public String decrypt(String encryptedMessage) {
        byte[] encryptedBytes = decode(encryptedMessage);
        String response = "";
        try {
            byte[] decryptedMessage = aesDecCipher.doFinal(encryptedBytes);
            response = new String(decryptedMessage, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("whoopsie");
            e.printStackTrace();
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
        List<String> responseList = new ArrayList<>();
        if (startSecureStep == 0) {
            responseList.add("ok " + componentId);
        }
        responseMap.put("startsecure", responseList);
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
                        "data " + message.getData() +
                        "hash " + "dlfjasöflLFJLSJDflsdselfie49" + "\n" +
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
        System.out.println("hallo startSecure undso");
        List<String> responseList = new ArrayList<>();
        String response = "ok ";
        try {
            byte[] requestBytes = decode(request);
            System.out.println(requestBytes.length + " " + request.length());
            byte[] decryptedMessage = rsaCipher.doFinal(requestBytes);
            if (decryptedMessage.length != 86) {
                System.out.println("blöd"+decryptedMessage.length);
                String temp = new String(decryptedMessage, StandardCharsets.UTF_8);
                System.out.println(temp);
                System.out.println(temp.length());
            }
            //byte[] decryptedOk = new byte[2];
            //System.arraycopy(decryptedMessage, 0, decryptedOk, 0, 2);

            byte[] decryptedChallenge = new byte[32];
            System.arraycopy(decryptedMessage, 3, decryptedChallenge, 0, 32);
            System.out.println(new String(decryptedChallenge, StandardCharsets.UTF_8));

            byte[] decryptedCipher = new byte[32];
            System.arraycopy(decryptedMessage, 36, decryptedCipher, 0, 32);
            Key key = new SecretKeySpec(decryptedCipher, 0, 32, "AES");
            System.out.println(new String(decryptedCipher, StandardCharsets.UTF_8));

            byte[] decryptedVector = new byte[16];
            System.arraycopy(decryptedMessage, 69, decryptedVector, 0, 16);
            IvParameterSpec iv = new IvParameterSpec(decryptedVector);
            System.out.println(new String(decryptedVector, StandardCharsets.UTF_8));


            this.aesEncCipher = Cipher.getInstance("AES/CTR/NoPadding");
            this.aesDecCipher = Cipher.getInstance("AES/CTR/NoPadding");
            aesEncCipher.init(Cipher.ENCRYPT_MODE, key, iv);
            aesDecCipher.init(Cipher.DECRYPT_MODE, key, iv);
            response += new String(decryptedChallenge);
            startSecureStep++;

        } catch (Exception e) {
            System.out.println("ALARM");
            System.out.println(e.getMessage());
            e.printStackTrace();
            //
        }


        responseList.add(response);
        return responseList;
    }

}