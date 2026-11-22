package br.edu.alertas.ocorrencias;

import br.edu.alertas.areas.AreaRiscoRepository;
import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.auditoria.UsuarioAtual;
import br.edu.alertas.moradores.AutenticacaoMoradorService;
import br.edu.alertas.representantes.RepresentanteRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OcorrenciaService {
    @PersistenceContext private EntityManager em;
    private final OcorrenciaRepository repository;
    private final AreaRiscoRepository areas;
    private final RepresentanteRepository representantes;
    private final UsuarioAtual usuarios;
    private final AuditoriaService auditoria;
    private final AutenticacaoMoradorService moradores;
    private final Clock clock;

    public OcorrenciaService(OcorrenciaRepository repository, AreaRiscoRepository areas,
            RepresentanteRepository representantes, UsuarioAtual usuarios, AuditoriaService auditoria,
            AutenticacaoMoradorService moradores, Clock clock) {
        this.repository = repository; this.areas = areas; this.representantes = representantes;
        this.usuarios = usuarios; this.auditoria = auditoria; this.moradores = moradores; this.clock = clock;
    }

    @Transactional
    public Ocorrencia registrar(DadosOcorrencia dados) {
        var ator = usuarios.exigirRepresentante();
        var representante = representantes.buscarPorIdComBloqueio(ator.id())
                .orElseThrow(() -> new AccessDeniedException("Entre novamente na sua conta."));
        // A validação anterior ao lock pode ter carregado uma versão anterior à desativação concorrente.
        em.refresh(representante);
        usuarios.exigirRepresentante();
        if (dados == null || dados.envioId() == null) { throw new ErroOcorrencia("envioId", "Reabra o formulário para preparar o envio."); }
        var existente = repository.findByRepresentante_IdAndEnvioId(ator.id(), dados.envioId());
        if (existente.isPresent()) { return existente.get(); }
        if (dados.areaId() == null) { throw new ErroOcorrencia("areaId", "Selecione uma área de risco cadastrada."); }
        var area = areas.findById(dados.areaId()).orElseThrow(() -> new ErroOcorrencia("areaId", "A área selecionada não está disponível. Selecione outra área."));
        var ocorrencia = repository.saveAndFlush(new Ocorrencia(dados, area, representante, null, clock.instant()));
        auditoria.registrar("REGISTRAR_OCORRENCIA #" + ocorrencia.getProtocolo());
        return ocorrencia;
    }

    /**
     * RF009: só representantes decidem, em qualquer área; a confirmação não dispara alerta (regra 3).
     * Regra 4 (aviso de alteração se já houver alerta) depende do RF010: hoje nenhuma ocorrência tem alerta.
     */
    @Transactional
    public Ocorrencia verificar(UUID protocolo, SituacaoOcorrencia decisao, String justificativa) {
        var ator = usuarios.exigirRepresentante();
        var representante = representantes.buscarPorIdComBloqueio(ator.id())
                .orElseThrow(() -> new AccessDeniedException("Entre novamente na sua conta."));
        em.refresh(representante); // mesma proteção do registrar contra desativação concorrente
        usuarios.exigirRepresentante();
        var ocorrencia = repository.buscarComBloqueio(protocolo).orElseThrow(() -> new EntityNotFoundException("Ocorrência não encontrada."));
        var anterior = ocorrencia.getSituacao();
        em.persist(ocorrencia.verificar(decisao, justificativa, representante, clock.instant()));
        // RF013 regras 1 e 4: valores anterior e novo; a justificativa fica fora da auditoria (interna, RF008 regra 6).
        auditoria.registrar("VERIFICAR_OCORRENCIA #" + protocolo + ": " + anterior + " -> " + ocorrencia.getSituacao());
        return ocorrencia;
    }

    /** RF008 regra 6: histórico com justificativas, só para representantes. */
    public List<VerificacaoOcorrencia> verificacoes(UUID protocolo) {
        usuarios.exigirRepresentante();
        return repository.verificacoes(protocolo);
    }

    /** Morador autenticado com a residência atual, para preencher a área do formulário. */
    @Transactional
    public br.edu.alertas.moradores.Morador moradorAtual() {
        return moradores.bloquearAutor(usuarios.exigirMorador());
    }

    /** RF006 regra 4: a área vem da residência do morador no momento do registro; areaId enviado é ignorado. */
    @Transactional
    public Ocorrencia registrarPorMorador(DadosOcorrencia dados) {
        var morador = moradores.bloquearAutor(usuarios.exigirMorador());
        if (dados == null || dados.envioId() == null) { throw new ErroOcorrencia("envioId", "Reabra o formulário para preparar o envio."); }
        var existente = repository.findByMorador_IdAndEnvioId(morador.getId(), dados.envioId());
        if (existente.isPresent()) { return existente.get(); }
        var area = areas.findById(morador.getAreaId()).orElseThrow(() -> new ErroOcorrencia("areaId", "A área da sua residência não está disponível."));
        var ocorrencia = repository.saveAndFlush(new Ocorrencia(dados, area, null, morador, clock.instant()));
        auditoria.registrar("REGISTRAR_OCORRENCIA #" + ocorrencia.getProtocolo());
        return ocorrencia;
    }

    /** RF008 regra 4: representante consulta todas as áreas e pode filtrar por área. */
    // shortcut: sem paginação, a lista traz todas as ocorrências filtradas; paginar quando o volume exigir.
    public List<ResumoOcorrencia> pesquisar(UUID protocolo, TipoOcorrencia tipo, SituacaoOcorrencia situacao, Long areaId) {
        usuarios.exigirRepresentante();
        return repository.pesquisar(protocolo, tipo, situacao, areaId, null, null);
    }

    /** RF008 regras 1 e 5: próprios relatos (mesmo de área antiga) e relatos da área atual da residência. */
    @Transactional // a leitura com lock (de bloquearAutor) não roda em transação somente leitura no PostgreSQL
    public List<ResumoOcorrencia> pesquisarDoMorador(UUID protocolo, TipoOcorrencia tipo, SituacaoOcorrencia situacao) {
        var morador = moradores.bloquearAutor(usuarios.exigirMorador()); // relê a residência atual e revalida a sessão
        return repository.pesquisar(protocolo, tipo, situacao, null, morador.getId(), morador.getAreaId());
    }

    @Transactional
    public Ocorrencia buscarDoMorador(UUID protocolo) {
        var morador = moradores.bloquearAutor(usuarios.exigirMorador());
        return repository.buscarVisivelAoMorador(protocolo, morador.getId(), morador.getAreaId())
                .orElseThrow(() -> new EntityNotFoundException("Ocorrência não encontrada."));
    }

    @Transactional
    public FotoOcorrencia fotoDoMorador(UUID protocolo, int ordem) {
        return foto(buscarDoMorador(protocolo), ordem);
    }

    public Ocorrencia buscar(UUID protocolo) {
        usuarios.exigirRepresentante();
        return repository.findByProtocolo(protocolo).orElseThrow(() -> new EntityNotFoundException("Ocorrência não encontrada."));
    }

    public FotoOcorrencia foto(UUID protocolo, int ordem) {
        return foto(buscar(protocolo), ordem);
    }

    private static FotoOcorrencia foto(Ocorrencia ocorrencia, int ordem) {
        return ocorrencia.getFotos().stream().filter(foto -> foto.getOrdem() == ordem).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Foto não encontrada."));
    }
}
