package dslab.transfer;

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

/**
 * TransferServerProtocolTest.
 */
public class TransferServerProtocolTest extends TestBase {

    private static final Log LOG = LogFactory.getLog(TransferServerProtocolTest.class);

    private String componentId = "transfer-1";

    private ITransferServer component;
    private int serverPort;

    @Before
    public void setUp() throws Exception {
        component = ComponentFactory.createTransferServer(componentId, in, out);
        serverPort = new Config(componentId).getInt("tcp.port");
        new Thread(component).start();

        LOG.info("Waiting for server socket to appear");
        Sockets.waitForSocket("localhost", serverPort, Constants.COMPONENT_STARTUP_WAIT);
    }

    @After
    public void tearDown() throws Exception {
        in.addLine("shutdown"); // send "shutdown" command to command line
        Thread.sleep(Constants.COMPONENT_TEARDOWN_WAIT);
    }

    @Test(timeout = 15000)
    public void defaultDmtpInteractionTest() throws Exception {
        try (JunitSocketClient client = new JunitSocketClient(serverPort, err)) {
            client.verify("ok DMTP");
            client.sendAndVerify("begin", "ok");
            client.sendAndVerify("from trillian@earth.planet", "ok");
            client.sendAndVerify("to arthur@earth.planet", "ok 1");
            client.sendAndVerify("subject hello", "ok");
            client.sendAndVerify("data hello from junit", "ok");
            client.sendAndVerify("send", "ok");
            client.sendAndVerify("quit", "ok bye");
        }
    }

    @Test(timeout = 15000)
    public void sendWithoutRecipient_returnsErrorOnSend() throws Exception {
        try (JunitSocketClient client = new JunitSocketClient(serverPort, err)) {
            client.verify("ok DMTP");
            client.sendAndVerify("begin", "ok");
            client.sendAndVerify("from trillian@earth.planet", "ok");
            client.sendAndVerify("subject hello", "ok");
            client.sendAndVerify("data hello from junit", "ok");
            client.sendAndVerify("send", "error");
            client.sendAndVerify("quit", "ok bye");
        }
    }

}
