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

package org.apache.zeppelin.notebook.injector.impl;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.notebook.injector.Injector;
import org.apache.zeppelin.notebook.injector.ParagraphInjector;
import org.apache.zeppelin.util.HttpClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * paragraphInjector.getContext().getNoteId();
 * paragraphInjector.getContext().getParagraphId();
 * paragraphInjector.getContext().getAuthenticationInfo().getUser();
 * Interpreter setting : paragraphInjector.getInterpreter().getProperties()
 * Zeppelin data : paragraphInjector.getInterpreter().getInterpreterGroup.getConf()
 */
public class HttpConfigInjector implements Injector {

    private final Logger logger = LoggerFactory.getLogger(HttpConfigInjector.class);
    private static final String HTTP_CONFIG_URL = "inject.http.config.url";
    private static final String HTTP_CONFIG_TOKEN = "inject.http.config.token";
    private static final Gson GSON = new Gson();

    @Override
    public String inject(String script, ParagraphInjector paragraphInjector) {
        InterpreterContext context = paragraphInjector.getContext();
        Interpreter interpreter = paragraphInjector.getInterpreter();
        if (script == null || context == null || interpreter == null) {
            logger.warn("Param unexpected, script:{}, context:{}, interpreter:{}", script, context, interpreter);
            return script;
        }

        String httpURL = interpreter.getProperty(HTTP_CONFIG_URL);
        if (httpURL == null || httpURL.isEmpty()) {
            logger.warn("the interpreter property of 'config.http.url' is not config when injectCredentials is true.");
            return script;
        }

        String httpToken = interpreter.getProperty(HTTP_CONFIG_TOKEN);
        Map<String, String> configs = getConfigs(httpURL, httpToken, context);

        injectLocalProperties(context, configs);
        logger.info("begin to inject variables find in {} configs", configs.size());
        return paragraphInjector.inject(doInject(configs, script));
    }

    private void injectLocalProperties(InterpreterContext context, Map<String, String> configs) {
        Map<String, String> localProperties = context.getLocalProperties();
        localProperties.forEach((key, value) -> {
            String finalValue = doInject(configs, value);
            if (!finalValue.equals(value)) {
                localProperties.put(key, finalValue);
            }
        });
    }

    private String doInject(Map<String, String> configs, String script) {
        Matcher matcher = Constants.VARIABLE_PATTERN.matcher(script);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            if (configs.containsKey(key)) {
                String quotedValue = Matcher.quoteReplacement(configs.get(key));
                matcher.appendReplacement(sb, quotedValue);
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    private Map<String, String> getConfigs(String httpURL, String httpToken, InterpreterContext context) {
        try {
            String jsonResult = HttpClientUtils.post(httpURL, buildHeaders(httpToken), GSON.toJson(buildParams(context)));
            ConfigResponse configResponse = GSON.fromJson(jsonResult, ConfigResponse.class);
            if (configResponse.code != 0 || configResponse.data == null) {
                logger.error("Config get result abnormal: {}", jsonResult);
            }

            if (configResponse.code == 0 && configResponse.data != null) {
                return configResponse.data;
            }
        } catch (Exception e) {
            logger.error("Config get failed, url : {}", httpURL, e);
        }

        return new HashMap<>();
    }

    private Map<String, String> buildHeaders(String httpToken) {
        Map<String, String> params = new HashMap<>();
        params.put("content-type", "application/json");

        if (httpToken != null) {
            params.put("Authorization", "Bearer " + httpToken);
        }

        return params;
    }

    private Map<String, String> buildParams(InterpreterContext context) {
        Map<String, String> params = new HashMap<>();
        params.put("notebookId", context.getNoteId());
        params.put("paragraphId", context.getParagraphId());
        params.put("userName", context.getAuthenticationInfo().getUser());

        return params;
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");

        Map<String, String> params = new HashMap<>();
        params.put("notebookId", "notebookId");
        params.put("paragraphId", "paragraphId");
        params.put("userName", "userName");

        String jsonResult = HttpClientUtils.post("http://127.0.0.1:8080/rest/config/paragraph/list", headers, GSON.toJson(params));
        ConfigResponse configResponse = GSON.fromJson(jsonResult, ConfigResponse.class);
        System.out.println(GSON.toJson(configResponse));
    }

    private class ConfigResponse {

        private int code;
        private String tag;
        private String msg;
        private Map<String, String> data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public Map<String, String> getData() {
            return data;
        }

        public void setData(Map<String, String> data) {
            this.data = data;
        }
    }
}
