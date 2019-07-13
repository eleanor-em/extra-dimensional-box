package unimelb.bitbox.util.config;

import functional.algebraic.Maybe;
import functional.throwing.ThrowingConsumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An exception that may be thrown while parsing a configuration file.
 *
 * @author Eleanor McMurtry
 */
class ConfigException extends RuntimeException {
    private final Maybe<Exception> cause;

    /**
     * Occurs when a key is missing.
     * @param key the key we tried to find
     */
    ConfigException(String key) {
        super("Error parsing configuration value `" + key + "`: key not found");
        cause = Maybe.nothing();
    }

    /**
     * Occurs when we tried to load an enum of type ?, but it was not an enum.
     * @param key the key we tried to find
     * @param type the type of the alleged enum
     */
    ConfigException(String key, Class<?> type) {
        super("Error parsing configuration value `" + key + ": type " + type.getName() + " is not an enum");
        cause = Maybe.nothing();
    }

    /**
     * Generic constructor for other cases.
     * @param key the key we tried to find
     * @param cause the error that caused the exceptino
     */
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

/**
 * Represents a value that is to be loaded from a configuration file.
 * Automatically updates when the file is modified.
 * @param <T> the ttype of the value
 */
public class CfgValue<T> {
    private final String propertyName;
    private final Function<? super String, T> converter;
    private T cached;
    private String strValue;

    // This flag avoids infinite recursion in get()
    private boolean updated = false;

    private final List<ThrowingConsumer<T, ?>> actions = Collections.synchronizedList(new ArrayList<>());

    /**
     * Create a String-typed config value.
     * @param propertyName the key to look up in the config file
     * @return the created config value
     */
    public static CfgValue<String> createString(String propertyName) {
        CfgValue<String> ret = new CfgValue<>(propertyName);
        ret.cached = ret.strValue;
        return ret;
    }

    /**
     * Create an Integer-typed config value.
     * @param propertyName the key to look up in the config file
     * @return the created config value
     */
    public static CfgValue<Integer> createInt(String propertyName) {
        return new CfgValue<>(propertyName, Integer::parseInt);
    }

    /**
     * Create a Long-typed config value.
     * @param propertyName the key to look up in the config file
     * @return the created config value
     */
    public static CfgValue<Long> createLong(String propertyName) {
        return new CfgValue<>(propertyName, Long::parseLong);
    }


    /**
     * Create a generically-typed config value.
     * @param propertyName the key to look up in the config file
     * @param converter a function that converts a String to a T
     * @param <T> the type to store
     * @return the created config value
     */
    public static <T> CfgValue<T> create(String propertyName, Function<? super String, T> converter) {
        return new CfgValue<>(propertyName, converter);
    }

    private CfgValue(String propertyName) {
        this.propertyName = propertyName;
        if (Configuration.missingKey(propertyName)) {
            throw new ConfigException(propertyName);
        }
        Configuration.getConfigurationValue(propertyName)
                     .match(str -> strValue = str,
                            () ->  { throw new ConfigException(propertyName); });

        //noinspection unchecked
        converter = str -> (T) str;
        update();
    }

    // Package-private for inheriting classes etc.
    CfgValue(String propertyName, Function<? super String, T> converter) {
        this.propertyName = propertyName;
        if (Configuration.missingKey(propertyName)) {
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

    /**
     * Gets the value of this configuration value, updating it if necessary.
     * @return the value
     */
    public T get() {
        if (hasChanged()) {
            Configuration.log.fine("Configuation value `" + propertyName + "` changed");
            update();

            if (!updated) {
                // If we think we're out of date, we're now definitely up to date
                updated = true;
                synchronized (actions) {
                    for (ThrowingConsumer<T, ?> action : actions) {
                        try {
                            action.accept(cached);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
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

    /**
     * Sets an action to be run when the CfgValue changes.
     * @param action the action to run, which accepts the new value
     */
    public void setOnChanged(Consumer<T> action) {
        actions.add(action::accept);
    }

    /**
     * Sets an action to be run when the CfgValue changes.
     * @param action the action to run, which accepts the new value
     */
    public <E extends Throwable> void setOnChangedT(ThrowingConsumer<T, E> action) {
        actions.add(action);
    }

    /**
     * Sets an action to be run when the CfgValue changes.
     * @param action the action to run
     */
    public void setOnChanged(Runnable action) {
        setOnChanged(ignored -> action.run());
    }
}

