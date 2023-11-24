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

package org.apache.zeppelin.util;

import java.io.IOException;
import java.util.Map;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class HttpClientUtils {

    // 编码格式。发送编码格式统一用UTF-8
    private static final String ENCODING_UTF_8 = "UTF-8";

    // 请求获取数据的超时时间(即响应时间)，单位毫秒。
    private static final int SOCKET_TIMEOUT = 6000;

    // 设置连接超时时间，单位毫秒。
    private static final int CONNECT_TIMEOUT = 6000;

    public static String get(String url, Map<String, String> headers, Map<String, String> params) throws Exception {
        URIBuilder uriBuilder = new URIBuilder(url);
        if (params != null && !params.isEmpty()) {
            params.forEach(uriBuilder::setParameter);
        }

        return doRequest(new HttpGet(uriBuilder.build()), headers);
    }

    public static String post(String url, Map<String, String> headers, String jsonBody) throws Exception {
        StringEntity requestBody = new StringEntity(jsonBody, ENCODING_UTF_8);
        requestBody.setContentType("application/json; charset=UTF-8");

        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(requestBody);

        return doRequest(httpPost, headers);
    }


    private static String doRequest(HttpRequestBase httpMethod, Map<String, String> headers) throws IOException {
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(httpMethod::setHeader);
        }

        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(SOCKET_TIMEOUT).build();
        httpMethod.setConfig(requestConfig);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse httpResponse = httpClient.execute(httpMethod);
        if (httpResponse == null || httpResponse.getStatusLine() == null) {
            throw new RuntimeException("Http请求失败[无响应]...");
        }

        if (httpResponse.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Http请求失败[" + httpResponse.getStatusLine().getStatusCode() + "]...");
        }

        if (httpResponse.getEntity() == null) {
            throw new RuntimeException("Http请求失败[" + httpResponse.getStatusLine().getStatusCode() + "]...");
        }

        try {
            return EntityUtils.toString(httpResponse.getEntity(), ENCODING_UTF_8);
        } finally {
            release(httpResponse, httpClient);
        }
    }

    private static void release(CloseableHttpResponse httpResponse, CloseableHttpClient httpClient) throws IOException {
        // 释放资源
        if (httpResponse != null) {
            httpResponse.close();
        }

        if (httpClient != null) {
            httpClient.close();
        }
    }

}
