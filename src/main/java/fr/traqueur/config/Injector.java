package fr.traqueur.config;

import fr.traqueur.config.logger.InternalLogger;
import fr.traqueur.config.logger.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;

public class Injector {

    private static Injector injector;

    /**
     * Private constructor to prevent instantiation.
     */
    public static Injector get() {
        if (injector == null) {
            injector = new Injector();
        }
        return injector;
    }

    private Logger logger;

    private Injector() {
        this.logger = new InternalLogger();
    }

    /**
     * Sets the logger to be used by the Injector.
     *
     * @param logger The logger to set.
     */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }



    /**
     * Injects the values from the YAML file into the enum constants.
     *
     * @param file       The YAML file to read from.
     * @param enumClass  The enum class to inject values into.
     * @param <T>        The type of the enum.
     */
    public <T extends Enum<T> & Configurable> void injectEnum(File file, Class<T> enumClass) {
      try (InputStream input = Files.newInputStream(file.toPath())) {
          Yaml yaml = new Yaml();
          Map<String, Object> datas = yaml.load(input);
            for (T constant : enumClass.getEnumConstants()) {
                String key = upperSnakeCaseToKebabCase(constant.name());
                if (!datas.containsKey(key)) {
                    throw new IllegalArgumentException("Key " + key + " not found in config file");
                }
                Object value = datas.get(key);
                if(value instanceof Map<?, ?>) {
                    Map<String,Object> mapValue = (Map<String, Object>) value;
                    mapValue.forEach( (k, v) -> {
                        String fieldName = kebabToCamelCase(k);
                        this.setField(constant, fieldName, value);
                    });
                } else {
                    Field field = constant.getClass().getDeclaredFields()[enumClass.getEnumConstants().length];
                    this.setField(constant, field.getName(), value);
                }
            }

      } catch (IOException e) {
            e.printStackTrace();
      }
    }

    private void setField(Object object, String fieldName, Object value) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    // une-liste-1 en uneListe1
    private String kebabToCamelCase(String str) {
        StringBuilder builder = new StringBuilder();
        boolean nextUpper = false;
        for (char c : str.toCharArray()) {
            if (c == '-') {
                nextUpper = true;
            } else {
                builder.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return builder.toString();
    }

    private String upperSnakeCaseToKebabCase(String str) {
        return str.replaceAll("_", "-").toLowerCase();
    }


}
