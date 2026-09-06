package com.tethervault.app.domain.usecase

import com.tethervault.app.domain.model.AccessLogAction
import com.tethervault.app.domain.model.ConnectedDevice
import com.tethervault.app.domain.repository.AccessLogRepository
import com.tethervault.app.domain.repository.ConnectedDeviceRepository
import com.tethervault.app.domain.repository.VoucherRepository
import com.tethervault.app.util.ArpResolver
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
            macAddress = resolveMacAddress(ipAddress),
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

    // /proc/net/arp is restricted on many Android 10+ builds; fall back to a
    // stable per-IP placeholder so the device stays addressable in the UI.
    private fun resolveMacAddress(ipAddress: String): String =
        ArpResolver.getMacFromIp(ipAddress) ?: "unknown:$ipAddress"
}

sealed class VoucherException(message: String) : Exception(message) {
    class NotFound(code: String) : VoucherException("Voucher '$code' not found")
    class AlreadyUsed(code: String) : VoucherException("Voucher '$code' is already used")
    class Expired(code: String) : VoucherException("Voucher '$code' has expired")
}
