/*
package com.example.flow


import co.paralleluniverse.fibers.Suspendable
import com.example.contract.IOUContract
import com.example.flow.service.ExchangeRateFinder
import com.example.state.IOUState
import net.corda.core.contracts.Command
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap
import java.security.InvalidParameterException

object CollectOracleSignatureFlow {
    @InitiatingFlow
    class Initiator(val filteredTransaction: FilteredTransaction,
                    val oracleParty: Party) : FlowLogic<TransactionSignature>() {
        @Suspendable
        override fun call(): TransactionSignature {
            val oraclePartySession = initiateFlow(oracleParty)
            // Notarise and record the transaction in both parties' vaults.
            return oraclePartySession.sendAndReceive<TransactionSignature>(filteredTransaction).unwrap { it -> it }
        }
    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val filteredTransaction = otherPartySession.receive<FilteredTransaction>().unwrap {it}
            if (checkForValidity(filteredTransaction)) {
                otherPartySession.send(serviceHub.createSignature(filteredTransaction , serviceHub.myInfo.legalIdentities.first().owningKey))
            } else {
                throw InvalidParameterException("Transaction: ${filteredTransaction.id} is invalid")
            }
        }

        private  fun checkForValidity(filteredTransaction : FilteredTransaction):Boolean{
            //commands() ,state
            return filteredTransaction.checkWithFun { element->
                when {
                    (element is Command<*> && element.value is IOUContract.Commands.Create) -> {
                        val command = element.value as IOUContract.Commands.Create
                        require(serviceHub.myInfo.legalIdentities.first().owningKey in element.signers) //true
                        validateExchangeRate(command.iouValue, command.currency, command.exchangeRate) // false
                    }
                    else -> false
                }
            }
        }

        private fun validateExchangeRate(iouValue: Double, currency: String, exchangeRate: Double): Boolean {
            val exchangeRateFromExternalSource = serviceHub.cordaService(ExchangeRateFinder::class.java).exchangeRate("INR", currency)
            return exchangeRate == exchangeRateFromExternalSource
        }
    }
}
*/
