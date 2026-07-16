package com.tellenn.artifacts.controller

import com.tellenn.artifacts.services.GeMissionResult
import com.tellenn.artifacts.services.GrandExchangeOrderService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class GeBuyRequest(val code: String, val quantity: Int, val maxUnitPrice: Int)
data class GeSellRequest(val code: String, val quantity: Int, val unitPrice: Int)
data class GeCancelRequest(val orderId: String)
data class GeMissionResponse(val status: String, val reason: String? = null)

@RestController
@RequestMapping("/grand-exchange")
class GrandExchangeController(private val grandExchangeOrderService: GrandExchangeOrderService) {

    @PostMapping("/buy")
    fun buy(@RequestBody request: GeBuyRequest): ResponseEntity<GeMissionResponse> {
        validate(request.code, request.quantity, request.maxUnitPrice)?.let { return it }
        return toResponse(grandExchangeOrderService.requestBuy(request.code, request.quantity, request.maxUnitPrice))
    }

    @PostMapping("/sell")
    fun sell(@RequestBody request: GeSellRequest): ResponseEntity<GeMissionResponse> {
        validate(request.code, request.quantity, request.unitPrice)?.let { return it }
        return toResponse(grandExchangeOrderService.requestSell(request.code, request.quantity, request.unitPrice))
    }

    @PostMapping("/cancel")
    fun cancel(@RequestBody request: GeCancelRequest): ResponseEntity<GeMissionResponse> {
        if (request.orderId.isBlank()) {
            return badRequest("orderId requis")
        }
        return toResponse(grandExchangeOrderService.requestCancel(request.orderId))
    }

    private fun validate(code: String, quantity: Int, price: Int): ResponseEntity<GeMissionResponse>? = when {
        code.isBlank() -> badRequest("code requis")
        quantity <= 0 -> badRequest("quantity doit être > 0")
        price <= 0 -> badRequest("prix doit être > 0")
        else -> null
    }

    private fun badRequest(reason: String): ResponseEntity<GeMissionResponse> =
        ResponseEntity.badRequest().body(GeMissionResponse("invalid", reason))

    private fun toResponse(result: GeMissionResult): ResponseEntity<GeMissionResponse> = when (result) {
        is GeMissionResult.Accepted -> ResponseEntity.status(HttpStatus.ACCEPTED).body(GeMissionResponse("accepted"))
        is GeMissionResult.Rejected -> ResponseEntity.status(HttpStatus.CONFLICT).body(GeMissionResponse("rejected", result.reason))
    }
}
