package dslab.client;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.net.Socket;
import java.security.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.MissingResourceException;

import static dslab.util.Util.getDomainName;
import static dslab.util.Util.getWholeSocketAddress;
import static java.lang.Integer.parseInt;

public class MessageClient implements IMessageClient, Runnable {

    private final String componentId;
    private final Config config;
    private final InputStream in;
    private final PrintStream out;
    private final Shell shell;
    private BufferedReader mailboxBufferedReader;
    private BufferedWriter mailboxServerBufferedWriter;
    private Cipher aesEncCipher;
    private Cipher aesDecCipher;

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;

        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "-mailbox> ");
    }

    @Override
    public void run() {
        try {
            Socket clientSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            mailboxBufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            mailboxServerBufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            String line = mailboxBufferedReader.readLine(); // just to read the ok DMAP2.0

            try {
                startSecure();
            } catch (IOException e) {
                clientSocket.close();
                return;
            }

            writeToServer("login "
                    + config.getString("mailbox.user")
                    + " "
                    + config.getString("mailbox.password"));

            line = readLineFromServer();//mailboxBufferedReader.readLine();

            shell.run();
        } catch (IOException e) {
            System.err.println("ERROR client socket");
        }
    }

    /**
     * Outputs the contents of the user's inbox on the shell.
     */
    @Command
    @Override
    public void inbox() {
        try {
            writeToServer("list\n");

            String totalString = "";

            String readString = readLineFromServer() + "\n";
            boolean emptyInbox = readString.equals("ok\n");

            while (!readString.equals("ok\n")) {
                totalString += readString;
                readString = readLineFromServer() + "\n";
            }

            if (emptyInbox) {
                shell.out().println("Your inbox is empty.");
            } else {
                List<String> allMessagesInDetailFormat = getAllMessagesInDetailFormat(totalString);
                for (String message : allMessagesInDetailFormat) {
                    shell.out().println(message);
                }
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
                writeToServer("show " + id + "\n");

                String totalString = "\nMESSAGE WITH ID " + id + ": \n";

                String from = readLineFromServer();
                totalString += from + '\n';
                String to = readLineFromServer();
                totalString += to + '\n';
                String subject = readLineFromServer();
                totalString += subject + '\n';
                String data = readLineFromServer();
                totalString += data + '\n';
                String hash = readLineFromServer();

                // just read the "ok" at the end of show, but don't treat it
                String ok = readLineFromServer();

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
    @Command
    public void delete(String id) {
        try {
            writeToServer("delete " + id + "\n");
            String serverResponse = readLineFromServer();

            if (serverResponse.startsWith("error")) {
                shell.out().println(serverResponse);
            } else {
                shell.out().println("ok");
            }

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
    @Command
    public void verify(String id) {
        try {
            writeToServer("show " + id + "\n");

            String totalString = "";
            String readString = readLineFromServer() + "\n";
            while (!readString.equals("ok\n")) {
                if (readString.startsWith("error")) {
                    shell.out().println("error - message integrity could not be verified because the message could not be found");
                    return;
                }
                totalString += readString;
                readString = readLineFromServer() + "\n";
            }

            String messageText = parseIntoFormat(totalString);
            String hash = getHash(totalString);

            if (hash == null) {
                shell.out().println("error - message integrity could not be verified because the message did not have a hash");
            } else {
                byte[] receivedHash = Base64.getDecoder().decode(hash);
                byte[] computedHash = computeHash(messageText);

                boolean validHash = MessageDigest.isEqual(computedHash, receivedHash);

                if (validHash) {
                    shell.out().println("ok - message integrity was successfully verified");
                } else {
                    shell.out().println("error - message integrity could not be verified - invalid");
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
        NOTE: there should actually be no newline after the last line
    */
    private String parseIntoFormat(String wholeMessageString) {
        StringBuilder finalString = new StringBuilder();
        String[] lines = wholeMessageString.split("\n");

        for (String line : lines) {
            if (line.startsWith("hash") || line.equals("ok")) continue;

            finalString
                    .append(line.substring(line.indexOf(' ')))
                    .append("\n");
        }
        // NOTE: substring to exclude the last '\n'
        return finalString.toString().substring(0, finalString.length() - 1);
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
     * @param to      comma separated list of recipients
     * @param subject the message subject
     * @param data    the message data
     */
    @Override
    @Command
    public void msg(String to, String subject, String data) {
        try {
            String from = config.getString("transfer.email");

            // because the format is: msg <to> "<subject>" "<data>" ->
//            String subjectWithoutQuotes = subject.substring(1,subject.length()-1);
//            String dataWithoutQuotes = data.substring(1,data.length()-1);

            String messageTextWithoutHashNorOk = "";
            messageTextWithoutHashNorOk += "from " + from + "\n";
            messageTextWithoutHashNorOk += "to " + to + "\n";
            messageTextWithoutHashNorOk += "subject " + subject + "\n";
            messageTextWithoutHashNorOk += "data " + data + "\n";

            String formattedText = parseIntoFormat(messageTextWithoutHashNorOk);

            byte[] hashBytes = computeHash(formattedText);
            String hash = Base64.getEncoder().encodeToString(hashBytes);

            String[] recipientEmails = to.split(",");
            if (recipientEmails.length == 0) throw new RuntimeException("Fatal Error 1");


            String targetHost;
            String targetPort;
            try {
                targetHost = config.getString("transfer.host"); // format: ip-address:port
                targetPort = config.getString("transfer.port"); // format: ip-address:port
            } catch (MissingResourceException mre) {
                shell.out().println("error could not find transfer server properties");
                return;
            }

            for (String recipientEmail : recipientEmails) {

                String response = "no response received yet";
                try {
                    Socket socket = new Socket(targetHost, parseInt(targetPort));

                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    bufferedWriter.write("begin\n");
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("to " + to + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("from " + from + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("subject " + subject + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("data " + data + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("hash " + hash + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("send\n");
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    if (response.startsWith("error")) throw new IOException("custom");

                    shell.out().println("ok");
                } catch (IOException se) {
                    shell.out().println("error while sending message to " + recipientEmail + "1: " + response);
                    return;
                }
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    /**
     * executes the startsecure handshake with the server
     * terminates the connection in case of an error
     */
    private void startSecure() throws IOException {
        mailboxServerBufferedWriter.write("startsecure\n");
        mailboxServerBufferedWriter.flush();

        String response = mailboxBufferedReader.readLine();
        if (!response.startsWith("ok ") || response.length() < 4) {
            startSecureShutdown();
        }
        String componentId = response.substring(3);
        Cipher rsaCipher = null;
        try {
            rsaCipher = getPublicKey(componentId);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            startSecureShutdown();
        }

        SecureRandom random = null;
        try {
            random = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            startSecureShutdown();
        }
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);

        String challengeString = encode(challenge);

        KeyGenerator keyGenerator = null;
        try {
            keyGenerator = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            startSecureShutdown();
        }
        keyGenerator.init(256);
        Key key = keyGenerator.generateKey();
        String aesCipher = encode(key.getEncoded());


        byte[] iv = new byte[16];
        random.nextBytes(iv);
        String ivString = encode(iv);

        try {
            this.aesEncCipher = Cipher.getInstance("AES/CTR/NoPadding");

            this.aesDecCipher = Cipher.getInstance("AES/CTR/NoPadding");
            aesEncCipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            aesDecCipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException |
                 InvalidKeyException e) {
            startSecureShutdown();
        }
        String message = "ok " + challengeString + " " + aesCipher + " " + ivString;
        String encryptedMessage = "";
        try {
            encryptedMessage = encryptRsa(message, rsaCipher);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            startSecureShutdown();
        }
        mailboxServerBufferedWriter.write(encryptedMessage + "\n");
        mailboxServerBufferedWriter.flush();

        response = readLineFromServer();

        if (!response.equals("ok " + challengeString)) {
            startSecureShutdown();
        } else {
            mailboxServerBufferedWriter.write(encrypt("ok") + "\n");
            mailboxServerBufferedWriter.flush();
        }
    }

    /**
     * encrypts the given message with the given RSA cipher
     *
     * @param message to be encrypted
     * @param cipher  to encrypt with
     * @return the encrypted message
     */
    private String encryptRsa(String message, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        byte[] messageToBytes = message.getBytes();
        byte[] encryptedBytes = cipher.doFinal(messageToBytes);
        return encode(encryptedBytes);
    }

    /**
     * encrypts given message with AES cipher and encodes it to base64
     *
     * @param message to be encrypted
     * @return the encrypted and encoded message
     */
    private String encrypt(String message) {
        byte[] messageToBytes = message.getBytes();
        byte[] encryptedBytes = aesEncCipher.update(messageToBytes);
        return encode(encryptedBytes);
    }

    /**
     * decodes and decryptes a given message
     *
     * @param encryptedMessage to be decrypted
     * @return decoded and decrypted message
     */
    public String decrypt(String encryptedMessage) {
        byte[] encryptedBytes = decode(encryptedMessage);
        byte[] decryptedMessage = aesDecCipher.update(encryptedBytes);
        return new String(decryptedMessage);
    }

    /**
     * encodes given data to base64
     *
     * @param data to be encoded
     * @return encoded data as a string
     */
    private String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * decodes given string from base64 to bytes
     *
     * @param data to be decoded
     * @return decoded bytes
     */
    private byte[] decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    /**
     * reads public key of server with given component id and inits RSA cipher
     *
     * @param componentId of server whose key to read
     * @return initialized RSA cipher
     */
    private Cipher getPublicKey(String componentId) throws NoSuchPaddingException, NoSuchAlgorithmException, IOException, InvalidKeyException {
        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        String publicKeyFileName = "keys/client/" + componentId + "_pub.der";

        PublicKey publicKey = Keys.readPublicKey(new File(publicKeyFileName));

        rsaCipher.init(Cipher.PUBLIC_KEY, publicKey);
        return rsaCipher;
    }

    /**
     * encrypts given message and sends it to the server
     *
     * @param message message to be sent
     */
    private void writeToServer(String message) throws IOException {
        mailboxServerBufferedWriter.write(encrypt(message) + "\n");
        mailboxServerBufferedWriter.flush();
    }

    /**
     * reads line from server and decrypts it
     *
     * @return the decrypted message
     */
    private String readLineFromServer() throws IOException {
        String message = mailboxBufferedReader.readLine();
        if (message == null) {
            throw new IOException("error server terminated connection");
        }
        if (message.startsWith("error")) {
            return message;
        }
        return decrypt(message);
    }

    /**
     * when an error during startsecure occured client disconnects from the server and shuts down
     */
    private void startSecureShutdown() throws IOException {
        System.err.println("Error during startSecure, terminating connection");
        throw new IOException("error during startsecure");
    }

    @Override
    @Command
    public void shutdown() {
        try {
            mailboxServerBufferedWriter.write("quit\n");
            mailboxServerBufferedWriter.flush();
            String line = mailboxBufferedReader.readLine();
        } catch (IOException e) {
            System.err.println("error during shutdown");
        }
        throw new StopShellException();

    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
