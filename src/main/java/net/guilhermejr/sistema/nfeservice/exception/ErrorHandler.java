package net.guilhermejr.sistema.nfeservice.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.guilhermejr.sistema.nfeservice.exception.dto.ErrorDefaultDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(ExceptionDefault.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ErrorDefaultDTO> handleErroDefault(ExceptionDefault ex, WebRequest request) {

        log.error(ex.getMessage(), ex);
        List<ErrorDefaultDTO> dto = new ArrayList<>();
        dto.add(new ErrorDefaultDTO(ex.getMessage()));
        return dto;

    }

}
