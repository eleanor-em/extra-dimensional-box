package unimelb.bitbox.util.config;

import unimelb.bitbox.util.functional.algebraic.Maybe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

class ConfigException extends RuntimeException {
    private final Maybe<Exception> cause;

    ConfigException(String key) {
        super("Error parsing configuration value `" + key + "`: key not found");
        cause = Maybe.nothing();
    }

    ConfigException(String key, Class<?> type) {
        super("Error parsing configuration value `" + key + ": type " + type.getName() + " is not an enum");
        cause = Maybe.nothing();
    }

    ConfigException(String key, Exception cause) {
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
    private final Function<? super String, T> converter;
    private T cached;
    private String strValue;

    // This flag avoids infinite recursion in get()
    private boolean updated = false;

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
    public static <T> CfgValue<T> create(String propertyName, Function<? super String, T> converter) {
        return new CfgValue<>(propertyName, converter);
    }

    private CfgValue(String propertyName) {
        this.propertyName = propertyName;
        if (!Configuration.contains(propertyName)) {
            throw new ConfigException(propertyName);
        }
        Configuration.getConfigurationValue(propertyName)
                     .match(str -> strValue = str,
                            () ->  { throw new ConfigException(propertyName); });

        //noinspection unchecked
        converter = str -> (T) str;
        update();
    }
    CfgValue(String propertyName, Function<? super String, T> converter) {
        this.propertyName = propertyName;
        if (!Configuration.contains(propertyName)) {
            throw new ConfigException(propertyName);
        }

        Configuration.getConfigurationValue(propertyName)
                .match(str -> strValue = str,
                       () ->  { throw new ConfigException(propertyName); });
        Configuration.addValue(this);

        this.converter = converter;
        update();
    }

    private void update() {
        Configuration.getConfigurationValue(propertyName)
                .match(str -> strValue = str,
                       () ->  { throw new ConfigException(propertyName); });
        try {
            cached = converter.apply(strValue);
        } catch (ClassCastException e) {
            throw new ConfigException(propertyName, e);
        }
    }

    public T get() {
        if (hasChanged()) {
            Configuration.log.fine("Configuation value `" + propertyName + "` changed");
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

    private boolean hasChanged() {
        Configuration.updateValues();
        return !Configuration.getConfigurationValue(propertyName).map(str -> str.equals(strValue)).orElse(false);
    }

    public void setOnChanged(Consumer<T> action) {
        actions.add(action);
    }
    public void setOnChanged(Runnable action) {
        setOnChanged(ignored -> action.run());
    }
}

