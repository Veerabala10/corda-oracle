package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import sun.util.resources.cldr.ca.CurrencyNames_ca
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for IOUState.
 */
object OracleBalanceSchema

/**
 * An IOUState schema.
 */
object OracleBalanceSchemaV1 : MappedSchema(
        schemaFamily = IOUSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentIOU::class.java)) {
    @Entity
    @Table(name = "Oracle_Balance_Data")
    class PersistentIOU(
            @Column(name = "lender")
            var payeeName: String,

            @Column(name = "value")
            var value: Double,

            @Column(name = "linear_id")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("", 0.0 ,UUID.randomUUID())
    }
}