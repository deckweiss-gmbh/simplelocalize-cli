package io.simplelocalize.cli.util;

import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.env.EnvScalarConstructor;
import org.yaml.snakeyaml.error.MissingEnvironmentVariableException;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;

import java.util.regex.Matcher;

public class YamlConstructor extends Constructor {
    public YamlConstructor(Class<? extends Object> theRoot) {
        super(theRoot);
        this.yamlConstructors.put(EnvScalarConstructor.ENV_TAG, new YamlConstructor.ConstructEnv());
    }

    public String apply(String name, String separator, String value, String environment) {
        if (environment != null && !environment.isEmpty()) {
            return environment;
        } else {
            if (separator != null) {
                if (separator.equals("?") && environment == null) {
                    throw new MissingEnvironmentVariableException("Missing mandatory variable " + name + ": " + value);
                }

                if (separator.equals(":?")) {
                    if (environment == null) {
                        throw new MissingEnvironmentVariableException("Missing mandatory variable " + name + ": " + value);
                    }

                    if (environment.isEmpty()) {
                        throw new MissingEnvironmentVariableException("Empty mandatory variable " + name + ": " + value);
                    }
                }

                if (separator.startsWith(":")) {
                    if (environment == null || environment.isEmpty()) {
                        return value;
                    }
                } else if (environment == null) {
                    return value;
                }
            }

            return "";
        }
    }

    public String getEnv(String key) {
        return System.getenv(key);
    }

    private class ConstructEnv extends AbstractConstruct {
        private ConstructEnv() {
        }

        public Object construct(Node node) {
            String val = YamlConstructor.this.constructScalar((ScalarNode)node);
            Matcher matcher = EnvScalarConstructor.ENV_FORMAT.matcher(val);
            matcher.matches();
            String name = matcher.group("name");
            String value = matcher.group("value");
            String separator = matcher.group("separator");
            return YamlConstructor.this.apply(name, separator, value != null ? value : "", YamlConstructor.this.getEnv(name));
        }
    }
}
