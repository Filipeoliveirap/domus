package com.domus.api.modules.contapagar;

import com.domus.api.modules.anexo.dto.AnexoUploadResponse;
import com.domus.api.modules.contapagar.dto.*;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/contas-a-pagar")
@RequiredArgsConstructor
@Validated
public class ContaAPagarController {

    private final ContaAPagarService service;
    private final UsuarioAutenticado usuarioAutenticado;

    private void exigirFinanceiro() {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException(
                    "Só um administrador ou tesoureiro pode acessar contas a pagar.");
        }
    }

    // ---- listagem e consulta ----

    @GetMapping
    public PagedResponse<ContaResponse> listar(
            @RequestParam(required = false) StatusConta status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte,
            @RequestParam(required = false) @Size(max = 200, message = "Termo de busca muito longo.") String q,
            @RequestParam(required = false) String beneficiario,
            @RequestParam(required = false) String fornecedor,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano,
            org.springframework.data.domain.Pageable pageable) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String termo = q;
        if (termo == null || termo.isBlank()) termo = beneficiario;
        if (termo == null || termo.isBlank()) termo = fornecedor;
        return PagedResponse.from(service.listar(igrejaId, status, vencimentoAte, termo, pageable));
    }

    @GetMapping("/{id}")
    public ContaResponse buscar(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.buscar(id, igrejaId);
    }

    @GetMapping({"/series/{id}/projecao", "/{id}/serie/projecao"})
    public List<ProjecaoItem> projetarSerie(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.projetarSerie(id, igrejaId);
    }

    @GetMapping("/resumo")
    public ResumoResponse resumo(
            @RequestParam(required = false) String mesReferencia,
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        if (mesReferencia != null && !mesReferencia.isBlank()) {
            try {
                if (mesReferencia.length() >= 7) {
                    YearMonth ym = YearMonth.parse(mesReferencia.substring(0, 7));
                    mes = ym.getMonthValue();
                    ano = ym.getYear();
                }
            } catch (Exception ignored) {}
        }
        return service.resumo(igrejaId, mes, ano);
    }

    // ---- criação ----

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse criar(@Valid @RequestBody ContaRequest dto) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return service.criar(dto, igrejaId, usuarioId);
    }

    // ---- edição completa (PUT) ----

    @PutMapping("/{id}")
    public ContaResponse editarCompleto(@PathVariable UUID id,
                                         @RequestParam(defaultValue = "ESTA") EscopoEdicaoSerie escopo,
                                         @Valid @RequestBody ContaRequest dto) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return service.editar(id, dto, escopo, igrejaId, usuarioId);
    }

    // ---- edição parcial (PATCH) ----

    @PatchMapping("/{id}")
    public ContaResponse editarParcial(@PathVariable UUID id,
                                        @RequestParam(defaultValue = "ESTA") EscopoEdicaoSerie escopo,
                                        @Valid @RequestBody(required = false) ContaPatchRequest dto) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return service.editar(id, dto, escopo, igrejaId, usuarioId);
    }

    // ---- exclusão ----

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id,
                        @RequestParam(defaultValue = "ESTA") EscopoEdicaoSerie escopo) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        service.excluir(id, escopo, igrejaId, usuarioId);
    }

    // ---- pagamento ----

    @PostMapping("/{id}/pagamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse pagar(@PathVariable UUID id,
                               @Valid @RequestBody PagamentoRequest dto) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return service.pagar(id, dto, igrejaId, usuarioId);
    }

    // ---- dar baixa restante ----

    @PostMapping("/{id}/dar-baixa-restante")
    public ContaResponse darBaixaRestante(@PathVariable UUID id) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return service.darBaixaRestante(id, igrejaId, usuarioId);
    }

    // ---- estorno ----

    @PostMapping("/pagamentos/{pagamentoId}/estorno")
    public PagamentoResponse estornar(@PathVariable UUID pagamentoId) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return service.estornar(pagamentoId, igrejaId, usuarioId);
    }

    // ---- anexos ----

    @PostMapping("/{contaId}/anexos")
    @ResponseStatus(HttpStatus.CREATED)
    public AnexoUploadResponse uploadAnexo(
            @PathVariable UUID contaId,
            @RequestParam("arquivo") MultipartFile arquivo) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return service.anexarAnexo(contaId, arquivo, igrejaId);
    }

    @GetMapping("/{contaId}/anexos/{anexoId}")
    public void baixarAnexo(@PathVariable UUID contaId,
                            @PathVariable UUID anexoId,
                            HttpServletResponse response)
            throws IOException {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        byte[] bytes = service.lerAnexo(anexoId, contaId, igrejaId);
        AnexoUploadResponse anexo = service.buscarAnexoMeta(anexoId, contaId, igrejaId);
        response.setContentType(anexo.tipo());
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + anexo.nomeOriginal() + "\"");
        response.getOutputStream().write(bytes);
    }

    @DeleteMapping("/{contaId}/anexos/{anexoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAnexo(@PathVariable UUID contaId,
                              @PathVariable UUID anexoId) {
        exigirFinanceiro();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        service.removerAnexo(anexoId, contaId, igrejaId);
    }
}
