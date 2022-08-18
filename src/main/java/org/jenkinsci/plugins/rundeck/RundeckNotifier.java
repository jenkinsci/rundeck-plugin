package org.jenkinsci.plugins.rundeck;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import hudson.*;
import hudson.model.*;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Run.Artifact;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.rundeck.cache.DummyRundeckJobCache;
import org.jenkinsci.plugins.rundeck.cache.InMemoryRundeckJobCache;
import org.jenkinsci.plugins.rundeck.cache.RundeckJobCache;
import org.jenkinsci.plugins.rundeck.cache.RundeckJobCacheConfig;
import org.jenkinsci.plugins.rundeck.client.ExecutionData;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;
import org.jenkinsci.plugins.rundeck.client.RundeckManager;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.rundeck.client.api.model.*;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * Jenkins {@link Notifier} that runs a job on Rundeck (via the {@link RundeckClientManager})
 *
 * @author Vincent Behar
 */
public class RundeckNotifier extends Notifier implements SimpleBuildStep {

    private static final Logger log = Logger.getLogger(RundeckNotifier.class.getName());

    /** Pattern used for the token expansion of $ARTIFACT_NAME{regex} */
    private static final transient Pattern TOKEN_ARTIFACT_NAME_PATTERN = Pattern.compile("\\$ARTIFACT_NAME\\{(.+)\\}");

    /** Pattern used for extracting the job reference (project:group/name) */
    private static final transient Pattern JOB_REFERENCE_PATTERN = Pattern.compile("^([^:]+?):(.*?)\\/?([^/]+)$");

    private static final int DELAY_BETWEEN_POLLS_IN_MILLIS = 5000;

    private String rundeckInstance; //TODO: Could be renamed to rundeckInstanceName

    private final String jobId;

    private final String options;

    private final String nodeFilters;

    @Deprecated
    private transient final String tag;

    @XStreamAlias("tags")
    private String[] tagsList;

    private final Boolean shouldWaitForRundeckJob;

    private final Boolean shouldFailTheBuild;

    private final Boolean notifyOnAllStatus;

    private final Boolean includeRundeckLogs;

    private final Boolean tailLog;

    /** for multiple rundeck users */
    private String jobUser;
    private Secret jobPassword;
    private Secret jobToken;
    /** rundeck user during job perform */
    private String performUser;

    RundeckNotifier(String rundeckInstance, String jobId, String options, String nodeFilters, String tags,
                    Boolean shouldWaitForRundeckJob, Boolean shouldFailTheBuild, Boolean includeRundeckLogs, Boolean tailLog,
                    String jobUser, Secret jobPassword, Secret jobToken) {
        this(rundeckInstance, jobId, options, nodeFilters, tags, shouldWaitForRundeckJob, shouldFailTheBuild, false, includeRundeckLogs, tailLog, jobUser, jobPassword, jobToken);
    }

    RundeckNotifier(String rundeckInstance, String jobId, String options, String nodeFilters, String tag,
                    Boolean shouldWaitForRundeckJob, Boolean shouldFailTheBuild,
                    String jobUser, Secret jobPassword, Secret jobToken) {
        this(rundeckInstance, jobId, options, nodeFilters, tag, shouldWaitForRundeckJob, shouldFailTheBuild, false, false, false, jobUser, jobPassword, jobToken);
    }

    @DataBoundConstructor
    public RundeckNotifier(String rundeckInstance, String jobId, String options, String nodeFilters, String tags,
                           Boolean shouldWaitForRundeckJob, Boolean shouldFailTheBuild, Boolean notifyOnAllStatus,
                           Boolean includeRundeckLogs, Boolean tailLog,
                           String jobUser, Secret jobPassword, Secret jobToken) {

        this.rundeckInstance = rundeckInstance;
        this.jobId = jobId;
        this.options = options;
        this.nodeFilters = nodeFilters;
        this.tagsList = extracttags(tags,",");
        this.tag = null;
        this.shouldWaitForRundeckJob = shouldWaitForRundeckJob;
        this.shouldFailTheBuild = shouldFailTheBuild;
        this.notifyOnAllStatus = notifyOnAllStatus;
        this.includeRundeckLogs = includeRundeckLogs;
        this.tailLog = tailLog;
        this.jobUser = jobUser;
        this.jobPassword = jobPassword;
        this.jobToken = jobToken;

    }

