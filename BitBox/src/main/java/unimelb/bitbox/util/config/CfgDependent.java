package unimelb.bitbox.util.config;

import unimelb.bitbox.util.concurrency.LazyInitialiser;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

public class CfgDependent<T> {
    private final LazyInitialiser<T> value;
    private final Collection<CfgValue<?>> dependsOn;

    public CfgDependent(CfgValue<?> dependsOn, Supplier<T> calc) {
        this(Collections.singletonList(dependsOn), calc);
    }
    public CfgDependent(Collection<CfgValue<?>> dependsOn, Supplier<T> calc) {
        this.dependsOn = dependsOn;
        value = new LazyInitialiser<>(calc);
        setOnChanged(() -> value.set(calc.get()));
    }

    public T get() {
        Configuration.updateValues();
        return value.get();
    }

    public void setOnChanged(Runnable action) {
        dependsOn.forEach(val -> val.setOnChanged(action));
    }
}