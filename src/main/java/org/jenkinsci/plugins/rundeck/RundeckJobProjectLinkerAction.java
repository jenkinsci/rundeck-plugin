package org.jenkinsci.plugins.rundeck;

import hudson.model.Action;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.rundeck.client.RundeckManager;
import org.kohsuke.stapler.export.Exported;
import org.rundeck.client.api.model.JobItem;

/**
 * {@link Action} used to display a Rundeck icon with a link to the Rundeck job page, and some information about the
 * Rundeck job, on the Jenkins job page.
 *
 * @author Vincent Behar
 */
public class RundeckJobProjectLinkerAction implements Action {

    private final String rundeckInstanceName;

    private final RundeckManager rundeck;

    private final JobItem rundeckJob;

    private final String rundeckJobUrl;

    /**
     * Load the Rundeck job details (name, description, and so on) using the Rundeck API.
     *
     * @param rundeckInstanceName Rundeck instance name
     * @param rundeck client used for talking to the Rundeck API
     * @param rundeckJobId ID of the Rundeck job
     * @throws Exception in case of error while loading the job details from Rundeck API
     * @throws IllegalArgumentException if rundeck or rundeckJobId is null
     */
    public RundeckJobProjectLinkerAction(String rundeckInstanceName, RundeckManager rundeck, String rundeckJobId) throws Exception,
            IllegalArgumentException {
        if (rundeck == null) {
            throw new IllegalArgumentException("rundeckClient should not be null !");
        }
        this.rundeckInstanceName = rundeckInstanceName;
        this.rundeck = rundeck;
        this.rundeckJob = RundeckNotifier.RundeckDescriptor.findJob(rundeckJobId, rundeckInstanceName, rundeck);
        if (rundeckJob == null) {
            throw new IllegalArgumentException("Could not find a job with the identifier : " + rundeckJobId);
        }
        this.rundeckJobUrl = buildRundeckJobUrl();
    }

    /**
     * Build the absolute url to the Rundeck job page.
     *
     * @return the absolute url to the Rundeck job page, or null if unable to build it
     */
    private String buildRundeckJobUrl() {
        StringBuilder url = new StringBuilder();
        url.append(rundeck.getRundeckInstance().getUrl());
        if (!StringUtils.endsWith(rundeck.getRundeckInstance().getUrl(), "/")) {
            url.append("/");
        }
        url.append("job/show/");
        url.append(rundeckJob.getId());
        return url.toString();
    }

    @Exported
    public JobItem getRundeckJob() {
        return rundeckJob;
    }

    public String getIconFileName() {
        return "/plugin/rundeck/images/rundeck_24x24.png";
    }

    public String getDisplayName() {
        return "Job: [" + rundeckJob.getProject() + "] " + rundeckJob.getName();
    }

    public String getUrlName() {
        return rundeckJobUrl;
    }

    public String getInstanceName() {
        return rundeckInstanceName;
    }
}
