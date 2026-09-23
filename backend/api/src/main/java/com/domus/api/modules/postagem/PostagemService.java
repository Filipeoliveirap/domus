package com.domus.api.modules.postagem;

import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.postagem.dto.*;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.RegraNegocioException;
import com.domus.api.shared.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostagemService {

    private final PostagemRepository postagemRepository;
    private final CurtidaPostagemRepository curtidaRepository;
    private final ComentarioPostagemRepository comentarioRepository;
    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository pessoaRepository;
    private final FotoRepository fotoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;

    @Transactional(readOnly = true)
    public List<PostagemResponse> listarMural(UUID igrejaId) {
        return postagemRepository.findMuralAvisos(igrejaId).stream()
                .map(p -> toResponse(p, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PostagemResponse> listarFeed(UUID igrejaId, TipoPostagem tipoFilter, Pageable pageable) {
        return postagemRepository.findFeed(igrejaId, tipoFilter, pageable)
                .map(p -> toResponse(p, null));
    }

    @Transactional
    public PostagemResponse criarPostagem(UUID igrejaId, UUID autorPessoaId, String perfilUsuario, CriarPostagemRequest request) {
        if (request.oficial() && !"ADMIN_IGREJA".equals(perfilUsuario) && !"LIDER".equals(perfilUsuario)) {
            throw new RegraNegocioException("Apenas administradores e líderes podem publicar avisos oficiais no mural.");
        }

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Igreja não encontrada."));

        Pessoa autor = pessoaRepository.findById(autorPessoaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pessoa autor não encontrada."));

        Foto foto = null;
        if (request.fotoId() != null) {
            foto = fotoRepository.findById(request.fotoId()).orElse(null);
        }

        Postagem post = Postagem.builder()
                .igreja(igreja)
                .autorPessoa(autor)
                .tipo(request.tipo())
                .oficial(request.oficial())
                .titulo(request.titulo())
                .conteudo(request.conteudo())
                .foto(foto)
                .versiculoRef(request.versiculoRef())
                .fixado(request.fixado())
                .build();

        Postagem salva = postagemRepository.save(post);
        return toResponse(salva, autorPessoaId);
    }

    @Transactional
    public PostagemResponse alternarCurtida(UUID igrejaId, UUID pessoaId, UUID postagemId, TipoReacao tipoReacao) {
        Postagem post = postagemRepository.findByIdAndIgrejaId(postagemId, igrejaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Postagem não encontrada."));

        Optional<CurtidaPostagem> existente = curtidaRepository.findByPostagemIdAndPessoaId(postagemId, pessoaId);

        if (existente.isPresent()) {
            CurtidaPostagem c = existente.get();
            if (c.getTipoReacao() == tipoReacao) {
                curtidaRepository.delete(c);
            } else {
                c.setTipoReacao(tipoReacao);
                curtidaRepository.save(c);
            }
        } else {
            Pessoa pessoa = pessoaRepository.findById(pessoaId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Pessoa não encontrada."));

            CurtidaPostagem nova = CurtidaPostagem.builder()
                    .postagem(post)
                    .pessoa(pessoa)
                    .tipoReacao(tipoReacao)
                    .build();
            curtidaRepository.save(nova);

            // Notificar autor da postagem se não for a própria pessoa
            if (!post.getAutorPessoa().getId().equals(pessoaId)) {
                usuarioRepository.findByPessoaId(post.getAutorPessoa().getId()).ifPresent(u ->
                        notificacaoService.criar(
                                TipoNotificacao.SISTEMA,
                                igrejaId,
                                u.getId(),
                                pessoa.getNome() + " reagiu à sua postagem.",
                                "/inicio"
                        )
                );
            }
        }

        return toResponse(post, pessoaId);
    }

    @Transactional
    public ComentarioResponse comentar(UUID igrejaId, UUID pessoaId, UUID postagemId, String conteudo) {
        Postagem post = postagemRepository.findByIdAndIgrejaId(postagemId, igrejaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Postagem não encontrada."));

        Pessoa autor = pessoaRepository.findById(pessoaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pessoa não encontrada."));

        ComentarioPostagem comentario = ComentarioPostagem.builder()
                .postagem(post)
                .autorPessoa(autor)
                .conteudo(conteudo)
                .build();

        ComentarioPostagem salvo = comentarioRepository.save(comentario);

        // Notificar autor da postagem se não for a própria pessoa
        if (!post.getAutorPessoa().getId().equals(pessoaId)) {
            usuarioRepository.findByPessoaId(post.getAutorPessoa().getId()).ifPresent(u ->
                    notificacaoService.criar(
                            TipoNotificacao.SISTEMA,
                            igrejaId,
                            u.getId(),
                            autor.getNome() + " comentou na sua postagem.",
                            "/inicio"
                    )
            );
        }

        return ComentarioResponse.from(salvo);
    }

    @Transactional
    public void deletarPostagem(UUID igrejaId, UUID pessoaId, String perfilUsuario, UUID postagemId) {
        Postagem post = postagemRepository.findByIdAndIgrejaId(postagemId, igrejaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Postagem não encontrada."));

        boolean ehAutor = post.getAutorPessoa().getId().equals(pessoaId);
        boolean ehAdmin = "ADMIN_IGREJA".equals(perfilUsuario);

        if (!ehAutor && !ehAdmin) {
            throw new RegraNegocioException("Você não tem permissão para excluir esta postagem.");
        }

        postagemRepository.delete(post);
    }

    private PostagemResponse toResponse(Postagem p, UUID pessoaIdContexto) {
        long totalCurtidas = curtidaRepository.countByPostagemId(p.getId());
        long totalComentarios = comentarioRepository.countByPostagemId(p.getId());

        TipoReacao minhaReacao = null;
        if (pessoaIdContexto != null) {
            minhaReacao = curtidaRepository.findByPostagemIdAndPessoaId(p.getId(), pessoaIdContexto)
                    .map(CurtidaPostagem::getTipoReacao)
                    .orElse(null);
        }

        List<ComentarioResponse> comentarios = comentarioRepository.findByPostagemIdOrderByCriadoEmAsc(p.getId())
                .stream()
                .map(ComentarioResponse::from)
                .toList();

        return PostagemResponse.from(p, totalCurtidas, totalComentarios, minhaReacao, comentarios);
    }
}
