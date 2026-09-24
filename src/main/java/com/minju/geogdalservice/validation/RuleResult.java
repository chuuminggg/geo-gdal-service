package com.minju.geogdalservice.validation;

public record RuleResult(String code, Severity severity, boolean passed, String message) {

    public static RuleResult pass(String code, String message) {
        return new RuleResult(code, Severity.INFO, true, message);
    }

    public static RuleResult warn(String code, String message) {
        return new RuleResult(code, Severity.WARN, false, message);
    }

    public static RuleResult error(String code, String message) {
        return new RuleResult(code, Severity.ERROR, false, message);
    }

    public boolean isBlocking() {
        return severity == Severity.ERROR;
    }
}
