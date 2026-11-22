package br.edu.alertas.ocorrencias;

import br.edu.alertas.auditoria.AuditoriaService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;

// RF006 regras 1, 4, 5, 7 e 10 para o morador; RF013 regra 1 (auditoria do registro).
@SpringBootTest
class OcorrenciaMoradorServiceTests extends OcorrenciasFixtures {
    @MockitoSpyBean AuditoriaService auditoria; // mesma configuração de contexto de OcorrenciaServiceTests: reaproveita o pool

    @Test
    void moradorRegistraNaAreaDaResidenciaIgnorandoAreaEnviadaEReplayNaoDuplica() {
        var acesso = entrarComoMorador(novoMorador(area));
        long outra = novaArea();
        var envio = UUID.randomUUID();
        var dados = new DadosOcorrencia(envio, TipoOcorrencia.OBRA, null, "Descrição fictícia", "Esquina fictícia", outra, List.of(imagem("png")));

        var salva = service.registrarPorMorador(dados);
        assertThat(salva.getAreaId()).isEqualTo(area);
        assertThat(salva.getMoradorId()).isEqualTo(acesso.id());
        assertThat(salva.getRepresentanteId()).isNull();
        assertThat(salva.getSituacao()).isEqualTo("Em verificação");
        assertThat(service.registrarPorMorador(dados).getProtocolo()).isEqualTo(salva.getProtocolo());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ocorrencias WHERE morador_id = ?", Long.class, acesso.id())).isEqualTo(1);
        assertThat(acoesDoMorador(acesso.id())).containsExactly("REGISTRAR_OCORRENCIA #" + salva.getProtocolo());
        assertThat(service.buscarDoMorador(salva.getProtocolo()).getFotos()).hasSize(1);
    }

    @Test
    void mudancaDeResidenciaUsaNovaAreaSemTransferirAntigas() {
        var acesso = entrarComoMorador(novoMorador(area));
        var antiga = service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.DESASTRE, null, "Antes", "Local", null, List.of(imagem("png"))));
        long novaArea = novaArea();
        long novaResidencia = jdbc.queryForObject("INSERT INTO residencias(rua_logradouro, numero, bairro, municipio, area_id) VALUES ('Rua Nova', '2', 'Bairro', 'Município', ?) RETURNING id", Long.class, novaArea);
        residencias.add(novaResidencia);
        jdbc.update("UPDATE moradores SET residencia_id = ? WHERE id = ?", novaResidencia, acesso.id());

        var nova = service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.DESASTRE, null, "Depois", "Local", null, List.of(imagem("png"))));
        assertThat(nova.getAreaId()).isEqualTo(novaArea);
        assertThat(service.buscarDoMorador(antiga.getProtocolo()).getAreaId()).isEqualTo(area);
    }

    @Test
    void validacoesDoRegistroValemParaMorador() {
        entrarComoMorador(novoMorador(area));
        assertThatThrownBy(() -> service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OUTROS, " ", "D", "L", null, List.of(imagem("png")))))
                .isInstanceOfSatisfying(ErroOcorrencia.class, e -> assertThat(e.getCampo()).isEqualTo("especificacaoOutros"));
        assertThatThrownBy(() -> service.registrarPorMorador(new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", null, List.of())))
                .isInstanceOfSatisfying(ErroOcorrencia.class, e -> assertThat(e.getCampo()).isEqualTo("fotos"));
    }

    @Test
    void perfisNaoSeMisturamEMoradorNaoVeRegistroDeOutraArea() {
        var deRepresentante = service.registrar(dados(UUID.randomUUID()));
        var semResidenciaPropria = new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", area, List.of(imagem("png")));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.registrarPorMorador(semResidenciaPropria));

        String cpfOutro = novoMorador(novaArea()); // outra área: RF008 regra 1 libera só a própria área
        entrarComoMorador(novoMorador(novaArea()));
        var propria = service.registrarPorMorador(semResidenciaPropria);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.registrar(dados(UUID.randomUUID())));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.buscar(propria.getProtocolo()));
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> auditoria.pesquisar(null, null, null, null));
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.buscarDoMorador(deRepresentante.getProtocolo()));

        entrarComoMorador(cpfOutro);
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.buscarDoMorador(propria.getProtocolo()));
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.fotoDoMorador(propria.getProtocolo(), 1));
    }

    @Test
    void contaDesativadaOuComTrocaPendenteNaoRegistra() {
        var acesso = entrarComoMorador(novoMorador(area));
        var dados = new DadosOcorrencia(UUID.randomUUID(), TipoOcorrencia.OBRA, null, "D", "L", null, List.of(imagem("png")));
        jdbc.update("UPDATE moradores SET ativa = false WHERE id = ?", acesso.id());
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.registrarPorMorador(dados));
        jdbc.update("UPDATE moradores SET ativa = true, troca_senha_obrigatoria = true WHERE id = ?", acesso.id());
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> service.registrarPorMorador(dados));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ocorrencias WHERE morador_id = ?", Long.class, acesso.id())).isZero();
    }
}
