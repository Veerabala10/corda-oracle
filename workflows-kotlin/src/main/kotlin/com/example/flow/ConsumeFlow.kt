package com.example.flow



import co.paralleluniverse.fibers.Suspendable
import com.example.contract.IOUContract
import com.example.flow.ExampleFlow.Acceptor
import com.example.flow.ExampleFlow.Initiator
import com.example.flow.service.ExchangeRateFinder
import com.example.state.IOUState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import java.security.InvalidParameterException
import java.util.*
import java.util.function.Predicate

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the IOU encapsulated
 * within an [IOUState].
 *
 * In our simple example, the [Acceptor] always accepts a valid IOU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
object ConsumeFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val otherParty: Party) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object GATHERING_SIGNATURE_FROM_ORACLE : Step("Gathering orcale's signature.")
            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    GATHERING_SIGNATURE_FROM_ORACLE,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val oracleParty = serviceHub.identityService.partiesFromName("Oracle", false).single()
            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val iouState = serviceHub.vaultService.queryBy(IOUState::class.java,QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)).states.last()
            println(iouState.state.encumbrance)
            val txCommand = Command(IOUContract.Commands.Consume(), listOf(otherParty.owningKey, serviceHub.myInfo.legalIdentities.first().owningKey))
            val txBuilder = TransactionBuilder(notary, UUID.randomUUID())
                    .addInputState(iouState)
                    .addCommand(txCommand)
            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)
            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counterparty, and receive it back with their signature.
            val otherPartySession = initiateFlow(otherParty)


            //Note CollectSignatureFlow is used not CollectSignaturesFlow.
            val transactionSignature: TransactionSignature = subFlow(CollectSignatureFlow(partSignedTx, otherPartySession, otherParty.owningKey)).single()
            val counterPartySignedTransaction = partSignedTx.withAdditionalSignature(transactionSignature)
            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(counterPartySignedTransaction, setOf(otherPartySession), FINALISING_TRANSACTION.childProgressTracker()))
        }
        private fun getExchangeRate(iouValue: Double, currency: String): Double {
            val exchangeRateFromExternalService = serviceHub.cordaService(ExchangeRateFinder::class.java).exchangeRate("INR",currency);
            return exchangeRateFromExternalService;
        }
    }


    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                }
            }
            val txId = subFlow(signTransactionFlow).id
            // filtered transaction and verification

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }

    }
}
