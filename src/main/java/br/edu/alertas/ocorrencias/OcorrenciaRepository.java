package br.edu.alertas.ocorrencias;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {
    Optional<Ocorrencia> findByProtocolo(UUID protocolo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Ocorrencia o where o.protocolo = :protocolo")
    Optional<Ocorrencia> buscarComBloqueio(UUID protocolo);

    @Query("select v from VerificacaoOcorrencia v where v.ocorrencia.protocolo = :protocolo order by v.verificadaEm desc, v.id desc")
    List<VerificacaoOcorrencia> verificacoes(UUID protocolo);

    @Query("select a from AlteracaoOcorrencia a where a.ocorrencia.protocolo = :protocolo order by a.alteradaEm desc, a.id desc")
    List<AlteracaoOcorrencia> alteracoes(UUID protocolo);
    Optional<Ocorrencia> findByRepresentante_IdAndEnvioId(Long representanteId, UUID envioId);
    Optional<Ocorrencia> findByMorador_IdAndEnvioId(Long moradorId, UUID envioId);

    // RF008: moradorId/areaMorador nulos = representante (todas as áreas); senão próprios relatos ou área atual da residência.
    @Query("""
            select new br.edu.alertas.ocorrencias.ResumoOcorrencia(o.protocolo, o.registradaEm, o.tipo, o.situacao, o.area.nome, o.localizacao)
            from Ocorrencia o
            where (:protocolo is null or o.protocolo = :protocolo)
              and (:tipo is null or o.tipo = :tipo)
              and (:situacao is null or o.situacao = :situacao)
              and (:areaId is null or o.area.id = :areaId)
              and (:moradorId is null or o.morador.id = :moradorId or o.area.id = :areaMorador)
            order by o.registradaEm desc, o.id desc""")
    List<ResumoOcorrencia> pesquisar(UUID protocolo, TipoOcorrencia tipo, SituacaoOcorrencia situacao, Long areaId,
            Long moradorId, Long areaMorador);

    @Query("select o from Ocorrencia o where o.protocolo = :protocolo and (o.morador.id = :moradorId or o.area.id = :areaMorador)")
    Optional<Ocorrencia> buscarVisivelAoMorador(UUID protocolo, Long moradorId, Long areaMorador);
}
