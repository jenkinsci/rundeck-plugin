package org.jenkinsci.plugins.rundeck.domain;

import java.util.Properties;
import junit.framework.TestCase;
import org.jenkinsci.plugins.rundeck.domain.RundeckApiException.RundeckApiJobRunException;
import org.jenkinsci.plugins.rundeck.domain.RundeckApiException.RundeckApiLoginException;

/**
 * Test the integration with a {@link RundeckInstance} : login, run job, etc. Does nothing if the RunDeck instance is
 * not alive.
 * 
 * @author Vincent Behar
 */
public class RundeckInstanceTest extends TestCase {

    private RundeckInstance rundeck;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        rundeck = new RundeckInstance("http://localhost:4440", "user", "user");
    }

    public void testInvalidLogin() throws Exception {
        rundeck = new RundeckInstance(rundeck.getUrl(), "invalid", "invalidToo");
        if (rundeck.isAlive()) {
            try {
                rundeck.runJob("1", null);
                fail("login for an invalid user should have failed !");
            } catch (RundeckApiLoginException e) {
                assertNull("Login failure for invalid user should not have a cause ! cause : " + e.getCause(),
                           e.getCause());
            }
        } else {
            System.out.println("No live RunDeck instance at " + rundeck.getUrl() + " - doing nothing...");
        }
    }

    public void testInvalidJob() throws Exception {
        if (rundeck.isAlive()) {
            try {
                rundeck.runJob("4242424242", null);
                fail("running an invalid job should have failed !");
            } catch (RundeckApiJobRunException e) {
                assertNull("failure when running an invalid job should not have a cause ! cause : " + e.getCause(),
                           e.getCause());
            }
        } else {
            System.out.println("No live RunDeck instance at " + rundeck.getUrl() + " - doing nothing...");
        }
    }

    public void testValidJob() throws Exception {
        if (rundeck.isAlive()) {
            Properties options = new Properties();
            options.setProperty("command", "ls");
            options.setProperty("dir", "/tmp");
            RundeckExecution execution = rundeck.runJob("1", options);
            assertEquals(rundeck.getLogin(), execution.getStartedBy());
        } else {
            System.out.println("No live RunDeck instance at " + rundeck.getUrl() + " - doing nothing...");
        }
    }
}
