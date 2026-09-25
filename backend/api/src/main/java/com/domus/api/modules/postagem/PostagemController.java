package com.domus.api.modules.postagem;

import com.domus.api.modules.postagem.dto.*;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/postagens")
@RequiredArgsConstructor
public class PostagemController {

    private final PostagemService postagemService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping("/mural")
    public List<PostagemResponse> listarMural() {
        return postagemService.listarMural(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole()
        );
    }

    @GetMapping("/feed")
    public Page<PostagemResponse> listarFeed(
            @RequestParam(required = false) TipoPostagem tipo,
            Pageable pageable) {
        return postagemService.listarFeed(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                tipo,
                pageable
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostagemResponse criar(@RequestBody @Valid CriarPostagemRequest request) {
        return postagemService.criarPostagem(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                request
        );
    }

    @PostMapping("/{id}/curtir")
    public PostagemResponse curtir(
            @PathVariable UUID id,
            @RequestBody @Valid CurtidaRequest request) {
        return postagemService.alternarCurtida(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                id,
                request.tipo()
        );
    }

    @PostMapping("/{id}/comentarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ComentarioResponse comentar(
            @PathVariable UUID id,
            @RequestBody @Valid CriarComentarioRequest request) {
        return postagemService.comentar(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                id,
                request.conteudo(),
                request.paiComentarioId()
        );
    }

    @PostMapping("/comentarios/{comentarioId}/curtir")
    public ComentarioResponse curtirComentario(@PathVariable UUID comentarioId) {
        return postagemService.alternarCurtidaComentario(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                comentarioId
        );
    }

    @PutMapping("/{id}")
    public PostagemResponse atualizar(
            @PathVariable UUID id,
            @RequestBody @Valid CriarPostagemRequest request) {
        return postagemService.atualizarPostagem(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                id,
                request
        );
    }

    @PutMapping("/{postagemId}/comentarios/{comentarioId}")
    public ComentarioResponse atualizarComentario(
            @PathVariable UUID postagemId,
            @PathVariable UUID comentarioId,
            @RequestBody @Valid CriarComentarioRequest request) {
        return postagemService.atualizarComentario(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                postagemId,
                comentarioId,
                request.conteudo()
        );
    }

    @DeleteMapping("/{postagemId}/comentarios/{comentarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarComentario(
            @PathVariable UUID postagemId,
            @PathVariable UUID comentarioId) {
        postagemService.deletarComentario(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                postagemId,
                comentarioId
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable UUID id) {
        postagemService.deletarPostagem(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getRole(),
                id
        );
    }
}
