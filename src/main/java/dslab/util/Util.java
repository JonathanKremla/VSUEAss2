package dslab.util;

public class Util {
    public static String getWholeSocketAddress(String domain) {
        Config domainsConfig = new Config("domains.properties");
        return domainsConfig.getString(domain);
    }

    public static String getDomainName(String email) {
        return email.split("@")[1];
    }
}
