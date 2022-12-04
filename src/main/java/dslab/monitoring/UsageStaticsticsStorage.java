package dslab.monitoring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Storage for Usage Statistics
 */
public class UsageStaticsticsStorage {
  //stores Usage statistics in form <host>:<port> <email-address>
  private static final List<String> usageStatistics = new ArrayList<>();
  private static final Log LOG = LogFactory.getLog(UsageStaticsticsStorage.class);

  public static boolean add(String usageStatistic) {
    usageStatistic = usageStatistic.split("\n")[0];
    if (!usageStatistic.matches("(.*):[0-9]* (.*)@(.*)")) {
      return false;
    }
    usageStatistics.add(usageStatistic);
    return true;
  }

  public static List<String> addresses() {
    LOG.info("addresses()");
    List<String> addresses = new ArrayList<>();
    HashMap<String, Integer> count = new HashMap<>();
    for (String usageStatistic : usageStatistics) {
      String address = usageStatistic.split(" ")[1];
      int c = count.get(address) == null ? 0 : count.get(address);
      count.put(address, c + 1);
      if (!addresses.contains(address)) {
        addresses.add(address);
      }
    }
    return addresses.stream().map(x -> x + " " + count.get(x)).collect(Collectors.toList());
  }

  public static List<String> servers() {
    LOG.info("servers()");
    List<String> servers = new ArrayList<>();
    HashMap<String, Integer> count = new HashMap<>();
    for (String usageStatistic : usageStatistics) {
      String server = usageStatistic.split(" ")[0];
      int c = count.get(server) == null ? 0 : count.get(server);
      count.put(server, c + 1);
      if (!servers.contains(server)) {
        servers.add(server);
      }
    }
    return servers.stream().map(x -> x + " " + count.get(x)).collect(Collectors.toList());
  }

  public static void clear() {
    usageStatistics.clear();
  }

}
