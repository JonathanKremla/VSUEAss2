package dslab.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.SecretKey;
import javax.crypto.Mac;

public class MessageClient implements IMessageClient, Runnable {

    private final String componentId;
    private final Config config;
    private final InputStream in;
    private final PrintStream out;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        try {
            Socket clientSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            // todo: here the code for "startsecure" will be inserted
            bufferedWriter.write(
                    "login "
                    + config.getString("mailbox.user")
                    + " "
                    + config.getString("mailbox.password")
                    + "\n"
            );
            bufferedWriter.flush();

            String line = bufferedReader.readLine();
            System.out.println("MAILBOX RESPONSE AFTER LOGIN: " + line);

        } catch (IOException e) {
            System.out.println("ERROR client socket");
        }

    }

    /**
     * Outputs the contents of the user's inbox on the shell.
     */
    @Override
    public void inbox() {
        try {
            bufferedWriter.write("list\n");
            bufferedWriter.flush();

            String listResponse = bufferedReader.readLine();

            List<String> allMessagesInDetailFormat = getAllMessagesInDetailFormat(listResponse);

            for (String message : allMessagesInDetailFormat) {
                System.out.println(message);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> getAllMessagesInDetailFormat(String listResponse) {
        String[] allMessages = listResponse.split("\n");

        List<String> allMessagesInDetailFormat = new ArrayList<>();

        for (String message : allMessages) {
            // todo: rn I am assuming no invalid message formats would ever be stored
            String id = message.split(" ")[0];

            try {
                bufferedWriter.write("show " + id + "\n");
                bufferedWriter.flush();
                String showResponse = bufferedReader.readLine();

                String totalString = "MESSAGE WITH ID " + id + ": \n";
                totalString += showResponse + '\n';

                allMessagesInDetailFormat.add(totalString);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return allMessagesInDetailFormat;
    }

    /**
     * Deletes the mail with the given id. Prints 'ok' if
     * the mail was deleted successfully, 'error {explanation}'
     * otherwise.
     *
     * @param id the mail id
     */
    @Override
    public void delete(String id) {
        try {
            bufferedWriter.write("delete + " + id + "\n");
            bufferedWriter.flush();
            String serverResponse = bufferedReader.readLine();

            if (serverResponse.startsWith("error")) {
                System.out.println(serverResponse);
            } else {
                System.out.println("ok");
            }

            System.out.println("MAILBOX RESPONSE AFTER LOGIN: " + serverResponse);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Verifies the signature of the message by calculating
     * its hash value using the shared secret. Prints 'ok' if the
     * message integrity was successfully verified, or 'error' otherwise.
     *
     * @param id the message id
     */
    @Override
    public void verify(String id) {
        try {
            bufferedWriter.write("show + " + id + "\n");
            bufferedWriter.flush();
            String serverResponse = bufferedReader.readLine();

            String messageText = parseIntoFormat(serverResponse);
            String hash = getHash(serverResponse);

            if (hash == null) {
                System.out.println("error - message integrity could not be verified because the message did not have a hash");
            } else {
                byte[] receivedHash = hash.getBytes();
                byte[] computedHash = computeHash(messageText);

                boolean validHash = MessageDigest.isEqual(computedHash, receivedHash);

                if (validHash) {
                    System.out.println("ok - message integrity was successfully verified");
                } else {
                    System.out.println("error - message integrity could not be verified");
                }
            }
        } catch (IOException ioe) {
            System.err.println("ERROR: opening a key file failed");
        } catch (NoSuchAlgorithmException nae) {
            System.err.println("ERROR: specified hashing algorithm could not be identified");
        } catch (InvalidKeyException e) {
            System.err.println("ERROR: the key file seems to be invalid");
        }
    }

    private String getHash(String serverResponse) {
        String[] lines = serverResponse.split("\n");

        for (String line : lines) {
            if (line.startsWith("hash")) {
                String[] parts = line.split(" ");

                if (parts.length == 2) {
                    return parts[1];
                } else {
                    return null;
                }
            }
        }
        throw new RuntimeException("FATAL MAILBOX-ERROR - all messages are expected to contain the hash field, even if empty");
    }

    /*
    Input format:
    S: from deep@tought.ze
    S: to zaphod@univer.ze,trillian@planet.earth
    S: subject my answer
    S: hash BLABLABLA
    S: data I have thought about it and the answer is clearly

    Output format:
    zaphod@univer.ze
    trillian@earth.planet,ford@earth.planet
    restaurant
    i know this nice restaurant at the end of the universe, wanna go?
     */
    private String parseIntoFormat(String wholeMessageString) {
        StringBuilder finalString = new StringBuilder();
        String[] lines = wholeMessageString.split("\n");

        for (String line : lines) {
            if (line.startsWith("hash")) continue;

            finalString
                    .append(line.split(" ", 2)[1])
                    .append("\n");
        }

        return finalString.toString();
    }

    private byte[] computeHash(String messageText) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        File file = new File("keys/hmac.key");
        SecretKey secretKey = Keys.readSecretKey(file);
        Mac hMac = Mac.getInstance("HmacSHA256");
        hMac.init(secretKey);
        hMac.update(messageText.getBytes());
        byte[] computedHash = hMac.doFinal();
        return computedHash;
    }

    /**
     * Sends a message from the mail client's user to the given recipient(s)
     *
     * @param to comma separated list of recipients
     * @param subject the message subject
     * @param data the message data
     */
    @Override
    public void msg(String to, String subject, String data) {
        try {
            String from = config.getString("transfer.email");

            String messageTextWithoutHash = "";
            messageTextWithoutHash += from + "\n";
            messageTextWithoutHash += to + "\n";
            messageTextWithoutHash += subject + "\n";
            messageTextWithoutHash += data + "\n";

            String formattedText = parseIntoFormat(messageTextWithoutHash);

            byte[] hashBytes = computeHash(formattedText);

            String hash = new String(hashBytes, StandardCharsets.UTF_8);

            bufferedWriter.write("begin\n");
            bufferedWriter.flush();
            String line = bufferedReader.readLine();
            System.out.println("CLIENT READS LINE: " + line);

            bufferedWriter.write("to " + to + '\n');
            bufferedWriter.flush();
            line = bufferedReader.readLine();
            System.out.println("CLIENT READS LINE: " + line);

            bufferedWriter.write("from " + from + '\n');
            bufferedWriter.flush();
            line = bufferedReader.readLine();
            System.out.println("CLIENT READS LINE: " + line);

            bufferedWriter.write("subject " + subject + '\n');
            bufferedWriter.flush();
            line = bufferedReader.readLine();
            System.out.println("CLIENT READS LINE: " + line);

            bufferedWriter.write("data " + data + '\n');
            bufferedWriter.flush();
            line = bufferedReader.readLine();
            System.out.println("CLIENT READS LINE: " + line);

            bufferedWriter.write("hash " + hash + '\n');
            bufferedWriter.flush();
            line = bufferedReader.readLine();
            System.out.println("CLIENT READS LINE: " + line);

            bufferedWriter.write("send\n");
            bufferedWriter.flush();
            line = bufferedReader.readLine();
            System.out.println("CLIENT READS LINE: " + line);

            bufferedWriter.write("quit\n");
            bufferedWriter.flush();
            line = bufferedReader.readLine();
            System.out.println("CLIENT READS LINE: " + line);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {

    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
