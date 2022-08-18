package org.jenkinsci.plugins.rundeck;

import hudson.util.Secret;
import org.jenkinsci.plugins.rundeck.client.RundeckClientManager;
import org.jenkinsci.plugins.rundeck.client.RundeckManager;

public class RundeckInstanceBuilder {
    private String url;
    private int apiVersion=RundeckClientManager.API_VERSION;
    private String login;
    private Secret token;
    private Secret password;
    RundeckManager client;
    private boolean allowSelfSignedSSL;

    public RundeckInstanceBuilder() {
    }

    void setClient(RundeckManager client){
        this.client = client;
    }

    RundeckManager getClient(){
        return client;
    }

    RundeckInstanceBuilder client(RundeckManager client){
        this.url = client.getRundeckInstance().getUrl();
         if(client.getRundeckInstance().getPassword()!=null){
            this.password = client.getRundeckInstance().getPassword();
        }
        if(client.getRundeckInstance().getToken()!=null){
            this.token = client.getRundeckInstance().getToken();
        }
        this.login = client.getRundeckInstance().getLogin();

        if(client.getRundeckInstance().getApiVersion()!=null){
            this.apiVersion = client.getRundeckInstance().getApiVersion();
        }else{
            this.apiVersion = RundeckClientManager.API_VERSION;
        }

        return this;
    }

    RundeckInstanceBuilder client(RundeckInstance client){
        this.url = client.getUrl();
        if(client.getPassword()!=null){
            this.password = client.getPassword();
        }
        if(client.getToken()!=null){
            this.token = client.getToken();
        }
        this.login = client.getLogin();
        this.apiVersion = client.getApiVersion();
        return this;
    }

    RundeckInstanceBuilder url(String url){
        this.url = url;
        return this;
    }

    RundeckInstanceBuilder version(int apiVersion){
        this.apiVersion = apiVersion;
        return this;
    }

    RundeckInstanceBuilder login(String login, Secret password){
        this.login = login;
        this.password = password;
        return this;
    }

    RundeckInstanceBuilder token(Secret token){
        this.token = token;
        return this;
    }

    RundeckInstanceBuilder allowSelfSignedSSL(boolean allowSelfSignedSSL){
        this.allowSelfSignedSSL = allowSelfSignedSSL;
        return this;
    }

    public RundeckInstance build() {
        RundeckInstance client = new RundeckInstance();
        client.setUrl(this.url);
        client.setApiVersion(this.apiVersion);
        client.setLogin(this.login);
        client.setPassword(this.password);
        client.setToken(this.token);
        client.setSslCertificateTrustAllowSelfSigned(this.allowSelfSignedSSL);

        return client;
    }


    static RundeckClientManager createClient(RundeckInstance instance){
        RundeckClientManager clientManager = new RundeckClientManager(instance);
        return clientManager;
    }

    @Override
    public String toString() {
        return "RundeckInstanceBuilder{" +
                "url='" + url + '\'' +
                ", apiVersion=" + apiVersion +
                ", login='" + login + '\'' +
                ", token=" + token +
                ", password=" + password +
                ", client=" + client +
                '}';
    }
}
