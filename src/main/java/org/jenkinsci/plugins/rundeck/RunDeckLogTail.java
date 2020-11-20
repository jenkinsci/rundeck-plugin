package org.jenkinsci.plugins.rundeck;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.NoSuchElementException;
import org.rundeck.api.RundeckApiException;
import org.rundeck.api.RundeckClient;
import org.rundeck.api.domain.RundeckOutput;
import org.rundeck.api.domain.RundeckOutputEntry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements logtailing for Rundeck.
 * 
 * @author ckramer
 */
public class RunDeckLogTail implements Iterable<List<RundeckOutputEntry>> {

    private static final Logger log = Logger.getLogger(RunDeckLogTail.class.getName());

    private final RundeckClient rundeckClient;
    private final Long executionId;
    private final int maxlines;
    private final int maxRetries;
    private final long sleepRetry;
    private final long sleepUnmodified;
    private final long sleepModified;

    /**
     * Standard constructor that contains sensible defaults for handling the API calls correctly.
     * 
     * @param rundeckClient
     * @param executionId
     */
    public RunDeckLogTail(RundeckClient rundeckClient, Long executionId) {
        this(rundeckClient, executionId, 50, 5, 15000L, 5000L, 2000L);
    }

    /**
     * Extended constructor containing all the variables that can be set.
     * 
     * @param rundeckClient
     *            the runDeckClient
     * @param executionId
     *            the id of the RunDeck job
     * @param maxlines
     *            the maximum number of lines to fetch on each API call
     * @param maxRetries
     *            the maximum number of retry attempts if the api call fails
     * @param sleepRetry
     *            sleep time in ms that will be triggered if a api call fails
     * @param sleepUnmodified
     *            sleep time in ms when the results are unmodified
     * @param sleepModified
     *            sleep time in ms when the results are modified.
     */
    public RunDeckLogTail(RundeckClient rundeckClient, Long executionId, int maxlines, int maxRetries, long sleepRetry, long sleepUnmodified, long sleepModified) {
        this.rundeckClient = rundeckClient;
        this.executionId = executionId;
        this.maxlines = maxlines;
        this.maxRetries = maxRetries;
        this.sleepRetry = sleepRetry;
        this.sleepUnmodified = sleepUnmodified;
        this.sleepModified = sleepModified;

    }

    public RunDeckLogTailIterator iterator() {
        return new RunDeckLogTailIterator();
    }

    protected class RunDeckLogTailIterator implements Iterator<List<RundeckOutputEntry>> {

        protected int offset;
        protected boolean completed;
        protected int retries = 0;
        protected int controlMaxlines = maxlines;

        protected List<RundeckOutputEntry> next;

        /**
         * This will clear and update the result set for the @link {@link #next()} call using the RunDeck Client to perform an API call, it will also update the
         * offset and last modification date for the next API call. If there are no changes since the last call, this method will sleep for 5 seconds. If there
         * are changes, it will sleep for 2 seconds so it won't overload the API. Once the API call returns with 'completed' the next call to this method will
         * return false.</br> If for some reason the sleep is interrupted, the next call to this method will return false.</br>
         */
        public boolean hasNext() {

            if (completed) {
                return false;
            }

            next = new ArrayList<RundeckOutputEntry>(maxlines);

            try {
                try {
                    log.log(Level.FINE, "Performing API call for executionId [{0}], using offset [{1}]. fetching a maximum of [{2}] lines.", new Object[] {
                            executionId, offset, maxlines });
                    RundeckOutput rundeckOutput = rundeckClient.getExecutionOutputState(executionId, false, offset, -1, controlMaxlines);
                    completed = checkCompletionState(rundeckOutput);

                    boolean offsetChanged = updateIterationState(rundeckOutput);
                    addRunDeckOutputEntriesToResults(rundeckOutput);

                    if(checkExecCompletionState(rundeckOutput)){
                        log.log(Level.FINE, "RunDecks Execution Output is not yet completed but the rundeck job just finished.");
                        controlMaxlines = -1;
                    }

                    if (!completed) {
                        log.log(Level.FINE, "RunDecks Execution Output is not yet completed. Initializing pause to prevent API hammering");
                        handleSleep(offsetChanged);
                    }
                    retries = 0;
                } catch (RundeckApiException e) {
                    log.log(Level.WARNING, "Caught RuntimeException while handling API call for logs. Will retry for max [{0}] times or rethrow exception.", new Object[] {
                            maxRetries, e });
                    sleepOrThrowException(e);
                }
            } catch (InterruptedException e) {
                log.warning("Caught InterruptedException, will set completed to 'true'.");
                completed = true;
            }
            return true;
        }

