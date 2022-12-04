package dslab.mailbox;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * Communication Interface with Client through socket
 * Essentially wraps the functionality of Connecting and
 * Communicating with a Client via a TCP Port.
 */
public class ClientCommunicator {

  private final ServerSocket serverSocket;
  private final Log LOG = LogFactory.getLog(ClientCommunicator.class);
  private Socket socket;
  private BufferedReader reader;
  private PrintWriter writer;

  public ClientCommunicator(ServerSocket serverSocket) {
    this.serverSocket = serverSocket;
  }


  public boolean establishConnection() {
    try {
      socket = serverSocket.accept();
      reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      writer = new PrintWriter(socket.getOutputStream());
    } catch (SocketException e) {
      // when the socket is closed, the I/O methods of the Socket will throw a SocketException
      // almost all SocketException cases indicate that the socket was closed
      LOG.error("SocketException while handling socket: " + e.getMessage());
      return false;
    } catch (IOException e) {
      // you should properly handle all other exceptions
      throw new UncheckedIOException(e);
    }
    return true;
  }

  public String readLine() {
    try {
      return reader.readLine();
    } catch (IOException e) {
      LOG.error(e.getMessage());
      return null;
    } catch (NullPointerException e) {
      return null;
    }
  }

  public void println(String line) {
    writer.println(line);
  }

  public void flush() {
    writer.flush();
  }

  public void close() {
    try {
      socket.close();
      reader.close();
      writer.close();
    } catch (IOException e) {
      LOG.error(e.getMessage());
    }
  }
}
