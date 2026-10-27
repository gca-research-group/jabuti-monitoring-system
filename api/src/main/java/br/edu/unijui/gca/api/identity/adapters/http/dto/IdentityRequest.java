package br.edu.unijui.gca.api.identity.adapters.http.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Strict input policy applies only to identity HTTP requests. */
public abstract class IdentityRequest {
    @JsonAnySetter
    public final void rejectUnknownField(String field, Object value) {
        throw new UnknownField(field);
    }

    public static final class InvalidField extends IllegalArgumentException {
        private final String field;

        public InvalidField(String field) {
            super("Invalid request field");
            this.field = field;
        }

        public String field() { return field; }
    }

    public static final class UnknownField extends IllegalArgumentException {
        private final String field;

        public UnknownField(String field) {
            super("Unknown request field");
            this.field = field;
        }

        public String field() { return field; }
    }
}
