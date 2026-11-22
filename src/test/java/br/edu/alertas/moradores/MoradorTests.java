package br.edu.alertas.moradores;

import br.edu.alertas.areas.AreaRisco;
import br.edu.alertas.residencias.DadosResidencia;
import br.edu.alertas.residencias.Residencia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class MoradorTests {
    private final Residencia residencia = new Residencia(new DadosResidencia("Rua fictícia", "s/n", "Bairro",
            "Município", null, null, 1L), new AreaRisco("Área fictícia", "Limites"));

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"11111111111", "00000000000", "12345678900", "1234567890", "123456789012",
            "529.982.247-25", "52998224724", "52998224715", "５２９９８２２４７２５", " 52998224725"})
    void rejeitaCpfInvalido(String cpf) {
        assertThatIllegalArgumentException().isThrownBy(() -> novo("Pessoa fictícia", cpf, "81900000001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "01234567890"})
    void aceitaCpfComVerificadoresValidosIncluindoZeroInicial(String cpf) {
        assertThat(novo("Pessoa fictícia", cpf, "81900000001").getCpf()).isEqualTo(cpf);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"8190000000", "5581900000001", "(81)90000-0001", "８１９０００００００１", "8190000000x"})
    void rejeitaCelularForaDeOnzeDigitos(String celular) {
        assertThatIllegalArgumentException().isThrownBy(() -> novo("Pessoa fictícia", "52998224725", celular));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n"})
    void exigeNome(String nome) {
        assertThatIllegalArgumentException().isThrownBy(() -> novo(nome, "52998224725", "81900000001"));
    }

    @Test
    void exigeResidenciaECadaEscolhaDeCanal() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Morador(dados(null, PreferenciaAlertas.AMBOS), residencia));
        assertThatIllegalArgumentException().isThrownBy(() -> new Morador(dados(CanalCredenciais.SMS, null), residencia));
        assertThatIllegalArgumentException().isThrownBy(() -> new Morador(dados(CanalCredenciais.SMS, PreferenciaAlertas.AMBOS), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new Morador(null, residencia));
    }

    @Test
    void alteracaoInvalidaNaoAplicaParteDosCampos() {
        var morador = novo("Nome original", "52998224725", "81900000001");
        assertThatIllegalArgumentException().isThrownBy(() -> morador.alterar("Nome corrigido", "11144477735", "x", residencia));
        assertThat(morador.getNomeCompleto()).isEqualTo("Nome original");
        assertThat(morador.getCpf()).isEqualTo("52998224725");
        assertThat(morador.getCelular()).isEqualTo("81900000001");
    }

    @Test
    void escolhasSaoSeparadasENaoLimitamTextos() {
        for (var canal : CanalCredenciais.values()) {
            for (var preferencia : PreferenciaAlertas.values()) {
                var morador = new Morador(dados(canal, preferencia), residencia);
                assertThat(morador.getCanalCredenciais()).isEqualTo(canal);
                assertThat(morador.getPreferenciaAlertas()).isEqualTo(preferencia);
            }
        }
        String nome = "Nome fictício ".repeat(100);
        assertThat(novo(nome, "52998224725", "81900000001").getNomeCompleto()).isEqualTo(nome);
    }

    private DadosMorador dados(CanalCredenciais canal, PreferenciaAlertas preferencia) {
        return new DadosMorador("Pessoa fictícia", "52998224725", "81900000001", 1L, canal, preferencia);
    }

    private Morador novo(String nome, String cpf, String celular) {
        return new Morador(new DadosMorador(nome, cpf, celular, 1L, CanalCredenciais.SMS, PreferenciaAlertas.AMBOS), residencia);
    }
}
