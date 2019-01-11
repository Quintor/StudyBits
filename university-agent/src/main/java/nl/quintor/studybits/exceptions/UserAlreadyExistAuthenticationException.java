package nl.quintor.studybits.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class UserAlreadyExistAuthenticationException extends RuntimeException {

    public UserAlreadyExistAuthenticationException(String msg) {
        super(msg);
    }
}
