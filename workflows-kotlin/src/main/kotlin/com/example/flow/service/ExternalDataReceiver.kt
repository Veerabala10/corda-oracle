package com.example.flow.service

import com.example.flow.dto.ExchangeRateDTO
import com.fasterxml.jackson.databind.ObjectMapper
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class ExchangeRateFinder(private val serviceHub: AppServiceHub) :
        SingletonSerializeAsToken() {
    private var parameterName :String? =null
    fun exchangeRate(nativeCurrencySymbol: String, foreignCurrencySymbol: String): Double {
/*        val client = OkHttpClient()
        val converterMapString= "${foreignCurrencySymbol}_${nativeCurrencySymbol}"
        println(converterMapString)
        //create request
        val request: Request = Request.Builder().url("https://free.currconv.com/api/v7/convert?q=$converterMapString&compact=ultra&apiKey=4bdd51f55edb36a7456c").build()
        val call = client.newCall(request);
        val response = call.execute()
        //Convert received value to Exchange Rate Dto Object
        val mapper = ObjectMapper()

        val value = mapper.readTree(response.body().toString())
        println("Value is ${value[converterMapString]}")
        return value[converterMapString].asDouble();*/
        return 73.2;
    }
}