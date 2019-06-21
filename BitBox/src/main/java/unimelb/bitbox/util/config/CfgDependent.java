package unimelb.bitbox.util.config;

import unimelb.bitbox.util.concurrency.LazyInitialiser;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * A value that is dependent on one or several configuration values.
 * Updates whenever the configuration value(s) change.
 * @param <T> the type of the value
 */
public class CfgDependent<T> {
    private final LazyInitialiser<T> value;
    private final Collection<? extends CfgValue<?>> dependsOn;

    /**
     * Create a value dependent on a single configuration value.
     * @param dependsOn the configuration value this depends on
     * @param calc a function that produces the current value
     */
    public CfgDependent(CfgValue<?> dependsOn, Supplier<T> calc) {
        this(Collections.singletonList(dependsOn), calc);
    }

    /**
     * Create a value dependent on several configuration values.
     * @param dependsOn the configuration values this depends on
     * @param calc a function that produces the current value
     */
    public CfgDependent(Collection<? extends CfgValue<?>> dependsOn, Supplier<T> calc) {
        this.dependsOn = dependsOn;
        value = new LazyInitialiser<>(calc);
        setOnChanged(() -> value.set(calc.get()));
    }

    /**
     * @return the current value, loading the configuration values if necessary
     */
    public T get() {
        Configuration.updateValues();
        return value.get();
    }

    /**
     * Sets an action that should be performed whenever the value changes.
     * @param action the action to perform
     */
    public final void setOnChanged(Runnable action) {
        dependsOn.forEach(val -> val.setOnChanged(action));
    }
}