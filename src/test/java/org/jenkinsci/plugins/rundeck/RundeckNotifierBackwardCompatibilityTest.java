package org.jenkinsci.plugins.rundeck;

import hudson.model.FreeStyleProject;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import hudson.util.Secret;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.rundeck.RundeckNotifier.RundeckDescriptor;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Test the backward compatibility of {@link RundeckNotifier}
 *
 * @author Joel Ringuette
 */
public class RundeckNotifierBackwardCompatibilityTest extends HudsonTestCase {

    @LocalData
    public void test_GivenADescriptorConfigWithoutRundeckInstance_WhenInstanciatingDescriptor_ThenDescriptorContainsDefaultInstance() throws Exception {
        RundeckDescriptor descriptor = (RundeckDescriptor) this.jenkins.getDescriptorOrDie(RundeckNotifier.class);

        assertNotNull(descriptor.getRundeckInstances());

        assertEquals(1, descriptor.getRundeckInstances().size());

        RundeckInstance instance = descriptor.getRundeckInstances().get("Default");

        assertNotNull(instance);
        assertEquals("http://rundeck.org", instance.getUrl());
        assertEquals("login", instance.getLogin());
        assertEquals("password", instance.getPassword().getPlainText());
        assertEquals("9", instance.getApiVersion().toString());
        assertEquals(false, descriptor.getRundeckJobCacheConfig().isEnabled());
        assertEquals(1080, descriptor.getRundeckJobCacheConfig().getAfterAccessExpirationInMinutes());
        assertEquals(500, descriptor.getRundeckJobCacheConfig().getMaximumSize());
        assertEquals(200, descriptor.getRundeckJobCacheConfig().getCacheStatsDisplayHitThreshold());
    }
    
    public void test_GivenADescriptorWithMultipleRundeckInstances_WhenSavingIt_ThenPersistedConfigContainsAllInstances() throws Exception {
        RundeckDescriptor descriptor = new RundeckDescriptor();

        Map<String, RundeckInstance> instances = new LinkedHashMap<String, RundeckInstance>();

        Secret password = Secret.fromString("password");
        Secret token = Secret.fromString("password");

        instances.put("first", RundeckInstance.builder().url("http://first").login("login", password).build());
        instances.put("second", RundeckInstance.builder().url("http://second").token(token).build());

        descriptor.setRundeckInstances(instances);

        descriptor.save();

        String storedConfig = FileUtils.readFileToString(new File(this.jenkins.getRootDir(), descriptor.getId() + ".xml"));

        final String expected = "<?xml version='1.1' encoding='UTF-8'?>\n" +
                "<org.jenkinsci.plugins.rundeck.RundeckNotifier_-RundeckDescriptor>\n" +
                "  <rundeckInstances class=\"linked-hash-map\">\n" +
                "    <entry>\n" +
                "      <string>first</string>\n" +
                "      <org.jenkinsci.plugins.rundeck.RundeckInstance>\n" +
                "        <url>http://first</url>\n" +
                "        <apiVersion>21</apiVersion>\n" +
                "        <login>login</login>\n" +
                "        <password>"+password.getEncryptedValue()+"</password>\n" +
                "        <sslHostnameVerifyAllowAll>false</sslHostnameVerifyAllowAll>\n" +
                "        <sslCertificateTrustAllowSelfSigned>false</sslCertificateTrustAllowSelfSigned>\n" +
                "        <systemProxyEnabled>false</systemProxyEnabled>\n" +
                "        <useIntermediateStreamFile>false</useIntermediateStreamFile>\n" +
                "      </org.jenkinsci.plugins.rundeck.RundeckInstance>\n" +
                "    </entry>\n" +
                "    <entry>\n" +
                "      <string>second</string>\n" +
                "      <org.jenkinsci.plugins.rundeck.RundeckInstance>\n" +
                "        <url>http://second</url>\n" +
                "        <apiVersion>21</apiVersion>\n" +
                "        <token>"+token.getEncryptedValue()+"</token>\n" +
                "        <sslHostnameVerifyAllowAll>false</sslHostnameVerifyAllowAll>\n" +
                "        <sslCertificateTrustAllowSelfSigned>false</sslCertificateTrustAllowSelfSigned>\n" +
                "        <systemProxyEnabled>false</systemProxyEnabled>\n" +
                "        <useIntermediateStreamFile>false</useIntermediateStreamFile>\n" +
                "      </org.jenkinsci.plugins.rundeck.RundeckInstance>\n" +
                "    </entry>\n" +
                "  </rundeckInstances>\n" +
                "  <rundeckJobCacheConfig>\n" +
                "    <enabled>false</enabled>\n" +
                "    <afterAccessExpirationInMinutes>1080</afterAccessExpirationInMinutes>\n" +
                "    <maximumSize>500</maximumSize>\n" +
                "    <cacheStatsDisplayHitThreshold>200</cacheStatsDisplayHitThreshold>\n" +
                "  </rundeckJobCacheConfig>\n" +
                "</org.jenkinsci.plugins.rundeck.RundeckNotifier_-RundeckDescriptor>";

        assertEquals(expected, storedConfig);
    }

