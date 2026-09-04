package com.ia.project.dynamicstudyplanner.api.exception;

/**
 * Não há trabalho com o identificador pedido.
 *
 * <p>Duas causas produzem o mesmo resultado, e a resposta não distingue: o identificador nunca
 * existiu, ou o registro já expirou. Distinguir vazaria a informação de que um identificador
 * <i>existiu</i>, e não muda nada para quem chama — nos dois casos o caminho é reenviar o pedido.
 */
public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String id) {
        super("No optimization job with id " + id);
    }
}
