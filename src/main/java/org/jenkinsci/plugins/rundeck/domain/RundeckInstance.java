package org.jenkinsci.plugins.rundeck.domain;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.rundeck.domain.RundeckApiException.RundeckApiJobRunException;
import org.jenkinsci.plugins.rundeck.domain.RundeckApiException.RundeckApiLoginException;

/**
 * Represents a RunDeck instance, and allows to communicate with it. Uses RunDeck 1.2 "WebApi" (version 1).
 * 
 * @author Vincent Behar
 */
public class RundeckInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String url;

    private final String login;

    private final String password;

    /**
     * Build a new RunDeck instance
     * 
     * @param url of the RunDeck server (http://localhost:4440)
     * @param login to use
     * @param password associated with the login
     */
    public RundeckInstance(String url, String login, String password) {
        super();
        this.url = url;
        this.login = login;
        this.password = password;
    }

    /**
     * @return true if the configuration is valid, false otherwise
     */
    public boolean isConfigurationValid() {
        return StringUtils.isNotBlank(url) && StringUtils.isNotBlank(login) && StringUtils.isNotBlank(password);
    }

    /**
     * @return true if this RunDeck instance is alive, false otherwise
     */
    public boolean isAlive() {
        HttpClient httpClient = instantiateHttpClient();
        try {
            HttpResponse response = httpClient.execute(new HttpGet(url));
            return response.getStatusLine().getStatusCode() / 100 == 2;
        } catch (IOException e) {
            return false;
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    /**
     * @return true if the credentials (login/password) are valid, false otherwise
     */
    public boolean isLoginValid() {
        HttpClient httpClient = instantiateHttpClient();
        try {
            doLogin(httpClient);
            return true;
        } catch (RundeckApiLoginException e) {
            return false;
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    /**
     * Get the details of a job, identified by the given id.
     *
     * @param jobId - mandatory
     * @return a {@link RundeckJob} instance (won't be null), with details on the job
     * @throws RundeckApiException in case of error calling the API
     * @throws RundeckApiLoginException if the login failed
     */
    public RundeckJob getJob(String jobId) throws RundeckApiException, RundeckApiLoginException {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is mandatory to get the details of a job !");
        }

        HttpClient httpClient = instantiateHttpClient();
        try {
            doLogin(httpClient);

            String jobUrl = url + "/api/1/job/" + jobId;

            HttpResponse response = null;
            try {
                response = httpClient.execute(new HttpGet(jobUrl));
            } catch (IOException e) {
                throw new RundeckApiException("Failed to get job definition for ID " + jobId + " on url : " + jobUrl, e);
            }
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new RundeckApiException("Invalid HTTP response '" + response.getStatusLine() + "' for " + jobUrl);
            }

            // retrieve execution details
            if (response.getEntity() == null) {
                throw new RundeckApiException("Empty RunDeck response ! HTTP status line is : "
                                              + response.getStatusLine());
            }
            try {
                RundeckJob job = RundeckUtils.parseJobDefinition(response.getEntity().getContent());
                EntityUtils.consume(response.getEntity());
                return job;
            } catch (IOException e) {
                throw new RundeckApiException("Failed to read RunDeck response", e);
            }
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    /**
     * Get the details of a job execution, identified by the given id.
     * 
     * @param executionId - mandatory
     * @return a {@link RundeckExecution} instance (won't be null), with details on the execution
     * @throws RundeckApiException in case of error calling the API
     * @throws RundeckApiLoginException if the login failed
     */
    public RundeckExecution getExecution(Long executionId) throws RundeckApiException, RundeckApiLoginException {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId is mandatory to get the details of an execution !");
        }

        HttpClient httpClient = instantiateHttpClient();
        try {
            doLogin(httpClient);

            String executionUrl = url + "/api/1/execution/" + executionId;

            HttpResponse response = null;
            try {
                response = httpClient.execute(new HttpGet(executionUrl));
            } catch (IOException e) {
                throw new RundeckApiException("Failed to get execution definition for ID " + executionId + " on url : "
                                              + executionUrl, e);
            }
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new RundeckApiException("Invalid HTTP response '" + response.getStatusLine() + "' for "
                                              + executionUrl);
            }

            // retrieve execution details
            if (response.getEntity() == null) {
                throw new RundeckApiException("Empty RunDeck response ! HTTP status line is : "
                                              + response.getStatusLine());
            }
            try {
                RundeckExecution execution = RundeckUtils.parseExecution(response.getEntity().getContent());
                EntityUtils.consume(response.getEntity());
                return execution;
            } catch (IOException e) {
                throw new RundeckApiException("Failed to read RunDeck response", e);
            }
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    /**
     * Run a job, identified by the given id.
     *
     * @param jobId - mandatory
     * @param options for the job, optional
     * @return a {@link RundeckExecution} instance (won't be null), with details on the execution
     * @throws RundeckApiLoginException if the login failed
     * @throws RundeckApiJobRunException if the run failed
     * @throws RundeckApiException in case of error calling the API
     */
    public RundeckExecution runJob(String jobId, Properties options) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiJobRunException {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is mandatory to run a job !");
        }

        HttpClient httpClient = instantiateHttpClient();
        try {
            doLogin(httpClient);

            String scheduleUrl = url + "/api/1/job/" + jobId + "/run";
            String argString = RundeckUtils.generateArgString(options);
            if (StringUtils.isNotBlank(argString)) {
                String encodedArgString;
                try {
                    encodedArgString = URLEncoder.encode(argString, "UTF8");
                } catch (UnsupportedEncodingException e) {
                    throw new RundeckApiJobRunException("Failed to run job with ID " + jobId, e);
                }
                scheduleUrl += "?argString=" + encodedArgString;
            }

            HttpResponse response = null;
            try {
                response = httpClient.execute(new HttpGet(scheduleUrl));
            } catch (IOException e) {
                throw new RundeckApiJobRunException("Failed to run job with ID " + jobId + " on url : " + scheduleUrl,
                                                    e);
            }
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new RundeckApiJobRunException("Invalid HTTP response '" + response.getStatusLine() + "' for "
                                                    + scheduleUrl);
            }

            // retrieve execution details
            if (response.getEntity() == null) {
                throw new RundeckApiJobRunException("Empty RunDeck response ! HTTP status line is : "
                                                    + response.getStatusLine());
            }
            try {
                RundeckExecution execution = RundeckUtils.parseExecution(response.getEntity().getContent());
                EntityUtils.consume(response.getEntity());
                return execution;
            } catch (IOException e) {
                throw new RundeckApiJobRunException("Failed to read RunDeck response", e);
            }
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    /**
     * Do the actual work of login, using the given {@link HttpClient} instance. You'll need to re-use this instance
     * when making API calls (such as running a job).
     * 
     * @param httpClient
     * @throws RundeckApiLoginException if the login failed
     */
    private void doLogin(HttpClient httpClient) throws RundeckApiLoginException {
        String location = url + "/j_security_check";

        while (true) {
            HttpPost postLogin = new HttpPost(location);
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("j_username", login));
            params.add(new BasicNameValuePair("j_password", password));
            params.add(new BasicNameValuePair("action", "login"));

            HttpResponse response = null;
            try {
                postLogin.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
                response = httpClient.execute(postLogin);
            } catch (IOException e) {
                throw new RundeckApiLoginException("Failed to post login form on " + location, e);
            }

            if (response.getStatusLine().getStatusCode() / 100 == 3) {
                // HTTP client refuses to handle redirects (code 3xx) for POST, so we have to do it manually...
                location = response.getFirstHeader("Location").getValue();
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (IOException e) {
                    throw new RundeckApiLoginException("Failed to consume entity (release connection)", e);
                }
                continue;
            }
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new RundeckApiLoginException("Invalid HTTP response '" + response.getStatusLine() + "' for "
                                                   + location);
            }
            try {
                String content = EntityUtils.toString(response.getEntity(), HTTP.UTF_8);
                if (StringUtils.contains(content, "j_security_check")) {
                    throw new RundeckApiLoginException("Login failed for user " + login);
                }
                try {
                    EntityUtils.consume(response.getEntity());
                } catch (IOException e) {
                    throw new RundeckApiLoginException("Failed to consume entity (release connection)", e);
                }
            } catch (IOException io) {
                throw new RundeckApiLoginException("Failed to read RunDeck result", io);
            } catch (ParseException p) {
                throw new RundeckApiLoginException("Failed to parse RunDeck response", p);
            }
            break;
        }
    }

    /**
     * Instantiate a new {@link HttpClient} instance, configured to accept all SSL certificates
     * 
     * @return an {@link HttpClient} instance - won't be null
     */
    private HttpClient instantiateHttpClient() {
        SSLSocketFactory socketFactory = null;
        try {
            socketFactory = new SSLSocketFactory(new TrustStrategy() {

                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            }, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }

        HttpClient httpClient = new DefaultHttpClient();
        httpClient.getConnectionManager().getSchemeRegistry().register(new Scheme("https", 443, socketFactory));
        return httpClient;
    }

    public String getUrl() {
        return url;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "RundeckInstance [url=" + url + ", login=" + login + ", password=" + password + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((login == null) ? 0 : login.hashCode());
        result = prime * result + ((password == null) ? 0 : password.hashCode());
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RundeckInstance other = (RundeckInstance) obj;
        if (login == null) {
            if (other.login != null)
                return false;
        } else if (!login.equals(other.login))
            return false;
        if (password == null) {
            if (other.password != null)
                return false;
        } else if (!password.equals(other.password))
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        return true;
    }

}
