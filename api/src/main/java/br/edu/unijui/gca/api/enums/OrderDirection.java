package br.edu.unijui.gca.api.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum OrderDirection {

    ASC("asc"),
    DESC("desc");

    private final String description;

    public static OrderDirection fromValue(String value) {
        for (OrderDirection direction : values()) {
            if (direction.description.equalsIgnoreCase(value)) {
                return direction;
            }
        }

        return null;
    }
}
