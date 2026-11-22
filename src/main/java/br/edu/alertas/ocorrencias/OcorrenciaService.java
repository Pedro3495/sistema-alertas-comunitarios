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

    // shortcut: o morador vê só a confirmação dos próprios registros; a consulta regional fica para o RF008.
    public Ocorrencia buscarDoMorador(UUID protocolo) {
        var ator = usuarios.exigirMorador();
        return repository.findByProtocoloAndMorador_Id(protocolo, ator.id()).orElseThrow(() -> new EntityNotFoundException("Ocorrência não encontrada."));
    }

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
