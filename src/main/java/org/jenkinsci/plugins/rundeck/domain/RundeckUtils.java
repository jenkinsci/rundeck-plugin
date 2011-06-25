package org.jenkinsci.plugins.rundeck.domain;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Map.Entry;
import org.apache.commons.lang.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.jenkinsci.plugins.rundeck.domain.RundeckExecution.ExecutionStatus;

/**
 * Helper methods for working with RunDeck
 * 
 * @author Vincent Behar
 */
public class RundeckUtils {

    /**
     * Generates an "argString" representing the given options. Format of the argString is
     * <code>"-key1 value1 -key2 'value 2 with spaces'"</code>
     * 
     * @param options to be converted
     * @return a string. null if options is null, empty if there are no valid options.
     */
    public static String generateArgString(Properties options) {
        if (options == null) {
            return null;
        }

        StringBuilder argString = new StringBuilder();
        for (Entry<Object, Object> option : options.entrySet()) {
            String key = String.valueOf(option.getKey());
            String value = String.valueOf(option.getValue());

            if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                if (argString.length() > 0) {
                    argString.append(" ");
                }
                argString.append("-").append(key);
                argString.append(" ");
                if (value.indexOf(" ") >= 0
                    && !(0 == value.indexOf("'") && (value.length() - 1) == value.lastIndexOf("'"))) {
                    argString.append("'").append(value).append("'");
                } else {
                    argString.append(value);
                }
            }
        }
        return argString.toString();
    }

    /**
     * Parse the result of a "job details" action
     * 
     * @param response as {@link InputStream}
     * @return a {@link RundeckJob} instance - won't be null
     * @throws RundeckApiException in case of error when reading the result
     */
    public static RundeckJob parseJobDefinition(InputStream response) throws RundeckApiException {
        SAXReader reader = new SAXReader();
        reader.setEncoding("UTF-8");
        Document document;
        try {
            document = reader.read(response);
        } catch (DocumentException e) {
            throw new RundeckApiException("Failed to read RunDeck reponse", e);
        }
        document.setXMLEncoding("UTF-8");

        Boolean failure = Boolean.valueOf(document.valueOf("result/@error"));
        if (failure) {
            throw new RundeckApiException(document.valueOf("result/error/message"));
        }

        Node jobNode = document.selectSingleNode("joblist/job");

        RundeckJob job = new RundeckJob();
        job.setId(jobNode.valueOf("id"));
        job.setName(jobNode.valueOf("name"));
        job.setDescription(jobNode.valueOf("description"));
        job.setGroup(jobNode.valueOf("group"));
        job.setProject(jobNode.valueOf("context/project"));
        return job;
    }

    /**
     * Parse the result of a job execution
     * 
     * @param response as {@link InputStream}
     * @return a {@link RundeckExecution} instance - won't be null
     * @throws RundeckApiException in case of error when reading the result
     */
    public static RundeckExecution parseExecution(InputStream response) throws RundeckApiException {
        SAXReader reader = new SAXReader();
        reader.setEncoding("UTF-8");
        Document document;
        try {
            document = reader.read(response);
        } catch (DocumentException e) {
            throw new RundeckApiException("Failed to read RunDeck reponse", e);
        }
        document.setXMLEncoding("UTF-8");

        Boolean success = Boolean.valueOf(document.valueOf("result/@success"));
        if (!success) {
            throw new RundeckApiException(document.valueOf("result/error/message"));
        }

        @SuppressWarnings("unchecked")
        List<Node> execNodes = document.selectNodes("result/executions/execution");
        Node execNode = execNodes.get(0);

        RundeckExecution execution = new RundeckExecution();
        execution.setId(Long.valueOf(execNode.valueOf("@id")));
        execution.setUrl(execNode.valueOf("@href"));
        execution.setStatus(ExecutionStatus.valueOf(StringUtils.upperCase(execNode.valueOf("@status"))));
        execution.setDescription(StringUtils.trimToNull(execNode.valueOf("description")));
        execution.setStartedBy(execNode.valueOf("user"));
        execution.setStartedAt(new Date(Long.valueOf(execNode.valueOf("date-started/@unixtime"))));
        execution.setAbortedBy(StringUtils.trimToNull(execNode.valueOf("abortedby")));
        String endedAt = StringUtils.trimToNull(execNode.valueOf("date-ended/@unixtime"));
        if (endedAt != null) {
            execution.setEndedAt(new Date(Long.valueOf(endedAt)));
        }

        Node jobNode = execNode.selectSingleNode("job");
        if (jobNode != null) {
            RundeckJob job = new RundeckJob();
            job.setId(jobNode.valueOf("@id"));
            job.setName(jobNode.valueOf("name"));
            job.setGroup(StringUtils.trimToNull(jobNode.valueOf("group")));
            job.setProject(jobNode.valueOf("project"));
            job.setDescription(StringUtils.trimToNull(jobNode.valueOf("description")));
            execution.setJob(job);
        }

        return execution;
    }
}
