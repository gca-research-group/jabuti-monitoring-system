package br.edu.unijui.gca.api.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum SmartContractExecutionStatus {
    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    PROCESSED("PROCESSED"),
    FAILED("FAILED");

    private final String name;
}
