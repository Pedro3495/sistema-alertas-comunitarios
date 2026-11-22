const { test } = require('node:test');
const assert = require('node:assert/strict');
const {
  LIMITE_FOTO, abrirBanco, guardar, recuperar, removerConfirmado,
  tipoImagem, prepararFotos, reduzirFoto, validar, iniciarFormulario, iniciarDetalhe
} = require('../../main/resources/static/js/ocorrencias.js');

const imediato = () => new Promise(resolve => setImmediate(resolve));
const jpeg = (tamanho = 24) => new Blob([new Uint8Array([255, 216, 255]), new Uint8Array(tamanho - 3)], { type: 'image/jpeg' });
const dadosValidos = { tipo: 'OBRA', especificacaoOutros: '', descricao: 'Relato de teste', localizacao: 'Ponto de referência', areaId: '1' };

// Dublê da API transacional: resolve somente no commit e clona Blobs como o IndexedDB.
function armazenamento() {
  const registros = new Map();
  const banco = {
    falhar: false,
    close() {},
    transaction() {
      const tx = { error: new Error('Quota excedida') };
      let alteracoes = [];
      const store = {
        put(valor) { alteracoes.push(() => registros.set(valor.chave, structuredClone(valor))); },
        delete(chave) { alteracoes.push(() => registros.delete(chave)); },
        get(chave) {
          const pedido = {};
          setImmediate(() => {
            pedido.result = structuredClone(registros.get(chave));
            pedido.onsuccess?.();
          });
          return pedido;
        }
      };
      tx.objectStore = () => store;
      setImmediate(() => setImmediate(() => {
        if (banco.falhar) tx.onabort?.();
        else { alteracoes.forEach(acao => acao()); tx.oncomplete?.(); }
      }));
      return tx;
    }
  };
  const indexedDB = {
    open() {
      const pedido = { result: banco };
      setImmediate(() => pedido.onsuccess());
      return pedido;
    }
  };
  return { banco, indexedDB, registros };
}

function pagina(chave, armazenamentoLocal, fetch) {
  const listeners = new Map();
  const ids = ['rascunho-status', 'envio-erro', 'dados-ocorrencia', 'enviar-ocorrencia', 'envioId', 'tipo',
    'campo-outros', 'especificacaoOutros', 'descricao', 'localizacao', 'areaId', 'fotos', 'fotos-selecionadas', 'revisao',
    'erro-tipo', 'erro-especificacaoOutros', 'erro-descricao', 'erro-localizacao', 'erro-areaId', 'erro-fotos', 'erro-revisao', 'protocolo'];
  const elementos = Object.fromEntries(ids.map(id => [id, {
    id, value: '', checked: false, textContent: '', hidden: false,
    options: [{ value: '' }, { value: '1' }],
    setAttribute(nome, valor) { this[nome] = valor; },
    replaceChildren(...filhos) { this.children = filhos; },
    append(filho) { this.options.push(filho); },
    focus() { this.focado = true; },
    addEventListener(nome, listener) { listeners.set(id + ':' + nome, listener); }
  }]));
  const form = {
    dataset: { rascunhoChave: chave }, action: 'https://exemplo.invalid/ocorrencias',
    addEventListener(nome, listener) { listeners.set('form:' + nome, listener); }
  };
  global.document = { getElementById: id => elementos[id], createElement: () => ({}) };
  let navegacao;
  global.window = {
    indexedDB: armazenamentoLocal.indexedDB,
    crypto: { randomUUID: () => '11111111-2222-4333-8444-555555555555' },
    fetch,
    location: { assign(url) { navegacao = url; } },
    requestAnimationFrame: callback => setImmediate(callback)
  };
  global.FormData = class {
    constructor() {
      this.campos = { _csrf: ['token-somente-na-rede'] };
      for (const campo of ['envioId', ...Object.keys(dadosValidos)]) {
        if (!elementos[campo].disabled) this.campos[campo] = [elementos[campo].value];
      }
    }
    delete(campo) { delete this.campos[campo]; }
    set(campo, valor) { this.campos[campo] = [valor]; }
    append(campo, valor) { (this.campos[campo] ||= []).push(valor); }
  };
  return { form, elementos, listeners, navegacao: () => navegacao };
}

