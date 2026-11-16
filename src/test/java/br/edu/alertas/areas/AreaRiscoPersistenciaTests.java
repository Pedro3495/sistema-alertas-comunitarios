package br.edu.alertas.areas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AreaRiscoPersistenciaTests {
    @Autowired
    AreaRiscoService service;

    @Autowired
    AreaRiscoRepository repository;

    @Autowired
    EntityManager entityManager;

    @Test
    void cadastraERecuperaCamposSemLimiteDeTamanhoInventado() {
        String nome = "Área " + "a".repeat(300);
        String descricao = "Localização e limites: " + "b".repeat(500);
        Long id = service.cadastrar(nome, descricao).getId();
        descarregarERecarregar();

        AreaRisco salva = repository.findById(id).orElseThrow();
        assertThat(salva.getNome()).isEqualTo(nome);
        assertThat(salva.getDescricaoLocalizacaoLimites()).isEqualTo(descricao);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void alteraRegistroMantendoIdentificadorSemCriarOutraArea() {
        Long id = service.cadastrar("Centro", "Praça.").getId();
        try {
            long quantidadeAnterior = repository.count();

            // Sem transação do teste: o serviço precisa confirmar a própria gravação.
            service.alterar(id, "Centro ampliado", "Praça e ruas adjacentes.");

            AreaRisco salva = repository.findById(id).orElseThrow();
            assertThat(salva.getNome()).isEqualTo("Centro ampliado");
            assertThat(salva.getDescricaoLocalizacaoLimites()).isEqualTo("Praça e ruas adjacentes.");
            assertThat(repository.count()).isEqualTo(quantidadeAnterior);
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    void naoExigeNomeExclusivoPoisEssaRegraNaoFoiAprovada() {
        Long primeiroId = service.cadastrar("Área com mesmo nome", "Limites da primeira.").getId();
        Long segundoId = service.cadastrar("Área com mesmo nome", "Limites da segunda.").getId();
        descarregarERecarregar();

        assertThat(segundoId).isNotEqualTo(primeiroId);
        assertThat(repository.findById(primeiroId)).isPresent();
        assertThat(repository.findById(segundoId)).isPresent();
    }

    @Test
    void pesquisaPorTrechoDoNomeIgnorandoMaiusculas() {
        Long esperado = service.cadastrar("Área de teste Centro Norte", "Limites norte.").getId();
        service.cadastrar("Área de teste Sul", "Limites sul.");
        descarregarERecarregar();

        assertThat(service.pesquisarPorNome("TESTE centro"))
                .extracting(AreaRisco::getId).containsExactly(esperado);
        assertThat(service.pesquisarPorNome("nome sem correspondência RF003")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_"})
    void trataCoringasComoTextoLiteralNaPesquisa(String caractere) {
        Long esperado = service.cadastrar("Área literal " + caractere, "Limites.").getId();
        service.cadastrar("Área literal comum", "Outros limites.");
        descarregarERecarregar();

        assertThat(service.pesquisarPorNome(caractere))
                .extracting(AreaRisco::getId).containsExactly(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void pesquisaSemNomeInformaCampoQuePrecisaSerPreenchido(String nome) {
        assertThatIllegalArgumentException().isThrownBy(() -> service.pesquisarPorNome(nome))
                .withMessage("Informe o nome da área de risco para pesquisar.");
    }

    @Test
    void alterarAreaInexistenteNaoCriaCadastro() {
        long quantidadeAnterior = repository.count();

        assertThatThrownBy(() -> service.alterar(Long.MAX_VALUE, "Inexistente", "Limites."))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Área de risco não encontrada.");
        assertThat(repository.count()).isEqualTo(quantidadeAnterior);
    }

    private void descarregarERecarregar() {
        entityManager.flush();
        entityManager.clear();
    }
}
