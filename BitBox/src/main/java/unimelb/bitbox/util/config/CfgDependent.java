package unimelb.bitbox.util.config;

import java.util.Collection;
import java.util.function.Supplier;

public class CfgDependent<T> {
    private T value;
    Supplier<T> calc;

    public CfgDependent(CfgEnumValue<?> dependsOn, Supplier<T> calc) {
        dependsOn.setOnChanged(() -> value = calc.get());
        this.calc = calc;
    }
    public CfgDependent(CfgValue<?> dependsOn, Supplier<T> calc) {
        dependsOn.setOnChanged(() -> value = calc.get());
        this.calc = calc;
    }
    public CfgDependent(Collection<CfgValue<?>> dependsOn, Supplier<T> calc) {
        dependsOn.forEach(val -> val.setOnChanged(() -> value = calc.get()));
        this.calc = calc;
    }

    public T get() {
        Configuration.updateValues();
        if (value == null) {
            value = calc.get();
        }
        return value;
    }

    public T lastValue() {
        return value;
    }
}