async function preencher(p) {
  Object.entries(dadosValidos).forEach(([id, valor]) => { p.elementos[id].value = valor; });
  await p.listeners.get('form:input')({ target: p.elementos.descricao });
  p.elementos.fotos.files = [jpeg()];
  await p.listeners.get('fotos:change')({ target: p.elementos.fotos });
  p.elementos.revisao.checked = true;
}

const enviar = p => p.listeners.get('form:submit')({ preventDefault() {} });
const resposta = (status, body) => ({ status, ok: status === 200, headers: { get: () => 'application/json' }, json: async () => body });

test('formatos aprovados são identificados pelos bytes; MIME e extensão não autorizam arquivo', async () => {
  assert.equal(await tipoImagem(jpeg()), 'image/jpeg');
  assert.equal(await tipoImagem(new Blob([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])])), 'image/png');
  for (const marca of ['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'hevm', 'hevs', 'mif1', 'msf1']) {
    assert.match(await tipoImagem(new Blob([new Uint8Array([0, 0, 0, 20]), 'ftyp', marca, new Uint8Array(8)])), /^image\/hei/);
  }
  await assert.rejects(tipoImagem(new Blob(['arquivo inválido'], { type: 'image/jpeg' })), /Formato não aceito/);
  await assert.rejects(prepararFotos([]), /uma a quatro/);
  await assert.rejects(prepararFotos(Array(5).fill(jpeg())), /uma a quatro/);
});

function ftyp(principal, compativeis = [], { estendido = false, versao = '\0\0\0\0' } = {}) {
  const cabecalho = estendido ? 16 : 8;
  const bytes = new Uint8Array(cabecalho + 8 + compativeis.length * 4);
  const view = new DataView(bytes.buffer);
  view.setUint32(0, estendido ? 1 : bytes.length);
  bytes.set(Buffer.from('ftyp'), 4);
  if (estendido) view.setBigUint64(8, BigInt(bytes.length));
  bytes.set(Buffer.from(principal + versao + compativeis.join('')), cabecalho);
  return bytes;
}

test('HEIC/HEIF reconhece todas as marcas e offsets ftyp normais e estendidos', async () => {
  for (const estendido of [false, true]) {
    for (const marca of ['heic', 'heix', 'hevc', 'hevx', 'heim', 'heis', 'hevm', 'hevs']) {
      assert.equal(await tipoImagem(new Blob([ftyp(marca, [], { estendido })])), 'image/heic');
      assert.equal(await tipoImagem(new Blob([ftyp('mif1', [marca], { estendido })])), 'image/heic');
    }
    for (const marca of ['mif1', 'msf1']) {
      assert.equal(await tipoImagem(new Blob([ftyp(marca, [], { estendido })])), 'image/heif');
    }
    assert.equal(await tipoImagem(new Blob([ftyp('mif1', [], { estendido, versao: 'heic' })])), 'image/heif');
    await assert.rejects(tipoImagem(new Blob([ftyp('xxxx', [], { estendido, versao: 'mif1' })])), /Formato não aceito/);
    // Marca no fim de uma caixa maior que o antigo prefixo fixo de 256 bytes.
    assert.equal(await tipoImagem(new Blob([ftyp('xxxx', [...Array(70).fill('xxxx'), 'heim'], { estendido })])), 'image/heic');
  }
});

test('AVIF/AVIS recusado em qualquer marca, mesmo compatível com mif1 ou heic', async () => {
  for (const estendido of [false, true]) {
    for (const marca of ['avif', 'avis']) {
      for (const [principal, compativeis] of [[marca, ['mif1']], ['mif1', [marca]], ['heic', [marca]], ['mif1', ['heic', marca]], ['mif1', [...Array(70).fill('xxxx'), marca]]]) {
        await assert.rejects(tipoImagem(new Blob([ftyp(principal, compativeis, { estendido })], { type: 'image/heic' })), /Formato não aceito/);
      }
    }
  }
});

