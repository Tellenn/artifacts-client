package com.tellenn.artifacts.controller

import com.tellenn.artifacts.services.MerchantMissionResult
import com.tellenn.artifacts.services.MerchantOrderService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class MerchantBuyRequest(val code: String, val quantity: Int)
data class MerchantMissionResponse(val status: String, val reason: String? = null)

@RestController
@RequestMapping("/merchant")
class MerchantController(private val merchantOrderService: MerchantOrderService) {

    @PostMapping("/buy")
    fun buy(@RequestBody request: MerchantBuyRequest): ResponseEntity<MerchantMissionResponse> {
        when {
            request.code.isBlank() -> return badRequest("code requis")
            request.quantity <= 0 -> return badRequest("quantity doit être > 0")
        }
        return toResponse(merchantOrderService.requestBuy(request.code, request.quantity))
    }

    private fun badRequest(reason: String): ResponseEntity<MerchantMissionResponse> =
        ResponseEntity.badRequest().body(MerchantMissionResponse("invalid", reason))

    private fun toResponse(result: MerchantMissionResult): ResponseEntity<MerchantMissionResponse> = when (result) {
        is MerchantMissionResult.Accepted -> ResponseEntity.status(HttpStatus.ACCEPTED).body(MerchantMissionResponse("accepted"))
        is MerchantMissionResult.Rejected -> ResponseEntity.status(HttpStatus.CONFLICT).body(MerchantMissionResponse("rejected", result.reason))
    }
}
