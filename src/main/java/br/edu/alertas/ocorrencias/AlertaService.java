package br.edu.alertas.ocorrencias;

import br.edu.alertas.auditoria.AuditoriaService;
import br.edu.alertas.auditoria.UsuarioAtual;
import br.edu.alertas.representantes.Representante;
import br.edu.alertas.representantes.RepresentanteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF010: emissão, distribuição e reenvio de alertas; aviso de alteração para quem já recebeu alerta (RF009 regra 4). */
// shortcut: o envio simulado roda dentro da transação; com provedor real, enviar depois do commit (fila/outbox).
@Service
@Transactional(readOnly = true)
public class AlertaService {
    @PersistenceContext private EntityManager em;
    private final ComunicadoRepository repository;
    private final OcorrenciaRepository ocorrencias;
    private final RepresentanteRepository representantes;
    private final UsuarioAtual usuarios;
    private final AuditoriaService auditoria;
    private final EnvioAlertas envio;
    private final Clock clock;

    public AlertaService(ComunicadoRepository repository, OcorrenciaRepository ocorrencias, RepresentanteRepository representantes,
            UsuarioAtual usuarios, AuditoriaService auditoria, EnvioAlertas envio, Clock clock) {
        this.repository = repository; this.ocorrencias = ocorrencias; this.representantes = representantes;
        this.usuarios = usuarios; this.auditoria = auditoria; this.envio = envio; this.clock = clock;
    }

    /** Regras 1, 2, 3 e 6: qualquer representante, qualquer área, nova emissão manual a cada chamada. */
    @Transactional
    public Comunicado emitir(UUID protocolo, String titulo, String mensagem, String orientacoes) {
        var representante = representanteAtual();
        var ocorrencia = ocorrencias.buscarComBloqueio(protocolo).orElseThrow(() -> new EntityNotFoundException("Ocorrência não encontrada."));
        var alerta = Comunicado.alerta(ocorrencia, titulo, mensagem, orientacoes,
                repository.moradoresAtivosDaArea(ocorrencia.getAreaId()), representante, clock.instant());
        alerta.distribuir(envio);
        repository.saveAndFlush(alerta);
        auditoria.registrar("EMITIR_ALERTA #" + protocolo); // RF013 regra 1
        return alerta;
    }

    /** Regra 4: reenvio manual só para quem falhou nos dois canais; o alerta continua o mesmo. */
    @Transactional
    public Comunicado reenviar(UUID protocolo, Long alertaId) {
        representanteAtual();
        ocorrencias.buscarComBloqueio(protocolo).orElseThrow(() -> new EntityNotFoundException("Ocorrência não encontrada."));
        var alerta = repository.findByIdAndOcorrencia_Protocolo(alertaId, protocolo)
                .filter(c -> c.getTipo() == TipoComunicado.ALERTA).orElseThrow(() -> new EntityNotFoundException("Alerta não encontrado."));
        alerta.distribuir(envio);
        auditoria.registrar("REENVIAR_ALERTA #" + protocolo + " alerta " + alertaId); // RF013: só protocolo e id, sem dado pessoal
        return alerta;
    }

    /** RF009 regra 4 / RF011 regras 2 e 3: só se a ocorrência já teve alerta; chamado dentro da transação da verificação. */
    void avisarAlteracao(Ocorrencia ocorrencia, String resumo, String orientacoes, Representante representante) {
        if (!repository.existsByOcorrencia_IdAndTipo(ocorrencia.getId(), TipoComunicado.ALERTA)) { return; }
        avisar(Comunicado.alteracao(ocorrencia, resumo, orientacoes,
                repository.destinatariosDosAlertas(ocorrencia.getId()), representante, clock.instant()));
    }

    /** RF012 regras 2 e 3: mesmo critério do aviso de alteração; sem alerta anterior, nenhum aviso (e o resumo não é exigido). */
    void avisarEncerramento(Ocorrencia ocorrencia, String resumo, String orientacoes, Representante representante) {
        if (!repository.existsByOcorrencia_IdAndTipo(ocorrencia.getId(), TipoComunicado.ALERTA)) { return; }
        avisar(Comunicado.encerramento(ocorrencia, resumo, orientacoes,
                repository.destinatariosDosAlertas(ocorrencia.getId()), representante, clock.instant()));
    }

    private void avisar(Comunicado aviso) {
        aviso.distribuir(envio);
        repository.save(aviso);
    }

    public List<Comunicado> daOcorrencia(UUID protocolo) {
        usuarios.exigirRepresentante();
        return repository.daOcorrencia(protocolo);
    }

    /** Pesquisa: o morador vê no aplicativo os comunicados dos quais é destinatário. */
    public List<AlertaDoMorador> doMorador() {
        return repository.doMorador(usuarios.exigirMorador().id());
    }

    // Mesma proteção do OcorrenciaService contra desativação concorrente do representante.
    private Representante representanteAtual() {
        var ator = usuarios.exigirRepresentante();
        var representante = representantes.buscarPorIdComBloqueio(ator.id())
                .orElseThrow(() -> new AccessDeniedException("Entre novamente na sua conta."));
        em.refresh(representante);
        usuarios.exigirRepresentante();
        return representante;
    }
}