test('ftyp recusa comprimentos inválidos ou truncados e ignora marcas fora da caixa', async () => {
  for (const estendido of [false, true]) {
    const valido = ftyp('heic', ['mif1'], { estendido });
    for (let tamanho = 0; tamanho < valido.length; tamanho++) {
      await assert.rejects(tipoImagem(new Blob([valido.slice(0, tamanho)])), /Formato não aceito/);
    }
    const desalinhado = ftyp('heic', [], { estendido });
    const view = new DataView(desalinhado.buffer);
    if (estendido) view.setBigUint64(8, BigInt(desalinhado.length - 1));
    else view.setUint32(0, desalinhado.length - 1);
    await assert.rejects(tipoImagem(new Blob([desalinhado])), /Formato não aceito/);
    await assert.rejects(tipoImagem(new Blob([ftyp('xxxx', [], { estendido }), 'heic'])), /Formato não aceito/);
    assert.equal(await tipoImagem(new Blob([ftyp('heic', [], { estendido }), 'avif'])), 'image/heic');
  }
  const enorme = ftyp('heic', [], { estendido: true });
  new DataView(enorme.buffer).setBigUint64(8, 2n ** 63n);
  await assert.rejects(tipoImagem(new Blob([enorme])), /Formato não aceito/);
  const ateFim = ftyp('heic');
  new DataView(ateFim.buffer).setUint32(0, 0);
  assert.equal(await tipoImagem(new Blob([ateFim])), 'image/heic');
});

test('redução automática acima de 5 MB, sem reduzir foto no limite; falha recusa arquivo', async () => {
  let reducoes = 0;
  const fotos = await prepararFotos([jpeg(LIMITE_FOTO), jpeg(LIMITE_FOTO + 1)], async () => { reducoes++; return jpeg(); });
  assert.equal(reducoes, 1);
  assert.equal(fotos[0].blob.size, LIMITE_FOTO);
  assert.equal(fotos[1].blob.size, 24);
  await assert.rejects(prepararFotos([jpeg(LIMITE_FOTO + 1)], async () => jpeg(LIMITE_FOTO + 2)), /redução falhou/);
  await assert.rejects(prepararFotos([jpeg(LIMITE_FOTO + 1)], async () => { throw new Error('decode falhou'); }), /decode falhou/);
});

test('canvas itera qualidade e dimensões; decode, canvas e toBlob falhos recusam a foto', async () => {
  const qualidades = [];
  const dimensoes = [];
  const canvas = {
    getContext: () => ({ fillRect() {}, drawImage() {} }),
    toBlob(callback, tipo, qualidade) {
      qualidades.push(qualidade);
      dimensoes.push(this.width);
      callback(jpeg(this.width > 75 ? LIMITE_FOTO + 1 : 24));
    }
  };
  assert.equal((await reduzirFoto(jpeg(), async () => ({ naturalWidth: 100, naturalHeight: 80 }), () => canvas)).size, 24);
  assert.deepEqual(qualidades, [0.9, 0.75, 0.6, 0.45, 0.9]);
  assert.deepEqual(dimensoes, [100, 100, 100, 100, 75]);
  await assert.rejects(reduzirFoto(jpeg(), async () => { throw new Error(); }), /Selecione outra foto/);
  await assert.rejects(reduzirFoto(jpeg(), async () => ({ naturalWidth: 1, naturalHeight: 1 }), () => ({ getContext: () => null })), /Selecione outra foto/);
  await assert.rejects(reduzirFoto(jpeg(), async () => ({ naturalWidth: 1, naturalHeight: 1 }), () => ({ ...canvas, toBlob: callback => callback(null) })), /Selecione outra foto/);
});

test('campos obrigatórios e Outros separado, sem limites de texto adicionais', () => {
  assert.deepEqual(validar({ ...dadosValidos, descricao: 'x'.repeat(100000) }, [jpeg()]), {});
  assert.equal(validar({ ...dadosValidos, tipo: 'OUTROS' }, [jpeg()]).especificacaoOutros, 'Especifique o outro tipo de ocorrência.');
  assert.deepEqual(Object.keys(validar({ tipo: '', especificacaoOutros: '', descricao: ' ', localizacao: '', areaId: '' }, [])), ['tipo', 'descricao', 'localizacao', 'areaId', 'fotos']);
});

