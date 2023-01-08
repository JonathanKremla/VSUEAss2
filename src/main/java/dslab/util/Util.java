package dslab.util;

import java.util.Base64;

public class Util {
    public static String getWholeSocketAddress(String domain) {
        Config domainsConfig = new Config("domains.properties");
        return domainsConfig.getString(domain);
    }

    public static String getDomainName(String email) {
        return email.split("@")[1];
    }

    public static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] decode(String data) {
        return Base64.getDecoder().decode(data);
    }
}
