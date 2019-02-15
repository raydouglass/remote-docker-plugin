/*
 * Copyright (c) 2019, NVIDIA CORPORATION.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gpuopenanalytics.jenkins.remotedocker.config;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.util.ArgumentListBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

/**
 * Additional environment variables passed as <code>-e KEY=VALUE</code>
 */
public class EnvironmentVariableConfigItem extends ConfigItem {

    private String environment;

    @DataBoundConstructor
    public EnvironmentVariableConfigItem(String environment) {
        this.environment = environment;
    }

    @Override
    public void validate() throws Descriptor.FormException {
        try {
            parsePropertiesString(environment);
        } catch (IOException e) {
            throw new Descriptor.FormException(e.getMessage(), "environment");
        }
    }

    @Override
    public void addArgs(ArgumentListBuilder args, AbstractBuild build) {
        try {
            Properties props = parsePropertiesString(environment);
            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                args.add("-e");
                args.addKeyValuePair("", key, value, false);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Properties parsePropertiesString(String s) throws IOException {
        final Properties p = new Properties();
        p.load(new StringReader(s));
        return p;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ConfigItem> {

        @Override
        public String getDisplayName() {
            return "Environment Variables";
        }
    }
}