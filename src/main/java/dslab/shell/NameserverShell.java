package dslab.shell;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.nameserver.Nameserver;

import java.io.InputStream;
import java.io.PrintStream;

public class NameserverShell implements IShell {

  private Shell shell;
  private Nameserver server;

  public NameserverShell(Nameserver nameserver, String componentId, InputStream inputStream, PrintStream outputStream) {
    shell = new Shell(inputStream, outputStream);
    shell.register(this);
    shell.setPrompt(componentId + "> ");
    this.server = nameserver;
  }

  @Command
  @Override
  public void shutdown() {
    throw new StopShellException();

  }

  @Command
  public void nameservers() {
    server.nameservers();
  }

  @Command
  public void addresses() {
    server.addresses();
  }

  @Override
  public void run() {
    shell.run();
    System.out.println("Exiting the shell, bye!");

  }
  public void println(String line) {
    shell.out().println(line);
  }
}
