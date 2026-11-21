package br.edu.alertas.administracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

class ConfigurarAdministradorRunnerTests {
    @Test
    void criarLeCredencialDoTerminalLimpaBufferENaoExibeSegredo() {
        AdministradorService service = mock(AdministradorService.class);
        TerminalFicticio terminal = new TerminalFicticio();
        ConfigurarAdministradorRunner.executar("criar", terminal, service);
        verify(service).criarPrimeiro(" admin literal ", "senha-ficticia");
        verify(service, never()).redefinirSenhaLocal(anyString(), anyString());
        assertThat(terminal.senha).containsOnly('\0');
        assertThat(String.join(" ", terminal.mensagens)).doesNotContain("senha-ficticia");
    }

    @Test
    void recuperarUsaMesmaContaELimpaBufferInclusiveQuandoFalha() {
        AdministradorService service = mock(AdministradorService.class);
        TerminalFicticio terminal = new TerminalFicticio();
        doThrow(new IllegalStateException("Falha fictícia")).when(service)
                .redefinirSenhaLocal(" admin literal ", "senha-ficticia");
        assertThatThrownBy(() -> ConfigurarAdministradorRunner.executar("recuperar", terminal, service))
                .isInstanceOf(IllegalStateException.class);
        verify(service, never()).criarPrimeiro(anyString(), anyString());
        assertThat(terminal.senha).containsOnly('\0');
        assertThat(terminal.mensagens).isEmpty();
    }

    @Test
    void opcaoInvalidaFalhaAntesDeLerCredenciaisOuEscreverConta() {
        AdministradorService service = mock(AdministradorService.class);
        var terminal = mock(ConfigurarAdministradorRunner.TerminalAdministrador.class);
        assertThatThrownBy(() -> ConfigurarAdministradorRunner.executar("apagar", terminal, service))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service, terminal);
    }

    @Test
    void fimDaLeituraNaoAlteraConta() {
        AdministradorService service = mock(AdministradorService.class);
        var terminal = mock(ConfigurarAdministradorRunner.TerminalAdministrador.class);
        when(terminal.lerLogin()).thenReturn("admin");
        when(terminal.lerSenha()).thenReturn(null);
        assertThatThrownBy(() -> ConfigurarAdministradorRunner.executar("criar", terminal, service))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(service);
    }

    @Test
    void propriedadeInvalidaNoRunnerNaoLeConsoleNemAlteraConta() {
        AdministradorService service = mock(AdministradorService.class);
        Environment environment = mock(Environment.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(environment.getProperty("alertas.admin")).thenReturn("apagar");
        var runner = new ConfigurarAdministradorRunner(service, environment, context);
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }

    private static class TerminalFicticio implements ConfigurarAdministradorRunner.TerminalAdministrador {
        final char[] senha = "senha-ficticia".toCharArray();
        final List<String> mensagens = new ArrayList<>();
        public String lerLogin() { return " admin literal "; }
        public char[] lerSenha() { return senha; }
        public void informar(String mensagem) { mensagens.add(mensagem); }
    }
}
