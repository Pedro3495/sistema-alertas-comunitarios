package br.edu.alertas.ocorrencias;

import br.edu.alertas.areas.AreaRiscoRepository;
import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.auditoria.UsuarioAtual;
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
    private final Clock clock;

    public OcorrenciaService(OcorrenciaRepository repository, AreaRiscoRepository areas,
            RepresentanteRepository representantes, UsuarioAtual usuarios, AuditoriaService auditoria, Clock clock) {
        this.repository = repository; this.areas = areas; this.representantes = representantes;
        this.usuarios = usuarios; this.auditoria = auditoria; this.clock = clock;
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
        var ocorrencia = repository.saveAndFlush(new Ocorrencia(dados, area, representante, clock.instant()));
        auditoria.registrar("REGISTRAR_OCORRENCIA #" + ocorrencia.getProtocolo());
        return ocorrencia;
    }

    public Ocorrencia buscar(UUID protocolo) {
        usuarios.exigirRepresentante();
        return repository.findByProtocolo(protocolo).orElseThrow(() -> new EntityNotFoundException("Ocorrência não encontrada."));
    }

    public FotoOcorrencia foto(UUID protocolo, int ordem) {
        return buscar(protocolo).getFotos().stream().filter(foto -> foto.getOrdem() == ordem).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Foto não encontrada."));
    }
}
