package unimelb.bitbox.util.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

class ConfigException extends RuntimeException {
    private Exception cause;

    public ConfigException(String key) {
        super("Error parsing configuration value `" + key + "`: key not found");
    }

    public ConfigException(String key, Class<?> type) {
        super("Error parsing configuration value `" + key + ": type " + type.getName() + " is not an enum");
    }

    public ConfigException(String key, Exception cause) {
        super("Error parsing configuration value `" + key + "`: " + cause.getMessage());
        this.cause = cause;
    }

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        if (cause != null) {
            System.out.println("Caused by: ");
            cause.printStackTrace();
        }
    }
}

public class CfgValue<T> {
    private String propertyName;
    private Function<String, T> converter;
    private T cached;
    private String strValue;

    // This flag avoids infinite recursion in get()
    private boolean updated;

    private final List<Consumer<T>> actions = Collections.synchronizedList(new ArrayList<>());

    public static CfgValue<String> create(String propertyName) {
        CfgValue<String> ret = new CfgValue<>(propertyName);
        ret.cached = ret.strValue;
        return ret;
    }
    public static CfgValue<Integer> createInt(String propertyName) {
        return new CfgValue<>(propertyName, Integer::parseInt);
    }
    public static <T> CfgValue<T> create(String propertyName, Function<String, T> converter) {
        return new CfgValue<>(propertyName, converter);
    }

    protected CfgValue(String propertyName) {
        this.propertyName = propertyName;
        if (!Configuration.contains(propertyName)) {
            throw new ConfigException(propertyName);
        }
        strValue = Configuration.getConfigurationValue(propertyName);
        Configuration.watchedValues.add(this);
    }
    protected CfgValue(String propertyName, Function<String, T> converter) {
        this(propertyName);

        this.converter = converter;
        cached = converter.apply(strValue);
    }

    public T get() {
        if (hasChanged()) {
            System.out.println(propertyName + " changed!");
            strValue = Configuration.getConfigurationValue(propertyName);
            if (converter != null) {
                cached = converter.apply(strValue);
            } else {
                try {
                    cached = (T) strValue;
                } catch (ClassCastException | IllegalArgumentException e) {
                    throw new ConfigException(propertyName, e);
                }
            }

            if (!updated) {
                // If we think we're out of date, we're now definitely up to date
                updated = true;
                synchronized (actions) {
                    actions.forEach(action -> action.accept(cached));
                    updated = false;
                }
            }
        }
        return cached;
    }

    public boolean hasChanged() {
        Configuration.updateValues();
        return !Configuration.getConfigurationValue(propertyName).equals(strValue);
    }

    public void validate() {
        hasChanged();
    }

    public void setOnChanged(Consumer<T> action) {
        actions.add(action);
    }
    public void setOnChanged(Runnable action) {
        setOnChanged(ignored -> action.run());
    }
    public static void setOnChanged(Collection<CfgValue<?>> values, Runnable action) {
        values.forEach(val -> val.setOnChanged(action));
    }
}

