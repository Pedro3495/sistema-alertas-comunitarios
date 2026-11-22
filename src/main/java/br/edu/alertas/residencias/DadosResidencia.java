package br.edu.alertas.residencias;

public record DadosResidencia(String ruaLogradouro, String numero, String bairro, String municipio,
        String complemento, String pontoReferencia, Long areaId) { }
