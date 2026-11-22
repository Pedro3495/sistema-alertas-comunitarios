package br.edu.alertas.ocorrencias;

import br.edu.alertas.auditoria.AuditoriaService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;

// RF008 regras 1, 3, 4, 5 e pesquisa, com PostgreSQL real.
@SpringBootTest
class OcorrenciaConsultaServiceTests extends OcorrenciasFixtures {
    @MockitoSpyBean AuditoriaService auditoria; // mesma configuração de contexto das outras classes: reaproveita o pool

    @Test
    void moradorVePropriosEAreaAtualEMantemAntigosAoMudarDeArea() {
        long outraArea = novaArea();
        var naArea = service.registrar(dados(UUID.randomUUID()));
        var naOutra = service.registrar(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", outraArea, List.of(imagem("png"))));
        String cpfVizinho = novoMorador(outraArea);
        String cpf = novoMorador(area);

        entrarComoMorador(cpfVizinho);
        var doVizinho = service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.DESASTRE, null, "Vizinho", "L", null, List.of(imagem("png"))));
        var acesso = entrarComoMorador(cpf);
        var proprio = service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.DESASTRE, null, "Meu", "L", null, List.of(imagem("png"))));

        assertThat(protocolosDoMorador()).contains(proprio.getProtocolo(), naArea.getProtocolo())
                .doesNotContain(naOutra.getProtocolo(), doVizinho.getProtocolo());
        assertThat(service.buscarDoMorador(naArea.getProtocolo()).getDescricao()).isEqualTo("Descrição fictícia");
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.buscarDoMorador(naOutra.getProtocolo()));

        // Regra 5: muda de área, mantém os próprios antigos; terceiros passam a ser os da nova área.
        long residenciaNova = jdbc.queryForObject("INSERT INTO residencias(rua_logradouro, numero, bairro, municipio, area_id) VALUES ('Rua Nova', '2', 'Bairro', 'Município', ?) RETURNING id", Long.class, outraArea);
        residencias.add(residenciaNova);
        jdbc.update("UPDATE moradores SET residencia_id = ? WHERE id = ?", residenciaNova, acesso.id());
        assertThat(protocolosDoMorador()).contains(proprio.getProtocolo(), naOutra.getProtocolo(), doVizinho.getProtocolo())
                .doesNotContain(naArea.getProtocolo());
        assertThat(service.buscarDoMorador(proprio.getProtocolo()).getAreaId()).isEqualTo(area);
        assertThat(service.fotoDoMorador(doVizinho.getProtocolo(), 1).getConteudo()).containsExactly(imagem("png"));
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.buscarDoMorador(naArea.getProtocolo()));
    }

    @Test
    void pesquisaPorProtocoloTipoSituacaoEAreaParaRepresentante() {
        long outraArea = novaArea();
        var desastre = service.registrar(dados(UUID.randomUUID()));
        var obra = service.registrar(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", area, List.of(imagem("png"))));
        var outra = service.registrar(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", outraArea, List.of(imagem("png"))));

        assertThat(protocolos(service.pesquisar(null, null, null, area))).containsExactlyInAnyOrder(desastre.getProtocolo(), obra.getProtocolo());
        assertThat(protocolos(service.pesquisar(null, TipoOcorrencia.OBRA, null, area))).containsExactly(obra.getProtocolo());
        assertThat(protocolos(service.pesquisar(null, null, null, null))).contains(desastre.getProtocolo(), obra.getProtocolo(), outra.getProtocolo());
        assertThat(protocolos(service.pesquisar(outra.getProtocolo(), null, null, null))).containsExactly(outra.getProtocolo());
        assertThat(protocolos(service.pesquisar(outra.getProtocolo(), null, null, area))).isEmpty();
        assertThat(protocolos(service.pesquisar(null, null, SituacaoOcorrencia.EM_VERIFICACAO, outraArea))).containsExactly(outra.getProtocolo());
        assertThat(service.pesquisar(null, null, SituacaoOcorrencia.ENCERRADA, area)).isEmpty();
        var resumo = service.pesquisar(obra.getProtocolo(), null, null, null).get(0);
        assertThat(resumo.situacao().getRotulo()).isEqualTo("Em verificação");
        assertThat(resumo.areaNome()).startsWith("Área RF006");

        entrarComoMorador(novoMorador(area));
        assertThat(protocolos(service.pesquisarDoMorador(null, TipoOcorrencia.OBRA, null))).contains(obra.getProtocolo())
                .doesNotContain(desastre.getProtocolo(), outra.getProtocolo());
        assertThat(protocolos(service.pesquisarDoMorador(desastre.getProtocolo(), null, null))).containsExactly(desastre.getProtocolo());
        assertThat(service.pesquisarDoMorador(outra.getProtocolo(), null, null)).isEmpty();
        assertThat(service.pesquisarDoMorador(null, null, SituacaoOcorrencia.CONFIRMADA)).isEmpty();
    }

    @Test
    void cadaPerfilUsaSoASuaConsulta() {
        entrarComoMorador(novoMorador(area));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.pesquisar(null, null, null, null));
        autenticar(ator, "REPRESENTANTE");
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.pesquisarDoMorador(null, null, null));
        SecurityContextHolder.clearContext();
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.pesquisar(null, null, null, null));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.pesquisarDoMorador(null, null, null));
    }

    private List<UUID> protocolosDoMorador() { return protocolos(service.pesquisarDoMorador(null, null, null)); }

    private static List<UUID> protocolos(List<ResumoOcorrencia> resumos) { return resumos.stream().map(ResumoOcorrencia::protocolo).toList(); }
}
