package br.edu.alertas.ocorrencias;

// RF008 regra 3. Confirmada e Não confirmada vêm do RF009 (V14); Encerrada vem do RF012.
public enum SituacaoOcorrencia {
    EM_VERIFICACAO("Em verificação"), CONFIRMADA("Confirmada"), NAO_CONFIRMADA("Não confirmada"), ENCERRADA("Encerrada");
    private final String rotulo;
    SituacaoOcorrencia(String rotulo) { this.rotulo = rotulo; }
    public String getRotulo() { return rotulo; }
}
