package br.edu.unijui.gca.api.enums;

import lombok.Getter;

@Getter
public enum OrderDirection {

    ASC("asc"),
    DESC("desc");

    private final String description;

    OrderDirection(String description) {
        this.description = description;
    }

    public static OrderDirection fromValue(String value) {
        for (OrderDirection direction : values()) {
            if (direction.description.equalsIgnoreCase(value)) {
                return direction;
            }
        }

        return null;
    }
}
