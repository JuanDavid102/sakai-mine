package edu.uc.ltigradebook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Grade not saved properly.")
public class AccountException extends Exception {
    private static final long serialVersionUID = 1L;
}