test('rascunho clonado com Blobs persiste em nova conexão; contas e envioId isolam remoção', async () => {
  const a = armazenamento();
  const banco = await abrirBanco(a.indexedDB);
  await guardar(banco, { chave: 'REPRESENTANTE:1', envioId: 'a', fotos: [jpeg()], tentativaIncerta: true });
  await guardar(banco, { chave: 'REPRESENTANTE:2', envioId: 'a', fotos: [jpeg()], tentativaIncerta: true });
  const recarregado = await recuperar(await abrirBanco(a.indexedDB), 'REPRESENTANTE:1');
  assert.equal(await tipoImagem(recarregado.fotos[0]), 'image/jpeg');
  assert.equal(await removerConfirmado(banco, 'REPRESENTANTE:1', 'outro-envio'), false);
  assert.ok(await recuperar(banco, 'REPRESENTANTE:1'));
  assert.equal(await removerConfirmado(banco, 'REPRESENTANTE:1', 'a'), true);
  assert.equal(await recuperar(banco, 'REPRESENTANTE:1'), undefined);
  assert.ok(await recuperar(banco, 'REPRESENTANTE:2'));
  banco.falhar = true;
  await assert.rejects(guardar(banco, { chave: 'REPRESENTANTE:3' }));
  assert.equal(a.registros.has('REPRESENTANTE:3'), false);
});

test('rede falha antes/depois do commit: retry manual mantém envioId e fotos, JSON não remove rascunho', async () => {
  const a = armazenamento();
  let tentativas = 0;
  const requests = [];
  const protocolo = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
  const fetch = async (_url, request) => {
    requests.push(request);
    assert.ok(a.registros.get('REPRESENTANTE:1').fotos[0].blob instanceof Blob);
    tentativas++;
    if (tentativas <= 2) throw new TypeError(tentativas === 1 ? 'offline antes do commit' : 'resposta perdida após commit');
    return resposta(200, { protocolo, detalheUrl: '/ocorrencias/' + protocolo });
  };
  let p = pagina('REPRESENTANTE:1', a, fetch);
  await iniciarFormulario(p.form);
  await preencher(p);
  assert.equal(tentativas, 0);
  await enviar(p);
  assert.match(p.elementos['envio-erro'].textContent, /conexão falhou/);
  assert.doesNotMatch(p.elementos['rascunho-status'].textContent, /Enviando ocorrência/);
  assert.match(p.elementos['rascunho-status'].textContent, /não foi concluído.*Revise.*manualmente/);
  assert.equal(p.elementos.revisao.checked, false);
  const id = a.registros.get('REPRESENTANTE:1').envioId;
  p = pagina('REPRESENTANTE:1', a, fetch);
  await iniciarFormulario(p.form);
  assert.equal(p.elementos.descricao.value, dadosValidos.descricao);
  assert.equal(p.elementos.revisao.checked, false);
  assert.equal(tentativas, 1);
  await enviar(p); // sem revisão, nenhuma rede
  assert.equal(tentativas, 1);
  p.elementos.revisao.checked = true;
  await enviar(p);
  assert.doesNotMatch(p.elementos['rascunho-status'].textContent, /Enviando ocorrência/);
  assert.match(p.elementos['rascunho-status'].textContent, /não foi concluído.*Revise.*manualmente/);
  p.elementos.revisao.checked = true;
  await enviar(p);
  assert.equal(tentativas, 3);
  assert.equal(p.navegacao(), '/ocorrencias/' + protocolo);
  assert.ok(a.registros.has('REPRESENTANTE:1'));
  for (const request of requests) {
    assert.deepEqual(request.body.campos.envioId, [id]);
    assert.deepEqual(request.body.campos._csrf, ['token-somente-na-rede']);
    assert.equal(request.body.campos.fotos.length, 1);
    assert.equal(request.credentials, 'same-origin');
    assert.equal(request.headers['Content-Type'], undefined);
  }
  assert.equal(JSON.stringify(a.registros.get('REPRESENTANTE:1')).includes('token-somente-na-rede'), false);
  p.elementos.protocolo.textContent = protocolo;
  await iniciarDetalhe({ dataset: { rascunhoChave: 'REPRESENTANTE:1', envioId: id } });
  assert.equal(a.registros.has('REPRESENTANTE:1'), false);
});

