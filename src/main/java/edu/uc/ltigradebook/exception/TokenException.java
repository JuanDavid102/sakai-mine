package edu.uc.ltigradebook.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Token not saved properly.")
public class TokenException extends Exception {

    private static final long serialVersionUID = 1L;

}
