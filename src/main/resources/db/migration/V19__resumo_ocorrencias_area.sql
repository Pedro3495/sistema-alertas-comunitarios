-- Resumo de ocorrências por área e situação, sem autor, descrição, fotos ou dados de moradores.
CREATE VIEW vw_resumo_ocorrencias_area AS
SELECT a.id AS area_id,
       a.nome AS area,
       o.situacao,
       COUNT(*) AS total,
       MAX(o.registrada_em) AS ultima_registrada_em
FROM ocorrencias o
JOIN areas_risco a ON a.id = o.area_id
GROUP BY a.id, a.nome, o.situacao;
