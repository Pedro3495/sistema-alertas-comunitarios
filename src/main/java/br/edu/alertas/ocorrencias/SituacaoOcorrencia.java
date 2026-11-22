package br.edu.alertas.ocorrencias;

// RF008 regra 3. Só EM_VERIFICACAO é gravada hoje; as demais virão do RF009/RF012 (e de uma migração que amplie o CHECK da V9).
public enum SituacaoOcorrencia {
    EM_VERIFICACAO("Em verificação"), CONFIRMADA("Confirmada"), NAO_CONFIRMADA("Não confirmada"), ENCERRADA("Encerrada");
    private final String rotulo;
    SituacaoOcorrencia(String rotulo) { this.rotulo = rotulo; }
    public String getRotulo() { return rotulo; }
}
