package unimelb.bitbox.util.config;

import java.util.HashMap;
import java.util.Map;

public class CfgEnumValue<T extends Enum<T>> extends CfgValue<T> {
    private static final Map<String, String> actualValues = new HashMap<>();

    public CfgEnumValue(String propertyName, Class<T> enumType) {
        super(propertyName, value -> convert(propertyName, value, enumType));
    }

    private static <T extends Enum<T>> T convert(String key, String value, Class<T> enumType) {
        if (actualValues.containsKey(value)) {
            return Enum.valueOf(enumType, actualValues.get(value));
        } else {
            for (T each : enumType.getEnumConstants()) {
                String name = each.name();
                if (name.compareToIgnoreCase(value) == 0) {
                    actualValues.put(value, name);
                    return each;
                }
            }
            throw new ConfigException(key, enumType);
        }
    }
}