test('erro junto ao campo, sessão vencida e armazenamento cheio preservam dados; falha local impede rede', async () => {
  const a = armazenamento();
  let chamadas = 0;
  let retorno = resposta(400, { campo: 'localizacao', erro: 'Informe o local.' });
  const p = pagina('REPRESENTANTE:1', a, async () => { chamadas++; return retorno; });
  await iniciarFormulario(p.form);
  await preencher(p);
  await enviar(p);
  assert.equal(p.elementos['erro-localizacao'].textContent, 'Informe o local.');
  assert.equal(p.elementos.descricao.value, dadosValidos.descricao);
  assert.equal(a.registros.get('REPRESENTANTE:1').fotos.length, 1);
  retorno = resposta(403, {});
  p.elementos.revisao.checked = true;
  await enviar(p);
  assert.match(p.elementos['envio-erro'].textContent, /sessão venceu/);
  assert.doesNotMatch(p.elementos['rascunho-status'].textContent, /Enviando ocorrência/);
  assert.match(p.elementos['rascunho-status'].textContent, /não foi concluído.*Revise.*manualmente/);
  assert.equal(p.elementos.revisao.checked, false);
  assert.ok(a.registros.has('REPRESENTANTE:1'));
  a.banco.falhar = true;
  p.elementos.revisao.checked = true;
  await enviar(p);
  assert.equal(chamadas, 2);
  assert.match(p.elementos['envio-erro'].textContent, /envio não foi iniciado/);
});

test('detalhe sem protocolo apresentado, sem autoria ou de outro envio nunca remove rascunho', async () => {
  const a = armazenamento();
  await guardar(a.banco, { chave: 'REPRESENTANTE:1', envioId: 'a' });
  const p = pagina('REPRESENTANTE:1', a);
  await iniciarDetalhe({ dataset: { rascunhoChave: 'REPRESENTANTE:1', envioId: 'a' } });
  assert.ok(a.registros.has('REPRESENTANTE:1'));
  p.elementos.protocolo.textContent = 'protocolo apresentado';
  await iniciarDetalhe({ dataset: {} });
  await iniciarDetalhe({ dataset: { rascunhoChave: 'REPRESENTANTE:1', envioId: 'b' } });
  assert.ok(a.registros.has('REPRESENTANTE:1'));
  await imediato();
});

test('área removida permanece identificada no rascunho, exige outra seleção e não envia', async () => {
  const a = armazenamento();
  await guardar(a.banco, { chave: 'REPRESENTANTE:1', envioId: 'a', dados: { ...dadosValidos, areaId: '99' }, fotos: [{ nome: 'Foto', blob: jpeg() }] });
  let chamadas = 0;
  const p = pagina('REPRESENTANTE:1', a, async () => { chamadas++; });
  await iniciarFormulario(p.form);
  assert.equal(p.elementos.areaId.value, '99');
  assert.match(p.elementos['erro-areaId'].textContent, /não está mais disponível/);
  assert.ok(p.elementos.areaId.options.find(opcao => opcao.value === '99' && opcao.disabled));
  p.elementos.revisao.checked = true;
  await enviar(p);
  assert.equal(chamadas, 0);
  assert.equal(a.registros.get('REPRESENTANTE:1').dados.areaId, '99');
});

