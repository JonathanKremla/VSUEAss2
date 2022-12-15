package dslab.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;

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
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
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
            System.out.println("000");
            Socket clientSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            mailboxBufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            mailboxServerBufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            String line = mailboxBufferedReader.readLine(); // just to read the ok DMAP2.0
            System.out.println("MAILBOX RESPONSE AFTER CONNECTION: " + line);

            startSecure();
            // todo: here the code for "startsecure" will be inserted

            writeToServer("login "
                    + config.getString("mailbox.user")
                    + " "
                    + config.getString("mailbox.password"));
            /*mailboxServerBufferedWriter.write(
                    "login "
                    + config.getString("mailbox.user")
                    + " "
                    + config.getString("mailbox.password")
                    + "\n"
            );
            mailboxServerBufferedWriter.flush();*/

            line =  readLineFromServer();//mailboxBufferedReader.readLine();
            System.out.println("MAILBOX RESPONSE AFTER LOGIN: " + line);

            shell.run();
        } catch (IOException e) {
            System.out.println("ERROR client socket");
        }
    }

    private void writeToServer(String message) throws IOException {
        mailboxServerBufferedWriter.write(encrypt(message)+"\n");
        mailboxServerBufferedWriter.flush();
    }

    private String readLineFromServer() throws IOException {
        String message = mailboxBufferedReader.readLine();
        if(message.startsWith("error")){
            return message;
        }
        return decrypt(message);
    }

    private void startSecure() throws IOException {
        mailboxServerBufferedWriter.write("startsecure\n");
        mailboxServerBufferedWriter.flush();

        String response = mailboxBufferedReader.readLine();
        if(!response.startsWith("ok ")||response.length()<4){
            System.out.println(response);
            System.out.println("wrong format");
        }
        String componentId = response.substring(3);
        Cipher rsaCipher = getPublicKey(componentId);

        SecureRandom random = null;
        try {
            random = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        String challengeString = encode(challenge);

        KeyGenerator keyGenerator = null;
        try {
            keyGenerator = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
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
            System.out.println("Message Client: "+ Arrays.toString(key.getEncoded()) +" "+ Arrays.toString(iv));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        String message = "ok "+challengeString +" "+ aesCipher +" "+ ivString;
        System.out.println(challengeString.length()+" "+aesCipher.length()+" "+ivString.length());
        String encryptedMessage="";
        try {
            encryptedMessage = encryptRsa(message, rsaCipher);
        }catch (Exception e){
            System.out.println("hallo");
        }

        mailboxServerBufferedWriter.write(encryptedMessage+"\n");
        mailboxServerBufferedWriter.flush();

        response = decrypt(mailboxBufferedReader.readLine());
        //System.out.println(encode(decode(response)));
        System.out.println("ok "+challengeString);
        if(!response.equals("ok "+challengeString)){
            int i=0;
            String test = "ok "+challengeString;
            for (char c:response.toCharArray()) {
                if(c!=test.charAt(i)){
                    System.out.println(i+" "+(int)c+c+" "+(int)test.charAt(i)+test.charAt(i));
                }
                i++;
            }
            System.out.println("oh no");
        }else{
            mailboxServerBufferedWriter.write(encrypt("ok")+"\n");
            mailboxServerBufferedWriter.flush();
        }
        System.out.println("end of startsecure: "+readLineFromServer());
    }

    private String encryptRsa(String message, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        byte[] messageToBytes = message.getBytes();
        byte[] encryptedBytes = cipher.doFinal(messageToBytes);
        return encode(encryptedBytes);
    }

    private String encrypt(String s) {

        byte[] messageToBytes = s.getBytes();
        try {
            byte[] encryptedBytes = aesEncCipher.doFinal(messageToBytes);
            return encode(encryptedBytes);
        } catch (Exception e) {
            System.out.println("whoopsie");
            e.printStackTrace();
        }
        return "";
    }

    public String decrypt(String encryptedMessage) {
        System.out.println("encryptedMessage "+encryptedMessage);
        byte[] encryptedBytes = decode(encryptedMessage);
        String response = "";
        try {
            byte[] decryptedMessage = aesDecCipher.doFinal(encryptedBytes);
            response = new String(decryptedMessage);
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
        System.out.println("decode "+data);
        return Base64.getDecoder().decode(data);
    }

    private Cipher getPublicKey(String componentId) {
        try {
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            String publicKeyFileName = "keys/client/" + componentId + "_pub.der";

            //SecretKeySpec keySpec = Keys.readSecretKey(new File(publicKeyFileName));
            Path path = Paths.get(publicKeyFileName);
            //byte[] pubKeyByteArray = Files.readAllBytes(path);

            //RSAPublicKeySpec keySpec = new PKCS8EncodedKeySpec(pubKeyByteArray);

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            PublicKey publicKey = Keys.readPublicKey(new File(publicKeyFileName));

            rsaCipher.init(Cipher.PUBLIC_KEY, publicKey);
            return rsaCipher;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Outputs the contents of the user's inbox on the shell.
     */
    @Command
    @Override
    public void inbox() {
        try {
            mailboxServerBufferedWriter.write("list\n");
            mailboxServerBufferedWriter.flush();
            System.out.println("Waiting on response after list command...");

            String totalString = "";
            String readString = mailboxBufferedReader.readLine() + "\n";
            boolean emptyInbox = readString.equals("ok\n");

            while ( ! readString.equals("ok\n")) {
                totalString += readString;
                readString = mailboxBufferedReader.readLine() + "\n";
                System.out.printf("totalString:%n%s%n", totalString);
            }

            if (emptyInbox) {
                shell.out().println("Your inbox is empty.");
            } else {
                System.out.println("AAA");

                List<String> allMessagesInDetailFormat = getAllMessagesInDetailFormat(totalString);

                System.out.println("BBB");

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
                mailboxServerBufferedWriter.write("show " + id + "\n");
                mailboxServerBufferedWriter.flush();

                String totalString = "\nMESSAGE WITH ID " + id + ": \n";

                String from = mailboxBufferedReader.readLine();
                totalString += from + '\n';
                String to = mailboxBufferedReader.readLine();
                totalString += to + '\n';
                String subject = mailboxBufferedReader.readLine();
                totalString += subject + '\n';
                String data = mailboxBufferedReader.readLine();
                totalString += data + '\n';

                String hash = mailboxBufferedReader.readLine();
                System.out.println("THE FOLLOWING SHOULD BE \"a hash\": " + hash);

                // just read the "ok" at the end of show, but don't treat it
                String ok = mailboxBufferedReader.readLine();
                System.out.println("THE FOLLOWING SHOULD BE \"ok\": " + ok);

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
            mailboxServerBufferedWriter.write("delete " + id + "\n");
            mailboxServerBufferedWriter.flush();
            String serverResponse = mailboxBufferedReader.readLine();

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
            mailboxServerBufferedWriter.write("show " + id + "\n");
            mailboxServerBufferedWriter.flush();


            String totalString = "";
            String readString = mailboxBufferedReader.readLine() + "\n";
            while ( ! readString.equals("ok\n")) {
                if (readString.startsWith("error")) {
                    shell.out().println("error - message integrity could not be verified because the message could not be found");
                    return;
                }
                totalString += readString;
                readString = mailboxBufferedReader.readLine() + "\n";
                System.out.printf("totalString:%n%s%n", totalString);
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
        return finalString.toString().substring(0,finalString.length()-1);
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

            for (String recipientEmail : recipientEmails) {

                String targetAddress;
                try {
                    targetAddress = getWholeSocketAddress(getDomainName(recipientEmail)); // format: ip-address:port
                } catch (MissingResourceException mre) {
                    shell.out().println("error could not find recipient: " + recipientEmail);
                    return;
                }

                String[] portDomain = targetAddress.split(":");
                assert(portDomain.length == 2);

                String response = "no response received yet";
                try {
                    Socket socket = new Socket(portDomain[0], parseInt(portDomain[1]));

                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    bufferedWriter.write("begin\n");
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    System.out.println("CLIENT READS LINE: " + response);
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("to " + to + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    System.out.println("CLIENT READS LINE: " + response);
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("from " + from + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    System.out.println("CLIENT READS LINE: " + response);
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("subject " + subject + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    System.out.println("CLIENT READS LINE: " + response);
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("data " + data + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    System.out.println("CLIENT READS LINE: " + response);
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("hash " + hash + '\n');
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    System.out.println("CLIENT READS LINE: " + response);
                    if (response.startsWith("error")) throw new IOException("custom");

                    bufferedWriter.write("send\n");
                    bufferedWriter.flush();
                    response = bufferedReader.readLine();
                    System.out.println("CLIENT READS LINE: " + response);
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

    @Override
    @Command
    public void shutdown() {
//                    bufferedWriter.write("quit\n");
//                    bufferedWriter.flush();
//                    line = bufferedReader.readLine();
//                    System.out.println("CLIENT READS LINE: " + line);


    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