    public Object readResolve() {
        if (StringUtils.isEmpty(rundeckInstance)) {
            this.rundeckInstance = "Default";
        }
        if (tagsList == null) {
            this.tagsList = extracttags(this.tag, ",");
        }
        return this;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws IOException {
        if (!Boolean.TRUE.equals(notifyOnAllStatus) && run.getResult() != Result.SUCCESS && run.getResult() != null) {
            return;
        }

        RundeckManager rundeckClientManager = getDescriptor().getRundeckJobInstance(this.rundeckInstance, this.jobUser, this.getPassword(), this.getToken());

        if (rundeckClientManager == null) {
            listener.getLogger().println("Rundeck configuration is not valid !");
            throw new AbortException("Rundeck configuration is not valid !");
        }
        this.performUser = rundeckClientManager.getRundeckInstance().getLogin();
        if(performUser==null && this.jobToken!=null){
            this.performUser ="Authenticate By token";
        }
        try {
            rundeckClientManager.ping();
        } catch (IOException e) {
            listener.getLogger().println("Rundeck is not running !");
            throw new AbortException("Rundeck is not running !");
        }

        if (shouldNotifyRundeck(run, listener)) {
            notifyRundeck(rundeckClientManager, run, listener);
        }
    }

    private ChangeLogSet<? extends Entry> getChangeSet(@Nonnull Run<?, ?> run) {
        if (run instanceof AbstractBuild<?,?>) {
            AbstractBuild<?,?> b = (AbstractBuild<?,?>) run;
            return b.getChangeSet();
        } else {
            return ChangeLogSet.createEmpty(run);
        }
    }

    /**
     * Check if we need to notify Rundeck for this build. If we have a tag, we will look for it in the changelog of the
     * build and in the changelog of all upstream builds.
     *
     * @param build for checking the changelog
     * @param listener for logging the result
     * @return true if we should notify Rundeck, false otherwise
     */
    private boolean shouldNotifyRundeck(@Nonnull Run<?, ?> build, @Nonnull TaskListener listener) {
        String info = "Instance '" + this.getRundeckInstance() + "' with rundeck user '" + this.performUser + "': Notifying Rundeck...";

        if (tagsList.length == 0) {
            listener.getLogger().println(info);
            return true;
        }

        // check for the tag in the changelog
        for (Entry changeLog : getChangeSet(build)) {
            for(String tag: tagsList) {
                if (StringUtils.containsIgnoreCase(changeLog.getMsg(), tag)) {
                    listener.getLogger().println("Found " + tag + " in changelog (from " + changeLog.getAuthor().getId()
                            + ") - Notifying Rundeck...");
                    return true;
                }
            }
        }

        // if we have an upstream cause, check for the tag in the changelog from upstream
        for (Cause cause : build.getCauses()) {
            if (UpstreamCause.class.isInstance(cause)) {
                UpstreamCause upstreamCause = (UpstreamCause) cause;
                TopLevelItem item = Hudson.getInstance().getItem(upstreamCause.getUpstreamProject());
                if (AbstractProject.class.isInstance(item)) {
                    AbstractProject<?, ?> upstreamProject = (AbstractProject<?, ?>) item;
                    AbstractBuild<?, ?> upstreamBuild = upstreamProject.getBuildByNumber(upstreamCause.getUpstreamBuild());
                    if (upstreamBuild != null) {
                        for (Entry changeLog : upstreamBuild.getChangeSet()) {
                            for(String tag: tagsList) {
                                if (StringUtils.containsIgnoreCase(changeLog.getMsg(), tag)) {
                                    listener.getLogger().println("Found " + tag + " in changelog (from "
                                            + changeLog.getAuthor().getId() + ") in upstream build ("
                                            + upstreamBuild.getFullDisplayName()
                                            + ") - Notifying Rundeck...");
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Notify Rundeck : run a job on Rundeck
     *
     * @param rundeckClientManager instance to notify
     * @param build for adding actions
     * @param listener for logging the result
     * @return true if successful, false otherwise
     */
    private void notifyRundeck(RundeckManager rundeckClientManager, Run<?, ?> build, TaskListener listener) throws AbortException {
        String runtimeJobId;
        // perform environment substitution before finding the rundeck job
        try {
            EnvVars env = build.getEnvironment(listener);
            runtimeJobId = env.expand(jobId);
            listener.getLogger().println("Looking for jobId : " + runtimeJobId);
        } catch (IOException | InterruptedException e) {
            listener.getLogger().println("Failed substituting environment in: " + jobId + " : " + e.getMessage());
            throw new AbortException("Failed substituting environment in: " + jobId + " : " + e.getMessage());
        }

        //if the jobId is in the form "project:[group/*]name", find the actual job ID first.
        String foundJobId = null;
        try {
            foundJobId = RundeckDescriptor.findJobId(runtimeJobId, rundeckClientManager);
        } catch (IOException e) {
            listener.getLogger().println("Failed to get job with the identifier : " + runtimeJobId + " : " +e.getMessage());
            throw new AbortException("Failed to get job with the identifier : " + runtimeJobId + " : " +e.getMessage());
        }
        if (foundJobId == null) {
            listener.getLogger().println("Could not find a job with the identifier : " + runtimeJobId);
            throw new AbortException("Could not find a job with the identifier : " + runtimeJobId);
        }
        try {

            Properties optionProperties = parseProperties(options, build, listener);
            Properties nodeFiltersProperties = parseProperties(nodeFilters, build, listener);

            Execution execution = rundeckClientManager.runExecution(foundJobId, optionProperties, nodeFiltersProperties);

            listener.getLogger().printf("Notification succeeded ! Execution #%s, at %s (status : %s)%n",
                    execution.getId(), execution.getPermalink(), execution.getStatus());
            build.addAction(new RundeckExecutionBuildBadgeAction(execution.getPermalink()));

            if (Boolean.TRUE.equals(shouldWaitForRundeckJob)) {
                listener.getLogger().println("Waiting for Rundeck execution to finish...");

                if (Boolean.TRUE.equals(includeRundeckLogs) && Boolean.TRUE.equals(tailLog)) {
                    execution = waitTailingRundeckLogsAndReturnExecution(rundeckClientManager, listener, execution);
                } else {
                    execution = waitForRundeckExecutionToFinishAndReturnIt(rundeckClientManager, listener, execution);

                    if (Boolean.TRUE.equals(includeRundeckLogs)) {
                        getAndPrintRundeckLogsForExecution(rundeckClientManager, listener, execution.getId());
                    }
                }

                switch (execution.getStatus()) {
                    case "succeeded":
                        return;
                    case "aborted":
                    case "failed":
                    case "running":   //possible if it was unable to abort execution after an interruption
                        if (getShouldFailTheBuild())
                            build.setResult(Result.FAILURE);
                        throw new AbortException();
                    default:
                        throw new IllegalStateException(format("Unexpected executions status: %s", execution.getStatus()));
                }
            } else {
                return;
            }
        } catch (IOException e) {
            listener.getLogger().println("Error while talking to Rundeck's API at " + rundeckClientManager.getRundeckInstance().getUrl() + " : "
                                         + e.getMessage());
            throw new AbortException("Error while talking to Rundeck's API at " + rundeckClientManager.getRundeckInstance().getUrl() + " : " + e.getMessage());
        }
    }

    private Execution waitTailingRundeckLogsAndReturnExecution(RundeckManager rundeckClientManager, TaskListener listener, Execution execution) throws IOException {
        listener.getLogger().println("BEGIN RUNDECK TAILED LOG OUTPUT");
        RunDeckLogTail runDeckLogTail = new RunDeckLogTail(rundeckClientManager, Long.valueOf(execution.getId()));
        PrintStream printStream = listener.getLogger();
        for (List<ExecLog> aRunDeckLogTail : runDeckLogTail) {
            for (ExecLog rundeckOutputEntry : aRunDeckLogTail) {
                printStream.println("[" + rundeckOutputEntry.node + "] " + "[" + rundeckOutputEntry.time + "] [" + rundeckOutputEntry.level + "] " + rundeckOutputEntry.log);
            }
        }
        listener.getLogger().println("END RUNDECK TAILED LOG OUTPUT");

        execution = rundeckClientManager.getExecution(execution.getId());
        logExecutionStatus(listener, execution, "finished");
        return execution;
    }

    private void logExecutionStatus(TaskListener listener, Execution execution, String operationName) {

        ExecutionData executionData = new ExecutionData(execution);
        String duration = executionData.getDuration();

        listener.getLogger().printf("Rundeck execution #%s %s in %s, with status : %s%n", execution.getId(), operationName,
                duration, execution.getStatus());
    }

    /**
     * Parse the given input (should be in the Java-Properties syntax) and expand Jenkins environment variables.
     *
     * @param input specified in the Java-Properties syntax (multi-line, key and value separated by = or :)
     * @param build for retrieving Jenkins environment variables
     * @param listener for retrieving Jenkins environment variables and logging the errors
     * @return A {@link Properties} instance (may be empty), or null if unable to parse the options
     */
    private Properties parseProperties(String input, Run<?, ?> build, TaskListener listener) {
        if (StringUtils.isBlank(input)) {
            return new Properties();
        }

        // try to expand jenkins env vars
        try {
            EnvVars envVars = build.getEnvironment(listener);
            input = Util.replaceMacro(input, envVars);
        } catch (Exception e) {
            listener.getLogger().println("Failed to expand environment variables : " + e.getMessage());
        }

        // expand our custom tokens : $ARTIFACT_NAME{regex} => name of the first matching artifact found
        // http://groups.google.com/group/rundeck-discuss/browse_thread/thread/94a6833b84fdc10b
        Matcher matcher = TOKEN_ARTIFACT_NAME_PATTERN.matcher(input);
        int idx = 0;
        while (matcher.reset(input).find(idx)) {
            idx = matcher.end();
            String regex = matcher.group(1);
            Pattern pattern = Pattern.compile(regex);
            for (@SuppressWarnings("rawtypes")
            Artifact artifact : build.getArtifacts()) {
                if (pattern.matcher(artifact.getFileName()).matches()) {
                    input = StringUtils.replace(input, matcher.group(0), artifact.getFileName());
                    idx = matcher.start() + artifact.getFileName().length();
                    break;
                }
            }
        }

        try {
            return Util.loadProperties(input);
        } catch (IOException e) {
            listener.getLogger().println("Failed to parse : " + input);
            listener.getLogger().println("Error : " + e.getMessage());
            return null;
        }
    }

    private Execution waitForRundeckExecutionToFinishAndReturnIt(RundeckManager rundeckClientManager, TaskListener listener,
                                                                 Execution execution) throws IOException {
        try {
            while (RundeckClientManager.ExecutionStatus.RUNNING.toString().equals(execution.getStatus())) {
                Thread.sleep(DELAY_BETWEEN_POLLS_IN_MILLIS);
                execution = rundeckClientManager.getExecution(execution.getId());
            }
            logExecutionStatus(listener, execution, "finished");
        } catch (InterruptedException | IOException e) {
            listener.getLogger().println("Waiting was interrupted. Probably build was cancelled. Reason: " + e);
            listener.getLogger().println("Trying to abort Rundeck execution...");
            AbortResult rundeckAbort = rundeckClientManager.abortExecution(execution.getId());
            listener.getLogger().printf("Abort status: %s%n", rundeckAbort.abort.status);
            execution = rundeckClientManager.getExecution(execution.getId());
            logExecutionStatus(listener, execution, "aborted");
        }
        return execution;
    }

    private void getAndPrintRundeckLogsForExecution(RundeckManager rundeckClientManager, TaskListener listener, String executionId) throws IOException {
        listener.getLogger().println("BEGIN RUNDECK LOG OUTPUT");

        ExecOutput rundeckOutput = rundeckClientManager.getOutput(executionId, 0L, 0L, 0L);
        if (null != rundeckOutput) {
            List<ExecLog> logEntries = rundeckOutput.entries;
            if (null != logEntries) {
                for (ExecLog rundeckOutputEntry : logEntries) {
                    listener.getLogger().println("["+ rundeckOutputEntry.node + "] " + "[" + rundeckOutputEntry.time + "] [" + rundeckOutputEntry.level + "] " + rundeckOutputEntry.log);

                }
            }
        }
        listener.getLogger().println("END RUNDECK LOG OUTPUT");
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        try {
            return new RundeckJobProjectLinkerAction(rundeckInstance,getDescriptor().getRundeckJobInstance(this.rundeckInstance, jobUser, this.getPassword(),this.getToken()), jobId);
        } catch (Exception e) {
            log.warning(format("Unable to create rundeck job project linked action for '%s'. Exception: %s: %s", project.getDisplayName(),
                    e.getClass().getSimpleName(), e.getMessage()));
            return null;
        }
    }

    /**
     * If we should not fail the build, we need to run after finalized, so that the result of "perform" is not used by
     * Jenkins
     */
    @Override
    public boolean needsToRunAfterFinalized() {
        return !shouldFailTheBuild;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public String getRundeckInstance() {
        return this.rundeckInstance;
    }

    public String getJobIdentifier() {
        return jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public String getOptions() {
        return options;
    }

    public String getNodeFilters() {
        return nodeFilters;
    }

    public String getTag() {
        StringBuilder builder = new StringBuilder();

        for (int i=0; i<tagsList.length; i++) {
            builder.append(tagsList[i]);

            if (i+1<tagsList.length) {
                builder.append(",");
            }
        }

        return builder.toString();
    }

    public String getTags() {
        return getTag();
    }

    public String[] getTagsList() {
        return Arrays.copyOf(tagsList, tagsList.length);
    }

    public Boolean getShouldWaitForRundeckJob() {
        return shouldWaitForRundeckJob;
    }

    public Boolean getShouldFailTheBuild() {
        return shouldFailTheBuild;
    }

    public Boolean getNotifyOnAllStatus() {
        return notifyOnAllStatus;
    }

    public Boolean getIncludeRundeckLogs() {
        return includeRundeckLogs;
    }

    public Boolean getTailLog() {
        return tailLog;
    }

    /**
     * optional non default rundeck user for actual job
     */
    public String getJobUser() {
        return jobUser;
    }

    public Secret getJobPassword() {
        return jobPassword;
    }

    public Secret getJobToken() {
        return jobToken;
    }

    public String getPassword(){
        if(this.jobPassword!=null){
            return this.jobPassword.getPlainText();
        }else{
            return null;
        }
    }

    public String getToken(){
        if(this.jobToken!=null){
            return this.jobToken.getPlainText();
        }else{
            return null;
        }
    }

    @Override
    public RundeckDescriptor getDescriptor() {
        return (RundeckDescriptor) super.getDescriptor();
    }

    @Extension(ordinal = 1000)
    public static final class RundeckDescriptor extends BuildStepDescriptor<Publisher> {

        @Deprecated
        private transient RundeckInstance rundeckInstance;

        @CopyOnWrite
        private volatile Map<String, RundeckInstance> rundeckInstances = new LinkedHashMap<>();

        private volatile boolean allowSelfSignedSSL = false;

        private volatile transient RundeckJobCache rundeckJobCache = new DummyRundeckJobCache();

        private volatile RundeckJobCacheConfig rundeckJobCacheConfig = RundeckJobCacheConfig.initializeWithDefaultValues();

        private volatile transient RundeckInstanceBuilder rundeckBuilder = null;

        public RundeckDescriptor() {
            super();
            load();
        }

        public synchronized void load() {
            super.load();
            initializeRundeckJobCache();
        }

        public RundeckInstanceBuilder getRundeckBuilder() {
            return rundeckBuilder;
        }

        public void setRundeckBuilder(RundeckInstanceBuilder rundeckBuilder) {
            this.rundeckBuilder = rundeckBuilder;
        }

        private void initializeRundeckJobCache() {
            if (rundeckJobCacheConfig.isEnabled()) {
                log.info("Rundeck job cache enabled. Using following configuration: " + rundeckJobCacheConfig);
                rundeckJobCache = new InMemoryRundeckJobCache(rundeckJobCacheConfig);
            } else {
                log.info("Rundeck job cache DISABLED.");
                rundeckJobCache.invalidate();
                rundeckJobCache = new DummyRundeckJobCache();
            }
        }

        // support backward compatibility
        protected Object readResolve() {
            if (rundeckInstance != null) {
                Map<String, RundeckInstance> instance = new LinkedHashMap<String, RundeckInstance>();
                instance.put("Default", RundeckInstance.builder().allowSelfSignedSSL(this.allowSelfSignedSSL).client(rundeckInstance).build());
                this.setRundeckInstances(instance);
            }
            return this;
        }

        @Override
        protected XmlFile getConfigFile() {
            // required to convert the Descriptor configuration (global setting)
            XStream2 xs = new XStream2();
            xs.addCompatibilityAlias("org.rundeck.api.RundeckClient", RundeckInstance.class);

            // same code as super.getConfigFile()
            return new XmlFile(xs, new File(Jenkins.getActiveInstance().getRootDir(),getId()+".xml"));
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {

            JSONArray instances = json.optJSONArray("inst");

            if (instances == null) {
                instances = new JSONArray();

                if (json.optJSONObject("inst") != null) {
                    instances.add(json.getJSONObject("inst"));
                }
            }

            Map<String, RundeckInstance> newInstances = new LinkedHashMap<String, RundeckInstance>(instances.size());
            this.setAllowSelfSignedSSL(json.getBoolean("allowSelfSignedSSL"));

            try {
                for (int i=0; i< instances.size(); i++) {
                    JSONObject instance = instances.getJSONObject(i);

                    if (!StringUtils.isEmpty(instance.getString("name"))) {
                        RundeckInstanceBuilder builder = RundeckInstance.builder();
                        builder.url(instance.getString("url")).allowSelfSignedSSL(this.allowSelfSignedSSL);
                        if (instance.get("authtoken") != null && !"".equals(instance.getString("authtoken"))) {
                            builder.token(Secret.fromString(instance.getString("authtoken")) );
                        } else {
                            builder.login(instance.getString("login"), Secret.fromString(instance.getString("password")));
                        }

                        if (instance.optInt("apiversion") > 0) {
                            builder.version(instance.getInt("apiversion"));
                        }else{
                            builder.version(RundeckClientManager.API_VERSION);
                        }
                        newInstances.put(instance.getString("name"), builder.build());
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warning(format("Unable to deserialize Rundeck instances fom JSON. %s: %s", e.getClass().getSimpleName(), e.getMessage()));
            }

            this.setRundeckInstances(newInstances);
            configureRundeckJobCache(json);

            save();
            return super.configure(req, json);
        }

        private void configureRundeckJobCache(JSONObject json) {
            boolean cacheEnabledAsBoolean = json.has("rundeckJobCacheEnabled");
            if (cacheEnabledAsBoolean == rundeckJobCacheConfig.isEnabled()) {   //nothing changed
                return;
            }

            rundeckJobCacheConfig.setEnabled(cacheEnabledAsBoolean);
            initializeRundeckJobCache();
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            String rundeckInstance = formData.getString("rundeckInstance");
            String jobIdentifier = formData.getString("jobIdentifier");
            String jobUser = formData.getString("jobUser");
            String jobPassword = formData.getString("jobPassword");
            String jobToken = formData.getString("jobToken");

            if (!jobIdentifier.contains("$")) {
                // Only check the job name if there are no environment variables to substitute
                JobItem job = null;
                try {

                    Secret password = Secret.fromString(jobPassword);
                    Secret token = Secret.fromString(jobToken);

                    job = findJobUncached(jobIdentifier, this.getRundeckJobInstance(rundeckInstance, jobUser,
                            Util.fixEmpty(password.getPlainText()),
                            Util.fixEmpty(token.getPlainText())));
                } catch (Exception e) {
                    throw new FormException("Failed to get job with the identifier : " + jobIdentifier, e, "jobIdentifier");
                }
                if (job == null) {
                    throw new FormException("Could not find a job with the identifier : " + jobIdentifier, "jobIdentifier");
                }
            }
            return new RundeckNotifier(rundeckInstance,
                    jobIdentifier,
                    formData.getString("options"),
                    formData.getString("nodeFilters"),
                    formData.getString("tag"),
                    formData.getBoolean("shouldWaitForRundeckJob"),
                    formData.getBoolean("shouldFailTheBuild"),
                    formData.getBoolean("notifyOnAllStatus"),
                    formData.getBoolean("includeRundeckLogs"),
                    formData.getBoolean("tailLog"),
                    jobUser,
                    Secret.fromString(jobPassword),
                    Secret.fromString(jobToken));
        }

        @SuppressWarnings("unused")
        @RequirePOST
        public FormValidation doDisplayCacheStatistics() {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

            return FormValidation.ok(rundeckJobCache.logAndGetStats());
        }

        @SuppressWarnings("unused")
        @RequirePOST
        public FormValidation doInvalidateCache() {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

            rundeckJobCache.invalidate();
            return FormValidation.ok("Done");
        }

        @SuppressWarnings("unused")
        @RequirePOST
        public FormValidation doTestConnection(@QueryParameter("rundeck.url") final String url,
                                               @QueryParameter("rundeck.login") final String login,
                                               @QueryParameter("rundeck.password") final Secret password,
                                               @QueryParameter("rundeck.authtoken") final Secret token,
                                               @QueryParameter("allowSelfSignedSSL") final boolean allowSelfSignedSSL,
                                               @QueryParameter(value = "rundeck.apiversion", fixEmpty = true) final Integer apiversion) {


            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

            RundeckInstanceBuilder builder = new RundeckInstanceBuilder().url(url).allowSelfSignedSSL(allowSelfSignedSSL);

            if (null != apiversion && apiversion > 0) {
                builder.version(apiversion);
            } else {
                builder.version(RundeckClientManager.API_VERSION);
            }
            try {
                if (!token.getPlainText().isEmpty()) {
                    builder.token(token);
                } else {
                    builder.login(login, password);
                }
            } catch (IllegalArgumentException e) {
                return FormValidation.error("Rundeck configuration is not valid ! %s", e.getMessage());
            }
            RundeckInstance instance = builder.build();
            RundeckClientManager rundeck = RundeckInstanceBuilder.createClient(instance);
            try {
                rundeck.ping();
            } catch (IOException e) {
                return FormValidation.error("We couldn't find a live Rundeck instance at %s", rundeck.getRundeckInstance().getUrl());
            }
            try {
                rundeck.testAuth();
            } catch (IOException e) {
                return FormValidation.error("Error authenticating Rundeck !",  rundeck.getRundeckInstance().getUrl());
            }
            return FormValidation.ok("Your Rundeck instance is alive, and your credentials are valid !");
        }

        /**
         * check valid job
         */
        @RequirePOST
        public FormValidation doCheckJobIdentifier(@QueryParameter("jobIdentifier") final String jobIdentifier,
                                                   @QueryParameter("rundeckInstance") final String rundeckInstance,
                                                   @QueryParameter("jobUser") final String user,
                                                   @QueryParameter("jobPassword") final Secret password,
                                                   @QueryParameter("jobToken") final Secret token,
                                                   @AncestorInPath Item item) {


            if (item == null) { // no context
                return FormValidation.ok();
            }

            item.checkPermission(Item.CONFIGURE);

            if (password==null && !StringUtils.isBlank(user)) {
                return FormValidation.error("The password is mandatory if user is not empty !");
            }

            if(rundeckInstance == null){
                return FormValidation.error("There are no rundeck instances configured. !");
            }

            RundeckManager client = null;


            try {
                client = this.getRundeckJobInstance(rundeckInstance,
                        user,
                        Util.fixEmpty(password.getPlainText()),
                        Util.fixEmpty(token.getPlainText()));
            }catch (Exception e){
                return FormValidation.error(e.getMessage());
            }

            if (client == null) {
                return FormValidation.error("Rundeck global configuration is not valid !");
            }
            if (StringUtils.isBlank(jobIdentifier)) {
                return FormValidation.error("The job identifier is mandatory !");
            }

            String userLogin=client.getRundeckInstance().getLogin();

            if(userLogin == null){
                userLogin="Authenticate by Token";
            }

            try {
                if (jobIdentifier.contains("$")) {
                    return FormValidation.warning("Unable to substitute environment at configuration time. " +
                            "The build will fail if the job does not exist");
                }
                JobItem job = findJobUncached(jobIdentifier, client);
                if (job == null) {
                    return FormValidation.error("Could not find a job with the identifier : %s", jobIdentifier);
                } else {
                    String fullname = job.getName();
                    if(job.getGroup()!= null && !job.getGroup().isEmpty()){
                        fullname =  job.getGroup()+"/"+job.getName();
                    }
                    return FormValidation.ok("Your Rundeck job is : [user:%s]  %s [%s] %s",
                            userLogin,
                            job.getId(),
                            job.getProject(),
                            fullname);
                }
            } catch (Exception e) {
                return FormValidation.error("Failed to get job details : %s", e.getMessage());
            }
        }

        /**
         * Return a rundeck Job ID, by find a rundeck job if the identifier is a project:[group/]*name format, otherwise
         * returning the original identifier as the ID.
         * @param jobIdentifier either a Job ID, or "project:[group/]*name"
         * @param rundeckClient the client instance
         * @return a job UUID
         * @throws IOException
         */
        static String findJobId(String jobIdentifier, RundeckManager rundeckClient) throws IOException,
                IllegalArgumentException {
            log.fine(format("findJobId request for jobId: %s", jobIdentifier));
            //TODO: Could be rewritten to be cache as well, if needed
            Matcher matcher = JOB_REFERENCE_PATTERN.matcher(jobIdentifier);
            if (matcher.find() && matcher.groupCount() == 3) {
                String project = matcher.group(1);
                String groupPath = matcher.group(2);
                String name = matcher.group(3);

                return rundeckClient.findJobId(project,name , groupPath);
            } else {
                return jobIdentifier;
            }
        }

        /**
         * Find a {@link JobItem} with the given identifier using internal cache if possible.
         *
         * @param jobIdentifier either a simple ID, an UUID or a reference (project:group/name)
         * @param rundeckInstanceName Rundeck instance name
         * @param rundeckInstance Rundeck client instance
         * @return the {@link JobItem} found, or null if not found
         */
        public static JobItem findJob(String jobIdentifier, String rundeckInstanceName, RundeckManager rundeckInstance) {
            RundeckJobCache rundeckJobCache = getRundeckDescriptor().rundeckJobCache;
            return rundeckJobCache.findJobById(jobIdentifier, rundeckInstanceName, rundeckInstance);
        }

        public static JobItem findJobUncached(String jobIdentifier, RundeckManager rundeckClient) {
            RundeckJobCacheConfig rundeckJobCacheConfig = getRundeckDescriptor().getRundeckJobCacheConfig();
            if (rundeckJobCacheConfig.isEnabled()) {
                log.info(format("NOT CACHED findJobUncached request for jobId: %s", jobIdentifier));
            } else {
                log.fine(format("findJobUncached request for jobId: %s (cache disabled)", jobIdentifier));
            }

            JobItem job = null;

            Matcher matcher = JOB_REFERENCE_PATTERN.matcher(jobIdentifier);
            if (matcher.find() && matcher.groupCount() == 3) {
                String project = matcher.group(1);
                String groupPath = matcher.group(2);
                String name = matcher.group(3);
                try{
                    job = rundeckClient.findJob(project, groupPath, name);
                }catch (Exception e){
                    log.warning(e.getMessage());
                }
            } else {
                try{
                    job = rundeckClient.getJob(jobIdentifier);
                }catch (Exception e){
                    log.warning(e.getMessage());
                }
            }
            return job;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Rundeck";
        }

        public RundeckInstance getRundeckInstance(String key) {
            return rundeckInstances.get(key);
        }


            /**
             * get RundeckClient if optional user is given
             * @param rundeckInstanceName rundeck instance
             * @param jobUser job user
             * @param jobPassword job user password
             * @param jobToken job  token
             * @return RundeckManager rundeck Manager result
             */
        public RundeckManager getRundeckJobInstance(String rundeckInstanceName,
                                                   String jobUser, String jobPassword, String jobToken) {


            RundeckInstance instance = rundeckInstances.get(rundeckInstanceName);

            if(instance==null){
                return null;
            }

            RundeckManager client;
            if(rundeckBuilder==null) {
                rundeckBuilder = new RundeckInstanceBuilder();//.allowSelfSignedSSL(true);
            }

            if(rundeckBuilder.getClient()==null){
                client = RundeckInstanceBuilder.createClient(instance);
            }else{
                client = rundeckBuilder.getClient();
            }

            int apiVersion = RundeckClientManager.API_VERSION;
            if(instance.getApiVersion()!=null){
                apiVersion=instance.getApiVersion();
            }

            if ((client != null) && (jobUser != null) && !jobUser.isEmpty() && !jobUser.equals(client.getRundeckInstance().getLogin()))
            {
                // create new instance with given user and password and URL from global instance
                RundeckInstance newInstance = new RundeckInstance();
                String url = client.getRundeckInstance().getUrl();
                newInstance.setUrl(url);
                newInstance.setLogin(jobUser);
                newInstance.setPassword(Secret.fromString(jobPassword));
                newInstance.setApiVersion(apiVersion);
                client = new RundeckClientManager(newInstance);
            }

            if ((client != null) && (jobToken != null) && !jobToken.isEmpty())
            {
                // create new instance with given user and password and URL from global instance
                RundeckInstance newInstance = new RundeckInstance();
                String url = client.getRundeckInstance().getUrl();
                newInstance.setUrl(url);
                newInstance.setToken(Secret.fromString(jobToken));
                newInstance.setApiVersion(apiVersion);
                client = new RundeckClientManager(newInstance);
            }

            return client;
        }

        public void addRundeckInstance(String key, RundeckInstance instance) {
            Map<String, RundeckInstance> instances = new LinkedHashMap<String, RundeckInstance>(this.rundeckInstances);
            instances.put(key, instance);
            this.setRundeckInstances(instances);
        }

        public Map<String, RundeckInstance> getRundeckInstances() {
            return rundeckInstances;
        }

        public void setRundeckInstances(Map<String, RundeckInstance> instances) {
            this.rundeckInstances = instances;
        }

        public boolean getAllowSelfSignedSSL() {
            return allowSelfSignedSSL;
        }

        public void setAllowSelfSignedSSL(boolean allowSelfSignedSSL) {
            this.allowSelfSignedSSL = allowSelfSignedSSL;
        }

        public RundeckJobCacheConfig getRundeckJobCacheConfig() {
            return rundeckJobCacheConfig;
        }
    }

    /**
     * {@link BuildBadgeAction} used to display a Rundeck icon + a link to the Rundeck execution page, on the Jenkins
     * build history and build result page.
     */
    public static class RundeckExecutionBuildBadgeAction implements BuildBadgeAction {

        private final String executionUrl;

        public RundeckExecutionBuildBadgeAction(String executionUrl) {
            super();
            this.executionUrl = executionUrl;
        }

        public String getDisplayName() {
            return "Rundeck Execution Result";
        }

        public String getIconFileName() {
            return "/plugin/rundeck/images/rundeck_24x24.png";
        }

        public String getUrlName() {
            return executionUrl;
        }

    }

    /**
     *
     * @param tagsStr
     * @return
     */
    private String[] extracttags(String tagsStr, String delimiter){

        if (tagsStr == null)
            return new String[0];
        List<String> list = new ArrayList<String>(Arrays.asList(tagsStr.split(delimiter)));

        for (ListIterator<String> iterator = list.listIterator(); iterator.hasNext(); ) {
            String tag  = iterator.next();
            tag=tag.replaceAll("\\s+","").trim();
            iterator.remove();
            if (!tag.equals(""))
                iterator.add(tag);

        }

        return list.toArray(new String[list.size()]);
    }

    private static RundeckDescriptor getRundeckDescriptor() {
        return Jenkins.getInstance().getExtensionList(RundeckDescriptor.class).get(0);
    }
}
