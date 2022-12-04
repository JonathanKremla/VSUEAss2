package dslab.monitoring;

/**
 * The monitoring service accepts incoming monitoring packets via UDP. It provides CLI commands to access the
 * information.
 * <p>
 * Do not change the existing method signatures!
 */
public interface IMonitoringServer extends Runnable {

  /**
   * Starts the server.
   */
  @Override
  void run();

  /**
   * CLI command to shut down the server. After this method, all resources should be closed, and the application
   * should terminate.
   */
  void shutdown();

  /**
   * CLI command to report usage statistics for transfer servers.
   */
  void servers();

  /**
   * CLI command to report usage statistics for individual senders.
   */
  void addresses();

}
