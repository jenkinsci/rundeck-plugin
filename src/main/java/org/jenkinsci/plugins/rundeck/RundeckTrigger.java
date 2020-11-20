package org.jenkinsci.plugins.rundeck;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.AbstractProject;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.rundeck.api.domain.RundeckExecution;
import org.rundeck.api.domain.RundeckJob;

/**
 * Triggers a build when we receive a WebHook notification from Rundeck.
 * 
 * @author Vincent Behar
 */
public class RundeckTrigger extends Trigger<AbstractProject<?, ?>> {

    private final Boolean filterJobs;

    private final List<String> jobsIdentifiers;

    private final List<String> executionStatuses;

    @DataBoundConstructor
    public RundeckTrigger(Boolean filterJobs, List<String> jobsIdentifiers, List<String> executionStatuses) {
        this.filterJobs = filterJobs != null ? filterJobs : false;
        this.jobsIdentifiers = jobsIdentifiers != null ? jobsIdentifiers : new ArrayList<String>();
        this.executionStatuses = executionStatuses != null ? executionStatuses : Arrays.asList("SUCCEEDED");
    }

    /**
     * Called when we receive a Rundeck notification
     * 
     * @param execution at the origin of the notification
     */
    public void onNotification(RundeckExecution execution) {
        if (shouldScheduleBuild(execution)) {
            if (job != null) {
                job.scheduleBuild(new RundeckCause(execution));
            }
        }
    }

    /**
     * Filter notifications based on the {@link RundeckExecution} and the trigger configuration
     * 
     * @param execution at the origin of the notification
     * @return true if we should schedule a new build, false otherwise
     */
    private boolean shouldScheduleBuild(RundeckExecution execution) {
        if (!executionStatuses.contains(execution.getStatus().toString())) {
            return false;
        }
        if (!filterJobs) {
            return true;
        }
        for (String jobIdentifier : jobsIdentifiers) {
            if (identifierMatchesJob(jobIdentifier, execution.getJob())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the given jobIdentifier matches (= identifies) the given job
     * 
     * @param jobIdentifier could be either a job's UUID, or a reference to a job in the format "project:group/job"
     * @param job to test
     * @return true if it matches, false otherwise
     */
    private boolean identifierMatchesJob(String jobIdentifier, RundeckJob job) {
        if (job == null || StringUtils.isBlank(jobIdentifier)) {
            return false;
        }

        // UUID
        if (StringUtils.equalsIgnoreCase(job.getId(), jobIdentifier)) {
            return true;
        }

        // "project:group/job" reference
        String jobReference = job.getProject() + ":" + job.getFullName();
        if (StringUtils.equalsIgnoreCase(jobReference, jobIdentifier)) {
            return true;
        }

        return false;
    }

    public Boolean getFilterJobs() {
        return filterJobs;
    }

    public List<String> getJobsIdentifiers() {
        return jobsIdentifiers;
    }

    public List<String> getExecutionStatuses() {
        return executionStatuses;
    }

    @Override
    public RundeckDescriptor getDescriptor() {
        return (RundeckDescriptor) super.getDescriptor();
    }

    @Extension
    public static class RundeckDescriptor extends TriggerDescriptor {

        public RundeckDescriptor() {
            super();
            load();
        }

        @Override
        public Trigger<?> newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new RundeckTrigger(formData.getJSONObject("filterJobs").getBoolean("value"),
                                      bindJSONToList(formData.getJSONObject("filterJobs").get("jobsIdentifiers")),
                                      bindJSONToList(formData.get("executionStatuses")));
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when we receive a notification from Rundeck";
        }

        /**
         * Simplistic version of StaplerRequest.bindJSONToList, in order to use a List of String
         */
        private List<String> bindJSONToList(Object src) {
            List<String> result = new ArrayList<String>();
            if (src instanceof String) {
                result.add((String) src);
            } else if (src instanceof JSONObject) {
                result.add(((JSONObject) src).getString("value"));
            } else if (src instanceof JSONArray) {
                for (Object elem : (JSONArray) src) {
                    if (elem instanceof String) {
                        result.add((String) elem);
                    } else if (elem instanceof JSONObject) {
                        result.add(((JSONObject) elem).getString("value"));
                    }
                }
            }
            return result;
        }
    }
}
