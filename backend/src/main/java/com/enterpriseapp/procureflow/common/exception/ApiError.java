package com.enterpriseapp.procureflow.common.exception;

import java.time.Instant;
import java.util.List;

/** Uniform error payload returned to API clients. */
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> details) {}