    @LocalData
    public void test_GivenAJobConfigWithoutRundeckInstance_WhenLoadingJob_ThenRundeckNotifierUseDefaultInstance() {
        RundeckDescriptor descriptor = (RundeckDescriptor) this.jenkins.getDescriptorOrDie(RundeckNotifier.class);

        RundeckNotifier notifier = (RundeckNotifier) getOldJob().getPublisher(descriptor);

        assertNotNull(notifier);

        assertEquals("Default", notifier.getRundeckInstance());
        assertEquals("ded72a13-8d82-48be-bec9-08a3870d5210", notifier.getJobId());
        assertEquals("test", notifier.getTag());
        assertTrue(Arrays.equals(new String[] {"test"}, notifier.getTags()));
    }

    @LocalData
    public void test_GivenAJobConfigWithoutRundeckInstance_WhenSavingJob_ThenPersistedConfigContainsInstanceName() throws Exception {
        FreeStyleProject job = getOldJob();

        job.save();

        String storedConfig = FileUtils.readFileToString(job.getConfigFile().getFile());

        String expected = "<?xml version='1.1' encoding='UTF-8'?>\n" +
                "<project>\n" +
                "  <actions/>\n" +
                "  <description></description>\n" +
                "  <keepDependencies>false</keepDependencies>\n" +
                "  <properties/>\n" +
                "  <scm class=\"hudson.scm.NullSCM\"/>\n" +
                "  <canRoam>true</canRoam>\n" +
                "  <disabled>false</disabled>\n" +
                "  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>\n" +
                "  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>\n" +
                "  <triggers/>\n" +
                "  <concurrentBuild>false</concurrentBuild>\n" +
                "  <builders/>\n" +
                "  <publishers>\n" +
                "    <org.jenkinsci.plugins.rundeck.RundeckNotifier>\n" +
                "      <rundeckInstance>Default</rundeckInstance>\n" +
                "      <jobId>ded72a13-8d82-48be-bec9-08a3870d5210</jobId>\n" +
                "      <options></options>\n" +
                "      <nodeFilters></nodeFilters>\n" +
                "      <tags>\n" +
                "        <string>test</string>\n" +
                "      </tags>\n" +
                "      <shouldWaitForRundeckJob>true</shouldWaitForRundeckJob>\n" +
                "      <shouldFailTheBuild>true</shouldFailTheBuild>\n" +
                "      <notifyOnAllStatus>false</notifyOnAllStatus>\n" +
                "      <includeRundeckLogs>false</includeRundeckLogs>\n" +
                "      <tailLog>false</tailLog>\n" +
                "    </org.jenkinsci.plugins.rundeck.RundeckNotifier>\n" +
                "  </publishers>\n" +
                "  <buildWrappers/>\n" +
                "</project>";

        assertEquals(expected, storedConfig);
    }

    @LocalData
    public void test_GivenADescriptorConfigWithoutCache_WhenInstanciatingDescriptorCacheWithDefaultValuesIsUsed() {
        RundeckDescriptor descriptor = (RundeckDescriptor) this.jenkins.getDescriptorOrDie(RundeckNotifier.class);

        assertEquals(false, descriptor.getRundeckJobCacheConfig().isEnabled());
        assertEquals(18 * 60, descriptor.getRundeckJobCacheConfig().getAfterAccessExpirationInMinutes());
        assertEquals(500, descriptor.getRundeckJobCacheConfig().getMaximumSize());
        assertEquals(200, descriptor.getRundeckJobCacheConfig().getCacheStatsDisplayHitThreshold());
    }

    private FreeStyleProject getOldJob() {
        return ((FreeStyleProject)this.jenkins.getItem("old"));
    }
}
