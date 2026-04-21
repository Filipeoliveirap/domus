package com.domus.api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ─── Erros de validação (@Valid no DTO) ───────────────────────
    // Quando um campo obrigatório vem vazio, email inválido, senha curta, etc.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacao(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> campos = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                campos.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Erro de validação. path={}, campos={}", request.getRequestURI(), campos);

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.ofValidacao(campos));
    }

    // ─── Regra de negócio violada ─────────────────────────────────
    // Email duplicado, CNPJ já cadastrado, etc.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex,
            HttpServletRequest request) {

        log.warn("Erro de negócio. path={}, mensagem={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, "ERRO_NEGOCIO", ex.getMessage()));
    }

    // ─── Recurso não encontrado ───────────────────────────────────
    // Membro, evento, movimentação com ID inexistente
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Recurso não encontrado. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "NAO_ENCONTRADO", ex.getMessage()));
    }

    // ─── Credenciais inválidas ────────────────────────────────────
    // Email ou senha incorretos no login
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(HttpServletRequest request) {

        // Mensagem genérica intencional — não revela se o email existe ou não
        log.warn("Tentativa de login com credenciais inválidas. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos"));
    }

    // ─── Usuário inativo ──────────────────────────────────────────
    // Admin desativou o usuário
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(HttpServletRequest request) {

        log.warn("Tentativa de login de usuário inativo. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "USUARIO_INATIVO", "Sua conta está desativada. Entre em contato com o administrador"));
    }

    // ─── Acesso negado (permissão insuficiente) ───────────────────
    // LIDER tentando acessar financeiro, MEMBRO tentando criar membro, etc.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(HttpServletRequest request) {

        log.warn("Acesso negado. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "ACESSO_NEGADO", "Você não tem permissão para acessar este recurso"));
    }

    // ─── Violação de tenant (multi-tenant) ───────────────────────
    // Usuário tentando acessar dado de outra igreja
    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErrorResponse> handleAcessoNegado(
            AcessoNegadoException ex,
            HttpServletRequest request) {

        // Log com WARN — pode ser tentativa maliciosa
        log.warn("Tentativa de acesso a recurso de outro tenant. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "ACESSO_NEGADO", ex.getMessage()));
    }

    // ─── Erro genérico — sempre o último ─────────────────────────
    // Qualquer exception não tratada pelos handlers acima
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request) {

        // ERROR — precisa de atenção, stack trace completo no log
        log.error("Erro inesperado. path={}", request.getRequestURI(), ex);

        // NUNCA expõe detalhes internos para o cliente
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(500, "ERRO_INTERNO", "Ocorreu um erro interno. Tente novamente mais tarde"));
    }
}