package org.jenkinsci.plugins.rundeck;

import hudson.util.Secret;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.RundeckClientBuilder;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class RundeckInstanceBuilder {
    private String url;
    private int apiVersion=RundeckClient.API_VERSION;
    private String login;
    private Secret token;
    private Secret password;
    RundeckClient client;

    public RundeckInstanceBuilder() {
    }

    void setClient(RundeckClient client){
        this.client = client;
    }

    RundeckClient getClient(){
        return client;
    }

    RundeckInstanceBuilder client(RundeckClient client){
        this.url = client.getUrl();
         if(client.getPassword()!=null){
            this.password = Secret.fromString(client.getPassword());
        }
        if(client.getToken()!=null){
            this.token = Secret.fromString(client.getToken());
        }
        this.login = client.getLogin();

        String apiVersionLoaded = this.getApiVersion(client);
        if(apiVersionLoaded!=null && !apiVersionLoaded.isEmpty()){
            this.apiVersion = Integer.parseInt(apiVersionLoaded);
        }else{
            this.apiVersion = RundeckClient.API_VERSION;
        }

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

    public RundeckInstance build() {
        RundeckInstance client = new RundeckInstance();
        client.setUrl(this.url);
        client.setApiVersion(this.apiVersion);
        client.setLogin(this.login);
        client.setPassword(this.password);
        client.setToken(this.token);

        return client;
    }


    static RundeckClient createClient(RundeckInstance instance){

        RundeckClientBuilder builder = RundeckClient.builder();
        builder.url(instance.getUrl());
        if (instance.getToken() != null && !"".equals(instance.getToken().getPlainText())) {
            builder.token(instance.getToken().getPlainText());
        } else {
            builder.login(instance.getLogin(), instance.getPasswordPlainText());
        }

        if (instance.getApiVersion() > 0) {
            builder.version(instance.getApiVersion());
        }

        return  builder.build();

    }

    public String getApiVersion(RundeckClient instance) {
        if (instance != null) {
            try {
                Method method = instance.getClass().getDeclaredMethod("getApiVersion");
                method.setAccessible(true);

                return method.invoke(instance).toString();
            } catch (SecurityException | NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException e) {
                return "";
            }
        }

        return "";
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
