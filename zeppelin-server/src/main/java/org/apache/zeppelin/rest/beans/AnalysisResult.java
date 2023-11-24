package org.apache.zeppelin.rest.beans;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.zeppelin.notebook.ParagraphTextParser;

public class AnalysisResult extends ParagraphTextParser.ParseResult {

    private final List<String> scripts = new ArrayList<>();
    private final Properties configurations = new Properties();

    public AnalysisResult(ParagraphTextParser.ParseResult result) {
        super(result.getIntpText(), result.getScriptText(), result.getLocalProperties());
    }

    public List<String> getScripts() {
        return scripts;
    }

    public Properties getConfigurations() {
        return configurations;
    }
}
