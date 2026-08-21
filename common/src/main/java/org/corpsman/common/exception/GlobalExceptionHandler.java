package org.corpsman.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.common.dto.ErrorDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * Глобальный обработчик исключений MVC.
 * <p>
 * Бизнес-исключения отображаются на страницу {@code error} с соответствующим
 * HTTP-статусом, все прочие — на страницу внутренней ошибки сервера.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Обрабатывает доменные исключения и отображает страницу ошибки.
     *
     * @param ex      бизнес-исключение
     * @param request текущий запрос
     * @return представление ошибки
     */
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Бизнес-ошибка при запросе {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorView(ErrorDto.of(ex.getErrorCode(), ex.getMessage()), ex.getErrorCode().getHttpStatus());
    }

    /**
     * Обрабатывает ошибки валидации JSON-запросов (AJAX).
     *
     * @param ex исключение валидации
     * @return DTO ошибки со статусом 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        log.warn("Ошибка валидации: {}", message);
        return ResponseEntity.badRequest().body(ErrorDto.of(ErrorCode.INVALID_INPUT, message));
    }

    /**
     * Обрабатывает запросы к несуществующим ресурсам.
     *
     * @param ex      исключение отсутствия ресурса
     * @param request текущий запрос
     * @return представление ошибки со статусом 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Ресурс не найден: {}", request.getRequestURI());
        ErrorDto error = new ErrorDto("NOT_FOUND", "Страница не найдена", LocalDateTime.now());
        return buildErrorView(error, HttpStatus.NOT_FOUND);
    }

    /**
     * Обрабатывает непредвиденные исключения (внутренняя ошибка сервера).
     *
     * @param ex      непредвиденное исключение
     * @param request текущий запрос
     * @return представление ошибки
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Необработанное исключение при запросе {}", request.getRequestURI(), ex);
        ErrorDto error = new ErrorDto("INTERNAL_ERROR", "Внутренняя ошибка сервера", LocalDateTime.now());
        return buildErrorView(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ModelAndView buildErrorView(ErrorDto error, HttpStatus status) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("error", error);
        mav.addObject("status", status.value());
        mav.setStatus(status);
        return mav;
    }
}