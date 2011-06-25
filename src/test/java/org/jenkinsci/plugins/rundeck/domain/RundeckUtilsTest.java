package org.jenkinsci.plugins.rundeck.domain;

import java.io.InputStream;
import java.util.Date;
import java.util.Properties;
import junit.framework.TestCase;
import org.jenkinsci.plugins.rundeck.domain.RundeckExecution.ExecutionStatus;

/**
 * Test the helper methods
 * 
 * @author Vincent Behar
 */
public class RundeckUtilsTest extends TestCase {

    public void testGenerateArgString() throws Exception {
        assertNull(RundeckUtils.generateArgString(null));
        assertEquals("", RundeckUtils.generateArgString(new Properties()));

        Properties options = new Properties();
        options.put("key1", "value1");
        options.put("key2", "value 2 with spaces");
        String argString = RundeckUtils.generateArgString(options);
        if (argString.startsWith("-key1")) {
            assertEquals("-key1 value1 -key2 'value 2 with spaces'", argString);
        } else {
            assertEquals("-key2 'value 2 with spaces' -key1 value1", argString);
        }
    }

    public void testParseJobDefinition() throws Exception {
        InputStream input = getClass().getResourceAsStream("job-definition.xml");
        RundeckJob job = RundeckUtils.parseJobDefinition(input);

        assertEquals("1", job.getId());
        assertEquals("job-name", job.getName());
        assertEquals("job description", job.getDescription());
        assertEquals("group-name", job.getGroup());
        assertEquals("project-name", job.getProject());
    }

    public void testParseJobDefinitionUnknown() throws Exception {
        InputStream input = getClass().getResourceAsStream("job-definition-unknown.xml");

        try {
            RundeckUtils.parseJobDefinition(input);
            fail("should have thrown an exception !");
        } catch (RundeckApiException e) {
            assertEquals("Job ID does not exist: 42", e.getMessage());
        }
    }

    public void testParseExecutionSuccess() throws Exception {
        InputStream input = getClass().getResourceAsStream("job-run-success.xml");
        RundeckExecution execution = RundeckUtils.parseExecution(input);
        RundeckJob job = execution.getJob();

        assertEquals(new Long(1), execution.getId());
        assertEquals("http://localhost:4440/execution/follow/1", execution.getUrl());
        assertEquals(ExecutionStatus.RUNNING, execution.getStatus());
        assertEquals("admin", execution.getStartedBy());
        assertEquals(new Date(1302183830082L), execution.getStartedAt());
        assertEquals(null, execution.getAbortedBy());
        assertEquals(null, execution.getEndedAt());
        assertEquals("ls ${option.dir}", execution.getDescription());

        assertEquals("1", job.getId());
        assertEquals("ls", job.getName());
        assertEquals("test", job.getGroup());
        assertEquals("test", job.getProject());
        assertEquals(null, job.getDescription());
    }

    public void testParseExecutionFailure() throws Exception {
        InputStream input = getClass().getResourceAsStream("job-run-failure.xml");

        try {
            RundeckUtils.parseExecution(input);
            fail("should have thrown an exception !");
        } catch (RundeckApiException e) {
            assertEquals("Option 'dir' is required. ", e.getMessage());
        }
    }

}