test('seleção inválida preserva fotos anteriores e bloqueia envio até nova seleção válida', async () => {
  const a = armazenamento();
  let chamadas = 0;
  const p = pagina('REPRESENTANTE:1', a, async () => { chamadas++; });
  await iniciarFormulario(p.form);
  await preencher(p);
  const antes = await a.registros.get('REPRESENTANTE:1').fotos[0].blob.arrayBuffer();
  p.elementos.fotos.files = [new Blob(['inválido'], { type: 'image/jpeg' })];
  await p.listeners.get('fotos:change')({ target: p.elementos.fotos });
  assert.match(p.elementos['erro-fotos'].textContent, /Formato não aceito/);
  assert.deepEqual(await a.registros.get('REPRESENTANTE:1').fotos[0].blob.arrayBuffer(), antes);
  p.elementos.revisao.checked = true;
  await enviar(p);
  assert.equal(chamadas, 0);
  p.elementos.fotos.files = [jpeg(40), jpeg(50)];
  await p.listeners.get('fotos:change')({ target: p.elementos.fotos });
  assert.deepEqual(a.registros.get('REPRESENTANTE:1').fotos.map(foto => foto.blob.size), [40, 50]);
  assert.equal(p.elementos.revisao.checked, false);
});

test('resposta perdida congela o conteúdo tentado até confirmação; 400 libera a correção', async () => {
  const a = armazenamento();
  const protocolo = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
  const corpos = [];
  let retorno = () => { throw new TypeError('resposta perdida após commit'); };
  const fetch = async (_url, request) => { corpos.push(request.body.campos); return retorno(); };
  let p = pagina('REPRESENTANTE:1', a, fetch);
  await iniciarFormulario(p.form);
  await preencher(p);
  await enviar(p);
  assert.equal(p.elementos.descricao.disabled, true);
  assert.equal(p.elementos.fotos.disabled, true);
  assert.equal(p.elementos.revisao.disabled, undefined);
  // Edição forçada durante a incerteza não altera o envio nem o rascunho.
  p.elementos.descricao.value = 'Correção que o servidor não receberia';
  await p.listeners.get('form:input')({ target: p.elementos.descricao });
  await p.listeners.get('fotos:change')({ target: { ...p.elementos.fotos, files: [jpeg(40)] } });
  assert.equal(p.elementos.descricao.value, dadosValidos.descricao);
  assert.equal(a.registros.get('REPRESENTANTE:1').dados.descricao, dadosValidos.descricao);
  assert.equal(a.registros.get('REPRESENTANTE:1').fotos[0].blob.size, 24);
  assert.equal(a.registros.get('REPRESENTANTE:1').tentativaIncerta, true);

  p = pagina('REPRESENTANTE:1', a, fetch);
  await iniciarFormulario(p.form);
  assert.equal(p.elementos.descricao.disabled, true);
  assert.match(p.elementos['rascunho-status'].textContent, /pode já ter sido registrada/);
  retorno = () => resposta(400, { campo: 'areaId', erro: 'Selecione uma área de risco disponível.' });
  p.elementos.revisao.checked = true;
  await enviar(p);
  assert.equal(p.elementos.descricao.disabled, false);
  assert.equal(a.registros.get('REPRESENTANTE:1').tentativaIncerta, false);
  p.elementos.descricao.value = 'Descrição corrigida';
  await p.listeners.get('form:input')({ target: p.elementos.descricao });
  for (let i = 0; i < 4; i++) await imediato();
  assert.equal(a.registros.get('REPRESENTANTE:1').dados.descricao, 'Descrição corrigida');

  retorno = () => resposta(200, { protocolo, detalheUrl: '/ocorrencias/' + protocolo });
  p.elementos.revisao.checked = true;
  await enviar(p);
  assert.deepEqual(corpos.map(c => c.descricao[0]), [dadosValidos.descricao, dadosValidos.descricao, 'Descrição corrigida']);
  assert.ok(corpos.every(c => c.envioId[0] === corpos[0].envioId[0]));
  assert.equal(p.navegacao(), '/ocorrencias/' + protocolo);
  p.elementos.protocolo.textContent = protocolo;
  await iniciarDetalhe({ dataset: { rascunhoChave: 'REPRESENTANTE:1', envioId: corpos[0].envioId[0] } });
  assert.equal(a.registros.has('REPRESENTANTE:1'), false);
});
