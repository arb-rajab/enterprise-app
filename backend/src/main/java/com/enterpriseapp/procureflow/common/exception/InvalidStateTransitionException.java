package com.enterpriseapp.procureflow.common.exception;

/** Raised when an operation would move a workflow entity into an illegal state. */
public class InvalidStateTransitionException extends RuntimeException {

  public InvalidStateTransitionException(String message) {
    super(message);
  }
}
