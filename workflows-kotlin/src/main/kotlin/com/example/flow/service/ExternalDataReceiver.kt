package com.example.flow.service

import com.example.flow.EchangeRateDTO
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import org.springframework.web.client.RestTemplate

@CordaService
class ExchangeRateFinder(private  val serviceHub: AppServiceHub) :
        SingletonSerializeAsToken() {
    private var parameterName :String? =null
    fun exchangeRate(nativeCurrencySymbol: String, foreignCurrencySymbol: String): Double {
       /* parameterName = "$nativeCurrencySymbol" + "_" + "$foreignCurrencySymbol"
        val restTemplates : RestTemplate = RestTemplate()
        val response1 = restTemplates.getForObject("https://free.currconv.com/api/v7/convert?q=$parameterName&compact=ultra&apiKey=4bdd51f55edb36a7456c",EchangeRateDTO::class.java)
        return response1.exchangeRate;*/
        return 75.1
    }
}