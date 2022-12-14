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
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.SecretKey;
import javax.crypto.Mac;

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

            // todo: here the code for "startsecure" will be inserted
            mailboxServerBufferedWriter.write(
                    "login "
                    + config.getString("mailbox.user")
                    + " "
                    + config.getString("mailbox.password")
                    + "\n"
            );
            mailboxServerBufferedWriter.flush();

            line =  mailboxBufferedReader.readLine();
            System.out.println("MAILBOX RESPONSE AFTER LOGIN: " + line);

            shell.run();
        } catch (IOException e) {
            System.out.println("ERROR client socket");
        }
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
     */
    private String parseIntoFormat(String wholeMessageString) {
        StringBuilder finalString = new StringBuilder();
        String[] lines = wholeMessageString.split("\n");

        for (String line : lines) {
            if (line.startsWith("hash") || line.equals("ok")) continue;

            finalString
                    .append(line.split(" ", 2)[1])
                    .append("\n");
        }

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

            // because the format is: msg <to> "<subject>" "<data>"
            String subjectWithoutQuotes = subject.substring(1,subject.length()-1);
            String dataWithoutQuotes = data.substring(1,data.length()-1);

            String messageTextWithoutHash = "";
            messageTextWithoutHash += from + "\n";
            messageTextWithoutHash += to + "\n";
            messageTextWithoutHash += subjectWithoutQuotes + "\n";
            messageTextWithoutHash += dataWithoutQuotes + "\n";

            String formattedText = parseIntoFormat(messageTextWithoutHash);

            byte[] hashBytes = computeHash(formattedText);

            String hash = new String(hashBytes, StandardCharsets.UTF_8);

            String[] emails = to.split(",");
            if (emails.length == 0) throw new RuntimeException("Fatal Error 1");

            for (String email : emails) {
                String targetAddress = getWholeSocketAddress(getDomainName(email)); // format: ip-address:port
                String[] portDomain = targetAddress.split(":");
                assert(portDomain.length == 2);
                Socket socket = new Socket(portDomain[0], parseInt(portDomain[1]));

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                // todo still: print error <optionally a description> when message sending failed for any reason
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

                bufferedWriter.write("subject " + subjectWithoutQuotes + '\n');
                bufferedWriter.flush();
                line = bufferedReader.readLine();
                System.out.println("CLIENT READS LINE: " + line);

                bufferedWriter.write("data " + dataWithoutQuotes + '\n');
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

                shell.out().println("ok");
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    @Override
    @Command
    public void shutdown() {

    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
