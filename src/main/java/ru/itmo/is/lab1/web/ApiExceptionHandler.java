package ru.itmo.is.lab1.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.itmo.is.lab1.domain.OrganizationType;
import ru.itmo.is.lab1.dto.ApiError;
import ru.itmo.is.lab1.service.exception.InvalidInputException;
import ru.itmo.is.lab1.service.exception.NotFoundException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String CHECK_FIELDS = "Проверьте правильность заполнения полей";
    private static final String NUMERIC_OVERFLOW =
            "Слишком большое число: результат операции не помещается в допустимый диапазон (bigint)";

    /** Ограничения БД (V1__schema.sql) -> понятное пользователю сообщение. */
    private static final Map<String, Map.Entry<String, String>> DB_CONSTRAINTS = Map.ofEntries(
            entry("coordinates_x_max", entry("coordinates.x", "X не может быть больше 549")),
            entry("coordinates_x_finite", entry("coordinates.x", "X должен быть конечным числом")),
            entry("coordinates_y_finite", entry("coordinates.y", "Y должен быть конечным числом")),
            entry("organization_name_not_empty", entry("name", "Название не может быть пустым")),
            entry("organization_annual_turnover_positive", entry("annualTurnover", "Годовой оборот должен быть больше 0")),
            entry("organization_employees_count_positive", entry("employeesCount", "Количество сотрудников должно быть больше 0")),
            entry("organization_rating_positive", entry("rating", "Рейтинг должен быть больше 0")),
            entry("organization_type_valid", entry("type", "Недопустимый тип организации")),
            entry("organization_coordinates_fk", entry("coordinates", "Выбранные координаты уже удалены")),
            entry("organization_official_address_fk", entry("officialAddress", "Выбранный адрес уже удалён")),
            entry("organization_postal_address_fk", entry("postalAddress", "Выбранный адрес уже удалён")));

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, e.getMessage(), Map.of());
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiError> invalidInput(InvalidInputException e) {
        return respond(HttpStatus.BAD_REQUEST, e.getMessage(), e.getFieldErrors());
    }

    /** Ошибки Bean Validation для тела запроса (@Valid) и привязки параметров. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> bindingFailed(BindException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : e.getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.isBindingFailure()
                    ? "Некорректное значение" : error.getDefaultMessage());
        }
        e.getGlobalErrors().forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return respond(HttpStatus.BAD_REQUEST, CHECK_FIELDS, errors);
    }

    /** Ошибки Bean Validation на уровне ORM (при сохранении сущности). */
    @ExceptionHandler({ConstraintViolationException.class, TransactionSystemException.class})
    public ResponseEntity<ApiError> entityValidationFailed(Exception e) {
        ConstraintViolationException violation = findCause(e, ConstraintViolationException.class);
        if (violation == null) {
            return unexpected(e);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : violation.getConstraintViolations()) {
            errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage());
        }
        return respond(HttpStatus.BAD_REQUEST, CHECK_FIELDS, errors);
    }

    /** Некорректный JSON: строка вместо числа, неизвестный тип, дробное число вместо целого и т.п. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadableBody(HttpMessageNotReadableException e) {
        JsonMappingException mapping = findCause(e, JsonMappingException.class);
        if (mapping == null || mapping.getPath().isEmpty()) {
            return respond(HttpStatus.BAD_REQUEST, "Тело запроса не является корректным JSON", Map.of());
        }
        String field = mapping.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : String.valueOf(ref.getIndex()))
                .collect(Collectors.joining("."));
        Class<?> targetType = mapping instanceof MismatchedInputException mismatch ? mismatch.getTargetType() : null;
        return respond(HttpStatus.BAD_REQUEST, CHECK_FIELDS, Map.of(field, describeExpectedType(targetType, mapping)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "Параметр '" + e.getName() + "': " + describeExpectedType(e.getRequiredType(), null);
        return respond(HttpStatus.BAD_REQUEST, message, Map.of(e.getName(), message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException e) {
        String message = "Не указан параметр '" + e.getParameterName() + "'";
        return respond(HttpStatus.BAD_REQUEST, message, Map.of(e.getParameterName(), message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrityViolation(DataIntegrityViolationException e) {
        String constraint = findConstraintName(e);
        Map.Entry<String, String> known = constraint == null ? null : DB_CONSTRAINTS.get(constraint);
        if (known != null) {
            return respond(HttpStatus.BAD_REQUEST, known.getValue(), Map.of(known.getKey(), known.getValue()));
        }
        // Spring относит ошибки данных SQLSTATE 22xxx к нарушению целостности; для пользователя это ошибка ввода
        PSQLException psql = findCause(e, PSQLException.class);
        if (psql != null && "22003".equals(psql.getSQLState())) {
            return respond(HttpStatus.BAD_REQUEST,
                    NUMERIC_OVERFLOW, Map.of());
        }
        if (psql != null && psql.getSQLState() != null && psql.getSQLState().startsWith("22")) {
            return respond(HttpStatus.BAD_REQUEST, databaseMessage(e), Map.of());
        }
        log.warn("Нарушено ограничение БД", e);
        return respond(HttpStatus.CONFLICT, "Операция нарушает ограничения базы данных: " + databaseMessage(e), Map.of());
    }

    /** Ошибки, выброшенные функциями БД (RAISE EXCEPTION), и прочие ошибки доступа к данным. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> dataAccess(DataAccessException e) {
        PSQLException psql = findCause(e, PSQLException.class);
        if (psql != null && psql.getServerErrorMessage() != null) {
            String sqlState = psql.getSQLState();
            // P0002 - no_data_found, 22xxx - некорректные данные, P0001 - raise_exception
            if ("P0002".equals(sqlState)) {
                return respond(HttpStatus.NOT_FOUND, databaseMessage(e), Map.of());
            }
            if ("22003".equals(sqlState)) {
                return respond(HttpStatus.BAD_REQUEST,
                        NUMERIC_OVERFLOW, Map.of());
            }
            if (sqlState != null && (sqlState.startsWith("22") || sqlState.startsWith("P0"))) {
                return respond(HttpStatus.BAD_REQUEST, databaseMessage(e), Map.of());
            }
        }
        return unexpected(e);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotSupported(HttpRequestMethodNotSupportedException e) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "Метод " + e.getMethod() + " не поддерживается", Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResource(NoResourceFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "Ресурс не найден: /" + e.getResourcePath(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Необработанная ошибка", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", Map.of());
    }

    private static String describeExpectedType(Class<?> type, JsonMappingException cause) {
        if (type == null) {
            return cause instanceof InvalidFormatException ? "Некорректное значение" : "Некорректный формат значения";
        }
        if (type.isEnum()) {
            String allowed = Arrays.stream(type.getEnumConstants()).map(String::valueOf).collect(Collectors.joining(", "));
            return (type == OrganizationType.class ? "Недопустимый тип организации" : "Недопустимое значение")
                    + ". Допустимые значения: " + allowed;
        }
        if (type == Long.class || type == long.class || type == Integer.class || type == int.class) {
            return "Ожидается целое число в допустимом диапазоне";
        }
        if (type == Double.class || type == double.class || type == Float.class || type == float.class) {
            return "Ожидается число";
        }
        if (type == String.class) {
            return "Ожидается строка";
        }
        return "Некорректное значение";
    }

    private static String findConstraintName(Throwable e) {
        org.hibernate.exception.ConstraintViolationException hibernate =
                findCause(e, org.hibernate.exception.ConstraintViolationException.class);
        if (hibernate != null && hibernate.getConstraintName() != null) {
            return hibernate.getConstraintName();
        }
        PSQLException psql = findCause(e, PSQLException.class);
        ServerErrorMessage server = psql == null ? null : psql.getServerErrorMessage();
        return server == null ? null : server.getConstraint();
    }

    private static String databaseMessage(Throwable e) {
        PSQLException psql = findCause(e, PSQLException.class);
        if (psql != null && psql.getServerErrorMessage() != null) {
            return psql.getServerErrorMessage().getMessage();
        }
        return e.getMessage();
    }

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, fieldErrors));
    }
}
