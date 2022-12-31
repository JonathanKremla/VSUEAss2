package dslab.nameserver;

import dslab.ComponentFactory;
import dslab.shell.NameserverShell;
import dslab.util.Config;

import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Nameserver implements INameserver, INameserverRemote {

    private final Config nsConfig;
    private final String domain;
    private final String registryPort;
    private final String registryHost;
    private final String rootId;
    private final InputStream in;
    private final PrintStream out;
    private ConcurrentHashMap<String, String> mailboxMap = new ConcurrentHashMap<>();
    private Registry registry;
    private NameserverShell shell;
    private boolean isRoot;
    private ConcurrentHashMap<String, INameserverRemote> children = new ConcurrentHashMap<>();


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
        } catch (RemoteException | AlreadyBoundException | AlreadyRegisteredException | InvalidDomainException |
                 NotBoundException e) {
            e.printStackTrace();
        }
        try {
            shell = ComponentFactory.createNameserverShell(this, "shell-ns", in, out);
            shell.run();
        } catch (Exception e) {
            e.printStackTrace();
            shutdown();
        }
        shutdown();
    }

    @Override
    public void nameservers() {
        var servers = Collections.list(children.keys());
        Collections.sort(servers);
        for (String server : servers) {
            shell.println(server);
        }
    }

    @Override
    public void addresses() {
        var addresses = Collections.list(mailboxMap.keys());
        Collections.sort(addresses);
        for (String address : addresses) {
            shell.println(address + " " + mailboxMap.get(address));
        }
    }

    @Override
    public void shutdown() {
        try {
            if (isRoot) {
                UnicastRemoteObject.unexportObject(registry, true);
            }
            UnicastRemoteObject.unexportObject(this, true);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        shell.println("Register Nameserver on domain: " + domain);
        String[] zones = domain.split("\\.");
        if (zones.length == 0) {
            zones = new String[]{domain};
        }
        if (zones.length > 1) {
            String nextSubDomain = zones[zones.length - 1];
            String remainingDomain = domain.substring(0, domain.length() - nextSubDomain.length() - 1);
            children.get(nextSubDomain).registerNameserver(remainingDomain, this);
        } else {
            if (this.children.get(zones[0]) == null) {
                this.children.put(zones[0], nameserver);
            } else {
                throw new AlreadyRegisteredException("Name server managing zone: " + zones[0] + " already exists");
            }
        }

    }

    private void registerRootServer(INameserverRemote remote) throws RemoteException, AlreadyBoundException {
        this.isRoot = true;
        registry = LocateRegistry.createRegistry(Integer.parseInt(this.registryPort));
        registry.bind(rootId, remote);
    }

    private void registerZoneServer(INameserverRemote remote) throws RemoteException, AlreadyRegisteredException, InvalidDomainException, NotBoundException {
        this.isRoot = false;
        registry = LocateRegistry.getRegistry(registryHost, Integer.parseInt(registryPort));
        Registry registry = LocateRegistry.getRegistry(registryHost, Integer.parseInt(registryPort));
        INameserverRemote root = (INameserverRemote) registry.lookup(rootId);
        root.registerNameserver(domain, remote);
    }


    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        shell.println("Register Mailbox Server on domain " + domain + " with address: " + address);
        String[] zones = domain.split("\\.");
        if (domain.contains(".")) {
            String nextSubDomain = zones.length > 1 ? zones[zones.length - 1] : zones[0];
            String remainingDomain = zones.length > 1
                    ? domain.substring(0, domain.length() - nextSubDomain.length() - 1)
                    : "";
            var nextNameserver = children.get(nextSubDomain);
            if (nextNameserver == null) {
                throw new InvalidDomainException("Domain: " + domain + " not found");
            }
            nextNameserver.registerMailboxServer(remainingDomain, address);
        } else {
            if (mailboxMap.get(domain) != null) {
                throw new AlreadyRegisteredException("Mailbox server with domain " + domain + " and address: " + address + " already registered");
            }
            mailboxMap.put(domain, address);
        }
    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        shell.println("Get Nameserver of zone: " + zone);
        return children.get(zone);
    }

    @Override
    public String lookup(String username) throws RemoteException {
        shell.println("lookup domain: " + username);
        return mailboxMap.get(username);
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }

}
