/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.zeppelin.flink.cmd;

import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterOutputListener;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessageOutput;
import org.apache.zeppelin.shell.ShellInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlinkCmdInterpreter extends ShellInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlinkCmdInterpreter.class);

  private String flinkHome;

  public FlinkCmdInterpreter(Properties property) {
    super(property);
    // Set time to be max integer so that the shell process won't timeout.
    setProperty("shell.command.timeout.millisecs", Integer.MAX_VALUE + "");
    this.flinkHome = properties.getProperty("FLINK_HOME");
    LOGGER.info("FLINK_HOME: " + flinkHome);
  }

  @Override
  public InterpreterResult internalInterpret(String cmd, InterpreterContext context) {
    String flinkCommand = flinkHome + "/bin/flink " + cmd.trim();
    LOGGER.info("Flink command: " + flinkCommand);
    context.out.addInterpreterOutListener(new FlinkCmdOutputListener(context, getProperties()));
    return super.internalInterpret(flinkCommand, context);
  }

  /**
   * InterpreterOutputListener which extract flink link from logs.
   */
  private static class FlinkCmdOutputListener implements InterpreterOutputListener {

    private Properties properties;
    private InterpreterContext context;
    private boolean isFlinkUrlSent;
    private String applicationId;
    private String jobId;

    public FlinkCmdOutputListener(InterpreterContext context, Properties properties) {
      this.properties = properties;
      this.context = context;
    }

    @Override
    public void onUpdateAll(InterpreterOutput out) {

    }

    @Override
    public void onAppend(int index, InterpreterResultMessageOutput out, byte[] line) {
      if (isFlinkUrlSent) {
        return;
      }

      String text = new String(line);
      if (text.contains("Submitted application") && text.lastIndexOf(" ") > 0) {
        this.applicationId = text.substring(text.lastIndexOf(" ") + 1); // yarn mode, extract yarn proxy url as flink ui link
      } else if (text.contains("Job has been submitted with JobID") && text.lastIndexOf(" ") > 0) {
        this.jobId = text.substring(text.lastIndexOf(" ") + 1);
      }

      if (StringUtils.isNoneBlank(applicationId) && StringUtils.isNoneBlank(jobId)) {
        sendFlinkUrl(applicationId, jobId);
        isFlinkUrlSent = true;
      }
    }

    @Override
    public void onUpdate(int index, InterpreterResultMessageOutput out) {

    }

    private void sendFlinkUrl(String applicationId, String jobId) {
      try {
        Map<String, String> infos = new java.util.HashMap<String, String>();
        infos.put("jobUrl", getDisplayedJMWebUrl(applicationId) + "#/job/" + jobId);
        infos.put("label", "Flink UI");
        infos.put("tooltip", "View in Flink web UI");
        infos.put("noteId", context.getNoteId());
        infos.put("paraId", context.getParagraphId());
        context.getIntpEventClient().onParaInfosReceived(infos);
      } catch (Exception e) {
        LOGGER.error("Fail to extract flink url", e);
      }
    }

    private String getDisplayedJMWebUrl(String applicationId) {
      // `zeppelin.flink.uiWebUrl` is flink jm url template, {{applicationId}} will be replaced with real yarn app id.
      String flinkUIWebUrl = properties.getProperty("zeppelin.flink.uiWebUrl");
      if (StringUtils.isNotBlank(flinkUIWebUrl)) {
        return flinkUIWebUrl.replace("{{applicationId}}", applicationId);
      } else {
        // YarnClient yarnClient = YarnClient.createYarnClient();
        // yarnClient.init(new YarnConfiguration());
        // yarnClient.start();

        // ApplicationReport applicationReport = yarnClient.getApplicationReport(ConverterUtils.toApplicationId(yarnAppId));
        // return applicationReport.getTrackingUrl();
        throw new RuntimeException("Flink web UI template is not config: zeppelin.flink.uiWebUrl");
      }
    }
  }
}
