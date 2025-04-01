package fr.traqueur.config;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Injector {

    public <T extends Enum<T> & Configurable<T>> void injectEnum(File file, Class<T> enumClass) {
      try (InputStream input = Files.newInputStream(file.toPath())) {
          Yaml yaml = new Yaml();
          Map<String, Object> datas = yaml.load(input);
            for (T constant : enumClass.getEnumConstants()) {
                String key = UpperSnakeToLowerKebab(constant.name());
                if (datas.containsKey(key)) {
                    Object value = datas.get(key);
                    if(value instanceof Map<?, ?>) {
                        Map<String,Object> mapValue = (Map<String, Object>) value;
                        mapValue.forEach( (k, v) -> {
                            String fieldName = kebabToCamelCase(k);
                            try {
                                Field field = constant.getClass().getDeclaredField(fieldName);
                                field.setAccessible(true);
                                field.set(constant, v);
                            } catch (NoSuchFieldException | IllegalAccessException e) {
                                e.printStackTrace();
                            }
                        });
                    } else {
                        Field field = constant.getClass().getDeclaredFields()[enumClass.getEnumConstants().length];
                        field.setAccessible(true);
                        field.set(constant, value);
                    }
                }
            }

      } catch (IOException | IllegalAccessException e) {
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

    private String UpperSnakeToLowerKebab(String str) {
        return str.replaceAll("_", "-").toLowerCase();
    }


}
