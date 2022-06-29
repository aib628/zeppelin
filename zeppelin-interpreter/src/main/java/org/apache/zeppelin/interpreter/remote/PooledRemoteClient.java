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

package org.apache.zeppelin.interpreter.remote;

import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.thrift.TException;
import org.apache.thrift.TServiceClient;
import org.apache.zeppelin.interpreter.thrift.InterpreterRPCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use this class to connect to remote thrift server and invoke any thrift rpc.
 * Underneath, it would create SocketClient via a ObjectPool.
 *
 * @param <T>
 */
public class PooledRemoteClient<T extends TServiceClient> implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(PooledRemoteClient.class);
  private static final int RETRY_COUNT = 3;

  private GenericObjectPool<T> clientPool;
  private RemoteClientFactory<T> remoteClientFactory;

  public PooledRemoteClient(SupplierWithIO<T> supplier, int connectionPoolSize) {
    this.remoteClientFactory = new RemoteClientFactory<>(supplier);
    GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
    poolConfig.setMaxTotal(connectionPoolSize);
    poolConfig.setMaxIdle(connectionPoolSize);
    // ZEPPELIN-5875 maven-shade-plugin issue
    // `org/apache/zeppelin/shaded/org.apache.zeppelin.shaded.org.apache.commons.pool2.impl.DefaultEvictionPolicy`
    poolConfig.setEvictionPolicyClassName(DefaultEvictionPolicy.class.getName());
    this.clientPool = new GenericObjectPool<>(remoteClientFactory, poolConfig);
  }

  public PooledRemoteClient(SupplierWithIO<T> supplier) {
    this(supplier, 10);
  }

  public synchronized T getClient() throws Exception {
    return clientPool.borrowObject(5_000);
  }

  @Override
  public void close() {
    // Close client socket connection
    if (remoteClientFactory != null) {
      remoteClientFactory.close();
      this.remoteClientFactory = null;
    }
    if (this.clientPool != null) {
      this.clientPool.close();
      this.clientPool = null;
    }
  }

  private void releaseClient(T client, boolean broken) {
    if (broken) {
      releaseBrokenClient(client);
    } else {
      try {
        clientPool.returnObject(client);
      } catch (Exception e) {
        LOGGER.warn("exception occurred during return thrift client", e);
      }
    }
  }

  private void releaseBrokenClient(T client) {
    try {
      LOGGER.warn("releasing broken client...");
      clientPool.invalidateObject(client);
    } catch (Exception e) {
      LOGGER.warn("exception occurred during releasing thrift client", e);
    }
  }

  public <R> R callRemoteFunction(RemoteFunction<R, T> func) {
    try {
      return retryableCallRemoteFunction(func);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private <R> R retryableCallRemoteFunction(RemoteFunction<R, T> func) throws Exception {
    Exception lastException = null;
    for (int i = 0; i <= RETRY_COUNT; i++) {
      boolean clientReleased = false;
      T client = getClient();
      if (client == null) {
        throw new RuntimeException("Failed to get client when call remote function");
      }

      try {
        return func.call(client);
      } catch (InterpreterRPCException e) {
        LOGGER.error("Failed to call remote function, for reason: {}", e.getMessage());
        releaseClient(client, true);
        clientReleased = true;
        throw e; // zeppelin side exception, no need to retry
      } catch (Exception e) {
        // thrift framework exception (maybe due to network issue), need to retry
        LOGGER.error("Failed to retryable call remote function, for reason: {}", e.getMessage());
        if (e.getMessage() != null && e.getMessage().contains("Broken pipe")) {
          releaseClient(client, true);
          clientReleased = true;
        }

        lastException = e;
      } finally {
        if (!clientReleased) {
          releaseClient(client, false);
        }
      }
    }

    throw lastException;
  }

  public interface RemoteFunction<R, T> {
    R call(T client) throws InterpreterRPCException, TException;
  }
}
