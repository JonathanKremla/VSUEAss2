package dslab.client;

import java.io.InputStream;
import java.io.PrintStream;

import dslab.ComponentFactory;
import dslab.util.Config;

public class MessageClient implements IMessageClient, Runnable {

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
    //TODO
    }

    @Override
    public void run() {

    }

    @Override
    public void inbox() {

    }

    @Override
    public void delete(String id) {

    }

    @Override
    public void verify(String id) {

    }

    @Override
    public void msg(String to, String subject, String data) {

    }

    @Override
    public void shutdown() {

    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
