package com.tethervault.app.domain.usecase

import com.tethervault.app.domain.model.AccessLogAction
import com.tethervault.app.domain.model.ConnectedDevice
import com.tethervault.app.domain.repository.AccessLogRepository
import com.tethervault.app.domain.repository.ConnectedDeviceRepository
import com.tethervault.app.domain.repository.VoucherRepository
import javax.inject.Inject

class AuthenticateDeviceUseCase @Inject constructor(
    private val voucherRepository: VoucherRepository,
    private val connectedDeviceRepository: ConnectedDeviceRepository,
    private val accessLogRepository: AccessLogRepository
) {

    suspend operator fun invoke(ipAddress: String, voucherCode: String): Result<Boolean> {
        val voucher = voucherRepository.findByCode(voucherCode)
            ?: return Result.failure(VoucherException.NotFound(voucherCode))

        if (voucher.isUsed) {
            return Result.failure(VoucherException.AlreadyUsed(voucherCode))
        }
        if (voucher.expiresAt <= System.currentTimeMillis()) {
            return Result.failure(VoucherException.Expired(voucherCode))
        }

        val existingDevice = connectedDeviceRepository.findByIpAddress(ipAddress)
        val device = (existingDevice ?: ConnectedDevice(
            // MAC address resolution from the TUN subnet comes in a later phase.
            macAddress = UNKNOWN_MAC_PREFIX + ipAddress,
            ipAddress = ipAddress,
            deviceName = "Unknown device",
            lastSeen = System.currentTimeMillis()
        )).copy(
            isAuthenticated = true,
            lastSeen = System.currentTimeMillis()
        )

        voucherRepository.markUsed(
            id = voucher.id,
            usedByMac = device.macAddress,
            usedByIp = ipAddress
        )
        connectedDeviceRepository.upsert(device)
        accessLogRepository.log(
            deviceMac = device.macAddress,
            action = AccessLogAction.LOGIN,
            details = "Voucher ${voucher.code} activated from $ipAddress"
        )

        return Result.success(true)
    }

    private companion object {
        const val UNKNOWN_MAC_PREFIX = "unknown:"
    }
}

sealed class VoucherException(message: String) : Exception(message) {
    class NotFound(code: String) : VoucherException("Voucher '$code' not found")
    class AlreadyUsed(code: String) : VoucherException("Voucher '$code' is already used")
    class Expired(code: String) : VoucherException("Voucher '$code' has expired")
}
