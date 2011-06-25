package org.jenkinsci.plugins.rundeck;

import hudson.model.Action;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.rundeck.domain.RundeckApiException;
import org.jenkinsci.plugins.rundeck.domain.RundeckInstance;
import org.jenkinsci.plugins.rundeck.domain.RundeckJob;
import org.kohsuke.stapler.export.Exported;

/**
 * {@link Action} used to display a RunDeck icon with a link to the RunDeck job page, and some information about the
 * RunDeck job, on the Jenkins job page.
 * 
 * @author Vincent Behar
 */
public class RundeckJobProjectLinkerAction implements Action {

    private final RundeckInstance rundeck;

    private final RundeckJob rundeckJob;

    private final String rundeckJobUrl;

    /**
     * Load the RunDeck job details (name, description, and so on) using the RunDeck API.
     * 
     * @param rundeck instance used for talking to the RunDeck API
     * @param rundeckJobId ID of the RunDeck job
     * @throws RundeckApiException in case of error while load the job details from RunDeck API
     */
    public RundeckJobProjectLinkerAction(RundeckInstance rundeck, String rundeckJobId) throws RundeckApiException {
        this.rundeck = rundeck;
        this.rundeckJob = rundeck.getJob(rundeckJobId);
        this.rundeckJobUrl = buildRundeckJobUrl();
    }

    /**
     * Build the absolute url to the RunDeck job page.
     * 
     * @return the absolute url to the RunDeck job page, or null if unable to build it
     */
    private String buildRundeckJobUrl() {
        StringBuilder url = new StringBuilder();
        url.append(rundeck.getUrl());
        if (!StringUtils.endsWith(rundeck.getUrl(), "/")) {
            url.append("/");
        }
        url.append("job/show/");
        url.append(rundeckJob.getId());
        return url.toString();
    }

    @Exported
    public RundeckJob getRundeckJob() {
        return rundeckJob;
    }

    public String getIconFileName() {
        return "/plugin/rundeck/images/rundeck_24x24.png";
    }

    public String getDisplayName() {
        return "RunDeck Job #" + rundeckJob.getId() + " : " + rundeckJob.getName();
    }

    public String getUrlName() {
        return rundeckJobUrl;
    }

}
