# nfe-ba-service

Microsserviço que **lê uma NFC-e no site da Secretaria da Fazenda da Bahia** e devolve os dados em JSON. Consumido pelo `supermercado-service`, que a partir daí monta a compra.

Substitui o serviço homônimo em PHP/Laravel, que fazia o mesmo apoiado na biblioteca `guilhermejr/busca-nfe-ba`.

## Stack

| Item | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Spring Cloud | 2025.1.3 |
| jsoup | 1.19.1 |

| Porta | Context path |
|---|---|
| 9011 | `/nfe-ba-service/` |

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/{nfe}` | busca a nota; `{nfe}` é o conteúdo do parâmetro `p=` do QR Code |

O `{nfe}` traz pipes (`29200835...|2|1|1|B75D96...`); quem chama precisa enviá-los percent-encoded (`%7C`), como o Feign já faz por padrão.

### Retorno

```json
{
  "url": "http://nfe.sefaz.ba.gov.br/servicos/nfce/qrcode.aspx?p=...",
  "retornou": true,
  "data": "08/08/2020 14:58:24",
  "total": "159,99",
  "cnpj": "35.133.777/0001-09",
  "ie": "162154257",
  "nome": "DINNI",
  "chaveDeAcesso": "2920 0835 1337 7700 0109 6500 1000 0001 2317 9663 3581",
  "informacoesComplementares": "",
  "produtos": [
    { "ean": "SEM GTIN", "nome": "ALPERCATA SOUL DEMOCRATA 015135 PRETO U", "qtd": "1,0000", "unidade": "PR", "valor": "159,99" }
  ]
}
```

Quando a SEFAZ não reconhece a nota, a resposta continua sendo `200` — o que muda é o corpo:

```json
{
  "url": "...",
  "retornou": false,
  "mensagem": "[QRCode v2.00]: Não foi possível obter informações sobre a NFC-e."
}
```

Cabe a quem chama olhar o `retornou` antes de usar o resto. É o que o `CompraService` do `supermercado-service` faz.

## Como a leitura funciona

O site da SEFAZ é ASP.NET WebForms: as abas da nota não têm URL própria, são postbacks do mesmo formulário. Para montar a resposta o serviço percorre três páginas, carregando o cookie de sessão entre elas:

1. abre a URL do QR Code;
2. clica em **Visualizar em Abas**;
3. dali clica em **Emitente** e em **Produtos / Serviços**.

O `NavegadorComponent` cuida dessa mecânica — reenviar os campos ocultos (`__VIEWSTATE` e companhia) mais o botão clicado. O `NFEService` extrai os campos das três páginas.

## Configuração

Como os demais serviços, não guarda configuração própria: busca tudo no arranque via `spring.config.import`, no Vault e no config-server. `VAULT_TOKEN` é obrigatório e não tem valor padrão.

| Propriedade | Padrão |
|---|---|
| `sistema.nfe-ba.url` | `http://nfe.sefaz.ba.gov.br/servicos/nfce/qrcode.aspx?p=` |
| `sistema.nfe-ba.timeout` | `30000` |
| `sistema.nfe-ba.userAgent` | um Chrome recente |

## Contato

Dúvidas e sugestões: falecom@guilhermejr.net
