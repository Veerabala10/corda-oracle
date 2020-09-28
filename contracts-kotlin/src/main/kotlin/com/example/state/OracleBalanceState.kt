package com.example.state

import com.example.contract.IOUContract
import com.example.contract.OracleBalanceContract
import com.example.schema.IOUSchemaV1
import com.example.schema.OracleBalanceSchema
import com.example.schema.OracleBalanceSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * The state object recording IOU agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 * @param value the value of the IOU.
 * @param lender the party issuing the IOU.
 * @param borrower the party receiving and approving the IOU.
 */
@BelongsToContract(OracleBalanceContract::class)
data class OracleBalanceState(
        val payee : Party,
        val receiver :Party,
        val value: Double,
        override val linearId: UniqueIdentifier = UniqueIdentifier()):
        LinearState, QueryableState {
    /** The public keys of the involved parties. */
    override val participants: List<AbstractParty> get() = listOf(payee,receiver)
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(OracleBalanceSchemaV1)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is OracleBalanceSchemaV1 -> OracleBalanceSchemaV1.PersistentIOU(
                    this.payee.name.toString(),
                    this.value,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }


}