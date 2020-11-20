## Change Log

#### Version 3.6.1 (Jan 26, 2017)

-   [JENKINS-34510](https://issues.jenkins-ci.org/browse/JENKINS-34510)
    Improve Jenkins startup time and Rundeck jobs generation with
    job-dsl thanks to caching (**please note**: it has to be enabled
    explicitly in the global config)
-   [JENKINS-31423](https://issues.jenkins-ci.org/browse/JENKINS-31423)
    Allow build parameters or references to environment variables in
    'Job Identifier' field
-   Add Jenkins Pipelines support to Option Provider
-   Update RunDeck API Java client library to 13.1

#### TODO list

-   Internationalization
-   Option provider : add integration with the [Promoted Builds
    Plugin](http://wiki.jenkins-ci.org/display/JENKINS/Promoted+Builds+Plugin)
    and/or [Promoted Builds Simple
    Plugin](http://wiki.jenkins-ci.org/display/JENKINS/Promoted+Builds+Simple+Plugin)
    so that we can filter only "promoted" builds.
-   Use a drop-down field to select the RunDeck job (list of jobs
    retrieved using the API) instead of the basic text field

### Previous versions

##### Version 3.6.0 - skipped

##### Version 3.5.4 (May 19, 2016)

-   New:
    [JENKINS-22851](https://issues.jenkins-ci.org/browse/JENKINS-31150)
    Support multiple Rundeck installations: Configure multiple Rundeck
    instances (in the global config), and choose the instance to use in
    the job configuration
-   New:
    [JENKINS-31150](https://issues.jenkins-ci.org/browse/JENKINS-31150)
    Support Multiple SCM tags for rundeck notifier
-   Fixed:
    [JENKINS-28697](https://issues.jenkins-ci.org/browse/JENKINS-28697)
    Aborted job is marked as SUCCESS if "Wait for Rundeck" is used

##### Version 3.5 (Oct 20, 2015)

-   Use display name for the build name
-   Set the build result to failure when rundeck fails AND
    shouldFailTheBuild is set to true. Previously only "marked" the
    failure; by changing the status of the build to FAIL, other plugins
    that might be called after rundeck will then be able to detect the
    status, and handle accordingly.
-   Fixed bug where log tailer returned completed even though the job
    was still running (output was completed). Now both execCompleted and
    completed are validated
-   Filtered out RundeckOutputEntries with empty messages
-   No longer depend on last mod for sleep timing since behaviour seems
    to be not aligned with expectations. Now checking if offset has
    changed.
-   Fixed bug where checkbox wasn't visible in Jenkins
-   \[[JENKINS-28059](https://issues.jenkins-ci.org/browse/JENKINS-28059)\]
    Validate API version in test connection

##### Version 3.4 (Apr 16, 2015)

-   Added support to include Rundeck logs in the Jenkins build log
-   Support jobs inside folders
-   Fix for Exception when using $ARTIFACT\_NAME
-   Fixed: Badge icons fail is jenkins is not root as "/"
-   Changed "RunDeck" text items to the correct "Rundeck"
-   Updated the Rundeck client to v12.0 \[Fixes
    [JENKINS-27971](https://issues.jenkins-ci.org/browse/JENKINS-27971)
    — NPE with Rundeck v2.4.2\]

##### Version 3.2 (April 15, 2014)

-   Fixed issue with parsing Job options when option values have hyphens

##### Version 3.1 (March 29, 2014)

-   Build the URL using the getRootUrl method, which will use the user
    configured root url

##### Version 3.0 (January 28, 2014)

-   Update rundeck API client lib to latest (9.3)
-   Support Token authentication
-   Fix authentication against Rundeck running as a war in Tomcat
-   Support RDECK\_EXEC\_ARG\_\[NAME\] in triggers from Rundeck webhook
    notifications
-   Update naming ("RunDeck" changed to "Rundeck"), update icon

##### Version 2.11 (January 4, 2012)

-   Fix
    [JENKINS-12228](https://issues.jenkins-ci.org/browse/JENKINS-12228)
    : allow to filter artifacts returned by the option provider, based
    on a java-regex

##### Version 2.10 (October 12, 2011)

-   Fix icon path URL - Thanks to [Joe
    Passavanti](https://github.com/joepcds) for the
    [patch](https://github.com/vbehar/jenkins-rundeck-plugin/pull/2) !
-   Small UI fix : don't display job's ID (in rundeck 1.3+, ID is an
    UUID, and it breaks the UI because it is too long)

##### Version 2.9 (September 18, 2011)

-   Allow to filter nodes when triggering a rundeck job (using the
    "nodeFilters" parameter)

##### Version 2.8 (September 16, 2011)

-   Configure RunDeck jobs with either a job ID, or an UUID (rundeck
    1.3+), or a "reference". A job reference is expressed in the format
    "project:group/job", for example :
    "my-project-name:main-group/sub-group/my-job-name", or
    "my-project-name:my-job-name" (for a job without a group).

##### Version 2.7 (September 14, 2011)

-   Add a build trigger, using RunDeck 1.3 [WebHook
    Notification](http://rundeck.org/docs/RunDeck-Guide.html#webhooks),
    so that you can run integration tests with Jenkins after a RunDeck
    deployment (alternative to the "Wait for RunDeck job to finish ?"
    checkbox in the notifier configuration and a post-build action to
    schedule another job)
-   Upgrade [RunDeck API Java
    client](http://vbehar.github.com/rundeck-api-java-client/) to
    version 1.2

##### Version 2.6 (September 2, 2011)

-   Add token expansion for $ARTIFACT\_NAME{regex} in options (see
    <http://groups.google.com/group/rundeck-discuss/browse_thread/thread/94a6833b84fdc10b>)

##### Version 2.5 (July 11, 2011)

-   Internal refactoring : use the [RunDeck API Java
    client](http://vbehar.github.com/rundeck-api-java-client/)
-   Never display the RunDeck password in logs (even in case of error)

##### Version 2.4 (June 28, 2011)

-   Change Job ID support to use Strings instead of Long, allowing UUIDs
    (coming in RunDeck 1.3) - Thanks to [Greg
    Schueler](https://github.com/gschueler) for the
    [patch](https://github.com/jenkinsci/rundeck-plugin/pull/1) !

##### Version 2.3.1 (June 22, 2011)

-   Fix a bug introduced in version 2.3 : NPE related to the new field
    (shouldWaitForRundeckJob) in already configured jobs. Workaround is
    to re-save job configuration or use version 2.3.1

##### Version 2.3 (June 21, 2011)

-   Add an option to wait for the RunDeck job to finish (by polling the
    execution every 5 seconds via the RunDeck API)
-   Add a validation button on the job configuration screen, to check
    the RunDeck job (display job name, group and project)

##### Version 2.2 (June 17, 2011)

-   Add SSL support for RunDeck REST API (trust all certificates and
    hosts)

##### Version 2.1 (June 8, 2011)

-   New feature : display information about the RunDeck job on the page
    of a Jenkins job (with a direct link to the RunDeck job details
    webpage)

##### Version 2.0.1 (June 8, 2011)

-   Rerelease 2.0 and mark it as incompatible with versions 1.x (jobs
    configuration needs to be updated), so that users can see it in the
    update-center before updating.

##### Version 2.0 (June 6, 2011)

Compatibility Warning !

This version won't work with RunDeck 1.0/1.1, and the configuration per
job has changed, you will need to update the configuration for all your
jobs that use this plugin !

-   Use the new [RunDeck 1.2+ HTTP REST
    API](http://rundeck.org/docs/RunDeck-Guide.html#rundeck-api), and
    thus is incompatible with RunDeck 1.0 or RunDeck 1.1
-   Use "jobId" to reference RunDeck jobs, instead of the
    "groupPath/jobName" couple, so you'll need to reconfigure your
    Jenkins jobs. We switched to the "jobId" reference because it is
    unique across all projects in a RunDeck instance, which is not the
    case for the "groupPath/jobName" couple.
-   Set required Jenkins version to 1.400

##### Version 1.8 (June 5, 2011)

-   Fix
    [JENKINS-9876](https://issues.jenkins-ci.org/browse/JENKINS-9876) :
    password field in system configuration should be hidden.

##### Version 1.7 (June 1, 2011)

-   New improvement to the option provider : you can now match artifacts
    with a java-regex in addition to exact-match of the artifact
    filename (see the new 'artifactRegex' parameter).

##### Version 1.6 (April 6, 2011)

-   Fix a bug with RunDeck 1.2 : scheduling a job with options did not
    work on RunDeck 1.2.
-   Set required Jenkins version to 1.399 ([See the thread on the
    jenkinsci-dev
    mailing-list](http://groups.google.com/group/jenkinsci-dev/msg/26408e6401dd6ee0)).

##### Version 1.5.1 (March 24, 2011)

-   Rerelease 1.5 to properly set required Jenkins version ([See the
    thread on the jenkinsci-dev
    mailing-list](http://groups.google.com/group/jenkinsci-dev/msg/26408e6401dd6ee0))
    : the plugin now depends on Jenkins 1.398 (or higher).

##### Version 1.5 (March 4, 2011)

-   Fix bug : when using a "tag" to auto-deploy, we should also check
    the SCM changelog from upstream builds. So that you can commit to an
    upstream job, and have all downstream jobs redeployed.

##### Version 1.4 (March 1, 2011)

-   New improvement to the option provider : in addition to the list of
    artifacts for a given build, you can now get the list of builds
    (versions) for a given artifact.

##### Version 1.3 (February 27, 2011)

-   Jenkins can now be used as an "[Option
    provider](http://rundeck.org/docs/RunDeck-Guide.html#option-model-provider)"
    for RunDeck, if you want to use your Jenkins build artifacts as an
    option to a RunDeck job.

##### Version 1.2 (February 27, 2011)

-   Jenkins environment variables specified in the "options" are now
    correctly expanded ([GitHub
    issue](https://github.com/vbehar/jenkins-rundeck-plugin/issues/1))

##### Version 1.1 (February 11, 2011)

-   Do nothing if the build is failing
-   Add a link to the RunDeck job execution page (on each Jenkins
    successful build)
-   Validation on the form fields (test if RunDeck is alive, test
    credentials, etc)

##### Version 1.0 (February 10, 2011)

-   Initial release
-   Compatible (and tested) with Jenkins 1.396 and RunDeck 1.1
