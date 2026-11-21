package br.edu.alertas.administracao;

import java.io.Console;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "alertas.admin")
public class ConfigurarAdministradorRunner implements ApplicationRunner {
    private final AdministradorService service;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    public ConfigurarAdministradorRunner(AdministradorService service, Environment environment,
            ConfigurableApplicationContext context) {
        this.service = service;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (context instanceof WebServerApplicationContext) {
            throw new IllegalStateException("Execute a configuração local sem servidor web, pelo script configurar-admin.sh.");
        }
        String acao = environment.getProperty("alertas.admin");
        validarAcao(acao);
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("A configuração exige um terminal interativo; senhas não são aceitas por argumentos ou entrada redirecionada.");
        }
        try {
            executar(acao, new TerminalAdministrador() {
                public String lerLogin() { return console.readLine("Login do administrador: "); }
                public char[] lerSenha() { return console.readPassword("Senha (mínimo 8 caracteres): "); }
                public void informar(String mensagem) { console.printf("%s%n", mensagem); }
            }, service);
        } finally {
            context.close();
        }
    }

    static void executar(String acao, TerminalAdministrador terminal, AdministradorService service) {
        validarAcao(acao);
        String login = terminal.lerLogin();
        char[] senha = terminal.lerSenha();
        if (senha == null) {
            throw new IllegalStateException("Leitura da senha cancelada; nenhuma alteração foi realizada.");
        }
        try {
            if (acao.equals("criar")) {
                service.criarPrimeiro(login, new String(senha));
                terminal.informar("Administrador configurado. Nenhuma senha foi exibida ou armazenada em texto original.");
            } else {
                service.redefinirSenhaLocal(login, new String(senha));
                terminal.informar("Senha redefinida localmente. As sessões administrativas anteriores foram revogadas.");
            }
        } finally {
            Arrays.fill(senha, '\0');
        }
    }

    private static void validarAcao(String acao) {
        if (!"criar".equals(acao) && !"recuperar".equals(acao)) {
            throw new IllegalArgumentException("Use alertas.admin=criar ou alertas.admin=recuperar.");
        }
    }

    interface TerminalAdministrador {
        String lerLogin();
        char[] lerSenha();
        void informar(String mensagem);
    }
}
