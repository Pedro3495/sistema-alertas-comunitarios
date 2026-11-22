package br.edu.alertas.moradores;

public record DadosMorador(String nomeCompleto, String cpf, String celular, Long residenciaId,
        CanalCredenciais canalCredenciais, PreferenciaAlertas preferenciaAlertas) { }
