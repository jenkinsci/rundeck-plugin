package org.jenkinsci.plugins.rundeck;

import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Cause.UpstreamCause;
import hudson.scm.SubversionSCM;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.rundeck.RundeckNotifier.RundeckExecutionBuildBadgeAction;
import org.jenkinsci.plugins.rundeck.domain.RundeckApiException;
import org.jenkinsci.plugins.rundeck.domain.RundeckExecution;
import org.jenkinsci.plugins.rundeck.domain.RundeckInstance;
import org.jenkinsci.plugins.rundeck.domain.RundeckJob;
import org.jenkinsci.plugins.rundeck.domain.RundeckApiException.RundeckApiJobRunException;
import org.jenkinsci.plugins.rundeck.domain.RundeckApiException.RundeckApiLoginException;
import org.jenkinsci.plugins.rundeck.domain.RundeckExecution.ExecutionStatus;
import org.junit.Assert;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;
import org.jvnet.hudson.test.HudsonHomeLoader.CopyExisting;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNClientManager;

/**
 * Test the {@link RundeckNotifier}
 * 
 * @author Vincent Behar
 */
public class RundeckNotifierTest extends HudsonTestCase {

    public void testCommitWithoutTag() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", createOptions(), "", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));

        addScmCommit(build.getWorkspace(), "commit message");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testStandardCommitWithTag() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", null, "#deploy", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        addScmCommit(build.getWorkspace(), "commit message");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));
    }

    public void testDeployCommitWithTagWontBreakTheBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", null, "#deploy", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        addScmCommit(build.getWorkspace(), "commit message - #deploy");

        // second build
        build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testDeployCommitWithTagWillBreakTheBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", null, "#deploy", false, true);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance() {

            private static final long serialVersionUID = 1L;

            @Override
            public RundeckExecution runJob(String jobId, Properties options) throws RundeckApiException,
                    RundeckApiLoginException, RundeckApiJobRunException {
                throw new RundeckApiJobRunException("Fake error for testing");
            }

        });

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        addScmCommit(build.getWorkspace(), "commit message - #deploy");

        // second build
        build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("Failed to run job"));
        assertTrue(s.contains("Fake error for testing"));
    }

    public void testExpandEnvVarsInOptions() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", createOptions(), null, false, true);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance() {

            private static final long serialVersionUID = 1L;

            @Override
            public RundeckExecution runJob(String jobId, Properties options) throws RundeckApiException,
                    RundeckApiLoginException, RundeckApiJobRunException {
                Assert.assertEquals(4, options.size());
                Assert.assertEquals("value 1", options.getProperty("option1"));
                Assert.assertEquals("1", options.getProperty("buildNumber"));
                Assert.assertEquals("my project name", options.getProperty("jobName"));
                return super.runJob(jobId, options);
            }

        });

        FreeStyleProject project = createFreeStyleProject("my project name");
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testUpstreamBuildWithTag() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", null, "#deploy", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject upstream = createFreeStyleProject("upstream");
        upstream.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        upstream.setScm(createScm());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild upstreamBuild = assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0,
                                                                               new UpstreamCause((Run<?, ?>) upstreamBuild))
                                                               .get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        addScmCommit(upstreamBuild.getWorkspace(), "commit message - #deploy");

        // second build
        upstreamBuild = assertBuildStatusSuccess(upstream.scheduleBuild2(0).get());
        build = assertBuildStatusSuccess(project.scheduleBuild2(0, new UpstreamCause((Run<?, ?>) upstreamBuild)).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("#deploy"));
        assertTrue(s.contains("in upstream build (" + upstreamBuild.getFullDisplayName() + ")"));
        assertTrue(s.contains("Notification succeeded !"));
    }

    public void testFailedBuild() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", createOptions(), "", false, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance());

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.FAILURE));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));

        addScmCommit(build.getWorkspace(), "commit message");

        // second build
        build = assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
        assertFalse(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        s = FileUtils.readFileToString(build.getLogFile());
        assertFalse(s.contains("Notifying RunDeck"));
    }

    public void testWaitForRundeckJob() throws Exception {
        RundeckNotifier notifier = new RundeckNotifier("1", createOptions(), "", true, false);
        notifier.getDescriptor().setRundeckInstance(new MockRundeckInstance() {

            private static final long serialVersionUID = 1L;

            @Override
            public RundeckExecution runJob(String jobId, Properties options) throws RundeckApiException,
                    RundeckApiLoginException, RundeckApiJobRunException {
                RundeckExecution execution = new RundeckExecution();
                execution.setStatus(ExecutionStatus.SUCCEEDED);
                return execution;
            }

        });

        FreeStyleProject project = createFreeStyleProject();
        project.getBuildersList().add(new MockBuilder(Result.SUCCESS));
        project.getPublishersList().add(notifier);
        project.setScm(createScm());

        // first build
        FreeStyleBuild build = assertBuildStatusSuccess(project.scheduleBuild2(0).get());
        assertTrue(buildContainsAction(build, RundeckExecutionBuildBadgeAction.class));
        String s = FileUtils.readFileToString(build.getLogFile());
        assertTrue(s.contains("Notifying RunDeck..."));
        assertTrue(s.contains("Notification succeeded !"));
        assertTrue(s.contains("Waiting for RunDeck execution to finish..."));
        assertTrue(s.contains("RunDeck execution finished, with status : SUCCEEDED"));
    }

    private String createOptions() {
        Properties options = new Properties();
        options.setProperty("option1", "value 1");
        options.setProperty("workspace", "$WORKSPACE");
        options.setProperty("jobName", "$JOB_NAME");
        options.setProperty("buildNumber", "$BUILD_NUMBER");

        StringWriter writer = new StringWriter();
        try {
            options.store(writer, "this is a comment line");
            return writer.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private SubversionSCM createScm() throws Exception {
        File emptyRepository = new CopyExisting(getClass().getResource("empty-svn-repository.zip")).allocate();
        return new SubversionSCM("file://" + emptyRepository.getPath());
    }

    private void addScmCommit(FilePath workspace, String commitMessage) throws Exception {
        SVNClientManager svnm = SubversionSCM.createSvnClientManager();

        FilePath newFilePath = workspace.child("new-file");
        File newFile = new File(newFilePath.getRemote());
        newFilePath.touch(System.currentTimeMillis());
        svnm.getWCClient().doAdd(newFile, false, false, false, SVNDepth.INFINITY, false, false);
        svnm.getCommitClient().doCommit(new File[] { newFile },
                                        false,
                                        commitMessage,
                                        null,
                                        null,
                                        false,
                                        false,
                                        SVNDepth.EMPTY);
    }

    /**
     * @param build
     * @param actionClass
     * @return true if the given build contains an action of the given actionClass
     */
    private boolean buildContainsAction(Build<?, ?> build, Class<?> actionClass) {
        for (Action action : build.getActions()) {
            if (actionClass.isInstance(action)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Just a mock {@link RundeckInstance} which is always successful
     */
    private static class MockRundeckInstance extends RundeckInstance {

        private static final long serialVersionUID = 1L;

        public MockRundeckInstance() {
            super("", "", "");
        }

        @Override
        public boolean isConfigurationValid() {
            return true;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public boolean isLoginValid() {
            return true;
        }

        @Override
        public RundeckExecution runJob(String jobId, Properties options) throws RundeckApiException,
                RundeckApiLoginException, RundeckApiJobRunException {
            RundeckExecution execution = new RundeckExecution();
            execution.setStatus(ExecutionStatus.RUNNING);
            return execution;
        }

        @Override
        public RundeckExecution getExecution(Long executionId) throws RundeckApiException, RundeckApiLoginException {
            RundeckExecution execution = new RundeckExecution();
            execution.setStatus(ExecutionStatus.SUCCEEDED);
            return execution;
        }

        @Override
        public RundeckJob getJob(String jobId) throws RundeckApiException, RundeckApiLoginException {
            RundeckJob job = new RundeckJob();
            return job;
        }

    }
}
