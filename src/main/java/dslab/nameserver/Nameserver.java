package dslab.nameserver;

import dslab.ComponentFactory;
import dslab.util.Config;

import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Nameserver implements INameserver, INameserverRemote {

  private final Config nsConfig;
  private final String domain;
  private final String registryPort;
  private final String registryHost;
  private final String rootId;
  private final InputStream in;
  private final PrintStream out;
  private Registry registry;
  private ConcurrentLinkedQueue<INameserverRemote> children = new ConcurrentLinkedQueue<>();


  /**
   * Creates a new server instance.
   *
   * @param componentId the id of the component that corresponds to the Config resource
   * @param config      the component config
   * @param in          the input stream to read console input from
   * @param out         the output stream to write console output to
   */
  public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
    String domain1;
    this.in = in;
    this.out = out;
    this.nsConfig = config;
    try {
      domain1 = nsConfig.getString("domain");
    } catch (MissingResourceException e) {
      domain1 = "";
    }
    this.domain = domain1;
    this.registryPort = nsConfig.getString("registry.port");
    this.registryHost = nsConfig.getString("registry.host");
    this.rootId = nsConfig.getString("root_id");
  }

  @Override
  public void run() {
    try {
      INameserverRemote remote = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);
      if (Objects.equals(this.domain, "")) {
        registerRootServer(remote);
      } else {
        registerZoneServer(remote);
      }
    }
    catch (RemoteException | AlreadyBoundException | AlreadyRegisteredException | InvalidDomainException e) {
      e.printStackTrace();
    }
  }

  private void registerRootServer(INameserverRemote remote) throws RemoteException, AlreadyBoundException {
    registry = LocateRegistry.createRegistry(Integer.parseInt(this.registryPort));
    // bind the obtained remote object on specified binding name in the registry
    registry.bind(rootId, remote);

  }

  private void registerZoneServer(INameserverRemote remote) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
    registry = LocateRegistry.getRegistry(registryHost, Integer.parseInt(registryPort));
    remote.registerNameserver(domain, this);
  }

  @Override
  public void nameservers() {
    // TODO
  }

  @Override
  public void addresses() {
    // TODO
  }

  @Override
  public void shutdown() {
    // TODO
  }

  @Override
  public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
    String[] zones = domain.split(".");
    if (zones.length > 1) {
      registerNameserver(domain.substring(0, domain.length() - zones[zones.length - 1].length() - 1), this);
    } else {
      this.children.add(nameserver);
    }

  }

  @Override
  public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

  }

  @Override
  public INameserverRemote getNameserver(String zone) throws RemoteException {
    return null;
  }

  @Override
  public String lookup(String username) throws RemoteException {
    return null;
  }

  public static void main(String[] args) throws Exception {
    INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
    component.run();
  }

}
