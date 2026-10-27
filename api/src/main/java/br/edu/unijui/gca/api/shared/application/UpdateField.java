package br.edu.unijui.gca.api.shared.application;

import java.util.function.Consumer;

/**
 * Opt-in update value: absent leaves a field unchanged; supplied null can clear it.
 * Request DTOs initialize fields with absent() and call supplied(value) in their
 * JSON setters. Validation and mapping decide whether a supplied value is legal.
 */
public record UpdateField<T>(boolean supplied, T value) {
    public static <T> UpdateField<T> absent() {
        return new UpdateField<>(false, null);
    }

    public static <T> UpdateField<T> supplied(T value) {
        return new UpdateField<>(true, value);
    }

    public void ifSupplied(Consumer<? super T> setter) {
        if (supplied) {
            setter.accept(value);
        }
    }
}
