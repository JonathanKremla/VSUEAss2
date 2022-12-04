package dslab.mailbox;

import dslab.util.Config;
import dslab.util.datastructures.Email;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stores all Messages of all Users
 */
public class MessageStorage {
  //Stores Email with according Index
  private static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, Email>> messages = new ConcurrentHashMap<>();
  //For each Key(user) the value represents the index at which the next email should be saved at
  //Atomic is needed here as otherwise 2 Threads could access the same index before it was
  // incremented by one and therefore put their Email on the same index
  private static final ConcurrentHashMap<String, AtomicInteger> indexMap = new ConcurrentHashMap<>();
  private static final Log LOG = LogFactory.getLog(MessageStorage.class);

  /**
   * Executed once to load all Users from the userConfig
   *
   * @param userConfig config in which users are defined
   */
  public static void loadUsers(Config userConfig) {
    for (String k : userConfig.listKeys()) {
      messages.put(k, new ConcurrentHashMap<>());
      indexMap.put(k, new AtomicInteger(1));
    }
  }

  /**
   * Maps an Email to a user
   *
   * @param user  user to which the email should be mapped to
   * @param value Email to be mapped to user
   */
  public static synchronized void put(String user, Email value) {
    LOG.info("put:(user: " + user + " value: " + value.toString() + ")");
    messages.get(user) //Hashmap<Index, Email> of user
            .put(indexMap.get(user).getAndIncrement(), value);
  }

  /**
   * Removes the Email on the index from the Storage of the specified user
   *
   * @param user  user in whose storage the Email with the given index should be deleted
   * @param index index of the email
   */
  public static void remove(String user, Integer index) {
    messages.get(user).remove(index);
  }

  /**
   * Retrieves Email with given index of given user
   *
   * @param user  of which email should be retrieved from
   * @param index of the email to retrieve
   * @return the Email saved to the specified User on the specified index
   */
  public static Email get(String user, Integer index) {
    return messages.get(user).get(index);
  }

  /**
   * retrieves current index of user, the index is always the index of the last saved email for the user + 1
   *
   * @param user user of which the index should be retrieved
   * @return the retrieved Index
   */
  public static Integer getIndex(String user) {
    return indexMap.get(user).get();
  }

}
