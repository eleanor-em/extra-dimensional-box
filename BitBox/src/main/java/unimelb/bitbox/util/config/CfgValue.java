package unimelb.bitbox.util.config;

import unimelb.bitbox.util.functional.algebraic.Maybe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

class ConfigException extends RuntimeException {
    private Maybe<Exception> cause = Maybe.nothing();

    public ConfigException(String key) {
        super("Error parsing configuration value `" + key + "`: key not found");
    }

    public ConfigException(String key, Class<?> type) {
        super("Error parsing configuration value `" + key + ": type " + type.getName() + " is not an enum");
    }

    public ConfigException(String key, Exception cause) {
        super("Error parsing configuration value `" + key + "`: " + cause.getMessage());
        this.cause = Maybe.just(cause);
    }

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        cause.consume(err -> {
            System.out.println("Caused by: ");
            err.printStackTrace();
        });
    }
}

public class CfgValue<T> {
    private final String propertyName;
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
    public static CfgValue<Long> createLong(String propertyName) {
        return new CfgValue<>(propertyName, Long::parseLong);
    }
    public static <T> CfgValue<T> create(String propertyName, Function<String, T> converter) {
        return new CfgValue<>(propertyName, converter);
    }

    private CfgValue(String propertyName) {
        this.propertyName = propertyName;
        if (!Configuration.contains(propertyName)) {
            throw new ConfigException(propertyName);
        }

        strValue = Configuration.getConfigurationValue(propertyName);

        //noinspection unchecked
        converter = str -> (T) str;
        update();
    }
    protected CfgValue(String propertyName, Function<String, T> converter) {
        this.propertyName = propertyName;
        if (!Configuration.contains(propertyName)) {
            throw new ConfigException(propertyName);
        }

        strValue = Configuration.getConfigurationValue(propertyName);
        Configuration.addValue(this);

        this.converter = converter;
        update();
    }

    private void update() {
        strValue = Configuration.getConfigurationValue(propertyName);
        try {
            cached = converter.apply(strValue);
        } catch (ClassCastException e) {
            throw new ConfigException(propertyName, e);
        }
    }

    public T get() {
        if (hasChanged()) {
            Configuration.log.info("Configuation value `" + propertyName + "` changed");
            update();

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

    public void setOnChanged(Consumer<T> action) {
        actions.add(action);
    }
    public void setOnChangedThrowable(Runnable action) {
        setOnChanged(ignored -> action.run());
    }
    public void setOnChanged(Runnable action) {
        setOnChangedThrowable(action);
    }
}

