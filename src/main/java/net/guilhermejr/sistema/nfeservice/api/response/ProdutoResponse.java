package net.guilhermejr.sistema.nfeservice.api.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ProdutoResponse {

    private String ean;
    private String nome;
    private String qtd;
    private String unidade;
    private String valor;

}
