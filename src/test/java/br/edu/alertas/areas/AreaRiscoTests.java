package br.edu.alertas.areas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AreaRiscoTests {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void exigeNomePreenchido(String nome) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AreaRisco(nome, "Entorno da praça e ruas adjacentes."))
                .withMessage("Informe o nome da área de risco.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void exigeDescricaoPreenchida(String descricao) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AreaRisco("Centro", descricao))
                .withMessage("Informe a descrição da localização e dos limites da área de risco.");
    }

    @Test
    void preservaDefinicaoAnteriorQuandoAlteracaoForInvalida() {
        var area = new AreaRisco("Centro", "Entorno da praça.");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> area.alterarDefinicao("Centro ampliado", " "));

        assertThat(area.getNome()).isEqualTo("Centro");
        assertThat(area.getDescricaoLocalizacaoLimites()).isEqualTo("Entorno da praça.");
    }

    @Test
    void permiteAlterarNomeEDescricaoJuntos() {
        var area = new AreaRisco("Centro", "Entorno da praça.");

        area.alterarDefinicao("Centro ampliado", "Praça e Rua das Flores.");

        assertThat(area.getNome()).isEqualTo("Centro ampliado");
        assertThat(area.getDescricaoLocalizacaoLimites()).isEqualTo("Praça e Rua das Flores.");
    }
}
