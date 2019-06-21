package unimelb.bitbox.util.config;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link CfgValue} that is an enumeration type.
 * Needs a specialisation the conversion from String to Enum is a bit awkward.
 * @param <T>
 */
public class CfgEnumValue<T extends Enum<T>> extends CfgValue<T> {
    private static final Map<String, String> actualValues = new HashMap<>();

    /**
     * Create an enumeration {@link CfgValue}.
     *
     * @param propertyName the name of the property in the config file
     * @param enumType     the class-type of the enumeration to load
     */
    public CfgEnumValue(String propertyName, Class<T> enumType) {
        super(propertyName, value -> convert(propertyName, value, enumType));
    }

    private static <T extends Enum<T>> String getKeyName(String enumValue, Class<T> enumType) {
        return enumType.getName() + ": " + enumValue;
}

    private static <T extends Enum<T>> T convert(String key, String value, Class<T> enumType) {
        // Check if we've cached the conversion
        if (actualValues.containsKey(getKeyName(value, enumType))) {
            return Enum.valueOf(enumType, actualValues.get(value));
        } else {
            // If not, find the conversion
            for (T each : enumType.getEnumConstants()) {
                String name = each.name();
                if (name.compareToIgnoreCase(value) == 0) {
                    actualValues.put(getKeyName(value, enumType), name);
                    return each;
                }
            }
            throw new ConfigException(key, enumType);
        }
    }
}
