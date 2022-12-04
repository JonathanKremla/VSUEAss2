package dslab.shell;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.monitoring.MonitoringServer;

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Shell for {@link MonitoringServer} it is used to block the main Thread
 * and listens for Commands sent over the Command line
 * Available Commands:
 * 'shutdown'
 * 'addresses'
 * 'servers'
 */
public class MonitoringShell implements IShell, Runnable {
  private Shell shell;
  private MonitoringServer server;

  public MonitoringShell(MonitoringServer server, String componentId, InputStream inputStream, PrintStream outputStream) {
    /*
     * First, create a new Shell instance and provide an InputStream to read from,
     * as well as an OutputStream to write to. If you want to test the application
     * manually, simply use System.in and System.out.
     */
    shell = new Shell(inputStream, outputStream);
    /*
     * Next, register all commands the Shell should support. In this example
     * this class implements all desired commands.
     */
    shell.register(this);

    /*
     * The prompt of a shell is just a visual aid that indicates that the shell
     * can read a command. Note that the prompt may not be output correctly when
     * running the application via ant.
     */
    shell.setPrompt(componentId + "> ");
    this.server = server;
  }

  @Override
  @Command
  public void shutdown() {
    throw new StopShellException();
  }

  /**
   * implements the addresses command
   * Lists all addresses and the number of messages they have sent (aggregated over all servers).
   * Example output:
   * zaphod@univer.ze 12
   * trillian@earth.planet 4
   */
  @Command
  public void addresses() {
    server.addresses();
  }

  /**
   * Lists all servers and the number of messages sent over these servers.
   * Example output:
   * 10.0.0.1:1337 321
   * 10.0.0.2:1338 512
   */
  @Command
  public void servers() {
    server.servers();
  }


  @Override
  public void run() {
    /*
     * Finally, make the Shell process the commands read from the
     * InputStream by invoking Shell.run(). Note that Shell implements the
     * Runnable interface, so you could theoretically run it in a new thread.
     * However, it is typically desirable to have one process blocking the main
     * thread. Reading from System.in (which is what the Shell does) is a good
     * candidate for this.
     */
    shell.run();

    /*
     * The run method blocks until the read loop exits. To exit the loop
     * programmatically, a Command method may throw a StopShellException, which is
     * caught inside the Shell run method, causing the loop to break gracefully.
     */
    System.out.println("Exiting the shell, bye!");

  }
}
