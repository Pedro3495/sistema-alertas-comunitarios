package br.edu.alertas.ocorrencias;

import br.edu.alertas.moradores.Morador;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ComunicadoRepository extends JpaRepository<Comunicado, Long> {
    @Query("select distinct c from Comunicado c left join fetch c.destinatarios d left join fetch d.morador where c.ocorrencia.protocolo = :protocolo order by c.emitidoEm desc, c.id desc")
    List<Comunicado> daOcorrencia(UUID protocolo);

    Optional<Comunicado> findByIdAndOcorrencia_Protocolo(Long id, UUID protocolo);

    boolean existsByOcorrencia_IdAndTipo(Long ocorrenciaId, TipoComunicado tipo);

    // RF010 regra 2: pessoas cadastradas na área (pela residência atual); RF001: só contas ativas.
    @Query("select m from Morador m where m.ativa = true and m.residencia.area.id = :areaId order by m.id")
    List<Morador> moradoresAtivosDaArea(Long areaId);

    // RF010 regra 7: união dos destinatários dos alertas anteriores, mesmo quem mudou de área, sem contas desativadas.
    @Query("""
            select distinct d.morador from DestinatarioComunicado d
            where d.comunicado.ocorrencia.id = :ocorrenciaId and d.comunicado.tipo = br.edu.alertas.ocorrencias.TipoComunicado.ALERTA
              and d.morador.ativa = true""")
    List<Morador> destinatariosDosAlertas(Long ocorrenciaId);

    @Query("""
            select new br.edu.alertas.ocorrencias.AlertaDoMorador(c.ocorrencia.protocolo, c.tipo, c.titulo, c.mensagem, c.orientacoes, c.emitidoEm)
            from DestinatarioComunicado d join d.comunicado c
            where d.morador.id = :moradorId order by c.emitidoEm desc, c.id desc""")
    List<AlertaDoMorador> doMorador(Long moradorId);
}
