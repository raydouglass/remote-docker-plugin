/*
 * The MIT License
 *
 * Copyright (c) 2019, NVIDIA CORPORATION.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.gpuopenanalytics.jenkins.remotedocker.pipeline;

import com.gpuopenanalytics.jenkins.remotedocker.DockerLauncher;
import com.gpuopenanalytics.jenkins.remotedocker.RemoteDockerBuildWrapper;
import com.gpuopenanalytics.jenkins.remotedocker.job.AbstractDockerConfiguration;
import com.gpuopenanalytics.jenkins.remotedocker.job.DockerImageConfiguration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;

public class RemoteDockerStepExecution extends StepExecution {

    private static final long serialVersionUID = 1L;


    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
    private transient RemoteDockerStep remoteDockerStep;
    private transient DockerLauncher dockerLauncher;

    private DockerState dockerState;

    public RemoteDockerStepExecution(@Nonnull StepContext context,
                                     RemoteDockerStep remoteDockerStep) {
        super(context);
        this.remoteDockerStep = remoteDockerStep;
    }

    @Override
    public boolean start() throws Exception {
        DockerImageConfiguration imageConfig = new DockerImageConfiguration(
                Collections.emptyList(), Collections.emptyList(),
                remoteDockerStep.getImage(), true);
        createDockerLauncher(imageConfig);
        dockerState = dockerLauncher.launchContainers();

        DockerLauncherDecorator dockerLauncherDecorator = new DockerLauncherDecorator(
                dockerLauncher.isDebug(),
                dockerLauncher.getMainContainerId(),
                imageConfig);

        LauncherDecorator launcherDecorator = BodyInvoker.mergeLauncherDecorators(
                getContext().get(LauncherDecorator.class),
                dockerLauncherDecorator);
        getContext().newBodyInvoker()
                .withContext(launcherDecorator)
                .withCallback(new Callback(dockerState))
                .start();


        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
        if (dockerState != null) {
            Launcher launcher = getContext().get(Launcher.class);
            dockerState.tearDown(launcher);
        }

    }

    @Override
    public void onResume() {

    }

    @CheckForNull
    @Override
    public String getStatus() {
        return null;
    }

    private void createDockerLauncher(AbstractDockerConfiguration dockerConfiguration) throws IOException, InterruptedException {

        RemoteDockerBuildWrapper buildWrapper = new RemoteDockerBuildWrapper(
                true, dockerConfiguration, null);
        Launcher launcher = getContext().get(Launcher.class);
        TaskListener listener = getContext().get(TaskListener.class);
        Run<?, ?> run = getContext().get(Run.class);
        FilePath workspace = getContext().get(FilePath.class);
        dockerLauncher = new DockerLauncher(true,
                                            new EnvVars(),
                                            workspace.toComputer(),
                                            workspace,
                                            launcher,
                                            listener,
                                            buildWrapper);
    }

    private static class Callback extends BodyExecutionCallback {

        private DockerState dockerState;

        public Callback(DockerState dockerState) {
            this.dockerState = dockerState;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            try {
                Launcher launcher = context.get(Launcher.class);
                dockerState.tearDown(launcher);
                context.onSuccess(result);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            try {
                Launcher launcher = context.get(Launcher.class);
                dockerState.tearDown(launcher);
                context.onFailure(t);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
