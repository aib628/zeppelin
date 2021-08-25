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
package org.apache.zeppelin.notebook.injector;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import org.apache.zeppelin.interpreter.Constants;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.user.UserCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Paragraph variables injector. User credentials inject support default, you can code you own inject logical by implements
 * org.apache.zeppelin.notebook.injector.Injector interface, and then register it by java SPI.
 * User credentials inject @see:org.apache.zeppelin.notebook.injector.CredentialInjector
 */
public class ParagraphInjector {

    private int index = 0;
    private final Interpreter interpreter;
    private final InterpreterContext context;
    private final List<Injector> injectors = new ArrayList<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(ParagraphInjector.class);

    private ParagraphInjector(InterpreterContext context, Interpreter interpreter) {
        loadSpiInjectors();
        this.context = context;
        this.interpreter = interpreter;
    }

    private void loadSpiInjectors() {
        ServiceLoader.load(Injector.class).iterator().forEachRemaining(this::registerInjector);
    }

    private void registerInjector(Injector injector) {
        LOGGER.info("Register custom paragraph injector : {}", injector.getClass().getCanonicalName());
        this.injectors.add(injector);
    }

    private String checkInjectDone(String script) {
        String injectDoneCheck = interpreter.getProperty(Constants.INJECT_DONE_CHECK, "false");
        injectDoneCheck = context.getStringLocalProperty(Constants.INJECT_DONE_CHECK, injectDoneCheck);

        if (Boolean.parseBoolean(injectDoneCheck)) {
            Matcher matcher = Constants.VARIABLE_PATTERN.matcher(script);
            if (matcher.find()) {
                throw new RuntimeException("Variable injection is not completed when check is enabled : " + matcher.group());
            }
        }

        return script;
    }

    public Interpreter getInterpreter() {
        return interpreter;
    }

    public InterpreterContext getContext() {
        return context;
    }

    // Inject entrance
    public String inject(String script) {
        if (index >= injectors.size()) {
            return checkInjectDone(script);
        }

        // Get the next and execute inject
        Injector injector = injectors.get(index++);

        // do inject
        return injector.inject(script, this);
    }

    // Hide password if use credential inject.
    public InterpreterResult hideCredentialPasswords(InterpreterResult ret) {
        for (Injector injector : injectors) {
            if (injector instanceof CredentialInjector) {
                CredentialInjector credentialInjector = (CredentialInjector) injector;
                return credentialInjector.hidePasswords(ret);
            }
        }

        return ret;
    }

    /**
     * Interpreter setting : interpreter.getProperties()
     * Zeppelin configs : interpreter.getInterpreterGroup.getConf()
     *
     * @param context     interpreter context,config in paragraph,eg:%flink.ssql(type=update,injectCredentials=true)
     * @param interpreter the interpreter
     * @return the injector
     */
    public static ParagraphInjector getInstance(InterpreterContext context, Interpreter interpreter) {
        return new ParagraphInjector(context, interpreter);
    }

    /**
     * Interpreter setting : interpreter.getProperties()
     * Zeppelin configs : interpreter.getInterpreterGroup.getConf()
     *
     * @param context     interpreter context,config in paragraph,eg:%flink.ssql(type=update,injectCredentials=true)
     * @param interpreter the interpreter
     * @param creds       user credentials that config in user settings page.
     * @return the injector
     */
    public static ParagraphInjector getInstance(InterpreterContext context, Interpreter interpreter, UserCredentials creds) {
        ParagraphInjector paragraphInjector = getInstance(context, interpreter);
        paragraphInjector.injectors.add(0, new CredentialInjector(creds));

        return paragraphInjector;
    }

}
