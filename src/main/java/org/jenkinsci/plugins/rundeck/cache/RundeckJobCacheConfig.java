package org.jenkinsci.plugins.rundeck.cache;

public class RundeckJobCacheConfig {

    private boolean enabled = false;
    private int jobDetailsAfterWriteExpirationInMinutes = 30;

    public RundeckJobCacheConfig(boolean enabled, int jobDetailsAfterWriteExpirationInMinutes) {
        this.enabled = enabled;
        this.jobDetailsAfterWriteExpirationInMinutes = jobDetailsAfterWriteExpirationInMinutes;
    }

    private RundeckJobCacheConfig() {
        System.out.println("======= RundeckJobCacheConfig created");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getJobDetailsAfterWriteExpirationInMinutes() {
        return jobDetailsAfterWriteExpirationInMinutes;
    }

    public static RundeckJobCacheConfig initializeWithDefaultValues() {
        return new RundeckJobCacheConfig();
    }

    @Override
    public String toString() {
        return "RundeckJobCacheConfig{" +
                "enabled=" + enabled +
                ", jobDetailsAfterWriteExpirationInMinutes=" + jobDetailsAfterWriteExpirationInMinutes +
                '}';
    }
}