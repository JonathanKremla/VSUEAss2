package dslab.mailbox;

import static dslab.StringMatches.matchesPattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dslab.ComponentFactory;
import dslab.Constants;
import dslab.JunitSocketClient;
import dslab.Sockets;
import dslab.TestBase;
import dslab.util.Config;

public class MailboxStartsecureTest extends TestBase {

    private static final Log LOG = LogFactory.getLog(MailboxServerProtocolTest.class);


    private int dmapServerPort;
    private int dmtpServerPort;

    @Before
    public void setUp() throws Exception {
        String componentId = "mailbox-earth-planet";

        IMailboxServer component = ComponentFactory.createMailboxServer(componentId, in, out);
        dmapServerPort = new Config(componentId).getInt("dmap.tcp.port");
        dmtpServerPort = new Config(componentId).getInt("dmtp.tcp.port");

        new Thread(component).start();

        LOG.info("Waiting for server sockets to appear");
        Sockets.waitForSocket("localhost", dmapServerPort, Constants.COMPONENT_STARTUP_WAIT);
        Sockets.waitForSocket("localhost", dmtpServerPort, Constants.COMPONENT_STARTUP_WAIT);
    }

    @After
    public void tearDown() throws Exception {
        in.addLine("shutdown"); // send "shutdown" command to command line
        Thread.sleep(Constants.COMPONENT_TEARDOWN_WAIT);
    }

    @Test(timeout = 15000)
    public void sendStartsecure() throws Exception {

        // a challenge, aes secret and iv param encrypted with the server's RSA key
        String testChallenge = "wTZqUdwD6RIWtgTrvoYecJgulKRQVActTzbaW7u4i0puTak8ymlUHmvVQGT6wCUVoByDaF3dEhRFku5uC4kap" +
                "9yd2FntrtIcuftaf36WSU/Qg2ue254TiEVmCLILd2eef8SxHh6U0hyWwXPdD+BHBplzrBeIIiTPqLteKKHl6veEzuEh+s/u66hcy" +
                "PG+3t18C4ZR1jo50VZhAa9Kfqeuj787llQTZMMv+2gEIRciKPu8pF5/57+hmOmcp+mAoBaK0XdjTZ1Win4bF1CP44sdHLgKy2Bfv" +
                "Gn69RN7ThWBEu8fXuBsxcflhLDus1OIlDv8YgoLVGiOCamtZf0TtqcErg==";

        try (JunitSocketClient client = new JunitSocketClient(dmapServerPort, err)) {
            // protocol check
            client.verify("ok DMAP2.0");

            // check that mailbox returns its component id
            client.sendAndVerify("startsecure", "ok mailbox-earth-planet");

            // send the challenge + aes init
            client.send(testChallenge);

            // response should be "ok <challenge>" (which is AES encrypted and base64 encoded)
            // specifically it should be g9UJxNFULO+H0otZoH5AVXoHv9TxJUEcbY/ScWoWMvcJYLz2lYBaZ16OtqEKtVk=
            err.checkThat("Expected server response to be Base64 encoded", client.listen(),
                    matchesPattern("^(?:[a-zA-Z0-9+/]{4})*(?:[a-zA-Z0-9+/]{2}==|[a-zA-Z0-9+/]{3}=)?$"));

            // send encrypted "ok" (with the aes cipher) in
            client.send("g9U=");
        }
    }


}
