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
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacao(MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> campos = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                campos.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Erro de validação. path={}, campos={}", request.getRequestURI(), campos);

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.ofValidacao(campos));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        log.warn("Erro de negócio. path={}, codigo={}", request.getRequestURI(), ex.getCodigo());
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(400, ex.getCodigo(), ex.getMessage()));
    }

    // 422, não 400: a inscrição não tem dado inválido — a PESSOA não é elegível para o
    // evento. Handler específico vence o de BusinessException acima (Spring casa pelo tipo
    // mais específico, não pela ordem de declaração).
    @ExceptionHandler(com.domus.api.modules.evento.elegibilidade.NaoElegivelException.class)
    public ResponseEntity<ErrorResponse> handleNaoElegivel(
            com.domus.api.modules.evento.elegibilidade.NaoElegivelException ex, HttpServletRequest request) {
        log.warn("Inscrição recusada por inelegibilidade. path={}", request.getRequestURI());
        // Mapeamento Impedimento -> DetalheErro: é aqui, e só aqui, que shared "enxerga" o
        // módulo evento (ver Javadoc de DetalheErro) — o JSON de resposta sai idêntico ao de
        // antes, só o tipo interno do campo mudou.
        java.util.List<DetalheErro> detalhes = ex.getImpedimentos().stream()
                .map(i -> new DetalheErro(i.codigo(), i.mensagem(), i.contornavel()))
                .toList();
        return ResponseEntity
                .status(422)
                .body(ErrorResponse.ofElegibilidade(ex.getCodigo(), ex.getMessage(), detalhes));
    }

    @ExceptionHandler(SessaoExpiradaException.class)
    public ResponseEntity<ErrorResponse> handleSessaoExpirada(SessaoExpiradaException ex, HttpServletRequest request) {
        log.warn("Sessão expirada ou inválida. path={}, codigo={}", request.getRequestURI(), ex.getCodigo());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, ex.getCodigo(), ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Recurso não encontrado. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "NAO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(HttpServletRequest request) {

        // Mensagem genérica intencional — não revela se o email existe ou não
        log.warn("Tentativa de login com credenciais inválidas. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos. Verifique seus dados e tente novamente."));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(HttpServletRequest request) {

        log.warn("Tentativa de login de usuário inativo. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "USUARIO_INATIVO", "Sua conta está desativada. Entre em contato com o administrador"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(HttpServletRequest request) {

        log.warn("Acesso negado. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "ACESSO_NEGADO", "Você não tem permissão para acessar este recurso"));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErrorResponse> handleAcessoNegado(AcessoNegadoException ex, HttpServletRequest request) {

        log.warn("Tentativa de acesso a recurso de outro tenant. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "ACESSO_NEGADO", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {

        log.error("Erro inesperado. path={}", request.getRequestURI(), ex);

        // Só erros inesperados (500) viram alerta no Sentry — fluxo esperado (400/401/403/404/429)
        // NÃO é bug e não polui o painel. Anexa o request_id do MDC para ligar o erro aos logs.
        io.sentry.Sentry.withScope(scope -> {
            String requestId = org.slf4j.MDC.get("request_id");
            if (requestId != null) scope.setTag("request_id", requestId);
            io.sentry.Sentry.captureException(ex);
        });

        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(500, "ERRO_INTERNO", "Ocorreu um erro interno. Tente novamente mais tarde"));
    }

    @ExceptionHandler(ContaBloqueadaException.class)
    public ResponseEntity<ErrorResponse> handleContaBloqueada(ContaBloqueadaException ex, HttpServletRequest request) {

        log.warn("Tentativa de login em conta bloqueada. path={}", request.getRequestURI());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(429, "CONTA_BLOQUEADA", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violação de integridade de dados. path={}", request.getRequestURI(), ex);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "CONFLITO_DADOS",
                        "A operação viola uma restrição de dados. Verifique se o registro já existe."));
    }
}