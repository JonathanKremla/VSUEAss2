package dslab.shell;

public interface IShell extends Runnable {

  /**
   * Performs a shutdown and a release of all resources.
   */
  void shutdown();
}
