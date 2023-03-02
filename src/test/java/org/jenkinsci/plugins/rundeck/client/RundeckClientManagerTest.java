package org.jenkinsci.plugins.rundeck.client;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import hudson.util.Secret;
import org.jenkinsci.plugins.rundeck.RundeckInstance;
import org.junit.Test;
import org.rundeck.client.RundeckClient;
import org.rundeck.client.RundeckClient.Builder;
import org.rundeck.client.api.RundeckApi;

public class RundeckClientManagerTest {

    @Test
    public void usingTokenDoesNotTryToSetUsernameAndPasswordCredentialsMethodWhenEarlierLoginInformationIsFoundInConfiguration() {

        Builder<RundeckApi> clientBuilderSpy = spy(RundeckClient.builder());

        RundeckInstance rundeckInstanceMock = mock(RundeckInstance.class);
        when(rundeckInstanceMock.getUrl()).thenReturn("http://localhost:4044");

        when(rundeckInstanceMock.getToken()).thenReturn(Secret.fromString("aToken"));
        when(rundeckInstanceMock.getTokenPlainText()).thenReturn("aToken");
        when(rundeckInstanceMock.getLogin()).thenReturn("bad_data_from_plugin_data_somehow");
        when(rundeckInstanceMock.getPassword()).thenReturn(Secret.fromString(""));
        when(rundeckInstanceMock.getPasswordPlainText()).thenReturn("");

        RundeckClientManager rundeckClientManager = new RundeckClientManager(rundeckInstanceMock);
        rundeckClientManager.setRundeckClientBuilder(clientBuilderSpy);
        rundeckClientManager.buildClient();

        verify(clientBuilderSpy, never()).passwordAuth(anyString(), anyString());

    }
}