        private boolean checkCompletionState(RundeckOutput rundeckOutput) {
            return checkOutputCompletionState(rundeckOutput) && checkExecCompletionState(rundeckOutput);
        }
        private boolean checkExecCompletionState(RundeckOutput rundeckOutput) {
            boolean execCompleted = Boolean.TRUE.equals(rundeckOutput.isExecCompleted());
            log.log(Level.FINE, "Checking completetion state with execCompleted [{0}]", new Object[] { execCompleted});
            return execCompleted;
        }

        private boolean checkOutputCompletionState(RundeckOutput rundeckOutput) {
            boolean outputCompleted = Boolean.TRUE.equals(rundeckOutput.isCompleted());
            log.log(Level.FINE, "Checking completetion state with outputCompleted [{0}]", new Object[] { outputCompleted });
            return outputCompleted;
        }

        private void sleepOrThrowException(RuntimeException e) throws InterruptedException {
            if (retries >= maxRetries) {
                log.log(Level.SEVERE, "Giving up after [{0}] retries...", new Object[] { maxRetries, e });
                throw e;
            }
            retries++;
            Thread.sleep(sleepRetry);
        }

        private boolean updateIterationState(RundeckOutput rundeckOutput) {
            int nextOffset = rundeckOutput.getOffset();
            if (offset != nextOffset) {
                offset = nextOffset;
                log.log(Level.FINE, "Offset is now set to [{0}]", new Object[] { offset });
                return true;
            }
            return false;
        }

        private void addRunDeckOutputEntriesToResults(RundeckOutput rundeckOutput) {

            List<RundeckOutputEntry> runDeckOutputEntries = rundeckOutput.getLogEntries();
            if (runDeckOutputEntries != null) {
                handleMaxlines(runDeckOutputEntries);
                log.log(Level.FINE, "Got [{0}] rundeckOutputEntries, filtering out empty results and appending resultset.", runDeckOutputEntries.size());
                for (RundeckOutputEntry rundeckOutputEntry : runDeckOutputEntries) {
                    if (rundeckOutputEntry.getMessage() != null) {
                        next.add(rundeckOutputEntry);
                    }
                }
            }
        }

        private void handleMaxlines(List<RundeckOutputEntry> runDeckOutputEntries){
            if(controlMaxlines >= maxlines * 10){
                controlMaxlines = -1;
                log.log(Level.FINE, "Maxlines has changed, requesting all last results");
            } else if(controlMaxlines == -1){
                controlMaxlines = maxlines;
                log.log(Level.FINE, "Maxlines has changed, requesting the last [{0}] lines.", controlMaxlines);
            } else if(runDeckOutputEntries.size() == controlMaxlines){
                controlMaxlines += maxlines;
                log.log(Level.FINE, "Maxlines has changed, requesting the last [{0}] lines.", controlMaxlines);
            } else if(runDeckOutputEntries.size() < controlMaxlines && controlMaxlines > maxlines){
                controlMaxlines -= maxlines;
                log.log(Level.FINE, "Maxlines has changed, requesting the last [{0}] lines.", controlMaxlines);
            }
        }

        private void handleSleep(boolean offsetChanged) throws InterruptedException {
            if (offsetChanged) {
                log.log(Level.FINE, "Offset has changed, sleeping for [{0}] ms.", sleepModified);
                Thread.sleep(sleepModified);
            } else {
                log.log(Level.FINE, "Results hasn't changed, sleeping for [{0}] ms.", sleepUnmodified);
                Thread.sleep(sleepUnmodified);
            }
        }

        /**
         * Returns the resultset which has been fetched using the hasNext method.
         */
        @SuppressFBWarnings(value = "IT_NO_SUCH_ELEMENT", justification = "weird design, needs reworking to fix")
        public List<RundeckOutputEntry> next() {
            return next;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

}
