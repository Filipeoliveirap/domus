package com.domus.api.modules.pessoa;

import com.domus.api.modules.pessoa.DTO.PessoaRequestDTO;
import com.domus.api.modules.pessoa.DTO.PessoaResponse;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/pessoas")
@RequiredArgsConstructor
public class PessoaController {

    private final PessoaService pessoaService;
    private final UsuarioAutenticado usuarioAutenticado;
    private final com.domus.api.modules.ministerio.MinisterioService ministerioService;

    @GetMapping
    public ResponseEntity<PagedResponse<PessoaResponse>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Vinculo vinculo,
            @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(
                pessoaService.listarMembros(igrejaId, q, pageable, podeVerDadosSensiveis(), vinculo));
    }

    @GetMapping("/bairros")
    public ResponseEntity<java.util.List<String>> bairros() {
        return ResponseEntity.ok(pessoaService.listarBairros(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping
    public ResponseEntity<PessoaResponse> cadastrar(
            @Valid @RequestBody PessoaRequestDTO data) {
        exigirGestaoPessoas();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        PessoaResponse response = pessoaService.cadastrarMembro(data, igrejaId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PessoaResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody PessoaRequestDTO data) {
        exigirGestaoPessoas();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(pessoaService.atualizarMembro(id, data, igrejaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PessoaResponse> buscarPorId(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(pessoaService.buscarPorId(id, igrejaId, podeVerDadosSensiveis()));
    }

    /**
     * Endereço e observações (notas pastorais) só saem da API para ADMIN_IGREJA.
     *
     * <p>LÍDER e MEMBRO enxergam o membro para o que precisam — contato, ministério, status —
     * mas não onde a pessoa mora nem o que foi anotado sobre ela. Barrar isso na tela não
     * adiantaria: o JSON está a um DevTools de distância.
     */
    private boolean podeVerDadosSensiveis() {
        return Permissoes.podeVerDadosSensiveisDePessoa(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras());
    }

    private void exigirGestaoPessoas() {
        if (!Permissoes.podeGerenciarPessoas(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException(
                    "Só um administrador ou secretário pode gerenciar pessoas.");
        }
    }

    /**
     * "Meu Perfil": sempre a pessoa vinculada a quem está logado, nunca um id do corpo/query.
     * Dados sensíveis (endereço, observações) sempre inclusos aqui — são os PRÓPRIOS dados de
     * quem pergunta, a restrição de `podeVerDadosSensiveis()` é sobre olhar o dado de OUTRA
     * pessoa.
     */
    @GetMapping("/me")
    public ResponseEntity<PessoaResponse> buscarMe() {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID pessoaId = usuarioAutenticado.getPessoaId();
        return ResponseEntity.ok(pessoaService.buscarPorId(pessoaId, igrejaId, true));
    }

    /**
     * ADMIN_IGREJA edita qualquer campo do próprio cadastro, MENOS e-mail: o e-mail do
     * payload é sempre substituído pelo e-mail já persistido antes de chamar o service, então
     * mesmo que o corpo mande um valor diferente ele é ignorado — o backend força a
     * imutabilidade, não confia em o front simplesmente não enviar o campo (email é a chave de
     * login, nativo e Google). LIDER/ACESSO_COMUM só trocam a própria foto: a checagem de
     * capacidade decide qual método do service roda, não um whitelist de campos dentro de
     * `atualizarMembro` (mais simples de auditar).
     */
    @PutMapping("/me")
    public ResponseEntity<PessoaResponse> atualizarMe(@Valid @RequestBody PessoaRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID pessoaId = usuarioAutenticado.getPessoaId();

        PessoaResponse resposta;
        if (Permissoes.podeGerenciarPessoas(usuarioAutenticado.getRole(),
                usuarioAutenticado.getCapacidadesExtras())) {
            PessoaResponse pessoaAtual = pessoaService.buscarPorId(pessoaId, igrejaId, true);
            PessoaRequestDTO dataComEmailImutavel = new PessoaRequestDTO(
                    data.nome(),
                    pessoaAtual.email(),
                    data.telefone(),
                    data.dataNascimento(),
                    data.endereco(),
                    data.vinculo(),
                    data.estadoCivil(),
                    data.sexo(),
                    data.cargo(),
                    data.observacoes(),
                    data.dataBatismo(),
                    data.fotoId()
            );
            resposta = pessoaService.atualizarMembro(pessoaId, dataComEmailImutavel, igrejaId);
        } else {
            resposta = pessoaService.atualizarMinhaFoto(pessoaId, data.fotoId(), igrejaId);
        }

        return ResponseEntity.ok(resposta);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        exigirGestaoPessoas();
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        pessoaService.arquivarMembro(id, igrejaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/ministerios")
    public ResponseEntity<java.util.List<com.domus.api.modules.ministerio.DTOs.MinisterioResponse>> ministerios(
            @PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(ministerioService.listarMinisteriosDaPessoa(id, igrejaId));
    }
}
