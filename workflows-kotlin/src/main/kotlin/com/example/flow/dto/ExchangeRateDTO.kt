package com.example.flow.dto

data class ExchangeRateDTO(val exchangeRate:Double) {
    override fun toString(): String {
        return "ExchangeRateDTO(exchangeRate=$exchangeRate)"
    }
}