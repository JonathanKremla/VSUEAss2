package dslab;

import java.io.InputStream;
import java.io.PrintStream;

import dslab.client.IMessageClient;
import dslab.client.MessageClient;
import dslab.mailbox.IMailboxServer;
import dslab.mailbox.MailboxServer;
import dslab.monitoring.IMonitoringServer;
import dslab.monitoring.MonitoringServer;
import dslab.nameserver.INameserver;
import dslab.nameserver.Nameserver;
import dslab.shell.BasicShell;
import dslab.shell.IShell;
import dslab.shell.MonitoringShell;
import dslab.transfer.ITransferServer;
import dslab.transfer.TransferServer;
import dslab.util.Config;

/**
 * The component factory provides methods to create the core components of the application. You can edit the method body
 * if the component instantiation requires additional logic.
 *
 * Do not change the existing method signatures!
 */
public final class ComponentFactory {

    private ComponentFactory() {
        // static utility class
    }

    /**
     * Creates a new {@link IMonitoringServer} instance.
     *
     * @param componentId the component id
     * @param in the input stream used for accepting cli commands
     * @param out the output stream to print to
     * @return a new MonitoringServer instance
     */
    public static IMonitoringServer createMonitoringServer(String componentId, InputStream in, PrintStream out)
            throws Exception {
        /*
         * TODO: Here you can modify the code (if necessary) to instantiate your components
         */

        Config config = new Config(componentId);
        return new MonitoringServer(componentId, config, in, out);
    }

    /**
     * Creates a new {@link IMailboxServer} instance.
     *
     * @param componentId the component id
     * @param in the input stream used for accepting cli commands
     * @param out the output stream to print to
     * @return a new MailboxServer instance
     */
    public static IMailboxServer createMailboxServer(String componentId, InputStream in, PrintStream out)
            throws Exception {
        /*
         * TODO: Here you can modify the code (if necessary) to instantiate your components
         */

        Config config = new Config(componentId);
        return new MailboxServer(componentId, config, in, out);
    }

    /**
     * Creates a new {@link ITransferServer} instance.
     *
     * @param componentId the component id
     * @param in the input stream used for accepting cli commands
     * @param out the output stream to print to
     * @return a new TransferServer instance
     */
    public static ITransferServer createTransferServer(String componentId, InputStream in, PrintStream out)
            throws Exception {
        /*
         * TODO: Here you can modify the code (if necessary) to instantiate your components
         */

        Config config = new Config(componentId);
        return new TransferServer(componentId, config, in, out);
    }

    /**
     * Creates a new {@link BasicShell} instance
     *
     * @param componentName name of the Shell
     * @param in            the input stream used for accepting commands
     * @param out           the output stream to print to
     * @return a new BasicShell instance
     */
    public static IShell createBasicShell(String componentName, InputStream in, PrintStream out)
            throws Exception {
        // Instantiate a new ShellExample with the given credentials and return
        // it
        return new BasicShell(componentName, in, out);
    }

    /**
     * Creates a new {@link MonitoringShell} instance
     *
     * @param server        {@link MonitoringServer} for which the Shell is created
     * @param componentName name of the Shell
     * @param in            the input stream used for accepting commands
     * @param out           the output stream to print to
     * @return a new MonitoringShell instance
     */
    public static IShell createMonitoringShell(MonitoringServer server, String componentName, InputStream in, PrintStream out)
            throws Exception {
        return new MonitoringShell(server, componentName, in, out);
    }

    /**
     * Creates a new {@link INameserver} instance.
     *
     * @param componentId the component id
     * @param in the input stream used for accepting cli commands
     * @param out the output stream to print to
     * @return a new Nameserver instance
     */
    public static INameserver createNameserver(String componentId, InputStream in, PrintStream out)
            throws Exception {
        /*
         * TODO: Here you can modify the code (if necessary) to instantiate your components
         */

        Config config = new Config(componentId);
        return new Nameserver(componentId, config, in, out);
    }

    /**
     * Creates a new {@link IMessageClient} instance.
     *
     * @param componentId the component id
     * @param in the input stream used for accepting cli commands
     * @param out the output stream to print to
     * @return a new MessageClient instance
     */
    public static IMessageClient createMessageClient(String componentId, InputStream in, PrintStream out)
            throws Exception {
        /*
         * TODO: Here you can modify the code (if necessary) to instantiate your components
         */

        Config config = new Config(componentId);
        return new MessageClient(componentId, config, in, out);
    }

}
