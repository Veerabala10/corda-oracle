package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.IOUContract
import com.example.contract.OracleBalanceContract
import com.example.flow.ExampleFlow.Responder
import com.example.flow.ExampleFlow.Initiator
import com.example.flow.service.ExchangeRateFinder
import com.example.state.IOUState
import com.example.state.OracleBalanceState
import net.corda.core.contracts.*
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step
import net.corda.core.utilities.unwrap
import java.security.InvalidParameterException
import java.time.Duration
import java.util.*
import java.util.function.Predicate
import javax.swing.plaf.nimbus.State
import kotlin.math.ceil
import kotlin.math.floor

/**
 * This flow allows two parties (the [Initiator] and the [Responder]) to come to an agreement about the IOU encapsulated
 * within an [IOUState].
 *
 * In our simple example, the [Responder] always accepts a valid IOU.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding. In
 * practice we would recommend splitting up the various stages of the flow into sub-routines.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
object ExampleFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val iouValue: Double,
                    val currency: String,
                    val otherParty: Party) : FlowLogic<SignedTransaction>() {
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


            val iouState = IOUState((iouValue * getExchangeRate(iouValue, currency)), currency, serviceHub.myInfo.legalIdentities.first(), otherParty)
            val oracleBalanceState = OracleBalanceState(serviceHub.myInfo.legalIdentities.first(),oracleParty, ceil(iouValue*10/100))
            val txCommand = Command(IOUContract.Commands.Create(currency, iouValue, getExchangeRate(iouValue, currency)), listOf(oracleParty.owningKey, otherParty.owningKey, serviceHub.myInfo.legalIdentities.first().owningKey))
            val oracleCommand = Command(OracleBalanceContract.Commands.Create(), listOf(oracleParty.owningKey,serviceHub.myInfo.legalIdentities.first().owningKey))
            val txBuilder = TransactionBuilder(notary, UUID.randomUUID())
                    .addOutputState(iouState, IOUContract.ID)
                    .addOutputState(oracleBalanceState)
                    .addCommand(txCommand)
                    .addCommand(oracleCommand)
            println(txBuilder.toString())
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

            //  ************** ------ORACLE IMPLEMENTATION STARTS------- ************ //

            //Note CollectSignatureFlow is used not CollectSignaturesFlow.
            val transactionSignature: TransactionSignature = subFlow(CollectSignatureFlow(partSignedTx, otherPartySession, otherParty.owningKey)).single()
            val counterPartySignedTransaction = partSignedTx.withAdditionalSignature(transactionSignature)

            //Build a filtered transaction
            val predicate = Predicate<Any> {
                when (it) {
                    is Command<*> -> oracleParty.owningKey in it.signers && (it.value is IOUContract.Commands.Create || it.value is OracleBalanceContract.Commands.Create) /// true
                    is TransactionState<*>-> it.data.participants.contains(oracleParty) //true
                    else -> false
                }
            }
            /*
            * test(Any):Boolean{
            *       conditions
            * }
            * */
            val filteredTransaction = counterPartySignedTransaction.buildFilteredTransaction(predicate)
            //Collect Signature from oracle service
            val oraclePartySession = initiateFlow(oracleParty)
            progressTracker.currentStep = GATHERING_SIGNATURE_FROM_ORACLE
           /* val oracleSignature = subFlow(CollectOracleSignatureFlow.Initiator(filteredTransaction,oracleParty)) //tx signature*/
            val oracleSignature = oraclePartySession.sendAndReceive<TransactionSignature>(filteredTransaction).unwrap { it -> it }
            val fullySignedTx = counterPartySignedTransaction.withAdditionalSignature(oracleSignature)

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, setOf(otherPartySession,oraclePartySession), FINALISING_TRANSACTION.childProgressTracker()))
        }
        private fun getExchangeRate(iouValue: Double, currency: String): Double {
            val exchangeRateFromExternalService = serviceHub.cordaService(ExchangeRateFinder::class.java).exchangeRate("INR",currency);
            return exchangeRateFromExternalService;
        }
    }


    @InitiatedBy(Initiator::class)
    class Responder(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    println(stx.tx.outputStates)
                    "Will accept only one IOU per transaction " using (stx.tx.outputsOfType<IOUState>().size==1)
                    val output = stx.tx.outputsOfType<IOUState>().single()
                    println(output)
                    "I won't accept IOUs with a value over 10000." using (output.value <= 10000)
                }
            }
            val txId = subFlow(signTransactionFlow).id
            // filtered transaction and verification

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }

    }

    @InitiatedBy(Initiator::class)
    class OracleResponder(private val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call() :SignedTransaction {
            val filteredTransaction = otherPartySession.receive<FilteredTransaction>().unwrap {it}
            println("Hey I am Oracle and I have been denied of many data in the transaction :( ")
            println("These are the only data that has been shared with me :(")
            println(filteredTransaction.outputs)
            println(filteredTransaction.inputs)
            println(filteredTransaction.commands)
            println("\n Hmmm. I don't believe that all the data that I am supposed to sign is given to me :/ ")
            //optional
            filteredTransaction.checkAllComponentsVisible(ComponentGroupEnum.COMMANDS_GROUP)
           //mandatory
            filteredTransaction.checkCommandVisibility(serviceHub.myInfo.legalIdentities.first().owningKey)
            println("Okay everything seems to be fine Let me check the validity of the data  :| ")
            println(checkForValidity(filteredTransaction))
            if (checkForValidity(filteredTransaction)) {
                otherPartySession.send(serviceHub.createSignature(filteredTransaction , serviceHub.myInfo.legalIdentities.first().owningKey))
                println("Everything is fine :) I have signed it")
            } else {
                println("Oops Something is wrong :( ")
                throw InvalidParameterException("Transaction: ${filteredTransaction.id} is invalid")
            }

            return subFlow(ReceiveFinalityFlow(otherPartySession))
        }

        private  fun checkForValidity(filteredTransaction : FilteredTransaction):Boolean{
            //commands() ,state
            return filteredTransaction.checkWithFun { element->
                when {
                    (element is Command<*> && element.value is IOUContract.Commands.Create) -> {
                        val command = element.value as IOUContract.Commands.Create
                        require(serviceHub.myInfo.legalIdentities.first().owningKey in element.signers) //true
                        println(Date(System.currentTimeMillis()))
                        println("in singers"+(serviceHub.myInfo.legalIdentities.first().owningKey in element.signers))
                        require(ceil((element.value as IOUContract.Commands.Create).iouValue*10/100)  == filteredTransaction.outputsOfType<OracleBalanceState>().single().value)
                        validateExchangeRate(command.iouValue, command.currency, command.exchangeRate)// true
                    }
                    (element is Command<*> && element.value is OracleBalanceContract.Commands.Create) -> {
                        true
                    }
                    (element is TransactionState<*> && element.data is OracleBalanceState) ->{
                        println(Date(System.currentTimeMillis()))
                        true
                    }
                    else -> false
                }
            }
        }

        private fun validateExchangeRate(iouValue: Double, currency: String, exchangeRate: Double): Boolean {
            val exchangeRateFromExternalSource = serviceHub.cordaService(ExchangeRateFinder::class.java).exchangeRate("INR", currency)
            println("$exchangeRate is ? equal to $exchangeRateFromExternalSource")
            return exchangeRate == exchangeRateFromExternalSource
        }

    }
}
